package com.polaroid.buscador

import com.polaroid.buscador.lojas.JsonLd
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Produtos em schema.org (JSON-LD) dentro da página: KaBuM, Miranda, Optisom. */
class JsonLdTest {

    private fun amostra(nome: String) =
        checkNotNull(javaClass.getResource("/amostras/$nome")) { "amostra $nome não encontrada" }.readText()

    private fun assertDinheiro(esperado: String, atual: BigDecimal?) =
        assertEquals(0, BigDecimal(esperado).compareTo(atual), "esperado $esperado, veio $atual")

    @Test
    fun `kabum le os 10 produtos com preco e link`() {
        val anuncios = JsonLd.interpretar("KaBuM", "https://www.kabum.com.br", amostra("jsonld_kabum.html"))

        assertEquals(10, anuncios.size)
        val primeiro = anuncios.first()
        assertEquals("Fujifilm Filme Instax Mini Com 20 Fotos", primeiro.titulo)
        assertEquals(14145L, primeiro.precoCentavos)
        assertEquals("https://www.kabum.com.br/produto/164680/fujifilm-filme-instax-mini-com-20-fotos", primeiro.link)
    }

    @Test
    fun `kabum no ranking sem o kit com camera`() {
        val ofertas = Ranking.montar(JsonLd.interpretar("KaBuM", "https://www.kabum.com.br", amostra("jsonld_kabum.html")))

        assertEquals(9, ofertas.size)
        assertEquals(40, ofertas.first().fotos) // "Kit 2x ... 20 Unidades, 40 Fotos" por R$ 249,41
        assertDinheiro("6.23525", ofertas.first().precoPorFoto)
    }

    @Test
    fun `miranda le disponibilidade em estoque`() {
        val ofertas = Ranking.montar(JsonLd.interpretar("Miranda", "https://www.miranda.com.br", amostra("jsonld_miranda.html")))

        assertEquals(8, ofertas.size)
        assertDinheiro("6.99", ofertas.first().precoPorFoto) // 10 fotos por R$ 69,90
    }

    @Test
    fun `optisom com kits grandes e titulos sem mini`() {
        val anuncios = JsonLd.interpretar("Optisom", "https://www.lojaoptisom.com.br", amostra("jsonld_optisom.html"))
        val ofertas = Ranking.montar(anuncios)

        assertEquals(12, anuncios.size)
        // Sai o álbum e os 2 títulos "Instax- Total De N Fotos" (sem "Mini": na dúvida, rejeita)
        assertEquals(9, ofertas.size)
        assertEquals(listOf(60, 100), ofertas.take(2).map { it.fotos })
    }

    @Test
    fun `ignora produto esgotado`() {
        val html = """<script type="application/ld+json">
            [{"@type":"Product","name":"Filme Instax Mini 10 Fotos","url":"/a",
              "offers":{"@type":"Offer","price":"80.00","availability":"https://schema.org/OutOfStock"}},
             {"@type":"Product","name":"Filme Instax Mini 20 Fotos","url":"/b",
              "offers":{"@type":"Offer","price":"130.00","availability":"https://schema.org/InStock"}}]
            </script>"""
        val anuncios = JsonLd.interpretar("Loja", "https://loja.com.br", html)

        assertEquals(listOf("Filme Instax Mini 20 Fotos"), anuncios.map { it.titulo })
    }

    @Test
    fun `aceita lista de ofertas, preco minimo, grafo e link relativo`() {
        val html = """<script type="application/ld+json">
            {"@graph":[
              {"@type":"Product","name":"Filme Instax Mini 10 Fotos","url":"/produto/a",
               "offers":[{"@type":"Offer","price":75.5}]},
              {"@type":"Product","name":"Filme Instax Mini 20 Fotos",
               "mainEntityOfPage":{"@id":"https://loja.com.br/produto/b"},
               "offers":{"@type":"AggregateOffer","lowPrice":"129,90"}}
            ]}</script>"""
        val anuncios = JsonLd.interpretar("Loja", "https://loja.com.br", html)

        assertEquals(listOf(7550L, 12990L), anuncios.map { it.precoCentavos })
        assertEquals(listOf("https://loja.com.br/produto/a", "https://loja.com.br/produto/b"), anuncios.map { it.link })
    }

    @Test
    fun `pagina sem dados ou com json quebrado nao derruba`() {
        assertTrue(JsonLd.interpretar("Loja", "https://x", "<html>bloqueado</html>").isEmpty())
        assertTrue(JsonLd.interpretar("Loja", "https://x", """<script type="application/ld+json">{quebrado</script>""").isEmpty())
    }
}
