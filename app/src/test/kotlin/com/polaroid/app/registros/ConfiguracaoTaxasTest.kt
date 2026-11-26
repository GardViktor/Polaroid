package com.polaroid.app.registros

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConfiguracaoTaxasTest {

    @Test
    fun `padrao`() {
        val c = ConfiguracaoTaxas.PADRAO
        assertEquals(0, c.taxa(FormaPagamento.PIX))
        assertEquals(0, c.taxa(FormaPagamento.DINHEIRO))
        assertEquals(199, c.taxa(FormaPagamento.DEBITO))
        assertEquals(499, c.taxa(FormaPagamento.CREDITO))
        assertEquals(10, c.limiteEstoqueBaixo)
    }

    @Test
    fun `texto da taxa para pontos-base`() {
        assertEquals(199, TextoTaxa.paraPontosBase("1,99"))
        assertEquals(350, TextoTaxa.paraPontosBase("3,5"))
        assertEquals(500, TextoTaxa.paraPontosBase("5"))
        assertEquals(0, TextoTaxa.paraPontosBase("0"))
        assertEquals(10000, TextoTaxa.paraPontosBase("100"))
    }

    @Test
    fun `taxa invalida`() {
        assertNull(TextoTaxa.paraPontosBase(""))
        assertNull(TextoTaxa.paraPontosBase("abc"))
        assertNull(TextoTaxa.paraPontosBase("1,999")) // mais de 2 casas
        assertNull(TextoTaxa.paraPontosBase("100,01")) // acima de 100%
    }

    @Test
    fun `pontos-base para texto`() {
        assertEquals("1,99", TextoTaxa.dePontosBase(199))
        assertEquals("5,00", TextoTaxa.dePontosBase(500))
        assertEquals("0,00", TextoTaxa.dePontosBase(0))
    }
}
