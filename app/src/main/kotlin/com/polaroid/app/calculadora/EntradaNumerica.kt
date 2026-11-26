package com.polaroid.app.calculadora

import java.math.BigDecimal

/** Valida e converte o que o usuário digita nos campos da calculadora. */
object EntradaNumerica {

    // Até 9 dígitos inteiros, um separador (vírgula ou ponto) e até 2 casas decimais
    private val FORMATO_VALOR = Regex("""^\d{0,9}([.,]\d{0,2})?$""")
    private val FORMATO_QUANTIDADE = Regex("""^\d{0,5}$""")

    /** true se o texto pode ficar no campo de valor (inclui valores parciais como "120,"). */
    fun aceitaValor(texto: String): Boolean = FORMATO_VALOR.matches(texto)

    /** Converte o texto em valor exato; null se vazio/incompleto. */
    fun converterValor(texto: String): BigDecimal? {
        if (!aceitaValor(texto)) return null
        val normalizado = texto.replace(',', '.').removeSuffix(".")
        if (normalizado.isEmpty()) return null
        return BigDecimal(if (normalizado.startsWith(".")) "0$normalizado" else normalizado)
    }

    /** true se o texto pode ficar no campo de quantidade. */
    fun aceitaQuantidade(texto: String): Boolean = FORMATO_QUANTIDADE.matches(texto)

    /** Converte a quantidade; null se vazio. */
    fun converterQuantidade(texto: String): Int? =
        if (aceitaQuantidade(texto)) texto.toIntOrNull() else null
}
