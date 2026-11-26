package com.polaroid.app.album

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CorridaLocalizacaoTest {

    private val pinheiros = Coordenadas(-23.5613, -46.6565)
    private val centro = Coordenadas(-23.5505, -46.6333)

    @Test
    fun `a primeira resposta vence`() = runTest {
        val resultado = primeiraLocalizacao(
            listOf(
                { delay(3_000); centro },
                { delay(500); pinheiros },
            ),
        )

        assertEquals(pinheiros, resultado)
        assertEquals(500, currentTime) // não esperou a fonte mais lenta
    }

    @Test
    fun `fonte que devolve null e ignorada`() = runTest {
        val resultado = primeiraLocalizacao(
            listOf(
                { delay(100); null },
                { delay(800); pinheiros },
            ),
        )

        assertEquals(pinheiros, resultado)
    }

    @Test
    fun `fonte que nunca responde nao trava as outras`() = runTest {
        var canceladaAoFinal = false
        val resultado = primeiraLocalizacao(
            listOf(
                {
                    try {
                        awaitCancellation()
                    } finally {
                        canceladaAoFinal = true
                    }
                },
                { delay(200); pinheiros },
            ),
        )

        assertEquals(pinheiros, resultado)
        assertTrue(canceladaAoFinal, "a fonte travada deveria ser cancelada")
    }

    @Test
    fun `fonte com erro e ignorada`() = runTest {
        val resultado = primeiraLocalizacao(
            listOf(
                { error("provedor quebrado") },
                { delay(100); pinheiros },
            ),
        )

        assertEquals(pinheiros, resultado)
    }

    @Test
    fun `nenhuma fonte encontra - null`() = runTest {
        val resultado = primeiraLocalizacao(listOf({ null }, { delay(100); null }))

        assertNull(resultado)
    }

    @Test
    fun `sem fontes - null`() = runTest {
        assertNull(primeiraLocalizacao(emptyList()))
    }
}
