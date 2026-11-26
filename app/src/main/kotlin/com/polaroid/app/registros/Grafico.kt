package com.polaroid.app.registros

import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class MetricaGrafico(val rotulo: String) { FOTOS("Fotos"), FATURAMENTO("Faturamento"), LUCRO("Lucro") }

/** Uma barra do gráfico. */
data class PontoGrafico(
    val rotulo: String,
    val fotosVendidas: Int,
    val faturamento: BigDecimal,
    val lucro: BigDecimal,
    val precoMedioPorFoto: BigDecimal?,
) {
    fun valor(metrica: MetricaGrafico): BigDecimal = when (metrica) {
        MetricaGrafico.FOTOS -> BigDecimal(fotosVendidas)
        MetricaGrafico.FATURAMENTO -> faturamento
        MetricaGrafico.LUCRO -> lucro
    }
}

/**
 * Divide o período em barras: Dia → horas, Semana → dias (seg–dom), Mês → dias,
 * Trimestre → semanas de 7 dias a partir do início, Ano → meses.
 */
object Grafico {

    private val DIAS_SEMANA = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
    private val MESES = listOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")
    private val DIA_MES = DateTimeFormatter.ofPattern("dd/MM")

    /** Nome da barra na posição [x]; nunca vazio (a Vico fecha o app com rótulo em branco). */
    fun rotuloDaBarra(rotulos: List<String>?, x: Double): String {
        val indice = x.toInt()
        return rotulos?.getOrNull(indice)?.takeIf { it.isNotBlank() } ?: "${indice + 1}"
    }

    private data class Faixa(val inicio: LocalDateTime, val fim: LocalDateTime, val rotulo: String)

    fun montar(registros: List<Registro>, periodo: Periodo, zona: ZoneId): List<PontoGrafico> {
        // Custo médio calculado uma vez sobre todo o histórico (e não por barra)
        val custos = Painel.custosDasSaidas(registros)
        val comData = registros.map { it to LocalDateTime.ofInstant(it.dataHora, zona) }

        return faixas(periodo).map { faixa ->
            val daFaixa = comData
                .filter { (_, quando) -> !quando.isBefore(faixa.inicio) && quando.isBefore(faixa.fim) }
                .map { it.first }
            val resumo = Painel.resumoDe(daFaixa, custos)
            PontoGrafico(faixa.rotulo, resumo.fotosVendidas, resumo.faturamento, resumo.lucro, resumo.precoMedioPorFoto)
        }
    }

    private fun faixas(periodo: Periodo): List<Faixa> {
        val inicio = periodo.inicio.atStartOfDay()
        val fim = periodo.fim.atStartOfDay()
        return when (periodo.tipo) {
            TipoPeriodo.DIA -> (0 until 24).map { h -> Faixa(inicio.plusHours(h.toLong()), inicio.plusHours(h + 1L), "${h}h") }
            TipoPeriodo.SEMANA -> (0 until 7).map { d ->
                Faixa(inicio.plusDays(d.toLong()), inicio.plusDays(d + 1L), DIAS_SEMANA[d])
            }
            TipoPeriodo.MES -> generateSequence(inicio) { it.plusDays(1) }.takeWhile { it.isBefore(fim) }
                .map { dia -> Faixa(dia, dia.plusDays(1), "${dia.dayOfMonth}") }.toList()
            TipoPeriodo.TRIMESTRE -> generateSequence(inicio) { it.plusDays(7) }.takeWhile { it.isBefore(fim) }
                .map { semana -> Faixa(semana, minOf(semana.plusDays(7), fim), DIA_MES.format(semana)) }.toList()
            TipoPeriodo.ANO -> (0 until 12).map { m ->
                Faixa(inicio.plusMonths(m.toLong()), inicio.plusMonths(m + 1L), MESES[m])
            }
        }
    }
}
