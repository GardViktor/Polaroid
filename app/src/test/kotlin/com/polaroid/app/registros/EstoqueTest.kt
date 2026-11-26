package com.polaroid.app.registros

import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EstoqueTest {

    private val dia1: Instant = Instant.parse("2026-11-01T12:00:00Z")
    private fun dia(n: Long): Instant = dia1.plus(n - 1, ChronoUnit.DAYS)

    private fun compra(id: Long, quando: Instant, fotos: Int) = Registro.Compra(id, quando, fotos, 10000)
    private fun venda(id: Long, quando: Instant, fotos: Int) =
        Registro.Venda(id, quando, fotos, 1000, FormaPagamento.PIX, 0)
    private fun perda(id: Long, quando: Instant, fotos: Int) = Registro.Perda(id, quando, fotos)

    // --- Saldo atual ---

    @Test
    fun `saldo e compras menos vendas e perdas`() {
        val registros = listOf(compra(1, dia(1), 140), venda(2, dia(2), 30), perda(3, dia(3), 2))
        assertEquals(108, Estoque.saldo(registros))
    }

    // --- Estoque negativo em algum momento ---

    @Test
    fun `sem falta quando o estoque cobre tudo`() {
        assertNull(Estoque.primeiraFalta(listOf(compra(1, dia(1), 140), venda(2, dia(2), 140))))
    }

    @Test
    fun `falta quando vende mais do que tem`() {
        val falta = assertNotNull(Estoque.primeiraFalta(listOf(compra(1, dia(1), 140), venda(2, dia(2), 150))))
        assertEquals(dia(2), falta.quando)
    }

    @Test
    fun `falta quando a venda e anterior a compra`() {
        val falta = assertNotNull(Estoque.primeiraFalta(listOf(venda(2, dia(1), 10), compra(1, dia(2), 140))))
        assertEquals(dia(1), falta.quando)
    }

    @Test
    fun `no mesmo minuto a compra entra antes da venda`() {
        assertNull(Estoque.primeiraFalta(listOf(venda(2, dia(1), 10), compra(1, dia(1), 10))))
    }

    @Test
    fun `compra com segundos conta para venda no mesmo minuto`() {
        // Compra gravada às 12:00:40 e venda escolhida para 12:00 (o seletor não tem segundos)
        val compra = compra(1, dia1.plusSeconds(40), 10)
        val venda = venda(2, dia1, 10)

        assertNull(Estoque.primeiraFalta(listOf(compra, venda)))
        assertEquals(10, Estoque.disponivelEm(listOf(compra), dia1))
    }

    @Test
    fun `perda tambem consome estoque`() {
        assertNotNull(Estoque.primeiraFalta(listOf(compra(1, dia(1), 10), venda(2, dia(2), 9), perda(3, dia(3), 2))))
    }

    @Test
    fun `menor saldo ao longo do tempo`() {
        assertEquals(0, Estoque.menorSaldo(emptyList()))
        assertEquals(0, Estoque.menorSaldo(listOf(compra(1, dia(1), 10), venda(2, dia(2), 10))))
        assertEquals(-10, Estoque.menorSaldo(listOf(compra(1, dia(1), 140), venda(2, dia(2), 150), compra(3, dia(3), 50))))
    }

    // --- Quanto dá para vender numa data ---

    @Test
    fun `antes de qualquer compra nao ha nada disponivel`() {
        assertEquals(0, Estoque.disponivelEm(listOf(compra(1, dia(5), 140)), dia(2)))
    }

    @Test
    fun `depois da compra tudo esta disponivel`() {
        assertEquals(140, Estoque.disponivelEm(listOf(compra(1, dia(1), 140)), dia(3)))
    }

    @Test
    fun `vendas futuras a data reservam estoque`() {
        // Compra 140 no dia 1, venda de 100 no dia 10: no dia 5 só dá para vender 40
        val registros = listOf(compra(1, dia(1), 140), venda(2, dia(10), 100))
        assertEquals(40, Estoque.disponivelEm(registros, dia(5)))
    }

    @Test
    fun `editar uma venda devolve a quantidade dela ao disponivel`() {
        val registros = listOf(compra(1, dia(1), 140), venda(2, dia(2), 100))
        assertEquals(40, Estoque.disponivelEm(registros, dia(3)))
        assertEquals(140, Estoque.disponivelEm(registros, dia(3), ignorarId = 2))
    }

    @Test
    fun `dados ja inconsistentes nunca dao disponivel negativo`() {
        val registros = listOf(compra(1, dia(1), 140), venda(2, dia(2), 150))
        assertEquals(0, Estoque.disponivelEm(registros, dia(3)))
    }
}
