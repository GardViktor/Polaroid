package com.polaroid.app.calculadora

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatadorTest {

    @Test
    fun `formata dinheiro no padrao brasileiro`() {
        assertEquals("R$ 0,00", Formatador.moeda(BigDecimal("0.00")))
        assertEquals("R$ 120,50", Formatador.moeda(BigDecimal("120.50")))
        assertEquals("R$ 1.234,56", Formatador.moeda(BigDecimal("1234.56")))
        assertEquals("R$ 1.234.567,89", Formatador.moeda(BigDecimal("1234567.89")))
    }

    @Test
    fun `formata prejuizo com sinal negativo`() {
        assertEquals("-R$ 20,00", Formatador.moeda(BigDecimal("-20.00")))
        assertEquals("-R$ 1.000,00", Formatador.moeda(BigDecimal("-1000.00")))
    }

    @Test
    fun `formata percentual com 2 casas`() {
        assertEquals("40,00%", Formatador.percentual(BigDecimal("40.00")))
        assertEquals("33,33%", Formatador.percentual(BigDecimal("33.33")))
        assertEquals("-20,00%", Formatador.percentual(BigDecimal("-20.00")))
        assertEquals("100,00%", Formatador.percentual(BigDecimal("100.00")))
    }
}
