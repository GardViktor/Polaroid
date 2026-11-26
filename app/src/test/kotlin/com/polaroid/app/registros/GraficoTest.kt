package com.polaroid.app.registros

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GraficoTest {

    private val fuso = ZoneId.of("America/Sao_Paulo")
    private fun em(mes: Int, dia: Int, hora: Int = 12) =
        LocalDateTime.of(2026, mes, dia, hora, 0).atZone(fuso).toInstant()
    private val hoje = LocalDate.of(2026, 11, 26) // quinta-feira

    private fun assertDinheiro(esperado: String, atual: BigDecimal) =
        assertEquals(0, BigDecimal(esperado).compareTo(atual), "esperado $esperado, veio $atual")

    // Compra 100 fotos a R$ 5 cada; vendas em dias e horários diferentes
    private val registros = listOf(
        Registro.Compra(1, em(10, 1), 100, 50000),
        Registro.Venda(2, em(11, 23, 10), 3, 1000, FormaPagamento.PIX, 0), // seg 23/11 10h: R$ 30
        Registro.Venda(3, em(11, 23, 15), 2, 1500, FormaPagamento.PIX, 0), // seg 23/11 15h: R$ 30
        Registro.Venda(4, em(11, 26, 10), 4, 1000, FormaPagamento.PIX, 0), // qui 26/11 10h: R$ 40
    )

    // --- Barras por tipo de período ---

    @Test
    fun `dia tem uma barra por hora`() {
        val pontos = Grafico.montar(registros, Periodo.atual(TipoPeriodo.DIA, hoje), fuso)
        assertEquals(24, pontos.size)
        assertEquals("0h", pontos.first().rotulo)
        assertEquals("23h", pontos.last().rotulo)
        assertEquals(4, pontos[10].fotosVendidas)
    }

    @Test
    fun `semana tem uma barra por dia de segunda a domingo`() {
        val pontos = Grafico.montar(registros, Periodo.atual(TipoPeriodo.SEMANA, hoje), fuso)
        assertEquals(listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"), pontos.map { it.rotulo })
        assertEquals(5, pontos[0].fotosVendidas)
        assertEquals(4, pontos[3].fotosVendidas)
    }

    @Test
    fun `mes tem uma barra por dia`() {
        val pontos = Grafico.montar(registros, Periodo.atual(TipoPeriodo.MES, hoje), fuso)
        assertEquals(30, pontos.size) // novembro
        assertEquals("1", pontos.first().rotulo)
        assertEquals("30", pontos.last().rotulo)
        assertEquals(5, pontos[22].fotosVendidas) // dia 23
    }

    @Test
    fun `trimestre tem uma barra por semana`() {
        val pontos = Grafico.montar(registros, Periodo.atual(TipoPeriodo.TRIMESTRE, hoje), fuso)
        assertEquals("01/10", pontos.first().rotulo) // semanas de 7 dias a partir do início
        assertEquals(14, pontos.size) // 92 dias = 13 semanas cheias + 1 parcial
        assertEquals(9, pontos.sumOf { it.fotosVendidas })
    }

    @Test
    fun `ano tem uma barra por mes`() {
        val pontos = Grafico.montar(registros, Periodo.atual(TipoPeriodo.ANO, hoje), fuso)
        assertEquals(12, pontos.size)
        assertEquals("Jan", pontos.first().rotulo)
        assertEquals("Dez", pontos.last().rotulo)
        assertEquals(9, pontos[10].fotosVendidas) // novembro
    }

    // --- Rótulo do eixo (a biblioteca fecha o app se receber texto vazio) ---

    @Test
    fun `rotulo da barra dentro da lista`() {
        assertEquals("Ter", Grafico.rotuloDaBarra(listOf("Seg", "Ter"), 1.0))
    }

    @Test
    fun `rotulo nunca fica vazio fora da lista`() {
        // Troca de outubro (31 barras) para novembro (30): por um instante o gráfico ainda pede a barra 31
        assertEquals("31", Grafico.rotuloDaBarra(List(30) { "${it + 1}" }, 30.0))
        assertEquals("1", Grafico.rotuloDaBarra(null, 0.0))
        assertEquals("3", Grafico.rotuloDaBarra(listOf("a", " "), 2.0))
        assertEquals("2", Grafico.rotuloDaBarra(listOf("a", " "), 1.0))
    }

    // --- Valores de cada barra ---

    @Test
    fun `cada barra tem faturamento, lucro e preco medio`() {
        val segunda = Grafico.montar(registros, Periodo.atual(TipoPeriodo.SEMANA, hoje), fuso)[0]

        assertEquals(5, segunda.fotosVendidas)
        assertDinheiro("60", segunda.faturamento) // 30 + 30
        assertDinheiro("35", segunda.lucro) // 60 − 5 fotos × R$ 5
        assertDinheiro("12", segunda.precoMedioPorFoto!!) // 60 / 5
    }

    @Test
    fun `barra sem vendas fica zerada e sem preco medio`() {
        val terca = Grafico.montar(registros, Periodo.atual(TipoPeriodo.SEMANA, hoje), fuso)[1]

        assertEquals(0, terca.fotosVendidas)
        assertDinheiro("0", terca.faturamento)
        assertNull(terca.precoMedioPorFoto)
    }

    @Test
    fun `valor da barra conforme a metrica escolhida`() {
        val segunda = Grafico.montar(registros, Periodo.atual(TipoPeriodo.SEMANA, hoje), fuso)[0]

        assertDinheiro("5", segunda.valor(MetricaGrafico.FOTOS))
        assertDinheiro("60", segunda.valor(MetricaGrafico.FATURAMENTO))
        assertDinheiro("35", segunda.valor(MetricaGrafico.LUCRO))
    }
}
