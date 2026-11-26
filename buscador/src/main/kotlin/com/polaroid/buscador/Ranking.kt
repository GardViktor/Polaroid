package com.polaroid.buscador

import java.math.BigDecimal
import java.math.MathContext

/** Anúncio como veio da loja, antes de filtrar. [freteCentavos] null = frete desconhecido. */
data class AnuncioBruto(
    val loja: String,
    val titulo: String,
    val precoCentavos: Long,
    val link: String,
    val freteCentavos: Long? = null,
    val categoriaGarantida: Boolean = false,
    /** Anúncio que a loja vende só no balcão (ex.: "À venda apenas na loja física"). */
    val somenteLojaFisica: Boolean = false,
)

/** Oferta compatível e pronta para o ranking. [fotos]/[precoPorFoto] null = quantidade desconhecida. */
data class Oferta(
    val loja: String,
    val titulo: String,
    val precoCentavos: Long,
    val freteCentavos: Long?,
    val link: String,
    val fotos: Int?,
    val precoPorFoto: BigDecimal?,
    val somenteLojaFisica: Boolean = false,
) {
    /** Só informativo (selo na tela); o frete NÃO entra no ranking. */
    val freteGratis: Boolean get() = freteCentavos == 0L
}

/**
 * Filtra os anúncios compatíveis com a Instax Mini 12 e ordena pelo preço por foto
 * (preço ÷ fotos, SEM frete, para comparar todas as lojas igual), do mais barato ao mais
 * caro. Empate: menor preço primeiro. Ofertas sem quantidade identificada vão para o fim.
 */
object Ranking {

    fun montar(anuncios: List<AnuncioBruto>): List<Oferta> =
        anuncios
            .asSequence()
            .filter { it.precoCentavos > 0 }
            .filter { FiltroCompatibilidade.compativel(it.titulo, it.categoriaGarantida) }
            .distinctBy { it.link }
            .map(::paraOferta)
            .sortedWith(
                compareBy<Oferta, BigDecimal?>(nullsLast()) { it.precoPorFoto }.thenBy { it.precoCentavos },
            )
            .toList()

    private fun paraOferta(anuncio: AnuncioBruto): Oferta {
        val fotos = ExtratorFotos.quantidade(anuncio.titulo)?.takeIf { it > 0 }
        return Oferta(
            loja = anuncio.loja,
            titulo = anuncio.titulo.trim().replace(Regex("\\s+"), " "),
            precoCentavos = anuncio.precoCentavos,
            freteCentavos = anuncio.freteCentavos,
            link = anuncio.link,
            somenteLojaFisica = anuncio.somenteLojaFisica,
            fotos = fotos,
            precoPorFoto = fotos?.let {
                BigDecimal.valueOf(anuncio.precoCentavos, 2).divide(BigDecimal(it), MathContext.DECIMAL128)
            },
        )
    }
}
