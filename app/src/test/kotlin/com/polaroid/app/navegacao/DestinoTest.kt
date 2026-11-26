package com.polaroid.app.navegacao

import kotlin.test.Test
import kotlin.test.assertEquals

class DestinoTest {

    @Test
    fun `menu tem calculadora, busca e album nessa ordem`() {
        assertEquals(listOf(Destino.CALCULADORA, Destino.BUSCAR, Destino.ALBUM), Destino.entries)
    }

    @Test
    fun `app abre na calculadora`() {
        assertEquals(Destino.CALCULADORA, Destino.INICIAL)
    }

    @Test
    fun `rotulos do menu`() {
        assertEquals("Calcular", Destino.CALCULADORA.rotulo)
        assertEquals("Buscar", Destino.BUSCAR.rotulo)
        assertEquals("Álbum", Destino.ALBUM.rotulo)
    }
}
