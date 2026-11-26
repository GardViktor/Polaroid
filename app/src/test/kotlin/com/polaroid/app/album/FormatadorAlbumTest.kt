package com.polaroid.app.album

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatadorAlbumTest {

    private val momento = ZonedDateTime.of(2026, 11, 26, 14, 32, 45, 0, ZoneId.of("America/Sao_Paulo"))

    @Test
    fun `data no padrao brasileiro`() {
        assertEquals("26/11/2026", FormatadorAlbum.data(momento))
    }

    @Test
    fun `hora com 24h sem segundos`() {
        assertEquals("14:32", FormatadorAlbum.hora(momento))
        assertEquals("09:05", FormatadorAlbum.hora(momento.withHour(9).withMinute(5)))
    }

    @Test
    fun `local com nome mostra o nome`() {
        val local = LocalFoto(Coordenadas(-23.5613, -46.6565), "Pinheiros, São Paulo")
        assertEquals("Pinheiros, São Paulo", FormatadorAlbum.local(local))
    }

    @Test
    fun `local sem nome mostra as coordenadas`() {
        val local = LocalFoto(Coordenadas(-23.56131, -46.65649), null)
        assertEquals("-23,5613, -46,6565", FormatadorAlbum.local(local))
    }

    @Test
    fun `sem local informa que nao foi registrado`() {
        assertEquals("Local não registrado", FormatadorAlbum.local(null))
    }
}
