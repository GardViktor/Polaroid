package com.polaroid.buscador.lojas

import com.polaroid.buscador.AnuncioBruto
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI

/** Primeiro "R$ 1.234,56" do texto → centavos. */
private val PRECO_NO_TEXTO = Regex("""R\$\s*([\d.]+,\d{2})""")

internal fun precoDoTexto(texto: String?): Long? =
    texto?.let { PRECO_NO_TEXTO.find(it.replace(' ', ' '))?.groupValues?.get(1) }?.let(::centavos)

internal fun absoluto(base: String, link: String): String =
    runCatching { URI(base.trimEnd('/') + "/").resolve(link.trim()).toString() }.getOrDefault(link)

private fun Element.textoLimpo(): String = text().replace(' ', ' ').trim()

/**
 * Regras comuns: preço do Pix/à vista quando a loja mostra (senão o preço único);
 * produto sem preço (indisponível) fica de fora; "só na loja física" entra com selo.
 */
object Kalunga {
    private const val BASE = "https://www.kalunga.com.br"

    fun urlBusca(termo: String) = "$BASE/busca/1?q=${codificar(termo.replace(' ', '-'))}"

    fun interpretar(html: String): List<AnuncioBruto> =
        Jsoup.parse(html).select("div.blocoproduto:has(a.blocoproduto__link)").mapNotNull { card ->
            val link = card.selectFirst("a.blocoproduto__link") ?: return@mapNotNull null
            val titulo = link.attr("title").ifBlank { link.textoLimpo() }.ifBlank { return@mapNotNull null }
            val preco = precoDoTexto(card.selectFirst(".blocoproduto__price")?.text()) ?: return@mapNotNull null
            AnuncioBruto(
                "Kalunga", titulo, preco, absoluto(BASE, link.attr("href")),
                somenteLojaFisica = card.text().contains("Disponível em algumas Lojas", ignoreCase = true),
            )
        }
}

object Itx {
    private const val BASE = "https://itxgamer.com.br"

    fun urlBusca(termo: String) = "$BASE/busca/${codificar(termo)}?categorias=cameras-e-fotos%2Ffilmes"

    fun interpretar(html: String): List<AnuncioBruto> =
        Jsoup.parse(html).select(".showcase-item").mapNotNull { card ->
            if (card.selectFirst(".showcase-prices_out-of-stock") != null) return@mapNotNull null
            val link = card.selectFirst("a.showcase-product_link_title") ?: return@mapNotNull null
            val titulo = link.attr("title").ifBlank { link.textoLimpo() }.ifBlank { return@mapNotNull null }
            // "R$ 105,90 à vista no PIX" (o preço riscado e o parcelado ficam em outros campos)
            val preco = precoDoTexto(card.selectFirst(".showcase-prices_price")?.text()) ?: return@mapNotNull null
            AnuncioBruto("ITX", titulo, preco, absoluto(BASE, link.attr("href")))
        }
}

object OficinaDosBits {
    private const val BASE = "https://www.oficinadosbits.com.br"

    fun urlBusca(termo: String) = "$BASE/produto/busca/?q=${codificar(termo).replace("%20", "+")}"

    fun interpretar(html: String): List<AnuncioBruto> =
        Jsoup.parse(html).select("li.product-item").mapNotNull { card ->
            val link = card.selectFirst("a.link-name") ?: return@mapNotNull null
            val titulo = link.textoLimpo().ifBlank { return@mapNotNull null }
            // "ou R$66,90 no PIX" (e não "12x R$6,19")
            val preco = precoDoTexto(card.selectFirst(".price.cash")?.text()) ?: return@mapNotNull null
            AnuncioBruto("Oficina dos Bits", titulo, preco, absoluto(BASE, link.attr("href")))
        }
}

object PaixaoCameras {
    private const val BASE = "https://www.paixaocameras.com.br"

