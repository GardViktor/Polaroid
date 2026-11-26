package com.polaroid.app.registros

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CentavosTest {

    @Test
    fun `converte reais em centavos exatos`() {
        assertEquals(1000L, Centavos.de(BigDecimal("10.00")))
        assertEquals(50L, Centavos.de(BigDecimal("0.5")))
        assertEquals(12000L, Centavos.de(BigDecimal("120")))
        assertEquals(1L, Centavos.de(BigDecimal("0.01")))
        assertEquals(0L, Centavos.de(BigDecimal("0")))
    }

    @Test
    fun `rejeita fracao de centavo em vez de arredondar`() {
        assertFailsWith<IllegalArgumentException> { Centavos.de(BigDecimal("10.005")) }
    }

    @Test
    fun `converte centavos em reais`() {
        assertEquals(BigDecimal("10.50"), Centavos.paraReais(1050))
        assertEquals(BigDecimal("0.01"), Centavos.paraReais(1))
    }

    @Test
    fun `texto para campo de edicao`() {
        assertEquals("10,50", Centavos.paraTextoCampo(1050))
        assertEquals("0,05", Centavos.paraTextoCampo(5))
        assertEquals("1234,00", Centavos.paraTextoCampo(123400))
    }
}
