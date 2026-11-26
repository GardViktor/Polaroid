package com.polaroid.app.registros

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DistribuicaoTest {

    private val fuso = ZoneId.of("America/Sao_Paulo")
    private fun em(mes: Int, dia: Int) = LocalDateTime.of(2026, mes, dia, 12, 0).atZone(fuso).toInstant()
    private val novembro = Periodo.atual(TipoPeriodo.MES, LocalDate.of(2026, 11, 26))

    private fun assertDinheiro(esperado: String, atual: BigDecimal) =
        assertEquals(0, BigDecimal(esperado).compareTo(atual), "esperado $esperado, veio $atual")

    // Compra 100 fotos a R$ 5; vende 24 Pix (R$ 10), 12 Crédito (R$ 12), 8 Dinheiro (R$ 10), 4 Débito (R$ 11); perde 2
    private val registros = listOf(
        Registro.Compra(1, em(10, 1), 100, 50000),
        Registro.Venda(2, em(11, 2), 24, 1000, FormaPagamento.PIX, 0),
        Registro.Venda(3, em(11, 3), 12, 1200, FormaPagamento.CREDITO, 499),
        Registro.Venda(4, em(11, 4), 8, 1000, FormaPagamento.DINHEIRO, 0),
        Registro.Venda(5, em(11, 5), 4, 1100, FormaPagamento.DEBITO, 199),
        Registro.Perda(6, em(11, 6), 2),
    )

    @Test
    fun `fatias por forma de pagamento e perdas, na ordem fixa`() {
        val fatias = Distribuicao.calcular(registros, novembro, fuso)

        assertEquals(listOf("Pix", "Dinheiro", "Débito", "Crédito", "Perdas"), fatias.map { it.rotulo })
        assertEquals(listOf(24, 8, 4, 12, 2), fatias.map { it.fotos })
    }

    @Test
    fun `percentual sobre o total de fotos que sairam`() {
        val fatias = Distribuicao.calcular(registros, novembro, fuso).associateBy { it.rotulo }

        // total = 50 fotos
        assertDinheiro("48", fatias.getValue("Pix").percentual)
        assertDinheiro("24", fatias.getValue("Crédito").percentual)
        assertDinheiro("4", fatias.getValue("Perdas").percentual)
    }

    @Test
    fun `valor e o faturamento da forma e o custo das perdas`() {
        val fatias = Distribuicao.calcular(registros, novembro, fuso).associateBy { it.rotulo }

        assertDinheiro("240", fatias.getValue("Pix").valor)
        assertDinheiro("144", fatias.getValue("Crédito").valor)
        assertDinheiro("10", fatias.getValue("Perdas").valor) // 2 × R$ 5 de custo
        assertTrue(fatias.getValue("Perdas").ePerda)
    }

    @Test
    fun `formas sem venda ficam de fora`() {
        val soPix = listOf(
            Registro.Compra(1, em(10, 1), 10, 5000),
            Registro.Venda(2, em(11, 2), 3, 1000, FormaPagamento.PIX, 0),
        )
        val fatias = Distribuicao.calcular(soPix, novembro, fuso)

        assertEquals(listOf("Pix"), fatias.map { it.rotulo })
        assertDinheiro("100", fatias.single().percentual)
    }

    @Test
    fun `periodo sem saidas nao tem fatias`() {
        assertTrue(Distribuicao.calcular(registros, novembro.anterior(), fuso).isEmpty())
    }
}
