package com.polaroid.app.album

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formata data, hora e local das fotos para exibição (padrão brasileiro). */
object FormatadorAlbum {

    private val BRASIL = Locale.forLanguageTag("pt-BR")
    private val FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy", BRASIL)
    private val FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm", BRASIL)

    fun data(dataHora: ZonedDateTime): String = FORMATO_DATA.format(dataHora)

    fun hora(dataHora: ZonedDateTime): String = FORMATO_HORA.format(dataHora)

    fun local(local: LocalFoto?): String = when {
        local == null -> "Local não registrado"
        local.nome != null -> local.nome
        else -> String.format(
            BRASIL,
            "%.4f, %.4f",
            local.coordenadas.latitude,
            local.coordenadas.longitude,
        )
    }
}
