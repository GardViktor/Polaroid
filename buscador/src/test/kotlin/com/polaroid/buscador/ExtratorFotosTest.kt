package com.polaroid.buscador

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExtratorFotosTest {

    private fun fotos(titulo: String) = ExtratorFotos.quantidade(titulo)

    @Test
    fun `numero seguido de fotos ou poses`() {
        assertEquals(10, fotos("Filme Fujifilm Instax Mini 10 Fotos"))
        assertEquals(20, fotos("Filme instantâneo Instax Mini Fujifilm 20 fotos"))
        assertEquals(10, fotos("FILME INSTAX MINI MONOCHROME 10 POSES"))
        assertEquals(100, fotos("Filme Instantâneo Instax Mini Com 100 Poses Fujifilm"))
        assertEquals(60, fotos("Filme Instax Mini Twin Pack 60 folhas"))
        assertEquals(10, fotos("Filme Instax Mini Pink Lemonade - 10 Fotos Fujifilm"))
    }

    @Test
    fun `numero do modelo da camera nao conta como fotos`() {
        assertEquals(20, fotos("Filme Para Instax Mini 12 - 20 Fotos"))
        assertEquals(10, fotos("Filme Instax Mini 11 12 40 Com 10 Fotos"))
    }

    @Test
    fun `multiplicacao de pacotes`() {
        assertEquals(20, fotos("Filme Instax Mini 2x10 Fotos"))
        assertEquals(50, fotos("Filme Instax Mini Pacote 5 x 10"))
        assertEquals(40, fotos("Filme Instax Mini 2 X 20 Poses"))
    }

    @Test
    fun `kit ou pacotes com quantidade por pacote`() {
        assertEquals(30, fotos("Kit 3 Filmes Instax Mini 10 Fotos Cada"))
        assertEquals(40, fotos("Kit 2 Pacotes Filme Instax Mini 20 Fotos"))
        assertEquals(60, fotos("3 Packs Filme Instax Mini 20 Fotos"))
    }

    @Test
    fun `soma quando o anuncio junta pacotes com mais`() {
        assertEquals(40, fotos("Filme Instax Mini 20 Fotos + 20 Fotos Black Com Caixa De Presente"))
        assertEquals(30, fotos("Filme Fuji Instax Mini 20 Fotos + 10 Fotos Macaron Para Mini 11 Mini 9"))
    }

    @Test
    fun `cartuchos de N poses nao multiplicam de novo o total`() {
        assertEquals(20, fotos("Filme Instantâneo Fujifilm Instax Mini, Borda Branca, 20 Fotos, 2 Cartuchos de 10 Poses"))
        assertEquals(30, fotos("Filme Instax Mini 3 Pacotes de 10 Fotos"))
    }

    @Test
    fun `titulos reais variados`() {
        assertEquals(40, fotos("Kit 2x Filme Instax ,Mini Instantâneo Fujifilm ,20 Unidades, 40 Fotos"))
        assertEquals(10, fotos("Filme Instax Mini Contact Sheet Para 10 Fotos - Fujifilm Para Instax Mini 7; 8; 9; 11; 12"))
        assertEquals(60, fotos("Kit 60 Fotos Filme Instax Mini Para câmeras e Impressoras Instax Mini"))
        assertEquals(10, fotos("Filme Instax Mini, Pack Com 10 Fotos, 1 Un, Fujifilm"))
    }

    @Test
    fun `pacote duplo sem numero vale 20`() {
        assertEquals(20, fotos("Filme Instax Mini Twin Pack"))
        assertEquals(20, fotos("Filme Instax Mini Pacote Duplo Branco"))
    }

    @Test
    fun `dimensoes em milimetros nao sao fotos`() {
        assertEquals(20, fotos("Filme Fujifilm Instax Mini Branco 20 Fotos, 54 X 86 Mm, Iso 800"))
        assertNull(fotos("Filme Instax Mini 54 x 86 mm Branco"))
    }

    @Test
    fun `sem quantidade identificavel`() {
        assertNull(fotos("Filme Instax Mini Rainbow"))
        assertNull(fotos("Filme Instax Mini Branco"))
    }
}
