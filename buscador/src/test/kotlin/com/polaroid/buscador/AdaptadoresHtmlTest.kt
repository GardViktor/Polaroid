package com.polaroid.buscador

import com.polaroid.buscador.lojas.Amazon
import com.polaroid.buscador.lojas.Carrefour
import com.polaroid.buscador.lojas.Itx
import com.polaroid.buscador.lojas.Kalunga
import com.polaroid.buscador.lojas.OficinaDosBits
import com.polaroid.buscador.lojas.PaixaoCameras
import com.polaroid.buscador.lojas.Tray
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Trechos reais das páginas de busca (nov/2026), salvos em resources/amostras. */
class AdaptadoresHtmlTest {

    private fun amostra(nome: String) =
        checkNotNull(javaClass.getResource("/amostras/$nome")) { "amostra $nome não encontrada" }.readText()

    private fun assertDinheiro(esperado: String, atual: BigDecimal?) =
        assertEquals(0, BigDecimal(esperado).compareTo(atual), "esperado $esperado, veio $atual")

    // --- Kalunga ---

    @Test
    fun `kalunga so traz o que tem preco e marca loja fisica`() {
        val anuncios = Kalunga.interpretar(amostra("html_kalunga.html"))

        // 4 produtos: 1 "Disponível em algumas Lojas" (com preço) e 3 "Produto indisponível" (sem preço)
        val unico = anuncios.single()
        assertEquals("Filme Instax mini Macaron, com 10 fotos, 705066516, Fuji Film CX 1 UN", unico.titulo)
        assertEquals(6340L, unico.precoCentavos)
        assertTrue(unico.somenteLojaFisica)
        assertEquals("https://www.kalunga.com.br/prod/filme-instax-mini-macaron-com-10-fotos-705066516-fuji-film-cx-1-un/478764", unico.link)
    }

    // --- ITX ---

    @Test
    fun `itx usa o preco do pix e ignora indisponiveis`() {
        val anuncios = Itx.interpretar(amostra("html_itx.html"))

        assertEquals(2, anuncios.size) // 3 dos 5 estão "Desculpe, indisponível."
        assertEquals(10590L, anuncios[0].precoCentavos) // "R$ 105,90 à vista no PIX"
        assertEquals(
            "https://itxgamer.com.br/filme-instantaneo-fujifilm-instax-mini-borda-branca-20-fotos-2-cartuchos-de-10-poses-p7941",
            anuncios[0].link,
        )
        assertEquals(8359L, anuncios[1].precoCentavos)
    }

    @Test
    fun `itx no ranking com 2 cartuchos de 10 poses`() {
        val melhor = Ranking.montar(Itx.interpretar(amostra("html_itx.html"))).first()
        assertEquals(20, melhor.fotos)
        assertDinheiro("5.295", melhor.precoPorFoto)
    }

    // --- Oficina dos Bits ---

    @Test
    fun `oficina dos bits usa o preco do pix`() {
        val anuncios = OficinaDosBits.interpretar(amostra("html_bits.html"))
        val filme = anuncios.first()

        assertEquals("Filme Fujifilm para Instax Mini - Bordas Brancas - 10 Fotos", filme.titulo)
        assertEquals(6690L, filme.precoCentavos) // "ou R$66,90 no PIX" (e não 12x de R$ 6,19)
        assertEquals("https://www.oficinadosbits.com.br/produto/filme-fujifilm-para-instax-mini-bordas-brancas-10-fotos/", filme.link)
        assertEquals(1, Ranking.montar(anuncios).size) // o resto são câmeras
    }

    // --- Paixão Câmeras ---

    @Test
    fun `paixao marca tudo como loja fisica e le o preco exato`() {
        val anuncios = PaixaoCameras.interpretar(amostra("html_paixao.html"))

        assertEquals(12, anuncios.size)
        assertTrue(anuncios.all { it.somenteLojaFisica }) // "À venda apenas na loja física"
        assertEquals("Filme Instax Mini Black 10 Fotos - Fujifilm", anuncios.first().titulo)
        assertEquals(8500L, anuncios.first().precoCentavos)
        // Só sobram os 6 filmes Instax Mini (tripés, estabilizador, Kodak etc. ficam de fora)
        assertEquals(6, Ranking.montar(anuncios).size)
    }

