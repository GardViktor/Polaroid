package com.polaroid.buscador

import java.text.Normalizer

/** Minúsculas, sem acentos, sem pontuação (exceto "x" entre números), espaços únicos. */
internal fun normalizar(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

/**
 * Decide se um anúncio é filme compatível com a Instax Mini 12 (formato Instax Mini),
 * em qualquer cor ou edição. Na dúvida, rejeita: é melhor perder uma oferta do que
 * mostrar um filme que não serve na câmera.
 */
object FiltroCompatibilidade {

    // Aparelhos e acessórios que aparecem nas buscas junto com os filmes
    private val NAO_E_FILME = Regex(
        "\\b(camera|cameras|impressora|printer|bolsa|case|capa|album|porta retrato|porta retratos|" +
            "link|evo|lente|lentes|tripe|projetor|brinquedo|lego|miniatura|figura|adesivo|adesivos)\\b",
    )

    // Outros formatos de filme instantâneo, incompatíveis com a Mini 12
    private val OUTRO_FORMATO = Regex("\\b(square|wide|polaroid|i type|itype|600|sx 70)\\b")

    private val MINI = Regex("\\bmini\\b")
    private val INSTAX = Regex("\\binstax\\b")
    private val E_FILME = Regex("\\b(filme|filmes|film|films|fotos|poses|folhas|pack|pacote)\\b")

    /** [categoriaGarantida]: o anúncio veio de uma categoria da loja que só tem filme Instax Mini. */
    fun compativel(titulo: String, categoriaGarantida: Boolean = false): Boolean {
        val t = normalizar(titulo)
        if (NAO_E_FILME.containsMatchIn(t) || OUTRO_FORMATO.containsMatchIn(t)) return false
        if (!MINI.containsMatchIn(t) || !E_FILME.containsMatchIn(t)) return false
        return categoriaGarantida || INSTAX.containsMatchIn(t)
    }
}

/** Descobre quantas fotos o anúncio tem a partir do título; null se não der para saber. */
object ExtratorFotos {

    private val FOTOS_POR_PACOTE = setOf(10, 20)
    private const val UNIDADE = "(fotos|foto|poses|pose|folhas|exposicoes|exposicao|filmes)"

    // "2x10", "5 x 10" (mas não "54 x 86 mm")
    private val MULTIPLICACAO = Regex("\\b(\\d{1,2}) ?x ?(\\d{1,3})\\b(?! ?mm)")
    // "kit 3 filmes", "2 pacotes", "3 packs"
    private val PACOTES = Regex("\\b(\\d{1,2}) (packs?|pacotes?|unidades|caixas|filmes|cartuchos)\\b")
    // "20 fotos", "100 poses", "60 folhas"
    private val NUMERO_COM_UNIDADE = Regex("\\b(\\d{1,3}) $UNIDADE\\b")
    private val DUPLO = Regex("\\b(twin pack|pacote duplo|pack duplo|duplo)\\b")

    fun quantidade(titulo: String): Int? {
        val t = normalizar(titulo)

        MULTIPLICACAO.find(t)?.let { m ->
            val pacotes = m.groupValues[1].toInt()
            val porPacote = m.groupValues[2].toInt()
            if (porPacote in FOTOS_POR_PACOTE && pacotes in 1..20) return pacotes * porPacote
        }

        val comUnidade = NUMERO_COM_UNIDADE.findAll(t).toList()
        // Número de fotos por pacote: o primeiro "N fotos/poses/folhas" que não seja "N filmes"
        val porPacote = comUnidade.firstOrNull { it.groupValues[2] != "filmes" }?.groupValues?.get(1)?.toInt()

        PACOTES.find(t)?.let { m ->
            val pacotes = m.groupValues[1].toInt()
            if (porPacote != null && pacotes in 2..10 && porPacote in FOTOS_POR_PACOTE) return pacotes * porPacote
        }

        comUnidade.firstOrNull()?.let { return it.groupValues[1].toInt() }
        if (DUPLO.containsMatchIn(t)) return 20
        return null
    }
}
