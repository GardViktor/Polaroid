package com.polaroid.calculadora

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalculadoraLucroTest {

    private fun dinheiro(valor: String) = BigDecimal(valor)

    @Test
    fun `calcula custo, lucro e margem no cenario padrao`() {
        // 2 pacotes de 10 fotos por R$ 120,00, vendendo cada foto a R$ 10,00
        val resultado = CalculadoraLucro.calcular(
            valorPago = dinheiro("120.00"),
            quantidadeFotos = 20,
            precoVendaPorFoto = dinheiro("10.00"),
        )

        assertEquals(dinheiro("6.00"), resultado.custoPorFoto)
        assertEquals(dinheiro("4.00"), resultado.lucroPorFoto)
        assertEquals(dinheiro("80.00"), resultado.lucroTotal)
        assertEquals(dinheiro("40.00"), resultado.margemLucro)
        // Divisões exatas: nenhum aviso de arredondamento
        assertFalse(resultado.valoresPorFotoArredondados)
        assertFalse(resultado.margemArredondada)
    }

    @Test
    fun `retorna lucro negativo quando vende abaixo do custo`() {
        val resultado = CalculadoraLucro.calcular(
            valorPago = dinheiro("120.00"),
            quantidadeFotos = 20,
            precoVendaPorFoto = dinheiro("5.00"),
        )

        assertEquals(dinheiro("6.00"), resultado.custoPorFoto)
        assertEquals(dinheiro("-1.00"), resultado.lucroPorFoto)
        assertEquals(dinheiro("-20.00"), resultado.lucroTotal)
        assertEquals(dinheiro("-20.00"), resultado.margemLucro)
    }

    @Test
    fun `lucro zero quando vende pelo preco de custo`() {
        val resultado = CalculadoraLucro.calcular(
            valorPago = dinheiro("120.00"),
            quantidadeFotos = 20,
            precoVendaPorFoto = dinheiro("6.00"),
        )

        assertEquals(dinheiro("0.00"), resultado.lucroPorFoto)
        assertEquals(dinheiro("0.00"), resultado.lucroTotal)
        assertEquals(dinheiro("0.00"), resultado.margemLucro)
    }

    @Test
    fun `divisao nao exata arredonda so o necessario e avisa`() {
        // 100 / 30 = 3,3333... -> 3,33 (inevitável)
        val resultado = CalculadoraLucro.calcular(
            valorPago = dinheiro("100.00"),
            quantidadeFotos = 30,
            precoVendaPorFoto = dinheiro("5.00"),
        )

        assertEquals(dinheiro("3.33"), resultado.custoPorFoto)
        // Calculado com o custo exato: 5,00 - 3,3333... = 1,6666... -> 1,67
        assertEquals(dinheiro("1.67"), resultado.lucroPorFoto)
        assertTrue(resultado.valoresPorFotoArredondados)

        // Lucro total é exato (150,00 - 100,00), sem nenhum arredondamento
        assertEquals(dinheiro("50.00"), resultado.lucroTotal)

        // 50 / 150 = 33,333...% -> 33,33 (inevitável)
        assertEquals(dinheiro("33.33"), resultado.margemLucro)
        assertTrue(resultado.margemArredondada)
    }

    @Test
    fun `lucro por foto usa o custo exato e nao o custo arredondado`() {
        // custo exato 2,005 (4,01 / 2) -> exibido 2,01
        // lucro exato = 3,00 - 2,005 = 0,995 -> 1,00 (HALF_UP)
        // Se usasse o custo arredondado: 3,00 - 2,01 = 0,99 (errado)
        val resultado = CalculadoraLucro.calcular(
            valorPago = dinheiro("4.01"),
            quantidadeFotos = 2,
            precoVendaPorFoto = dinheiro("3.00"),
        )

        assertEquals(dinheiro("2.01"), resultado.custoPorFoto)
        assertEquals(dinheiro("1.00"), resultado.lucroPorFoto)
        assertEquals(dinheiro("1.99"), resultado.lucroTotal)
        assertTrue(resultado.valoresPorFotoArredondados)
    }

    @Test
    fun `arredonda meio para cima`() {
        // 10,01 / 2 = 5,005 -> 5,01 (HALF_UP)
        val resultado = CalculadoraLucro.calcular(
            valorPago = dinheiro("10.01"),
            quantidadeFotos = 2,
            precoVendaPorFoto = dinheiro("10.00"),
        )

        assertEquals(dinheiro("5.01"), resultado.custoPorFoto)
        assertTrue(resultado.valoresPorFotoArredondados)
    }

    @Test
    fun `aceita valor pago zero`() {
        // Ex.: filmes ganhos de presente — todo o valor de venda é lucro
        val resultado = CalculadoraLucro.calcular(
            valorPago = dinheiro("0.00"),
            quantidadeFotos = 10,
            precoVendaPorFoto = dinheiro("8.00"),
        )

        assertEquals(dinheiro("0.00"), resultado.custoPorFoto)
        assertEquals(dinheiro("8.00"), resultado.lucroPorFoto)
        assertEquals(dinheiro("80.00"), resultado.lucroTotal)
        assertEquals(dinheiro("100.00"), resultado.margemLucro)
    }

    @Test
    fun `normaliza entradas sem casas decimais`() {
        val resultado = CalculadoraLucro.calcular(
            valorPago = dinheiro("120"),
            quantidadeFotos = 20,
            precoVendaPorFoto = dinheiro("10"),
        )

        assertEquals(dinheiro("6.00"), resultado.custoPorFoto)
        assertEquals(dinheiro("80.00"), resultado.lucroTotal)
    }

    @Test
    fun `aceita zeros extras a direita nos centavos`() {
        // 120,000 e 10,500 representam valores válidos em centavos
        val resultado = CalculadoraLucro.calcular(
            valorPago = dinheiro("120.000"),
            quantidadeFotos = 20,
            precoVendaPorFoto = dinheiro("10.500"),
        )

        assertEquals(dinheiro("6.00"), resultado.custoPorFoto)
        assertEquals(dinheiro("90.00"), resultado.lucroTotal)
    }

    @Test
    fun `rejeita valor pago com fracao de centavo`() {
        assertFailsWith<IllegalArgumentException> {
            CalculadoraLucro.calcular(dinheiro("120.005"), 20, dinheiro("10.00"))
        }
    }

    @Test
    fun `rejeita preco de venda com fracao de centavo`() {
        assertFailsWith<IllegalArgumentException> {
            CalculadoraLucro.calcular(dinheiro("120.00"), 20, dinheiro("10.005"))
        }
    }

    @Test
    fun `rejeita quantidade de fotos zero`() {
        assertFailsWith<IllegalArgumentException> {
            CalculadoraLucro.calcular(dinheiro("120.00"), 0, dinheiro("10.00"))
        }
    }

    @Test
    fun `rejeita quantidade de fotos negativa`() {
        assertFailsWith<IllegalArgumentException> {
            CalculadoraLucro.calcular(dinheiro("120.00"), -5, dinheiro("10.00"))
        }
    }

    @Test
    fun `rejeita valor pago negativo`() {
        assertFailsWith<IllegalArgumentException> {
            CalculadoraLucro.calcular(dinheiro("-1.00"), 20, dinheiro("10.00"))
        }
    }

    @Test
    fun `rejeita preco de venda negativo`() {
        assertFailsWith<IllegalArgumentException> {
            CalculadoraLucro.calcular(dinheiro("120.00"), 20, dinheiro("-10.00"))
        }
    }

    @Test
    fun `rejeita preco de venda zero`() {
        // Com receita zero a margem de lucro não pode ser calculada
        assertFailsWith<IllegalArgumentException> {
            CalculadoraLucro.calcular(dinheiro("120.00"), 20, dinheiro("0.00"))
        }
    }
}