    // --- Antares (plataforma Tray) ---

    @Test
    fun `tray usa o preco a vista com desconto exibido`() {
        val anuncios = Tray.interpretar("Antares Imagem", amostra("html_antares.html"))
        val spray = anuncios.first { it.titulo == "Filme Fujifilm Instax Mini Spray Art 10 Fotos" }

        assertEquals(7566L, spray.precoCentavos) // "R$ 75,66 à vista com desconto Pix" (e não R$ 78,00)
        assertEquals("https://www.antaresimagem.com.br/17-filme-instantaneo/filme-instax-mini-spray-art-10-fotos", spray.link)
        assertEquals(14, anuncios.size) // 16 cartões; 2 sem preço ficam de fora
    }

    @Test
    fun `tray no ranking sem lentes, oculos, impressora e square`() {
        val ofertas = Ranking.montar(Tray.interpretar("Antares Imagem", amostra("html_antares.html")))

        // Spray Art aparece 2x com o mesmo link (vira 1), + Rainbow, Branco e Contact Sheet
        assertEquals(4, ofertas.size)
        assertDinheiro("7.566", ofertas.first().precoPorFoto)
    }

    // --- Carrefour ---

    @Test
    fun `carrefour le os 14 produtos com o preco do pix`() {
        val anuncios = Carrefour.interpretar(amostra("html_carrefour.html"))

        assertEquals(14, anuncios.size)
        assertEquals("Filme Fujifilm Instax Mini Macaron - 10 Fotos", anuncios.first().titulo)
        assertEquals(8100L, anuncios.first().precoCentavos) // "R$ 81,00 à vista no Pix"
        assertTrue(anuncios.all { it.link.startsWith("https://www.carrefour.com.br/produto/") })
    }

    @Test
    fun `carrefour no ranking entende unidades e kits`() {
        val ofertas = Ranking.montar(Carrefour.interpretar(amostra("html_carrefour.html")))

        assertEquals(13, ofertas.size) // sai a câmera
        assertEquals(40, ofertas.first().fotos) // "Kit 2x ... 20 Unidades (40 Fotos)" por R$ 239,89
        assertDinheiro("5.99725", ofertas.first().precoPorFoto)
    }

    // --- Amazon ---

    @Test
    fun `amazon le preco principal e link limpo`() {
        val anuncios = Amazon.interpretar(amostra("html_amazon.html"))

        assertEquals(3, anuncios.size) // o patrocinado sem link de produto fica de fora
        assertEquals("Filme Instax Mini com 20 Fotos, Fujifilm", anuncios[0].titulo)
        assertEquals(11969L, anuncios[0].precoCentavos)
        assertEquals("https://www.amazon.com.br/Filme-Instax-Mini-Fotos-Fujifilm/dp/B07CBR7295", anuncios[0].link)
        // Cartão com vários preços (outras ofertas): vale o primeiro, o principal
        assertEquals(11419L, anuncios[1].precoCentavos)
    }

    @Test
    fun `amazon no ranking`() {
        val melhor = Ranking.montar(Amazon.interpretar(amostra("html_amazon.html"))).first()
        assertDinheiro("5.7095", melhor.precoPorFoto) // R$ 114,19 / 20
    }

    // --- Robustez ---

    @Test
    fun `pagina de bloqueio ou vazia nao derruba nenhum adaptador`() {
        val vazia = "<html><body>Acesso negado</body></html>"
        assertTrue(Kalunga.interpretar(vazia).isEmpty())
        assertTrue(Itx.interpretar(vazia).isEmpty())
        assertTrue(OficinaDosBits.interpretar(vazia).isEmpty())
        assertTrue(PaixaoCameras.interpretar(vazia).isEmpty())
        assertTrue(Tray.interpretar("Loja", vazia).isEmpty())
        assertTrue(Carrefour.interpretar(vazia).isEmpty())
        assertTrue(Amazon.interpretar(vazia).isEmpty())
    }
}
