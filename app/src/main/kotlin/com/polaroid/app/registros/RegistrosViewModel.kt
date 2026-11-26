package com.polaroid.app.registros

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polaroid.app.calculadora.EntradaNumerica
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class TipoRegistro { COMPRA, VENDA, PERDA }

enum class CampoRegistro { QUANTIDADE, VALOR, DATA_HORA }

/** Formulário de criação/edição. [idEditando] null = novo registro. */
data class Formulario(
    val tipo: TipoRegistro,
    val dataHora: LocalDateTime,
    val idEditando: Long? = null,
    val quantidade: String = "",
    val valor: String = "",
    val formaPagamento: FormaPagamento = FormaPagamento.PIX,
    val erros: Map<CampoRegistro, String> = emptyMap(),
    /** Taxa já gravada na venda em edição (preservada se a forma de pagamento não mudar). */
    val taxaOriginal: Pair<FormaPagamento, Int>? = null,
    /** Fotos que podem sair nessa data (venda/perda); null para compra. */
    val disponivel: Int? = null,
)

data class EstadoRegistros(
    val registros: List<Registro> = emptyList(),
    val formulario: Formulario? = null,
    val mensagem: String? = null,
    /** Dados antigos com mais saídas que entradas: mostrar aviso para revisar. */
    val estoqueNegativo: Boolean = false,
)

/**
 * Compras, vendas e perdas de fotos. Regras:
 * - nenhuma data futura (nem ao criar, nem ao editar);
 * - dinheiro gravado em centavos exatos.
 */
