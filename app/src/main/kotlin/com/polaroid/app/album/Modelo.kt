package com.polaroid.app.album

import java.time.ZonedDateTime

data class Coordenadas(val latitude: Double, val longitude: Double)

/** Local aproximado da foto; [nome] é null quando não deu para descobrir (ex.: sem internet). */
data class LocalFoto(val coordenadas: Coordenadas, val nome: String?)

/** Item do álbum. [id] é a URI da foto na galeria. */
data class FotoAlbum(val id: String, val dataHora: ZonedDateTime)

data class DetalhesFoto(val foto: FotoAlbum, val local: LocalFoto?)

/** Onde as fotos ficam salvas (galeria do celular, pasta Polaroid). */
interface RepositorioFotos {
    /** Fotos do álbum, em qualquer ordem. */
    suspend fun listar(): List<FotoAlbum>

    /** Move a foto temporária para a galeria, gravando data/hora e local dentro dela. */
    suspend fun salvar(arquivoTemporario: String, dataHora: ZonedDateTime, local: LocalFoto?): FotoAlbum

    suspend fun lerLocal(foto: FotoAlbum): LocalFoto?

    suspend fun atualizarNomeLocal(foto: FotoAlbum, nome: String)

    suspend fun apagar(foto: FotoAlbum)
}

/** Localização aproximada atual; null sem permissão ou indisponível. */
fun interface ProvedorLocalizacao {
    suspend fun localizacaoAtual(): Coordenadas?
}

/** Nome do lugar ("Bairro, Cidade"); null sem internet ou desconhecido. */
fun interface ProvedorNomeLocal {
    suspend fun nomeDoLocal(coordenadas: Coordenadas): String?
}
