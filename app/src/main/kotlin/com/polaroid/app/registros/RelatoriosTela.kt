package com.polaroid.app.registros

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.polaroid.app.calculadora.Formatador
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FORMATO_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy  HH:mm")

/** Etapa 1: lista de compras, vendas e perdas. Painel e gráficos entram nas próximas etapas. */
@Composable
fun RelatoriosTela(viewModel: RegistrosViewModel = viewModel(factory = FabricaRegistrosViewModel)) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    var apagando by remember { mutableStateOf<Registro?>(null) }
    val avisos = remember { SnackbarHostState() }

    LaunchedEffect(estado.mensagem) {
        estado.mensagem?.let {
            avisos.showSnackbar(it)
            viewModel.aoDispensarMensagem()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(avisos) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtendedFloatingActionButton(onClick = viewModel::abrirNovaPerda) { Text("− Perda") }
                ExtendedFloatingActionButton(onClick = { viewModel.abrirNovaVenda() }) { Text("+ Venda") }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Text("Relatórios", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 16.dp))

            if (estado.registros.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhum registro ainda.\nRegistre compras pela Calculadora e vendas pelo botão \"+ Venda\".",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 160.dp), // espaço para os botões flutuantes
                ) {
                    items(estado.registros, key = { it.id }) { registro ->
                        ItemRegistro(
                            registro = registro,
                            aoEditar = { viewModel.abrirEdicao(registro) },
                            aoApagar = { apagando = registro },
                        )
                    }
                }
            }
        }
    }

    estado.formulario?.let { formulario ->
        FormularioRegistroDialog(
            formulario = formulario,
            hoje = LocalDate.now(),
            aoMudarQuantidade = viewModel::aoMudarQuantidade,
            aoMudarValor = viewModel::aoMudarValor,
            aoMudarDataHora = viewModel::aoMudarDataHora,
            aoMudarForma = viewModel::aoMudarFormaPagamento,
            aoSalvar = viewModel::salvar,
            aoCancelar = viewModel::cancelar,
        )
    }

    apagando?.let { registro ->
        AlertDialog(
            onDismissRequest = { apagando = null },
            title = { Text("Apagar registro?") },
            text = { Text("Isso não pode ser desfeito.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.apagar(registro)
                    apagando = null
                }) { Text("Apagar") }
            },
            dismissButton = { TextButton(onClick = { apagando = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ItemRegistro(registro: Registro, aoEditar: () -> Unit, aoApagar: () -> Unit) {
    val (titulo, detalhe, valor) = when (registro) {
        is Registro.Compra -> Triple(
            "🛒 Compra",
            "${registro.quantidadeFotos} fotos",
            "−" + Formatador.moeda(Centavos.paraReais(registro.valorPagoCentavos)),
        )
        is Registro.Venda -> Triple(
            "💰 Venda · ${registro.formaPagamento.rotulo}",
            "${registro.quantidadeFotos} × ${Formatador.moeda(Centavos.paraReais(registro.precoPorFotoCentavos))}",
            "+" + Formatador.moeda(Centavos.paraReais(registro.precoPorFotoCentavos * registro.quantidadeFotos)),
        )
        is Registro.Perda -> Triple("🗑 Fotos perdidas", "${registro.quantidadeFotos} fotos", "")
    }
    val corValor = when (registro) {
        is Registro.Compra -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = aoEditar)) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(titulo, fontWeight = FontWeight.Bold)
                Text(detalhe)
                Text(
                    FORMATO_DATA_HORA.format(registro.dataHora.atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (valor.isNotEmpty()) Text(valor, color = corValor, fontWeight = FontWeight.Bold)
                TextButton(onClick = aoApagar) { Text("Apagar") }
            }
        }
    }
}
