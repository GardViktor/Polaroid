package com.polaroid.app.calculadora

import androidx.lifecycle.ViewModel
import com.polaroid.calculadora.CalculadoraLucro
import com.polaroid.calculadora.ResultadoCusto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal

enum class Campo { VALOR_PAGO, PRECO_VENDA }

/** Custo formatado, disponível assim que valor pago e pack são informados. */
data class CustoTela(
    val custoPorFoto: String,
    val precoMinimo: String,
    val custoAproximado: Boolean,
)

/** Lucro formatado, disponível quando o preço de venda também é informado. */
data class ResultadoTela(
    val lucroPorFoto: String,
    val lucroTotal: String,
    val margem: String,
    val lucroPorFotoAproximado: Boolean,
    val margemAproximada: Boolean,
    val prejuizo: Boolean,
)

data class DadosCompra(val valorPago: BigDecimal, val totalFotos: Int)

data class EstadoCalculadora(
    val valorPago: String = "",
    val tamanhoPack: Int? = null,
    val quantidadePacks: Int = 1,
    val precoVenda: String = "",
    val erros: Map<Campo, String> = emptyMap(),
    val custo: CustoTela? = null,
    val resultado: ResultadoTela? = null,
    val avisoPreco: String? = null,
) {
    val totalFotos: Int? get() = tamanhoPack?.let { it * quantidadePacks }
}

/**
 * Calcula enquanto o usuário digita. O custo aparece assim que valor pago e pack
 * são informados; o lucro, quando o preço de venda também é. Caracteres inválidos
 * são ignorados na hora; erros só aparecem depois que o usuário sai do campo.
 */
class CalculadoraViewModel : ViewModel() {

    companion object {
        /** Tamanhos de pack de filme Instax Mini vendidos no mercado brasileiro. */
        val TAMANHOS_PACK: List<Int> = listOf(10, 20, 40, 50, 60, 100)
        private const val MINIMO_PACKS = 1
        private const val MAXIMO_PACKS = 99
    }

    private val _estado = MutableStateFlow(EstadoCalculadora())
    val estado: StateFlow<EstadoCalculadora> = _estado.asStateFlow()

    private val camposVisitados = mutableSetOf<Campo>()

    fun aoDigitar(campo: Campo, texto: String) {
        if (!EntradaNumerica.aceitaValor(texto)) return
        atualizar {
            when (campo) {
                Campo.VALOR_PAGO -> it.copy(valorPago = texto)
                Campo.PRECO_VENDA -> it.copy(precoVenda = texto)
            }
        }
    }

    fun aoSairDoCampo(campo: Campo) {
        camposVisitados += campo
        atualizar { it }
    }

    fun aoSelecionarPack(tamanho: Int) {
        if (tamanho !in TAMANHOS_PACK) return
        atualizar { it.copy(tamanhoPack = tamanho) }
    }

    fun aoAumentarPacks() = atualizar {
        it.copy(quantidadePacks = (it.quantidadePacks + 1).coerceAtMost(MAXIMO_PACKS))
    }

    fun aoDiminuirPacks() = atualizar {
        it.copy(quantidadePacks = (it.quantidadePacks - 1).coerceAtLeast(MINIMO_PACKS))
    }

    /** Valor pago e total de fotos válidos, para registrar a compra; null se incompletos. */
    fun dadosCompra(): DadosCompra? {
        val estado = _estado.value
        val valorPago = EntradaNumerica.converterValor(estado.valorPago) ?: return null
        val totalFotos = estado.totalFotos ?: return null
        return DadosCompra(valorPago, totalFotos)
    }

    private fun atualizar(mudanca: (EstadoCalculadora) -> EstadoCalculadora) {
        _estado.value = recalcular(mudanca(_estado.value))
    }

    private fun recalcular(estado: EstadoCalculadora): EstadoCalculadora {
        val valorPago = validarValorPago(estado.valorPago)
        val precoVenda = validarPrecoVenda(estado.precoVenda)
        val totalFotos = estado.totalFotos

        val erros = mapOf(
            Campo.VALOR_PAGO to valorPago.erro,
            Campo.PRECO_VENDA to precoVenda.erro,
        ).filter { (campo, erro) -> erro != null && campo in camposVisitados }
            .mapValues { it.value!! }

        val custo = if (valorPago.valor != null && totalFotos != null) {
            CalculadoraLucro.calcularCusto(valorPago.valor, totalFotos)
        } else {
            null
        }

        val resultado = if (custo != null && precoVenda.valor != null) {
            calcularResultado(valorPago.valor!!, totalFotos!!, precoVenda.valor)
        } else {
            null
        }

        val avisoPreco = if (custo != null && precoVenda.valor != null &&
            precoVenda.valor < custo.precoMinimoSemPrejuizo
        ) {
            "Abaixo do preço mínimo (${Formatador.moeda(custo.precoMinimoSemPrejuizo)})"
        } else {
            null
        }

        return estado.copy(
            erros = erros,
            custo = custo?.let(::formatarCusto),
            resultado = resultado,
            avisoPreco = avisoPreco,
        )
    }

    private fun formatarCusto(custo: ResultadoCusto) = CustoTela(
        custoPorFoto = Formatador.moeda(custo.custoPorFoto),
        precoMinimo = Formatador.moeda(custo.precoMinimoSemPrejuizo),
        custoAproximado = custo.custoPorFotoArredondado,
    )

    private fun calcularResultado(valorPago: BigDecimal, totalFotos: Int, precoVenda: BigDecimal): ResultadoTela {
        val calculo = CalculadoraLucro.calcular(valorPago, totalFotos, precoVenda)
        return ResultadoTela(
            lucroPorFoto = Formatador.moeda(calculo.lucroPorFoto),
            lucroTotal = Formatador.moeda(calculo.lucroTotal),
            margem = Formatador.percentual(calculo.margemLucro),
            lucroPorFotoAproximado = calculo.valoresPorFotoArredondados,
            margemAproximada = calculo.margemArredondada,
            prejuizo = calculo.lucroTotal.signum() < 0,
        )
    }

    /** Valor validado ou mensagem de erro (exatamente um dos dois é não-nulo). */
    private data class Validacao<T>(val valor: T?, val erro: String?)

    private fun validarValorPago(texto: String): Validacao<BigDecimal> {
        if (texto.isEmpty()) return Validacao(null, "Informe o valor pago")
        val valor = EntradaNumerica.converterValor(texto) ?: return Validacao(null, "Valor inválido")
        return Validacao(valor, null)
    }

    private fun validarPrecoVenda(texto: String): Validacao<BigDecimal> {
        if (texto.isEmpty()) return Validacao(null, "Informe o preço de venda")
        val valor = EntradaNumerica.converterValor(texto) ?: return Validacao(null, "Valor inválido")
        if (valor.signum() <= 0) return Validacao(null, "O preço de venda deve ser maior que zero")
        return Validacao(valor, null)
    }
}
