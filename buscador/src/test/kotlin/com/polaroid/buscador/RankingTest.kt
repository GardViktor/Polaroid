package com.polaroid.buscador

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RankingTest {

    private fun anuncio(
        titulo: String,
        precoCentavos: Long,
        loja: String = "Loja",
        link: String = "https://loja/${titulo.hashCode()}",
        freteCentavos: Long? = null,
        categoriaGarantida: Boolean = false,
    ) = AnuncioBruto(loja, titulo, precoCentavos, link, freteCentavos, categoriaGarantida)

    private fun assertDinheiro(esperado: String, atual: BigDecimal?) =
        assertEquals(0, BigDecimal(esperado).compareTo(atual), "esperado $esperado, veio $atual")

    @Test
    fun `descarta anuncios incompativeis`() {
        val ofertas = Ranking.montar(
            listOf(
                anuncio("Filme Instax Mini 20 Fotos", 13000),
                anuncio("Câmera Instantânea Fujifilm Instax Mini 12", 49900),
                anuncio("Filme Instax Square 10 Fotos", 9000),
            ),
        )
        assertEquals(listOf("Filme Instax Mini 20 Fotos"), ofertas.map { it.titulo })
    }

    @Test
    fun `ordena do menor para o maior preco por foto`() {
        val ofertas = Ranking.montar(
            listOf(
                anuncio("Filme Instax Mini 10 Fotos", 8000), // R$ 8,00/foto
                anuncio("Filme Instax Mini 100 Poses", 55000), // R$ 5,50/foto
                anuncio("Filme Instax Mini 20 Fotos", 13000), // R$ 6,50/foto
            ),
        )
        assertEquals(listOf(100, 20, 10), ofertas.map { it.fotos })
        assertDinheiro("5.50", ofertas.first().precoPorFoto)
    }

    @Test
    fun `preco por foto exato, sem arredondar`() {
        val oferta = Ranking.montar(listOf(anuncio("Filme Instax Mini 2x10 Fotos", 10000))).single()
        assertDinheiro("5", oferta.precoPorFoto) // R$ 100 / 20
        val tresPacotes = Ranking.montar(listOf(anuncio("Kit 3 Filmes Instax Mini 10 Fotos", 10000))).single()
        assertEquals(0, tresPacotes.precoPorFoto!!.compareTo(BigDecimal("100").divide(BigDecimal(30), java.math.MathContext.DECIMAL128)))
    }

    @Test
    fun `frete conhecido entra no preco por foto`() {
        val oferta = Ranking.montar(listOf(anuncio("Filme Instax Mini 20 Fotos", 12000, freteCentavos = 2000))).single()
        assertDinheiro("7", oferta.precoPorFoto) // (120 + 20) / 20
        assertEquals(14000L, oferta.totalCentavos)
    }

    @Test
    fun `frete gratis e diferente de frete desconhecido`() {
        val gratis = Ranking.montar(listOf(anuncio("Filme Instax Mini 20 Fotos", 12000, freteCentavos = 0))).single()
        val desconhecido = Ranking.montar(listOf(anuncio("Filme Instax Mini 20 Fotos", 12000))).single()
        assertTrue(gratis.freteGratis)
        assertNull(desconhecido.freteCentavos)
    }

    @Test
    fun `quantidade desconhecida vai para o fim`() {
        val ofertas = Ranking.montar(
            listOf(
                anuncio("Filme Instax Mini Rainbow", 5000),
                anuncio("Filme Instax Mini 10 Fotos", 9000),
            ),
        )
        assertEquals(listOf(10, null), ofertas.map { it.fotos })
        assertNull(ofertas.last().precoPorFoto)
    }

    @Test
    fun `mesmo link aparece uma vez so`() {
        val ofertas = Ranking.montar(
            listOf(
                anuncio("Filme Instax Mini 20 Fotos", 13000, link = "https://loja/x"),
                anuncio("Filme Instax Mini 20 Fotos", 13000, link = "https://loja/x"),
            ),
        )
        assertEquals(1, ofertas.size)
    }

    @Test
    fun `preco zero ou negativo e descartado`() {
        assertTrue(Ranking.montar(listOf(anuncio("Filme Instax Mini 20 Fotos", 0))).isEmpty())
    }

    @Test
    fun `categoria garantida aceita titulo sem instax`() {
        val ofertas = Ranking.montar(listOf(anuncio("Mini Branco 10 Fotos", 9000, categoriaGarantida = true)))
        assertEquals(1, ofertas.size)
    }
}