class RegistrosViewModel(
    private val repositorio: RepositorioRegistros,
    private val relogio: Clock,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoRegistros())
    val estado: StateFlow<EstadoRegistros> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            repositorio.observar().collect { registros ->
                _estado.update {
                    it.copy(
                        registros = registros.sortedByDescending(Registro::dataHora),
                        estoqueNegativo = Estoque.primeiraFalta(registros) != null,
                        formulario = it.formulario?.let { f -> comDisponivel(f, registros) },
                    )
                }
            }
        }
    }

    fun registrarCompra(valorPago: BigDecimal, quantidadeFotos: Int) {
        val compra = Registro.Compra(0, relogio.instant(), quantidadeFotos, Centavos.de(valorPago))
        viewModelScope.launch {
            repositorio.salvar(compra)
            _estado.update { it.copy(mensagem = "Compra registrada") }
        }
    }

    fun abrirNovaVenda(precoSugerido: String = "") {
        val preco = precoSugerido.takeIf(EntradaNumerica::aceitaValor).orEmpty()
        abrir(Formulario(TipoRegistro.VENDA, agoraNoMinuto(), quantidade = "1", valor = preco))
    }

    fun abrirNovaPerda() = abrir(Formulario(TipoRegistro.PERDA, agoraNoMinuto(), quantidade = "1"))

    fun abrirEdicao(registro: Registro) {
        val dataHora = LocalDateTime.ofInstant(registro.dataHora, relogio.zone)
        val quantidade = registro.quantidadeFotos.toString()
        abrir(
            when (registro) {
                is Registro.Compra -> Formulario(
                    TipoRegistro.COMPRA, dataHora, registro.id, quantidade,
                    valor = Centavos.paraTextoCampo(registro.valorPagoCentavos),
                )
                is Registro.Venda -> Formulario(
                    TipoRegistro.VENDA, dataHora, registro.id, quantidade,
                    valor = Centavos.paraTextoCampo(registro.precoPorFotoCentavos),
                    formaPagamento = registro.formaPagamento,
                    taxaOriginal = registro.formaPagamento to registro.taxaPontosBase,
                )
                is Registro.Perda -> Formulario(TipoRegistro.PERDA, dataHora, registro.id, quantidade)
            },
        )
    }

    fun aoMudarQuantidade(texto: String) {
        if (!FORMATO_QUANTIDADE.matches(texto)) return
        atualizarFormulario(CampoRegistro.QUANTIDADE) { it.copy(quantidade = texto) }
    }

    fun aoSelecionarQuantidade(quantidade: Int) =
        atualizarFormulario(CampoRegistro.QUANTIDADE) { it.copy(quantidade = quantidade.toString()) }

    /** +1, sem passar do estoque disponível nessa data. */
    fun aoAumentarQuantidade() = atualizarFormulario(CampoRegistro.QUANTIDADE) {
        val atual = it.quantidade.toIntOrNull() ?: 0
        val limite = (it.disponivel ?: QUANTIDADE_MAXIMA).coerceAtLeast(1)
        if (atual + 1 > limite) it else it.copy(quantidade = (atual + 1).toString())
    }

    /** −1, com mínimo de 1 foto. */
    fun aoDiminuirQuantidade() = atualizarFormulario(CampoRegistro.QUANTIDADE) {
        val atual = it.quantidade.toIntOrNull() ?: 1
        it.copy(quantidade = (atual - 1).coerceAtLeast(1).toString())
    }

    fun aoMudarValor(texto: String) {
        if (!EntradaNumerica.aceitaValor(texto)) return
        atualizarFormulario(CampoRegistro.VALOR) { it.copy(valor = texto) }
    }

    fun aoMudarDataHora(dataHora: LocalDateTime) = atualizarFormulario(CampoRegistro.DATA_HORA) {
        comDisponivel(it.copy(dataHora = dataHora.truncatedTo(ChronoUnit.MINUTES)), _estado.value.registros)
    }

    fun aoMudarFormaPagamento(forma: FormaPagamento) =
        atualizarFormulario(null) { it.copy(formaPagamento = forma) }

    fun salvar() {
        val formulario = _estado.value.formulario ?: return
        val erros = validar(formulario)
        if (erros.isNotEmpty()) {
            _estado.update { it.copy(formulario = formulario.copy(erros = erros)) }
            return
        }

        val registro = montarRegistro(formulario)
        val registros = _estado.value.registros
        val depois = registros.filterNot { it.id == formulario.idEditando } + registro
        if (pioraEstoque(registros, depois)) {
            val disponivel = formulario.disponivel
            val mensagem = if (disponivel != null && registro.quantidadeFotos > disponivel) {
                "Estoque insuficiente: só há $disponivel fotos disponíveis nessa data"
            } else {
                "Essa mudança deixaria o estoque negativo em ${dataDaFalta(depois)}"
            }
            _estado.update { it.copy(formulario = formulario.copy(erros = mapOf(CampoRegistro.QUANTIDADE to mensagem))) }
            return
        }

        val editando = formulario.idEditando != null
        _estado.update { it.copy(formulario = null) }
        viewModelScope.launch {
            repositorio.salvar(registro)
            _estado.update { it.copy(mensagem = mensagemSalvo(formulario.tipo, editando)) }
        }
    }

    fun cancelar() {
        _estado.update { it.copy(formulario = null) }
    }

    fun apagar(registro: Registro) {
        val registros = _estado.value.registros
        val depois = registros.filterNot { it.id == registro.id }
        if (pioraEstoque(registros, depois)) {
            _estado.update {
                it.copy(mensagem = "Não é possível apagar: o estoque ficaria negativo em ${dataDaFalta(depois)}")
            }
            return
        }
        viewModelScope.launch { repositorio.apagar(registro) }
    }

    fun aoDispensarMensagem() {
        _estado.update { it.copy(mensagem = null) }
    }

    private fun abrir(formulario: Formulario) {
        _estado.update { it.copy(formulario = comDisponivel(formulario, it.registros)) }
    }

    /** Calcula quantas fotos podem sair na data do formulário (só venda e perda). */
    private fun comDisponivel(formulario: Formulario, registros: List<Registro>): Formulario {
        if (formulario.tipo == TipoRegistro.COMPRA) return formulario.copy(disponivel = null)
        val quando = formulario.dataHora.atZone(relogio.zone).toInstant()
        return formulario.copy(disponivel = Estoque.disponivelEm(registros, quando, formulario.idEditando))
    }

    /**
     * Bloqueia o que cria ou aprofunda um estoque negativo. Se os dados já estavam
     * inconsistentes, mudanças que não pioram (ex.: correções) continuam permitidas.
     */
    private fun pioraEstoque(antes: List<Registro>, depois: List<Registro>): Boolean =
        Estoque.menorSaldo(depois) < minOf(0, Estoque.menorSaldo(antes))

    private fun dataDaFalta(registros: List<Registro>): String =
        Estoque.primeiraFalta(registros)?.quando?.atZone(relogio.zone)?.format(FORMATO_DATA).orEmpty()

    /** Aplica a mudança e limpa o erro do campo alterado. */
    private fun atualizarFormulario(campo: CampoRegistro?, mudanca: (Formulario) -> Formulario) {
        _estado.update { estado ->
            val atual = estado.formulario ?: return@update estado
            val novo = mudanca(atual)
            estado.copy(formulario = if (campo != null) novo.copy(erros = novo.erros - campo) else novo)
        }
    }

    private fun agoraNoMinuto(): LocalDateTime =
        LocalDateTime.now(relogio).truncatedTo(ChronoUnit.MINUTES)

    private fun validar(formulario: Formulario): Map<CampoRegistro, String> = buildMap {
        val quantidade = formulario.quantidade.toIntOrNull()
        when {
            formulario.quantidade.isEmpty() -> put(CampoRegistro.QUANTIDADE, "Informe a quantidade de fotos")
            quantidade == null || quantidade <= 0 -> put(CampoRegistro.QUANTIDADE, "A quantidade deve ser maior que zero")
        }

        if (formulario.tipo != TipoRegistro.PERDA) {
            val valor = EntradaNumerica.converterValor(formulario.valor)
            val venda = formulario.tipo == TipoRegistro.VENDA
            when {
                formulario.valor.isEmpty() ->
                    put(CampoRegistro.VALOR, if (venda) "Informe o preço por foto" else "Informe o valor pago")
                valor == null -> put(CampoRegistro.VALOR, "Valor inválido")
                venda && valor.signum() <= 0 -> put(CampoRegistro.VALOR, "O preço deve ser maior que zero")
            }
        }

        if (formulario.dataHora.atZone(relogio.zone).toInstant().isAfter(relogio.instant())) {
            put(CampoRegistro.DATA_HORA, "Não é possível registrar em data futura")
        }
    }

    private fun montarRegistro(formulario: Formulario): Registro {
        val id = formulario.idEditando ?: 0L
        val instante = formulario.dataHora.atZone(relogio.zone).toInstant()
        val quantidade = formulario.quantidade.toInt()
        val centavos = { Centavos.de(EntradaNumerica.converterValor(formulario.valor)!!) }

        return when (formulario.tipo) {
            TipoRegistro.COMPRA -> Registro.Compra(id, instante, quantidade, centavos())
            TipoRegistro.PERDA -> Registro.Perda(id, instante, quantidade)
            TipoRegistro.VENDA -> {
                val forma = formulario.formaPagamento
                val taxa = formulario.taxaOriginal
                    ?.takeIf { (formaOriginal, _) -> formaOriginal == forma }
                    ?.second
                    ?: forma.taxaPadraoPontosBase
                Registro.Venda(id, instante, quantidade, centavos(), forma, taxa)
            }
        }
    }

    private fun mensagemSalvo(tipo: TipoRegistro, editando: Boolean): String {
        val nome = when (tipo) {
            TipoRegistro.COMPRA -> "Compra"
            TipoRegistro.VENDA -> "Venda"
            TipoRegistro.PERDA -> "Perda"
        }
        return if (editando) "$nome atualizada" else "$nome registrada"
    }

    private companion object {
        val FORMATO_QUANTIDADE = Regex("""^\d{0,5}$""")
        const val QUANTIDADE_MAXIMA = 99_999
        val FORMATO_DATA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}
