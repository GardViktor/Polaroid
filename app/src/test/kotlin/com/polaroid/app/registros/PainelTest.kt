package com.polaroid.app.registros

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PainelTest {

    private val fuso = ZoneId.of("America/Sao_Paulo")
    private fun em(dia: Int, hora: Int = 12) = LocalDateTime.of(2026, 11, dia, hora, 0).atZone(fuso).toInstant()
    private val novembro = Periodo.atual(TipoPeriodo.MES, LocalDate.of(2026, 11, 26))

    /** Compara valores em dinheiro ignorando zeros à direita (6.00 == 6). */
    private fun assertDinheiro(esperado: String, atual: BigDecimal) =
        assertEquals(0, BigDecimal(esperado).compareTo(atual), "esperado $esperado, veio $atual")

    // Compra 20 fotos por R$ 120 (R$ 6/foto); vende 5 a R$ 10 no Pix; vende 2 a R$ 10 no crédito (4,99%); perde 1
    private val basico = listOf(
        Registro.Compra(1, em(1), 20, 12000),
        Registro.Venda(2, em(2), 5, 1000, FormaPagamento.PIX, 0),
        Registro.Venda(3, em(3), 2, 1000, FormaPagamento.CREDITO, 499),
        Registro.Perda(4, em(3, 15), 1),
    )

    @Test
    fun `resumo do mes`() {
        val r = Painel.resumo(basico, novembro, fuso)

        assertEquals(7, r.fotosVendidas)
        assertEquals(1, r.fotosPerdidas)
        assertDinheiro("70.00", r.faturamento)
        assertDinheiro("0.998", r.taxas) // 4,99% de R$ 20 — exato, sem arredondar
        assertDinheiro("48", r.custoFotos) // (7 vendidas + 1 perdida) × R$ 6
        assertDinheiro("21.002", r.lucro) // 70 − 0,998 − 48
        assertDinheiro("10", r.precoMedioPorFoto!!)
    }

    @Test
    fun `resumo do dia so conta o dia`() {
        val dia2 = Periodo.atual(TipoPeriodo.DIA, LocalDate.of(2026, 11, 2))
        val r = Painel.resumo(basico, dia2, fuso)

        assertEquals(5, r.fotosVendidas)
        assertDinheiro("50", r.faturamento)
        assertDinheiro("20", r.lucro)
    }

    @Test
    fun `periodo sem vendas`() {
        val r = Painel.resumo(basico, novembro.anterior(), fuso)

        assertEquals(0, r.fotosVendidas)
        assertDinheiro("0", r.faturamento)
        assertNull(r.precoMedioPorFoto)
    }

    @Test
    fun `custo medio ponderado muda com nova compra mais cara`() {
        // Depois do básico sobram 12 fotos a R$ 6. Compra 10 por R$ 80 (R$ 8/foto):
        // novo custo médio = (12 × 6 + 80) / 22 = 6,909090...
        val registros = basico + listOf(
            Registro.Compra(5, em(4), 10, 8000),
            Registro.Venda(6, em(5), 2, 1000, FormaPagamento.PIX, 0),
        )
        val dia5 = Periodo.atual(TipoPeriodo.DIA, LocalDate.of(2026, 11, 5))

        val r = Painel.resumo(registros, dia5, fuso)

        // 2 × 152/22 = 13,8181...
        assertEquals("13.82", r.custoFotos.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
        // Vendas antigas continuam com o custo de R$ 6: dia 3 = 20 − 0,998 − (2 + 1 perdida) × 6
        val dia3 = Periodo.atual(TipoPeriodo.DIA, LocalDate.of(2026, 11, 3))
        assertDinheiro("1.002", Painel.resumo(registros, dia3, fuso).lucro)
    }

    @Test
    fun `saldo de caixa comeca negativo com a compra`() {
        val s = Painel.situacaoGeral(listOf(Registro.Compra(1, em(1), 20, 12000)))
        assertDinheiro("-120", s.saldoCaixa)
        assertEquals(20, s.estoque)
    }

    @Test
    fun `saldo de caixa soma vendas liquidas de taxa`() {
        // −120 + 50 + (20 − 0,998)
        val s = Painel.situacaoGeral(basico)
        assertDinheiro("-50.998", s.saldoCaixa)
        assertEquals(12, s.estoque)
    }

    @Test
    fun `variacao percentual`() {
        assertDinheiro("50", Painel.variacaoPercentual(BigDecimal("150"), BigDecimal("100"))!!)
        assertDinheiro("-25", Painel.variacaoPercentual(BigDecimal("75"), BigDecimal("100"))!!)
        assertNull(Painel.variacaoPercentual(BigDecimal("75"), BigDecimal.ZERO))
    }
}
