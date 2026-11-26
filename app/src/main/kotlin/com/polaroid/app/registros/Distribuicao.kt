package com.polaroid.app.registros

import java.math.BigDecimal
import java.math.MathContext
import java.time.ZoneId

/**
 * Fatia da pizza "para onde foram as fotos". [valor] = faturamento da forma de pagamento
 * ou, nas perdas, o custo das fotos perdidas.
 */
data class FatiaPizza(
    val rotulo: String,
    val fotos: Int,
    val percentual: BigDecimal,
    val valor: BigDecimal,
    val ePerda: Boolean,
)

/** Distribuição das fotos que saíram do estoque no período: por forma de pagamento + perdas. */
object Distribuicao {

    private val CEM = BigDecimal(100)

    fun calcular(registros: List<Registro>, periodo: Periodo, zona: ZoneId): List<FatiaPizza> {
        val doPeriodo = registros.filter { periodo.contem(it.dataHora.atZone(zona).toLocalDate()) }
        val vendas = doPeriodo.filterIsInstance<Registro.Venda>()
        val perdas = doPeriodo.filterIsInstance<Registro.Perda>()
        val total = vendas.sumOf { it.quantidadeFotos } + perdas.sumOf { it.quantidadeFotos }
        if (total == 0) return emptyList()

        fun percentual(fotos: Int) = BigDecimal(fotos).multiply(CEM).divide(BigDecimal(total), MathContext.DECIMAL128)

        // Ordem fixa (a mesma do formulário) para as cores não trocarem de lugar entre períodos
        val porForma = FormaPagamento.entries.mapNotNull { forma ->
            val daForma = vendas.filter { it.formaPagamento == forma }
            val fotos = daForma.sumOf { it.quantidadeFotos }
            if (fotos == 0) return@mapNotNull null
            val faturamento = daForma.fold(BigDecimal.ZERO) { soma, v ->
                soma + Centavos.paraReais(v.precoPorFotoCentavos * v.quantidadeFotos)
            }
            FatiaPizza(forma.rotulo, fotos, percentual(fotos), faturamento, ePerda = false)
        }

        val fotosPerdidas = perdas.sumOf { it.quantidadeFotos }
        val fatiaPerdas = if (fotosPerdidas > 0) {
            val custos = Painel.custosDasSaidas(registros)
            val custo = perdas.fold(BigDecimal.ZERO) { soma, p -> soma + (custos[p.id] ?: BigDecimal.ZERO) }
            FatiaPizza("Perdas", fotosPerdidas, percentual(fotosPerdidas), custo, ePerda = true)
        } else {
            null
        }

        return porForma + listOfNotNull(fatiaPerdas)
    }
}
