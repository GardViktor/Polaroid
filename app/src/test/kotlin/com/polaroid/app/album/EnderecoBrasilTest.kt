package com.polaroid.app.album

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EnderecoBrasilTest {

    // --- Extrair o bairro do endereço completo ---

    @Test
    fun `endereco com numero`() {
        assertEquals(
            "Pinheiros",
            EnderecoBrasil.extrairBairro("R. dos Pinheiros, 123 - Pinheiros, São Paulo - SP, 05422-000, Brasil", "São Paulo"),
        )
    }

    @Test
    fun `bairro com mais de uma palavra`() {
        assertEquals(
            "Bela Vista",
            EnderecoBrasil.extrairBairro("Av. Paulista, 1578 - Bela Vista, São Paulo - SP, 01310-200, Brasil", "São Paulo"),
        )
    }

    @Test
    fun `endereco sem numero`() {
        assertEquals(
            "Centro",
            EnderecoBrasil.extrairBairro("R. XV de Novembro - Centro, Curitiba - PR, 80020-310, Brasil", "Curitiba"),
        )
    }

    @Test
    fun `rodovia com km`() {
        assertEquals(
            "Jardim Presidente Dutra",
            EnderecoBrasil.extrairBairro(
                "Rod. Pres. Dutra, km 225 - Jardim Presidente Dutra, Guarulhos - SP, 07000-000, Brasil",
                "Guarulhos",
            ),
        )
    }

    @Test
    fun `endereco que comeca pelo bairro`() {
        assertEquals(
            "Copacabana",
            EnderecoBrasil.extrairBairro("Copacabana, Rio de Janeiro - RJ, Brasil", "Rio de Janeiro"),
        )
    }

    @Test
    fun `rua sem bairro nao vira bairro`() {
        assertNull(EnderecoBrasil.extrairBairro("R. Augusta, 500, São Paulo - SP, 01304-000, Brasil", "São Paulo"))
    }

    @Test
    fun `rua sem numero e sem bairro nao vira bairro`() {
        assertNull(EnderecoBrasil.extrairBairro("R. Augusta, São Paulo - SP, Brasil", "São Paulo"))
        assertNull(EnderecoBrasil.extrairBairro("Avenida Brasil, Rio de Janeiro - RJ, Brasil", "Rio de Janeiro"))
    }

    @Test
    fun `so a cidade`() {
        assertNull(EnderecoBrasil.extrairBairro("São Paulo - SP, Brasil", "São Paulo"))
    }

    @Test
    fun `cidade nao aparece no endereco`() {
        assertNull(EnderecoBrasil.extrairBairro("R. dos Pinheiros, 123 - Pinheiros, Brasil", "Campinas"))
    }

    @Test
    fun `sem endereco ou sem cidade`() {
        assertNull(EnderecoBrasil.extrairBairro(null, "São Paulo"))
        assertNull(EnderecoBrasil.extrairBairro("R. dos Pinheiros, 123 - Pinheiros, São Paulo - SP", null))
    }

    // --- Montar o nome exibido ("Bairro, Cidade") ---

    @Test
    fun `usa o campo de bairro quando o Android informa`() {
        assertEquals(
            "Pinheiros, São Paulo",
            EnderecoBrasil.montarNome(
                bairro = "Pinheiros",
                cidade = "São Paulo",
                municipio = null,
                linhaEndereco = "qualquer coisa",
            ),
        )
    }

    @Test
    fun `sem campo de bairro, extrai do endereco completo`() {
        assertEquals(
            "Pinheiros, São Paulo",
            EnderecoBrasil.montarNome(
                bairro = null,
                cidade = "São Paulo",
                municipio = null,
                linhaEndereco = "R. dos Pinheiros, 123 - Pinheiros, São Paulo - SP, 05422-000, Brasil",
            ),
        )
    }

    @Test
    fun `sem bairro em lugar nenhum mostra so a cidade`() {
        assertEquals(
            "São Paulo",
            EnderecoBrasil.montarNome(null, "São Paulo", null, "São Paulo - SP, Brasil"),
        )
    }

    @Test
    fun `sem cidade usa o municipio`() {
        assertEquals(
            "Centro, Curitiba",
            EnderecoBrasil.montarNome(null, null, "Curitiba", "R. XV de Novembro - Centro, Curitiba - PR, Brasil"),
        )
    }

    @Test
    fun `bairro igual a cidade nao repete`() {
        assertEquals("Santos", EnderecoBrasil.montarNome("Santos", "Santos", null, null))
    }

    @Test
    fun `nada conhecido`() {
        assertNull(EnderecoBrasil.montarNome(null, null, null, null))
        assertNull(EnderecoBrasil.montarNome("  ", "", null, ""))
    }
}
