package com.polaroid.app.registros

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class TipoPeriodo(val rotulo: String) { DIA("Dia"), SEMANA("Semana"), MES("Mês"), TRIMESTRE("Trimestre"), ANO("Ano") }

/** Intervalo [inicio, fim) de datas no fuso do celular. Semana vai de segunda a domingo. */
data class Periodo(val tipo: TipoPeriodo, val inicio: LocalDate) {

    val fim: LocalDate
        get() = when (tipo) {
            TipoPeriodo.DIA -> inicio.plusDays(1)
            TipoPeriodo.SEMANA -> inicio.plusWeeks(1)
            TipoPeriodo.MES -> inicio.plusMonths(1)
            TipoPeriodo.TRIMESTRE -> inicio.plusMonths(3)
            TipoPeriodo.ANO -> inicio.plusYears(1)
        }

    fun anterior(): Periodo = Periodo(
        tipo,
        when (tipo) {
            TipoPeriodo.DIA -> inicio.minusDays(1)
            TipoPeriodo.SEMANA -> inicio.minusWeeks(1)
            TipoPeriodo.MES -> inicio.minusMonths(1)
            TipoPeriodo.TRIMESTRE -> inicio.minusMonths(3)
            TipoPeriodo.ANO -> inicio.minusYears(1)
        },
    )

    fun proximo(): Periodo = Periodo(tipo, fim)

    fun contem(data: LocalDate): Boolean = !data.isBefore(inicio) && data.isBefore(fim)

    fun eFuturo(hoje: LocalDate): Boolean = inicio.isAfter(hoje)

    fun rotulo(): String = when (tipo) {
        TipoPeriodo.DIA -> DIA_MES_ANO.format(inicio)
        TipoPeriodo.SEMANA -> "${DIA_MES.format(inicio)} a ${DIA_MES_ANO.format(fim.minusDays(1))}"
        TipoPeriodo.MES -> {
            val mes = inicio.month.getDisplayName(TextStyle.FULL, BRASIL).replaceFirstChar { it.titlecase(BRASIL) }
            "$mes de ${inicio.year}"
        }
        TipoPeriodo.TRIMESTRE -> "${(inicio.monthValue - 1) / 3 + 1}º trimestre de ${inicio.year}"
        TipoPeriodo.ANO -> "${inicio.year}"
    }

    companion object {
        private val BRASIL = Locale.forLanguageTag("pt-BR")
        private val DIA_MES_ANO = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private val DIA_MES = DateTimeFormatter.ofPattern("dd/MM")

        fun atual(tipo: TipoPeriodo, hoje: LocalDate): Periodo = Periodo(
            tipo,
            when (tipo) {
                TipoPeriodo.DIA -> hoje
                TipoPeriodo.SEMANA -> hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                TipoPeriodo.MES -> hoje.withDayOfMonth(1)
                TipoPeriodo.TRIMESTRE -> LocalDate.of(hoje.year, (hoje.monthValue - 1) / 3 * 3 + 1, 1)
                TipoPeriodo.ANO -> hoje.withDayOfYear(1)
            },
        )
    }
}
