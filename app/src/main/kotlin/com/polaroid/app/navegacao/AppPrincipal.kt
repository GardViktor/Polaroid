package com.polaroid.app.navegacao

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.polaroid.app.album.AlbumTela
import com.polaroid.app.busca.BuscarTela
import com.polaroid.app.calculadora.CalculadoraTela

/**
 * Tela raiz com menu inferior. A aba escolhida sobrevive a rotação; o estado da
 * calculadora fica no ViewModel (escopo da Activity), então não se perde ao trocar de aba.
 */
@Composable
fun AppPrincipal() {
    var destinoAtual by rememberSaveable { mutableStateOf(Destino.INICIAL) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destino.entries.forEach { destino ->
                    NavigationBarItem(
                        selected = destino == destinoAtual,
                        onClick = { destinoAtual = destino },
                        icon = { Icon(painterResource(destino.icone), contentDescription = null) },
                        label = { Text(destino.rotulo) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            when (destinoAtual) {
                Destino.CALCULADORA -> CalculadoraTela()
                Destino.BUSCAR -> BuscarTela()
                Destino.ALBUM -> AlbumTela()
            }
        }
    }
}
