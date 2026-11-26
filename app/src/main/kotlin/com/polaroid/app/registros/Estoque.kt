package com.polaroid.app.registros

import java.time.Instant
import java.time.temporal.ChronoUnit

/** Momento em que o estoque ficaria negativo. */
data class FaltaEstoque(val quando: Instant, val saldo: Int)

/**
 * Regras do estoque de fotos: compras entram; vendas e perdas saem.
 * O estoque nunca pode ficar negativo em nenhum momento da história.
 */
object Estoque {

    fun saldo(registros: List<Registro>): Int = registros.sumOf(::variacao)

    /** Primeiro momento (em ordem de data) em que o estoque fica negativo; null se nunca. */
    fun primeiraFalta(registros: List<Registro>): FaltaEstoque? {
        var saldo = 0
        for (registro in emOrdem(registros)) {
            saldo += variacao(registro)
            if (saldo < 0) return FaltaEstoque(registro.dataHora, saldo)
        }
        return null
    }

    /** Menor saldo atingido ao longo do tempo (0 se nunca ficou negativo). */
    fun menorSaldo(registros: List<Registro>): Int {
        var saldo = 0
        var menor = 0
        for (registro in emOrdem(registros)) {
            saldo += variacao(registro)
            menor = minOf(menor, saldo)
        }
        return menor
    }

    /**
     * Quantas fotos podem sair em [quando] sem deixar o estoque negativo em nenhum
     * momento — inclusive depois, já que vendas posteriores também precisam de estoque.
     * [ignorarId] exclui o registro em edição (a quantidade dele volta a ficar disponível).
     */
    fun disponivelEm(registros: List<Registro>, quando: Instant, ignorarId: Long? = null): Int {
        val considerados = emOrdem(registros.filterNot { ignorarId != null && it.id == ignorarId })
        val limite = noMinuto(quando)
        val (ateAgora, depois) = considerados.partition { !noMinuto(it.dataHora).isAfter(limite) }

        var saldo = ateAgora.sumOf(::variacao)
        var menor = saldo
        for (registro in depois) {
            saldo += variacao(registro)
            menor = minOf(menor, saldo)
        }
        return menor.coerceAtLeast(0)
    }

    private fun variacao(registro: Registro): Int = when (registro) {
        is Registro.Compra -> registro.quantidadeFotos
        is Registro.Venda, is Registro.Perda -> -registro.quantidadeFotos
    }

    // Comparação por minuto (as datas da tela não têm segundos); no mesmo minuto,
    // compras entram antes das saídas
    private fun emOrdem(registros: List<Registro>) =
        registros.sortedWith(
            compareBy<Registro> { noMinuto(it.dataHora) }.thenBy { if (it is Registro.Compra) 0 else 1 },
        )

    private fun noMinuto(instante: Instant): Instant = instante.truncatedTo(ChronoUnit.MINUTES)
}
