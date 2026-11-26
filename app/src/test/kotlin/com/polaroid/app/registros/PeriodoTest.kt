package com.polaroid.app.registros

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PeriodoTest {

    // 26/11/2026 é uma quinta-feira
    private val hoje = LocalDate.of(2026, 11, 26)

    @Test
    fun `dia atual`() {
        val p = Periodo.atual(TipoPeriodo.DIA, hoje)
        assertEquals(hoje, p.inicio)
        assertEquals(hoje.plusDays(1), p.fim)
        assertEquals("26/11/2026", p.rotulo())
    }

    @Test
    fun `semana vai de segunda a domingo`() {
        val p = Periodo.atual(TipoPeriodo.SEMANA, hoje)
        assertEquals(LocalDate.of(2026, 11, 23), p.inicio)
        assertEquals(LocalDate.of(2026, 11, 30), p.fim)
        assertEquals("23/11 a 29/11/2026", p.rotulo())
    }

    @Test
    fun `mes atual`() {
        val p = Periodo.atual(TipoPeriodo.MES, hoje)
        assertEquals(LocalDate.of(2026, 11, 1), p.inicio)
        assertEquals(LocalDate.of(2026, 12, 1), p.fim)
        assertEquals("Novembro de 2026", p.rotulo())
    }

    @Test
    fun `trimestre atual`() {
        val p = Periodo.atual(TipoPeriodo.TRIMESTRE, hoje)
        assertEquals(LocalDate.of(2026, 10, 1), p.inicio)
        assertEquals(LocalDate.of(2027, 1, 1), p.fim)
        assertEquals("4º trimestre de 2026", p.rotulo())
    }

    @Test
    fun `ano atual`() {
        val p = Periodo.atual(TipoPeriodo.ANO, hoje)
        assertEquals(LocalDate.of(2026, 1, 1), p.inicio)
        assertEquals(LocalDate.of(2027, 1, 1), p.fim)
        assertEquals("2026", p.rotulo())
    }

    @Test
    fun `anterior e proximo`() {
        val mes = Periodo.atual(TipoPeriodo.MES, hoje)
        assertEquals(LocalDate.of(2026, 10, 1), mes.anterior().inicio)
        assertEquals(LocalDate.of(2026, 12, 1), mes.proximo().inicio)

        val tri = Periodo.atual(TipoPeriodo.TRIMESTRE, hoje)
        assertEquals(LocalDate.of(2026, 7, 1), tri.anterior().inicio)

        val semana = Periodo.atual(TipoPeriodo.SEMANA, hoje)
        assertEquals(LocalDate.of(2026, 11, 16), semana.anterior().inicio)
    }

    @Test
    fun `contem a data`() {
        val mes = Periodo.atual(TipoPeriodo.MES, hoje)
        assertTrue(mes.contem(LocalDate.of(2026, 11, 1)))
        assertTrue(mes.contem(LocalDate.of(2026, 11, 30)))
        assertFalse(mes.contem(LocalDate.of(2026, 12, 1)))
        assertFalse(mes.contem(LocalDate.of(2026, 10, 31)))
    }

    @Test
    fun `periodo futuro`() {
        val mes = Periodo.atual(TipoPeriodo.MES, hoje)
        assertFalse(mes.eFuturo(hoje))
        assertTrue(mes.proximo().eFuturo(hoje))
    }
}
