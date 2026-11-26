package com.polaroid.app.registros

import java.math.BigDecimal
import java.math.MathContext
import java.time.ZoneId

/**
 * Totais de um período. Dinheiro com precisão total (taxas e custo médio geram frações
 * de centavo); arredondar só na exibição.
 */
data class ResumoPeriodo(
    val fotosVendidas: Int,
    val fotosPerdidas: Int,
    val faturamento: BigDecimal,
    val taxas: BigDecimal,
    val custoFotos: BigDecimal,
    val lucro: BigDecimal,
    val precoMedioPorFoto: BigDecimal?,
)

/** Situação até agora, independente do período escolhido. */
data class SituacaoGeral(val saldoCaixa: BigDecimal, val estoque: Int)

object Painel {

    private val PRECISAO = MathContext.DECIMAL128
    private val CEM = BigDecimal(100)

    fun resumo(registros: List<Registro>, periodo: Periodo, zona: ZoneId): ResumoPeriodo {
        val custos = custosDasSaidas(registros)
        val doPeriodo = registros.filter { periodo.contem(it.dataHora.atZone(zona).toLocalDate()) }
        val vendas = doPeriodo.filterIsInstance<Registro.Venda>()
        val perdas = doPeriodo.filterIsInstance<Registro.Perda>()

        val fotosVendidas = vendas.sumOf { it.quantidadeFotos }
        val faturamento = vendas.fold(BigDecimal.ZERO) { soma, v -> soma + faturamento(v) }
        val taxas = vendas.fold(BigDecimal.ZERO) { soma, v -> soma + taxa(v) }
        val custoFotos = (vendas + perdas).fold(BigDecimal.ZERO) { soma, r -> soma + (custos[r.id] ?: BigDecimal.ZERO) }

        return ResumoPeriodo(
            fotosVendidas = fotosVendidas,
            fotosPerdidas = perdas.sumOf { it.quantidadeFotos },
            faturamento = faturamento,
            taxas = taxas,
            custoFotos = custoFotos,
            lucro = faturamento - taxas - custoFotos,
            precoMedioPorFoto = if (fotosVendidas > 0) faturamento.divide(BigDecimal(fotosVendidas), PRECISAO) else null,
        )
    }

    /** Saldo de caixa = vendas líquidas de taxa − compras. Começa negativo com a primeira compra. */
    fun situacaoGeral(registros: List<Registro>): SituacaoGeral {
        val saldo = registros.fold(BigDecimal.ZERO) { soma, r ->
            when (r) {
                is Registro.Compra -> soma - Centavos.paraReais(r.valorPagoCentavos)
                is Registro.Venda -> soma + faturamento(r) - taxa(r)
                is Registro.Perda -> soma
            }
        }
        return SituacaoGeral(saldo, Estoque.saldo(registros))
    }

    /** Variação % de [anterior] para [atual]; null se o anterior for zero (sem base de comparação). */
    fun variacaoPercentual(atual: BigDecimal, anterior: BigDecimal): BigDecimal? {
        if (anterior.signum() == 0) return null
        return (atual - anterior).multiply(CEM).divide(anterior.abs(), PRECISAO)
    }

    private fun faturamento(venda: Registro.Venda): BigDecimal =
        Centavos.paraReais(venda.precoPorFotoCentavos * venda.quantidadeFotos)

    /** Taxa exata: faturamento × pontos-base / 10.000 (ex.: 4,99% de R$ 20 = R$ 0,998). */
    private fun taxa(venda: Registro.Venda): BigDecimal =
        BigDecimal.valueOf(venda.precoPorFotoCentavos * venda.quantidadeFotos * venda.taxaPontosBase, 6)

    /**
     * Custo de cada venda/perda pelo custo médio ponderado: cada compra atualiza o custo
     * médio do estoque; cada saída leva o custo médio daquele momento.
     */
    private fun custosDasSaidas(registros: List<Registro>): Map<Long, BigDecimal> {
        val custos = mutableMapOf<Long, BigDecimal>()
        var estoque = 0
        var valorEstoque = BigDecimal.ZERO
        for (registro in Estoque.emOrdem(registros)) {
            when (registro) {
                is Registro.Compra -> {
                    estoque += registro.quantidadeFotos
                    valorEstoque += Centavos.paraReais(registro.valorPagoCentavos)
                }
                is Registro.Venda, is Registro.Perda -> {
                    val medio = if (estoque > 0) valorEstoque.divide(BigDecimal(estoque), PRECISAO) else BigDecimal.ZERO
                    val custo = medio.multiply(BigDecimal(registro.quantidadeFotos), PRECISAO)
                    custos[registro.id] = custo
                    valorEstoque -= custo
                    estoque -= registro.quantidadeFotos
                }
            }
        }
        return custos
    }
}
