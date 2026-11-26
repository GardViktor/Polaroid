package com.polaroid.calculadora

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Custo por foto e preço mínimo, calculados antes de existir preço de venda. */
class CalculadoraCustoTest {

    private fun dinheiro(valor: String) = BigDecimal(valor)

    @Test
    fun `divisao exata - custo e preco minimo iguais`() {
        val custo = CalculadoraLucro.calcularCusto(dinheiro("120.00"), 20)

        assertEquals(dinheiro("6.00"), custo.custoPorFoto)
        assertEquals(dinheiro("6.00"), custo.precoMinimoSemPrejuizo)
        assertFalse(custo.custoPorFotoArredondado)
    }

    @Test
    fun `preco minimo e arredondado para cima`() {
        // 100 / 30 = 3,3333... -> custo exibido 3,33; vender a 3,33 dá prejuízo de 0,10
        val custo = CalculadoraLucro.calcularCusto(dinheiro("100.00"), 30)

        assertEquals(dinheiro("3.33"), custo.custoPorFoto)
        assertEquals(dinheiro("3.34"), custo.precoMinimoSemPrejuizo)
        assertTrue(custo.custoPorFotoArredondado)
    }

    @Test
    fun `preco minimo sobe mesmo com fracao muito pequena`() {
        // 100 / 3 = 33,3333... -> mínimo 33,34
        val custo = CalculadoraLucro.calcularCusto(dinheiro("100.00"), 3)

        assertEquals(dinheiro("33.33"), custo.custoPorFoto)
        assertEquals(dinheiro("33.34"), custo.precoMinimoSemPrejuizo)
    }

    @Test
    fun `meio centavo - custo e minimo sobem`() {
        // 10,01 / 2 = 5,005 -> custo 5,01 (HALF_UP) e mínimo 5,01 (para cima)
        val custo = CalculadoraLucro.calcularCusto(dinheiro("10.01"), 2)

        assertEquals(dinheiro("5.01"), custo.custoPorFoto)
        assertEquals(dinheiro("5.01"), custo.precoMinimoSemPrejuizo)
    }

    @Test
    fun `valor pago zero - custo e minimo zero`() {
        val custo = CalculadoraLucro.calcularCusto(dinheiro("0.00"), 10)

        assertEquals(dinheiro("0.00"), custo.custoPorFoto)
        assertEquals(dinheiro("0.00"), custo.precoMinimoSemPrejuizo)
    }

    @Test
    fun `vender pelo preco minimo nunca da prejuizo`() {
        val casos = listOf(
            "100.00" to 30, "120.00" to 20, "10.01" to 2, "99.99" to 7,
            "250.00" to 60, "0.01" to 100, "1234.56" to 40,
        )
        casos.forEach { (valorPago, quantidade) ->
            val minimo = CalculadoraLucro.calcularCusto(dinheiro(valorPago), quantidade).precoMinimoSemPrejuizo
            if (minimo.signum() == 0) return@forEach // preço de venda precisa ser > 0
            val resultado = CalculadoraLucro.calcular(dinheiro(valorPago), quantidade, minimo)
            assertTrue(
                resultado.lucroTotal.signum() >= 0,
                "prejuízo vendendo a $minimo ($valorPago / $quantidade): ${resultado.lucroTotal}",
            )
        }
    }

    @Test
    fun `um centavo abaixo do minimo da prejuizo`() {
        val resultado = CalculadoraLucro.calcular(dinheiro("100.00"), 30, dinheiro("3.33"))

        assertEquals(dinheiro("-0.10"), resultado.lucroTotal)
    }

    @Test
    fun `rejeita quantidade zero`() {
        assertFailsWith<IllegalArgumentException> {
            CalculadoraLucro.calcularCusto(dinheiro("120.00"), 0)
        }
    }

    @Test
    fun `rejeita valor pago negativo`() {
        assertFailsWith<IllegalArgumentException> {
            CalculadoraLucro.calcularCusto(dinheiro("-1.00"), 20)
        }
    }

    @Test
    fun `rejeita valor pago com fracao de centavo`() {
        assertFailsWith<IllegalArgumentException> {
            CalculadoraLucro.calcularCusto(dinheiro("120.005"), 20)
        }
    }
}
