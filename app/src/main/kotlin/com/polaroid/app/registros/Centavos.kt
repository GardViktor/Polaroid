package com.polaroid.app.registros

import java.math.BigDecimal

/** Conversão exata entre reais (BigDecimal) e centavos (Long). */
object Centavos {

    /** Lança erro se houver fração de centavo: nunca arredonda dinheiro em silêncio. */
    fun de(reais: BigDecimal): Long {
        require(reais.stripTrailingZeros().scale() <= 2) { "Valor com fração de centavo: $reais" }
        return reais.movePointRight(2).longValueExact()
    }

    fun paraReais(centavos: Long): BigDecimal = BigDecimal.valueOf(centavos, 2)

    /** Texto para preencher campos ao editar: 1050 -> "10,50". */
    fun paraTextoCampo(centavos: Long): String = paraReais(centavos).toPlainString().replace('.', ',')
}
