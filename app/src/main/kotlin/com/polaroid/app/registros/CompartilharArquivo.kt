package com.polaroid.app.registros

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Salva o CSV numa pasta temporária do app e abre o "Compartilhar" do Android
 * (WhatsApp, Drive, e-mail…). O arquivo anterior é substituído a cada exportação.
 */
fun compartilharCsv(context: Context, arquivo: ArquivoExportacao) {
    val pasta = File(context.cacheDir, "exportacoes").apply {
        mkdirs()
        listFiles()?.forEach { it.delete() }
    }
    val destino = File(pasta, arquivo.nome).apply { writeText(arquivo.conteudo, Charsets.UTF_8) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.arquivos", destino)

    val envio = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, arquivo.nome.removeSuffix(".csv").replace('_', ' '))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(envio, "Exportar relatório"))
}
