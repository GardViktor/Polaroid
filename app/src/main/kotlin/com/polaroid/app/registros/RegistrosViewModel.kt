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
)

data class EstadoRegistros(
    val registros: List<Registro> = emptyList(),
    val formulario: Formulario? = null,
    val mensagem: String? = null,
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
                _estado.update { it.copy(registros = registros.sortedByDescending(Registro::dataHora)) }
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
        abrir(Formulario(TipoRegistro.VENDA, agoraNoMinuto(), valor = preco))
    }

    fun abrirNovaPerda() = abrir(Formulario(TipoRegistro.PERDA, agoraNoMinuto()))

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

    fun aoMudarValor(texto: String) {
        if (!EntradaNumerica.aceitaValor(texto)) return
        atualizarFormulario(CampoRegistro.VALOR) { it.copy(valor = texto) }
    }

    fun aoMudarDataHora(dataHora: LocalDateTime) =
        atualizarFormulario(CampoRegistro.DATA_HORA) { it.copy(dataHora = dataHora.truncatedTo(ChronoUnit.MINUTES)) }

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
        viewModelScope.launch { repositorio.apagar(registro) }
    }

    fun aoDispensarMensagem() {
        _estado.update { it.copy(mensagem = null) }
    }

    private fun abrir(formulario: Formulario) {
        _estado.update { it.copy(formulario = formulario) }
    }

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
    }
}
