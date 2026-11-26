package com.polaroid.app.calculadora

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Formata valores no padrão brasileiro para exibição. */
object Formatador {

    private fun formato() = DecimalFormat(
        "#,##0.00",
        DecimalFormatSymbols(Locale.forLanguageTag("pt-BR")),
    ).apply { roundingMode = RoundingMode.HALF_UP }

    fun moeda(valor: BigDecimal): String = sinal(valor) + "R$ " + formato().format(valor.abs())

    fun percentual(valor: BigDecimal): String = sinal(valor) + formato().format(valor.abs()) + "%"

    // Sinal sempre com hífen comum, independente da versão do Java/Android
    private fun sinal(valor: BigDecimal) = if (valor.signum() < 0) "-" else ""
}
