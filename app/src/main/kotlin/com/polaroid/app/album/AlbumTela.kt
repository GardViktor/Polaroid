package com.polaroid.app.album

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage

@Composable
fun AlbumTela(viewModel: AlbumViewModel = viewModel(factory = FabricaAlbumViewModel)) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var mostrandoCamera by rememberSaveable { mutableStateOf(false) }
    var cameraNegada by rememberSaveable { mutableStateOf(false) }

    val pedirPermissoes = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { resultado ->
        // Localização negada não impede a foto: só fica sem local
        if (resultado[Manifest.permission.CAMERA] == true) {
            cameraNegada = false
            viewModel.aoAbrirCamera()
            mostrandoCamera = true
        } else {
            cameraNegada = true
        }
    }
    val abrirCamera = { pedirPermissoes.launch(permissoesNecessarias()) }

    when {
        mostrandoCamera -> CameraTela(
            aoCapturar = {
                viewModel.aoFotoCapturada(it)
                mostrandoCamera = false
            },
            aoVoltar = { mostrandoCamera = false },
        )

        estado.fotoAberta != null -> DetalheFoto(
            detalhes = estado.fotoAberta!!,
            confirmandoApagar = estado.confirmandoApagar,
            aoVoltar = viewModel::aoFecharFoto,
            aoPedirApagar = viewModel::aoPedirApagar,
            aoConfirmarApagar = viewModel::aoConfirmarApagar,
            aoCancelarApagar = viewModel::aoCancelarApagar,
        )

        else -> GradeAlbum(
            estado = estado,
            cameraNegada = cameraNegada,
            aoTirarFoto = abrirCamera,
            aoAbrirFoto = viewModel::aoAbrirFoto,
            aoAbrirConfiguracoes = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
            aoDispensarErro = viewModel::aoDispensarErro,
        )
    }
}

private fun permissoesNecessarias(): Array<String> = buildList {
    add(Manifest.permission.CAMERA)
    // As duas juntas: o Android mostra "Precisa" / "Aproximada" para a pessoa escolher
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    // Android 9 ou anterior precisa de permissão para gravar na galeria
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
}.toTypedArray()

@Composable
private fun GradeAlbum(
    estado: EstadoAlbum,
    cameraNegada: Boolean,
    aoTirarFoto: () -> Unit,
    aoAbrirFoto: (FotoAlbum) -> Unit,
    aoAbrirConfiguracoes: () -> Unit,
    aoDispensarErro: () -> Unit,
) {
    val avisos = remember { SnackbarHostState() }
    LaunchedEffect(estado.erro) {
        estado.erro?.let {
            avisos.showSnackbar(it)
            aoDispensarErro()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(avisos) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = aoTirarFoto) { Text("📷  Tirar foto") }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Álbum", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))

            if (cameraNegada) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Para tirar fotos, permita o acesso à câmera.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = aoAbrirConfiguracoes) { Text("Abrir configurações") }
                }
            }

            if (estado.salvando) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    Text("Salvando foto…")
                }
            }

            when {
                estado.carregando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                estado.fotos.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhuma foto ainda.\nToque em \"Tirar foto\" para começar.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(estado.fotos, key = { it.id }) { foto ->
                        // Coil carrega miniatura reduzida (não a foto inteira) e mantém em cache
                        AsyncImage(
                            model = Uri.parse(foto.id),
                            contentDescription = "Foto de ${FormatadorAlbum.data(foto.dataHora)} às ${FormatadorAlbum.hora(foto.dataHora)}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { aoAbrirFoto(foto) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetalheFoto(
    detalhes: DetalhesFoto,
    confirmandoApagar: Boolean,
    aoVoltar: () -> Unit,
    aoPedirApagar: () -> Unit,
    aoConfirmarApagar: () -> Unit,
    aoCancelarApagar: () -> Unit,
) {
    BackHandler(onBack = aoVoltar)
    val foto = detalhes.foto

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FilledTonalButton(onClick = aoVoltar) { Text("Voltar") }
            OutlinedButton(onClick = aoPedirApagar) { Text("Apagar") }
        }

        AsyncImage(
            model = Uri.parse(foto.id),
            contentDescription = "Foto",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        Text("📅 ${FormatadorAlbum.data(foto.dataHora)}   🕐 ${FormatadorAlbum.hora(foto.dataHora)}")
        Text("📍 ${FormatadorAlbum.local(detalhes.local, detalhes.motivoSemLocal)}")
    }

    if (confirmandoApagar) {
        AlertDialog(
            onDismissRequest = aoCancelarApagar,
            title = { Text("Apagar foto?") },
            text = { Text("A foto será apagada do álbum e da galeria do celular. Isso não pode ser desfeito.") },
            confirmButton = {
                Button(onClick = aoConfirmarApagar) { Text("Apagar") }
            },
            dismissButton = {
                TextButton(onClick = aoCancelarApagar) { Text("Cancelar") }
            },
        )
    }
}
