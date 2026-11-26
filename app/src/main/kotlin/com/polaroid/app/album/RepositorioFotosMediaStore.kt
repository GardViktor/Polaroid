package com.polaroid.app.album

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.edit
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Fotos na galeria do celular (Pictures/Polaroid), visíveis em qualquer app de galeria
 * e preservadas se o app for desinstalado.
 *
 * Data/hora e coordenadas ficam DENTRO da foto (EXIF), então não se perdem.
 * O nome do lugar é só um cache local (dá para recalcular pelas coordenadas).
 */
class RepositorioFotosMediaStore(private val context: Context) : RepositorioFotos {

    private val resolver = context.contentResolver
    private val nomesLocais = context.getSharedPreferences("nomes_locais", Context.MODE_PRIVATE)

    private val colecao: Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    override suspend fun listar(): List<FotoAlbum> = withContext(Dispatchers.IO) {
        val projecao = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
        )
        val (selecao, argumentos) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?" to arrayOf("$PASTA_RELATIVA%")
        } else {
            @Suppress("DEPRECATION")
            "${MediaStore.Images.Media.DATA} LIKE ?" to arrayOf("%/$PASTA_RELATIVA%")
        }

        buildList {
            resolver.query(colecao, projecao, selecao, argumentos, null)?.use { cursor ->
                val colunaId = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val colunaTirada = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val colunaAdicionada = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val uri = ContentUris.withAppendedId(colecao, cursor.getLong(colunaId))
                    val instante = if (!cursor.isNull(colunaTirada)) {
                        Instant.ofEpochMilli(cursor.getLong(colunaTirada))
                    } else {
                        Instant.ofEpochSecond(cursor.getLong(colunaAdicionada))
                    }
                    add(FotoAlbum(uri.toString(), instante.atZone(ZoneId.systemDefault())))
                }
            }
        }
    }

    override suspend fun salvar(
        arquivoTemporario: String,
        dataHora: ZonedDateTime,
        local: LocalFoto?,
    ): FotoAlbum = withContext(Dispatchers.IO) {
        val temporario = File(arquivoTemporario)
        try {
            gravarExif(temporario, dataHora, local)
            val uri = inserirNaGaleria(temporario, dataHora)
            local?.nome?.let { nomesLocais.edit { putString(uri.toString(), it) } }
            FotoAlbum(uri.toString(), dataHora)
        } finally {
            temporario.delete()
        }
    }

    override suspend fun lerLocal(foto: FotoAlbum): LocalFoto? = withContext(Dispatchers.IO) {
        val coordenadas = resolver.openInputStream(Uri.parse(foto.id))?.use { entrada ->
            ExifInterface(entrada).latLong?.let { Coordenadas(it[0], it[1]) }
        } ?: return@withContext null
        LocalFoto(coordenadas, nomesLocais.getString(foto.id, null))
    }

    override suspend fun atualizarNomeLocal(foto: FotoAlbum, nome: String) {
        nomesLocais.edit { putString(foto.id, nome) }
    }

    override suspend fun apagar(foto: FotoAlbum) = withContext(Dispatchers.IO) {
        resolver.delete(Uri.parse(foto.id), null, null)
        nomesLocais.edit { remove(foto.id) }
    }

    private fun gravarExif(arquivo: File, dataHora: ZonedDateTime, local: LocalFoto?) {
        val exif = ExifInterface(arquivo)
        val texto = FORMATO_EXIF.format(dataHora)
        val fuso = dataHora.offset.id.let { if (it == "Z") "+00:00" else it }
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, texto)
        exif.setAttribute(ExifInterface.TAG_DATETIME, texto)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, fuso)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, fuso)
        local?.let { exif.setLatLong(it.coordenadas.latitude, it.coordenadas.longitude) }
        exif.saveAttributes()
    }

    private fun inserirNaGaleria(arquivo: File, dataHora: ZonedDateTime): Uri {
        val nomeArquivo = "Polaroid_${FORMATO_NOME.format(dataHora)}.jpg"
        val valores = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, nomeArquivo)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, dataHora.toInstant().toEpochMilli())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, PASTA_RELATIVA)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val pasta = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Polaroid")
                pasta.mkdirs()
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA, File(pasta, nomeArquivo).absolutePath)
            }
        }

        val uri = resolver.insert(colecao, valores) ?: error("A galeria recusou a foto")
        try {
            resolver.openOutputStream(uri)?.use { saida ->
                arquivo.inputStream().use { it.copyTo(saida) }
            } ?: error("Não foi possível gravar a foto")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    private companion object {
        const val PASTA_RELATIVA = "Pictures/Polaroid/"
        val FORMATO_EXIF: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
        val FORMATO_NOME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    }
}
