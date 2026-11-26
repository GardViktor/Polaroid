package com.polaroid.buscador

import com.polaroid.buscador.lojas.Shopify
import com.polaroid.buscador.lojas.Vtex
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Testes com trechos reais das respostas das lojas (nov/2026), salvos em resources/amostras. */
class AdaptadoresJsonTest {

    private fun amostra(nome: String) =
        checkNotNull(javaClass.getResource("/amostras/$nome")) { "amostra $nome não encontrada" }.readText()

    private fun assertDinheiro(esperado: String, atual: BigDecimal?) =
        assertEquals(0, BigDecimal(esperado).compareTo(atual), "esperado $esperado, veio $atual")

    // --- VTEX (Americanas, WebContinental, Líder, Ciência Moderna) ---

    @Test
    fun `vtex monta a url da busca publica`() {
        assertEquals(
            "https://www.webcontinental.com.br/api/catalog_system/pub/products/search?ft=filme%20instax%20mini&_from=0&_to=49",
            Vtex.urlBusca("https://www.webcontinental.com.br", "filme instax mini"),
        )
    }

    @Test
    fun `vtex le nome, preco exato e link`() {
        val anuncios = Vtex.interpretar("WebContinental", amostra("vtex_busca.json"))

        assertEquals(10, anuncios.size)
        val primeiro = anuncios.first()
        assertEquals("WebContinental", primeiro.loja)
        assertEquals("Filme Instax Mini 10 Fotos - Branco", primeiro.titulo)
        assertEquals(8999L, primeiro.precoCentavos)
        assertEquals("https://www.webcontinental.com.br/filme-instax-mini-10-fotos---branco-006463000162/p", primeiro.link)
    }

    @Test
    fun `vtex no ranking so sobram os filmes`() {
        val ofertas = Ranking.montar(Vtex.interpretar("WebContinental", amostra("vtex_busca.json")))

        // Câmeras (até R$ 2.999) e a miniatura do Minecraft ficam de fora
        assertEquals(3, ofertas.size)
        assertEquals(20, ofertas.first().fotos)
        assertDinheiro("5.9485", ofertas.first().precoPorFoto) // R$ 118,97 / 20
    }

    @Test
    fun `vtex ignora produto sem estoque e resposta invalida`() {
        val semEstoque = """[{"productName":"Filme Instax Mini 10 Fotos","link":"https://x/p",
            "items":[{"sellers":[{"commertialOffer":{"Price":80.0,"AvailableQuantity":0}}]}]}]"""
        assertTrue(Vtex.interpretar("Loja", semEstoque).isEmpty())
        assertTrue(Vtex.interpretar("Loja", "<html>bloqueado</html>").isEmpty())
    }

    // --- Shopify: busca (Syma, iSafe) ---

    @Test
    fun `shopify monta a url da busca`() {
        assertEquals(
            "https://www.syma.com.br/search/suggest.json?q=filme%20instax%20mini&resources%5Btype%5D=product&resources%5Blimit%5D=10",
            Shopify.urlBusca("https://www.syma.com.br", "filme instax mini"),
        )
    }

    @Test
    fun `shopify busca le titulo, preco e link sem rastreio`() {
        val anuncios = Shopify.interpretarBusca("Syma", "https://www.syma.com.br", amostra("shopify_sugestao.json"))

        assertEquals(10, anuncios.size)
        val primeiro = anuncios.first()
        assertEquals("Filme instantâneo Instax Mini Fujifilm 20 fotos", primeiro.titulo)
        assertEquals(11500L, primeiro.precoCentavos)
        // Link limpo, sem ?_pos=...&_psq=... (parâmetros de rastreio)
        assertEquals("https://www.syma.com.br/products/filme-instantaneo-instax-mini-fujifilm-20-fotos", primeiro.link)
    }

    @Test
    fun `shopify busca no ranking sem kit nem cameras`() {
        val ofertas = Ranking.montar(Shopify.interpretarBusca("Syma", "https://www.syma.com.br", amostra("shopify_sugestao.json")))

        assertEquals(5, ofertas.size)
        assertDinheiro("5.75", ofertas.first().precoPorFoto) // R$ 115 / 20
    }

    // --- Shopify: coleção da loja oficial Fujifilm ("filme-mini") ---

    @Test
    fun `shopify colecao marca categoria garantida e ignora indisponiveis`() {
        val anuncios = Shopify.interpretarColecao("Fujifilm", "https://loja.fujifilm.com.br", amostra("shopify_colecao.json"))

        assertTrue(anuncios.all { it.categoriaGarantida })
        assertEquals(6, anuncios.size) // 4 dos 10 estão esgotados
        assertEquals(
            "https://loja.fujifilm.com.br/products/combo-de-filmes-fujifilm-instax-mini-branco-40-fotos",
            anuncios.first { it.titulo.contains("Branco 40") }.link,
        )
    }

    @Test
    fun `shopify colecao no ranking descarta preco zero`() {
        val ofertas = Ranking.montar(Shopify.interpretarColecao("Fujifilm", "https://loja.fujifilm.com.br", amostra("shopify_colecao.json")))

        // "Monte seu combo" tem preço R$ 0,00 e sai; o combo de 100 fotos por R$ 470 lidera (R$ 4,70/foto)
        assertEquals(listOf(100, 40, 100, 30, 30), ofertas.map { it.fotos })
        assertDinheiro("4.70", ofertas.first().precoPorFoto)
    }
}
