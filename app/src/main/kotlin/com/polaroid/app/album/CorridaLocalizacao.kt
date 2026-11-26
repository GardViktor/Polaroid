package com.polaroid.app.album

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Uma fonte de localização (ex.: provedor fused, rede ou GPS). Devolve null se não achar. */
typealias FonteLocalizacao = suspend () -> Coordenadas?

/**
 * Consulta todas as fontes AO MESMO TEMPO e devolve a primeira coordenada encontrada,
 * cancelando as demais. Fontes que devolvem null ou dão erro são ignoradas.
 */
suspend fun primeiraLocalizacao(fontes: List<FonteLocalizacao>): Coordenadas? {
    if (fontes.isEmpty()) return null

    return coroutineScope {
        val respostas = Channel<Coordenadas?>(capacity = fontes.size)
        val consultas = fontes.map { fonte ->
            launch {
                val resposta = try {
                    fonte()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                respostas.send(resposta)
            }
        }

        var encontrada: Coordenadas? = null
        for (i in fontes.indices) {
            encontrada = respostas.receive()
            if (encontrada != null) break
        }
        consultas.forEach { it.cancel() }
        encontrada
    }
}
