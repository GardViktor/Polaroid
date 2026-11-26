package com.polaroid.buscador.lojas

import com.polaroid.buscador.AnuncioBruto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder

/** Termo de busca codificado para URL, com espaço como %20. */
internal fun codificar(termo: String): String = URLEncoder.encode(termo, Charsets.UTF_8).replace("+", "%20")

/**
 * "89.99", "129,90" ou "1.234,56" (texto ou número do JSON) → centavos; null se não for preço.
 * Com vírgula, segue o formato brasileiro: ponto é milhar e vírgula é decimal.
 */
internal fun centavos(valor: String?): Long? {
    val limpo = valor?.trim()?.removePrefix("R$")?.trim() ?: return null
    val decimal = if (',' in limpo) limpo.replace(".", "").replace(',', '.') else limpo
    return decimal.toBigDecimalOrNull()
        ?.setScale(2, RoundingMode.HALF_UP)
        ?.movePointRight(2)
        ?.toLong()
}

/** Lê o JSON sem quebrar: página de bloqueio ou resposta inválida viram null. */
internal fun lerJson(texto: String): JsonElement? = runCatching { Json.parseToJsonElement(texto) }.getOrNull()

private fun JsonElement?.obj(campo: String): JsonElement? = (this as? JsonObject)?.get(campo)
private fun JsonElement?.lista(): List<JsonElement> = (this as? JsonArray).orEmpty()
private fun JsonElement?.texto(): String? = (this as? JsonPrimitive)?.contentOrNull
private fun JsonElement?.booleano(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

/**
 * Lojas na plataforma VTEX (Americanas, WebContinental, Líder, Ciência Moderna):
 * busca pública de catálogo, a mesma usada pelo próprio site.
 */
object Vtex {

    fun urlBusca(base: String, termo: String): String =
        "$base/api/catalog_system/pub/products/search?ft=${codificar(termo)}&_from=0&_to=49"

    fun interpretar(loja: String, json: String): List<AnuncioBruto> =
        lerJson(json).lista().mapNotNull { produto ->
            val titulo = produto.obj("productName").texto() ?: return@mapNotNull null
            val link = produto.obj("link").texto() ?: return@mapNotNull null
            // Primeira oferta com estoque entre os itens/vendedores do produto
            val oferta = produto.obj("items").lista()
                .flatMap { it.obj("sellers").lista() }
                .map { it.obj("commertialOffer") }
                .firstOrNull { (it.obj("AvailableQuantity").texto()?.toBigDecimalOrNull() ?: BigDecimal.ZERO) > BigDecimal.ZERO }
                ?: return@mapNotNull null
            val preco = centavos(oferta.obj("Price").texto()) ?: return@mapNotNull null
            AnuncioBruto(loja, titulo, preco, link)
        }
}

/** Lojas Shopify (loja oficial Fujifilm, iSafe/Safe Imports, Syma). */
object Shopify {

    fun urlBusca(base: String, termo: String): String =
        "$base/search/suggest.json?q=${codificar(termo)}&resources%5Btype%5D=product&resources%5Blimit%5D=10"

    fun urlColecao(base: String, colecao: String): String = "$base/collections/$colecao/products.json?limit=50"

    fun interpretarBusca(loja: String, base: String, json: String): List<AnuncioBruto> =
        lerJson(json).obj("resources").obj("results").obj("products").lista().mapNotNull { produto ->
            if (produto.obj("available").booleano() == false) return@mapNotNull null
            val titulo = produto.obj("title").texto() ?: return@mapNotNull null
            val caminho = produto.obj("url").texto()?.substringBefore('?') ?: return@mapNotNull null
            val preco = centavos(produto.obj("price").texto()) ?: return@mapNotNull null
            AnuncioBruto(loja, titulo, preco, base + caminho)
        }

    /** Coleção que só tem filme Instax Mini (ex.: "filme-mini" da loja Fujifilm): categoria garantida. */
    fun interpretarColecao(loja: String, base: String, json: String): List<AnuncioBruto> =
        lerJson(json).obj("products").lista().mapNotNull { produto ->
            val variante = produto.obj("variants").lista().firstOrNull { it.obj("available").booleano() != false }
                ?: return@mapNotNull null
            val titulo = produto.obj("title").texto() ?: return@mapNotNull null
            val handle = produto.obj("handle").texto() ?: return@mapNotNull null
            val preco = centavos(variante.obj("price").texto()) ?: return@mapNotNull null
            AnuncioBruto(loja, titulo, preco, "$base/products/$handle", categoriaGarantida = true)
        }
}
