package com.polaroid.app.album

/**
 * Lê endereços no formato brasileiro devolvido pelo serviço de endereços do Android.
 *
 * No Brasil o campo "bairro" costuma vir vazio, mas o bairro aparece no endereço completo:
 * "R. dos Pinheiros, 123 - Pinheiros, São Paulo - SP, 05422-000, Brasil".
 */
object EnderecoBrasil {

    // Começos típicos de logradouro: um trecho assim é rua, não bairro
    private val LOGRADOURO = Regex(
        """^(r\.|rua|av\.|avenida|al\.|alameda|rod\.|rodovia|estr\.|estrada|tv\.|travessa|pç\.|praça|lgo\.|largo)\s""",
        RegexOption.IGNORE_CASE,
    )

    /** Bairro dentro do endereço completo, ou null se não houver um bairro claro. */
    fun extrairBairro(linhaEndereco: String?, cidade: String?): String? {
        if (linhaEndereco.isNullOrBlank() || cidade.isNullOrBlank()) return null

        // Trecho antes de ", <cidade>" (seguido de " -", "," ou fim)
        val posicaoCidade = Regex(""", ${Regex.escape(cidade)}(\s-|,|$)""")
            .find(linhaEndereco)?.range?.first ?: return null
        val antesDaCidade = linhaEndereco.substring(0, posicaoCidade)

        val temSeparador = " - " in antesDaCidade
        val candidato = antesDaCidade.substringAfterLast(" - ").trim()

        return candidato.takeIf {
            it.isNotEmpty() &&
                ',' !in it &&
                it.none(Char::isDigit) &&
                !it.equals(cidade, ignoreCase = true) &&
                (temSeparador || !LOGRADOURO.containsMatchIn(it))
        }
    }

    /** "Bairro, Cidade", só a cidade, ou null se nada for conhecido. */
    fun montarNome(bairro: String?, cidade: String?, municipio: String?, linhaEndereco: String?): String? {
        val cidadeFinal = cidade?.takeIf { it.isNotBlank() } ?: municipio?.takeIf { it.isNotBlank() }
        val bairroFinal = bairro?.takeIf { it.isNotBlank() } ?: extrairBairro(linhaEndereco, cidadeFinal)

        return listOfNotNull(bairroFinal, cidadeFinal)
            .distinctBy { it.lowercase() }
            .joinToString(", ")
            .ifBlank { null }
    }
}
