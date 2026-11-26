package com.polaroid.app.calculadora

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntradaNumericaTest {

    // --- Valores em dinheiro: o que pode ser digitado ---

    @Test
    fun `aceita valores parciais enquanto o usuario digita`() {
        listOf("", "1", "120", "120,", "120,5", "120,50", ",5").forEach {
            assertTrue(EntradaNumerica.aceitaValor(it), "deveria aceitar '$it'")
        }
    }

    @Test
    fun `aceita ponto como separador decimal`() {
        // Alguns teclados Android mostram ponto em vez de vírgula
        assertTrue(EntradaNumerica.aceitaValor("120.50"))
    }

    @Test
    fun `bloqueia caracteres invalidos no valor`() {
        listOf("abc", "12a", "-5", "R$ 10", " 10").forEach {
            assertFalse(EntradaNumerica.aceitaValor(it), "deveria bloquear '$it'")
        }
    }

    @Test
    fun `bloqueia mais de um separador decimal`() {
        listOf("1,2,3", "1.2,3", "1..2").forEach {
            assertFalse(EntradaNumerica.aceitaValor(it), "deveria bloquear '$it'")
        }
    }

    @Test
    fun `bloqueia mais de 2 casas decimais`() {
        assertFalse(EntradaNumerica.aceitaValor("10,005"))
        assertFalse(EntradaNumerica.aceitaValor("10.005"))
    }

    @Test
    fun `limita a 9 digitos na parte inteira`() {
        assertTrue(EntradaNumerica.aceitaValor("999999999,99"))
        assertFalse(EntradaNumerica.aceitaValor("1000000000"))
    }

    // --- Valores em dinheiro: conversão ---

    @Test
    fun `converte texto em valor exato`() {
        assertEquals(BigDecimal("120.50"), EntradaNumerica.converterValor("120,50"))
        assertEquals(BigDecimal("120.50"), EntradaNumerica.converterValor("120.50"))
        assertEquals(BigDecimal("0.5"), EntradaNumerica.converterValor(",5"))
        assertEquals(0, BigDecimal("120").compareTo(EntradaNumerica.converterValor("120,")!!))
    }

    @Test
    fun `nao converte texto vazio ou incompleto`() {
        assertNull(EntradaNumerica.converterValor(""))
        assertNull(EntradaNumerica.converterValor(","))
        assertNull(EntradaNumerica.converterValor("."))
    }
}
