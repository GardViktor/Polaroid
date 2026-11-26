package com.polaroid.app.album

import java.time.ZonedDateTime

data class Coordenadas(val latitude: Double, val longitude: Double)

/** Local aproximado da foto; [nome] é null quando não deu para descobrir (ex.: sem internet). */
data class LocalFoto(val coordenadas: Coordenadas, val nome: String?)

/** Por que uma foto ficou sem local. */
enum class MotivoSemLocal { SEM_PERMISSAO, LOCALIZACAO_DESLIGADA, NAO_ENCONTRADO }

sealed interface ResultadoLocalizacao {
    data class Encontrada(val coordenadas: Coordenadas) : ResultadoLocalizacao
    data class Indisponivel(val motivo: MotivoSemLocal) : ResultadoLocalizacao
}

/** Item do álbum. [id] é a URI da foto na galeria. */
data class FotoAlbum(val id: String, val dataHora: ZonedDateTime)

/** [motivoSemLocal] só é preenchido quando [local] é null e o motivo é conhecido. */
data class DetalhesFoto(
    val foto: FotoAlbum,
    val local: LocalFoto?,
    val motivoSemLocal: MotivoSemLocal? = null,
)

/** Onde as fotos ficam salvas (galeria do celular, pasta Polaroid). */
interface RepositorioFotos {
    /** Fotos do álbum, em qualquer ordem. */
    suspend fun listar(): List<FotoAlbum>

    /** Move a foto temporária para a galeria, gravando data/hora e local (ou o motivo de não ter) . */
    suspend fun salvar(
        arquivoTemporario: String,
        dataHora: ZonedDateTime,
        local: LocalFoto?,
        motivoSemLocal: MotivoSemLocal?,
    ): FotoAlbum

    suspend fun lerLocal(foto: FotoAlbum): LocalFoto?

    suspend fun lerMotivoSemLocal(foto: FotoAlbum): MotivoSemLocal?

    suspend fun atualizarNomeLocal(foto: FotoAlbum, nome: String)

    suspend fun apagar(foto: FotoAlbum)
}

/** Localização aproximada atual, ou o motivo de não conseguir. */
fun interface ProvedorLocalizacao {
    suspend fun localizacaoAtual(): ResultadoLocalizacao
}

/** Nome do lugar ("Bairro, Cidade"); null sem internet ou desconhecido. */
fun interface ProvedorNomeLocal {
    suspend fun nomeDoLocal(coordenadas: Coordenadas): String?
}
