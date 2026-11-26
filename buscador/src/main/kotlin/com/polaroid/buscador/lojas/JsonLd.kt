package com.polaroid.buscador.lojas

import com.polaroid.buscador.AnuncioBruto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.Jsoup
import java.net.URI

/**
 * Produtos no padrão schema.org (JSON-LD) embutidos na página de busca — usado por
 * KaBuM, Miranda, Optisom e qualquer outra loja que publique esses dados. Resiste a
 * mudanças de layout, porque não depende do HTML visível.
 */
object JsonLd {

    private val INDISPONIVEL = Regex("OutOfStock|SoldOut|Discontinued", RegexOption.IGNORE_CASE)

    fun interpretar(loja: String, base: String, html: String): List<AnuncioBruto> =
        Jsoup.parse(html).select("script[type=application/ld+json]")
            .mapNotNull { lerJson(it.data()) }
            .flatMap { produtos(it) }
            .mapNotNull { produto -> anuncio(loja, base, produto) }

    /** Todos os objetos "Product" do JSON, em qualquer nível (listas, @graph, itemListElement...). */
    private fun produtos(no: JsonElement): List<JsonObject> = when (no) {
        is JsonArray -> no.flatMap { produtos(it) }
        is JsonObject -> {
            val proprio = if (eProduto(no)) listOf(no) else emptyList()
            proprio + no.values.filter { it is JsonArray || it is JsonObject }.flatMap { produtos(it) }
        }
        else -> emptyList()
    }

    private fun eProduto(objeto: JsonObject): Boolean = when (val tipo = objeto["@type"]) {
        is JsonPrimitive -> tipo.contentOrNull == "Product"
        is JsonArray -> tipo.any { (it as? JsonPrimitive)?.contentOrNull == "Product" }
        else -> false
    }

    private fun anuncio(loja: String, base: String, produto: JsonObject): AnuncioBruto? {
        val titulo = produto.texto("name") ?: return null
        val ofertas = when (val o = produto["offers"]) {
            is JsonArray -> o.filterIsInstance<JsonObject>()
            is JsonObject -> listOf(o)
            else -> emptyList()
        }
        val oferta = ofertas.firstOrNull { INDISPONIVEL.find(it.texto("availability").orEmpty()) == null } ?: return null
        val preco = centavos(oferta.texto("price") ?: oferta.texto("lowPrice")) ?: return null
        val link = oferta.texto("url")
            ?: produto.texto("url")
            ?: (produto["mainEntityOfPage"] as? JsonObject)?.texto("@id")
            ?: produto.texto("mainEntityOfPage")
            ?: return null
        return AnuncioBruto(loja, titulo, preco, absoluto(base, link))
    }

    private fun JsonObject.texto(campo: String): String? = (this[campo] as? JsonPrimitive)?.contentOrNull

    private fun absoluto(base: String, link: String): String =
        runCatching { URI(base.trimEnd('/') + "/").resolve(link.trim()).toString() }.getOrDefault(link)
}
