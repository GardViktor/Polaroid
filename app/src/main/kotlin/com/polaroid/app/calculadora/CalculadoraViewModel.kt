package com.polaroid.app.calculadora

import androidx.lifecycle.ViewModel
import com.polaroid.calculadora.CalculadoraLucro
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal

enum class Campo { VALOR_PAGO, QUANTIDADE, PRECO_VENDA }

/** Resultado já formatado para exibição. */
data class ResultadoTela(
    val custoPorFoto: String,
    val lucroPorFoto: String,
    val lucroTotal: String,
    val margem: String,
    val valoresPorFotoAproximados: Boolean,
    val margemAproximada: Boolean,
    val prejuizo: Boolean,
)

data class EstadoCalculadora(
    val valorPago: String = "",
    val quantidade: String = "",
    val precoVenda: String = "",
    val erros: Map<Campo, String> = emptyMap(),
    val resultado: ResultadoTela? = null,
)

/**
 * Calcula enquanto o usuário digita. Caracteres inválidos são ignorados na hora;
 * mensagens de erro só aparecem depois que o usuário sai do campo pela primeira vez.
 */
class CalculadoraViewModel : ViewModel() {

    private val _estado = MutableStateFlow(EstadoCalculadora())
    val estado: StateFlow<EstadoCalculadora> = _estado.asStateFlow()

    private val camposVisitados = mutableSetOf<Campo>()

    fun aoDigitar(campo: Campo, texto: String) {
        val aceito = when (campo) {
            Campo.QUANTIDADE -> EntradaNumerica.aceitaQuantidade(texto)
            else -> EntradaNumerica.aceitaValor(texto)
        }
        if (!aceito) return

        val atual = _estado.value
        val atualizado = when (campo) {
            Campo.VALOR_PAGO -> atual.copy(valorPago = texto)
            Campo.QUANTIDADE -> atual.copy(quantidade = texto)
            Campo.PRECO_VENDA -> atual.copy(precoVenda = texto)
        }
        _estado.value = recalcular(atualizado)
    }

    fun aoSairDoCampo(campo: Campo) {
        camposVisitados += campo
        _estado.value = recalcular(_estado.value)
    }

    private fun recalcular(estado: EstadoCalculadora): EstadoCalculadora {
        val valorPago = validarValorPago(estado.valorPago)
        val quantidade = validarQuantidade(estado.quantidade)
        val precoVenda = validarPrecoVenda(estado.precoVenda)

        val erros = mapOf(
            Campo.VALOR_PAGO to valorPago.erro,
            Campo.QUANTIDADE to quantidade.erro,
            Campo.PRECO_VENDA to precoVenda.erro,
        ).filter { (campo, erro) -> erro != null && campo in camposVisitados }
            .mapValues { it.value!! }

        val resultado = if (valorPago.valor != null && quantidade.valor != null && precoVenda.valor != null) {
            calcularResultado(valorPago.valor, quantidade.valor, precoVenda.valor)
        } else {
            null
        }

        return estado.copy(erros = erros, resultado = resultado)
    }

    private fun calcularResultado(valorPago: BigDecimal, quantidade: Int, precoVenda: BigDecimal): ResultadoTela {
        val calculo = CalculadoraLucro.calcular(valorPago, quantidade, precoVenda)
        return ResultadoTela(
            custoPorFoto = Formatador.moeda(calculo.custoPorFoto),
            lucroPorFoto = Formatador.moeda(calculo.lucroPorFoto),
            lucroTotal = Formatador.moeda(calculo.lucroTotal),
            margem = Formatador.percentual(calculo.margemLucro),
            valoresPorFotoAproximados = calculo.valoresPorFotoArredondados,
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

    private fun validarQuantidade(texto: String): Validacao<Int> {
        if (texto.isEmpty()) return Validacao(null, "Informe a quantidade de fotos")
        val quantidade = EntradaNumerica.converterQuantidade(texto) ?: return Validacao(null, "Quantidade inválida")
        if (quantidade <= 0) return Validacao(null, "A quantidade deve ser maior que zero")
        return Validacao(quantidade, null)
    }

    private fun validarPrecoVenda(texto: String): Validacao<BigDecimal> {
        if (texto.isEmpty()) return Validacao(null, "Informe o preço de venda")
        val valor = EntradaNumerica.converterValor(texto) ?: return Validacao(null, "Valor inválido")
        if (valor.signum() <= 0) return Validacao(null, "O preço de venda deve ser maior que zero")
        return Validacao(valor, null)
    }
}
