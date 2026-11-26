package com.polaroid.app.album

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

/** Câmera do app (CameraX). Entrega o caminho de um arquivo temporário com a foto. */
@Composable
fun CameraTela(aoCapturar: (String) -> Unit, aoVoltar: () -> Unit) {
    val context = LocalContext.current
    val dono = LocalLifecycleOwner.current
    val executorPrincipal = remember { ContextCompat.getMainExecutor(context) }
    val captura = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    var capturando by remember { mutableStateOf(false) }

    BackHandler(onBack = aoVoltar)

    DisposableEffect(Unit) {
        val futuro = ProcessCameraProvider.getInstance(context)
        onDispose {
            futuro.addListener({ futuro.get().unbindAll() }, executorPrincipal)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).also { visor ->
                    val futuro = ProcessCameraProvider.getInstance(ctx)
                    futuro.addListener({
                        val provedor = futuro.get()
                        val previa = Preview.Builder().build().also {
                            it.setSurfaceProvider(visor.surfaceProvider)
                        }
                        provedor.unbindAll()
                        provedor.bindToLifecycle(dono, CameraSelector.DEFAULT_BACK_CAMERA, previa, captura)
                    }, executorPrincipal)
                }
            },
        )

        FilledTonalButton(
            onClick = aoVoltar,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        ) { Text("Voltar") }

        Button(
            onClick = {
                if (capturando) return@Button
                capturando = true
                val arquivo = File(context.cacheDir, "captura_${System.currentTimeMillis()}.jpg")
                captura.takePicture(
                    ImageCapture.OutputFileOptions.Builder(arquivo).build(),
                    executorPrincipal,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(resultado: ImageCapture.OutputFileResults) {
                            capturando = false
                            aoCapturar(arquivo.absolutePath)
                        }

                        override fun onError(erro: ImageCaptureException) {
                            capturando = false
                            arquivo.delete()
                            Toast.makeText(context, "Não foi possível tirar a foto", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
                .size(76.dp)
                .border(4.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                .semantics { contentDescription = "Tirar foto" },
        ) {
            if (capturando) CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}