    fun urlBusca(termo: String) = "$BASE/busca.html?search=${codificar(termo).replace("%20", "+")}"

    fun interpretar(html: String): List<AnuncioBruto> =
        Jsoup.parse(html).select("a.nome-produto").mapNotNull { link ->
            val card = link.parent() ?: return@mapNotNull null
            val titulo = link.textoLimpo().ifBlank { return@mapNotNull null }
            // Preço exato no atributo (content="145.00")
            val preco = centavos(card.selectFirst("[itemprop=price]")?.attr("content")) ?: return@mapNotNull null
            AnuncioBruto(
                "Paixão Câmeras", titulo, preco, absoluto(BASE, link.attr("href")),
                somenteLojaFisica = card.selectFirst(".status")?.text().orEmpty().contains("loja física", ignoreCase = true),
            )
        }
}

/** Lojas na plataforma Tray (ex.: Antares Imagem). */
object Tray {
    fun urlBusca(base: String, idLoja: String, termo: String) =
        "$base/loja/busca.php?loja=$idLoja&palavra_busca=${codificar(termo).replace("%20", "+")}"

    fun interpretar(loja: String, html: String): List<AnuncioBruto> =
        Jsoup.parse(html).select("a.product-info").mapNotNull { card ->
            val titulo = card.selectFirst(".product-name")?.textoLimpo()?.ifBlank { null } ?: return@mapNotNull null
            // Preço à vista com desconto (Pix) quando houver; senão o preço atual
            val preco = precoDoTexto(card.selectFirst(".preco-avista")?.text())
                ?: precoDoTexto(card.selectFirst(".current-price")?.text())
                ?: return@mapNotNull null
            AnuncioBruto(loja, titulo, preco, card.absUrl("href").ifBlank { card.attr("href") })
        }
}

object Carrefour {
    private const val BASE = "https://www.carrefour.com.br"

    fun urlBusca(termo: String) = "$BASE/busca/${codificar(termo)}"

    fun interpretar(html: String): List<AnuncioBruto> =
        Jsoup.parse(html).select("a[href^=/produto/]:has(h2)").mapNotNull { card ->
            val titulo = card.selectFirst("h2")?.textoLimpo()?.ifBlank { null } ?: return@mapNotNull null
            // Preço em destaque: "R$ 81,00 à vista no Pix"
            val preco = precoDoTexto(card.selectFirst("span.leading-none")?.text()) ?: return@mapNotNull null
            AnuncioBruto("Carrefour", titulo, preco, absoluto(BASE, card.attr("href")))
        }.distinctBy { it.link }
}

object Amazon {
    private const val BASE = "https://www.amazon.com.br"
    private val PATROCINADO = Regex("^Anúncio patrocinado\\s*[–-]\\s*", RegexOption.IGNORE_CASE)

    fun urlBusca(termo: String) = "$BASE/s?k=${codificar(termo).replace("%20", "+")}"

    fun interpretar(html: String): List<AnuncioBruto> =
        Jsoup.parse(html).select("[data-component-type=s-search-result]").mapNotNull { card ->
            val titulo = (card.selectFirst("h2[aria-label]")?.attr("aria-label") ?: card.selectFirst("h2")?.textoLimpo())
                ?.replace(PATROCINADO, "")?.trim()?.ifBlank { null } ?: return@mapNotNull null
            val href = card.selectFirst("a[href*=/dp/]")?.attr("href") ?: return@mapNotNull null
            // Link limpo: só até /dp/CODIGO (sem /ref=... nem parâmetros de rastreio)
            val limpo = href.substringBefore('?').substringBefore("/ref=")
            // Preço principal: o primeiro a-price que não é preço riscado
            val preco = precoDoTexto(card.selectFirst("span.a-price:not(.a-text-price) span.a-offscreen")?.text())
                ?: return@mapNotNull null
            AnuncioBruto("Amazon", titulo, preco, absoluto(BASE, limpo))
        }
}
