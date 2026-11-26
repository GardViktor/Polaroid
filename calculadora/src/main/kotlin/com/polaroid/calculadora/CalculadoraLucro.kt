package com.polaroid.calculadora

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Resultado do cálculo. Todos os valores têm 2 casas decimais.
 *
 * @property custoPorFoto quanto custou cada foto (valor pago ÷ quantidade)
 * @property lucroPorFoto preço de venda − custo por foto (negativo = prejuízo)
 * @property lucroTotal receita total − valor pago (sempre exato; negativo = prejuízo)
 * @property margemLucro percentual do lucro sobre a receita (ex.: 40.00 = 40%)
 * @property valoresPorFotoArredondados true quando custo/lucro por foto precisaram ser arredondados
 * @property margemArredondada true quando a margem precisou ser arredondada
 */
data class ResultadoCalculo(
    val custoPorFoto: BigDecimal,
    val lucroPorFoto: BigDecimal,
    val lucroTotal: BigDecimal,
    val margemLucro: BigDecimal,
    val valoresPorFotoArredondados: Boolean,
    val margemArredondada: Boolean,
)

/**
 * Custo calculado só com a compra (antes de existir preço de venda).
 *
 * @property custoPorFoto valor pago ÷ quantidade (2 casas, HALF_UP)
 * @property precoMinimoSemPrejuizo menor preço em centavos que não dá prejuízo (arredondado para cima)
 * @property custoPorFotoArredondado true quando a divisão não foi exata
 */
data class ResultadoCusto(
    val custoPorFoto: BigDecimal,
    val precoMinimoSemPrejuizo: BigDecimal,
    val custoPorFotoArredondado: Boolean,
)

/**
 * Calcula custo e lucro da venda de fotos.
 *
 * Regra: nenhum centavo se perde. As contas internas usam precisão total e só
 * os valores exibidos que não cabem em centavos (custo/lucro por foto e margem
 * em divisões não exatas) são arredondados, com aviso no resultado.
 */
object CalculadoraLucro {

    private const val CASAS_CENTAVOS = 2
    private val PRECISAO_INTERNA = MathContext.DECIMAL128
    private val CEM = BigDecimal(100)

    fun calcularCusto(valorPago: BigDecimal, quantidadeFotos: Int): ResultadoCusto {
        validarCompra(valorPago, quantidadeFotos)

        val custoPorFotoExato = valorPago.divide(BigDecimal(quantidadeFotos), PRECISAO_INTERNA)
        val custoPorFoto = arredondar(custoPorFotoExato)

        return ResultadoCusto(
            custoPorFoto = custoPorFoto,
            // Para cima: vender a qualquer valor >= este nunca dá prejuízo
            precoMinimoSemPrejuizo = custoPorFotoExato.setScale(CASAS_CENTAVOS, RoundingMode.CEILING),
            custoPorFotoArredondado = custoPorFoto.compareTo(custoPorFotoExato) != 0,
        )
    }

    private fun validarCompra(valorPago: BigDecimal, quantidadeFotos: Int) {
        require(quantidadeFotos > 0) { "A quantidade de fotos deve ser maior que zero" }
        require(valorPago.signum() >= 0) { "O valor pago não pode ser negativo" }
        require(temNoMaximoCentavos(valorPago)) { "O valor pago não pode ter fração de centavo" }
    }

    fun calcular(
        valorPago: BigDecimal,
        quantidadeFotos: Int,
        precoVendaPorFoto: BigDecimal,
    ): ResultadoCalculo {
        validarCompra(valorPago, quantidadeFotos)
        require(precoVendaPorFoto.signum() > 0) { "O preço de venda deve ser maior que zero" }
        require(temNoMaximoCentavos(precoVendaPorFoto)) { "O preço de venda não pode ter fração de centavo" }

        val quantidade = BigDecimal(quantidadeFotos)

        val custoPorFotoExato = valorPago.divide(quantidade, PRECISAO_INTERNA)
        val lucroPorFotoExato = precoVendaPorFoto.subtract(custoPorFotoExato)

        // Entradas em centavos -> receita e lucro total são exatos, sem arredondar
        val receitaTotal = precoVendaPorFoto.multiply(quantidade)
        val lucroTotal = receitaTotal.subtract(valorPago).setScale(CASAS_CENTAVOS)

        val margemExata = lucroTotal.multiply(CEM).divide(receitaTotal, PRECISAO_INTERNA)

        val custoPorFoto = arredondar(custoPorFotoExato)
        val margemLucro = arredondar(margemExata)

        return ResultadoCalculo(
            custoPorFoto = custoPorFoto,
            lucroPorFoto = arredondar(lucroPorFotoExato),
            lucroTotal = lucroTotal,
            margemLucro = margemLucro,
            valoresPorFotoArredondados = custoPorFoto.compareTo(custoPorFotoExato) != 0,
            margemArredondada = margemLucro.compareTo(margemExata) != 0,
        )
    }

    private fun temNoMaximoCentavos(valor: BigDecimal): Boolean =
        valor.stripTrailingZeros().scale() <= CASAS_CENTAVOS

    private fun arredondar(valor: BigDecimal): BigDecimal =
        valor.setScale(CASAS_CENTAVOS, RoundingMode.HALF_UP)
}
