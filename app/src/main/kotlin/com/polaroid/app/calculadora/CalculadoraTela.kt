package com.polaroid.app.calculadora

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import com.polaroid.app.registros.FabricaRegistrosViewModel
import com.polaroid.app.registros.FormularioRegistroDialog
import com.polaroid.app.registros.RegistrosViewModel
import java.time.LocalDate
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private const val APROXIMADO = "≈ "
private const val MAXIMO_PACKS = 99

@Composable
fun CalculadoraTela(
    viewModel: CalculadoraViewModel = viewModel(),
    registros: RegistrosViewModel = viewModel(factory = FabricaRegistrosViewModel),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val estadoRegistros by registros.estado.collectAsStateWithLifecycle()
    var confirmandoCompra by remember { mutableStateOf<DadosCompra?>(null) }
    val avisos = remember { SnackbarHostState() }

    LaunchedEffect(estadoRegistros.mensagem) {
        estadoRegistros.mensagem?.let {
            avisos.showSnackbar(it)
            registros.aoDispensarMensagem()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(avisos) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Calculadora de Lucro", style = MaterialTheme.typography.headlineSmall)

            CampoValor(
                rotulo = "Valor pago (filmes + frete)",
                texto = estado.valorPago,
                erro = estado.erros[Campo.VALOR_PAGO],
                aoDigitar = { viewModel.aoDigitar(Campo.VALOR_PAGO, it) },
                aoSair = { viewModel.aoSairDoCampo(Campo.VALOR_PAGO) },
            )

            SeletorPack(
                selecionado = estado.tamanhoPack,
                aoSelecionar = viewModel::aoSelecionarPack,
            )

            ContadorPacks(
                quantidade = estado.quantidadePacks,
                totalFotos = estado.totalFotos,
                aoAumentar = viewModel::aoAumentarPacks,
                aoDiminuir = viewModel::aoDiminuirPacks,
            )

            estado.custo?.let { CartaoCusto(it) }

            CampoValor(
                rotulo = "Preço de venda por foto",
                texto = estado.precoVenda,
                erro = estado.erros[Campo.PRECO_VENDA],
                aviso = estado.avisoPreco,
                ultimo = true,
                aoDigitar = { viewModel.aoDigitar(Campo.PRECO_VENDA, it) },
                aoSair = { viewModel.aoSairDoCampo(Campo.PRECO_VENDA) },
            )

            estado.resultado?.let { CartaoResultado(it) }

            // Registrar compra fica sempre visível, logo acima de Registrar venda;
            // desativado (com dica) até valor pago e pack estarem preenchidos
            val podeRegistrarCompra = estado.custo != null
            OutlinedButton(
                onClick = { confirmandoCompra = viewModel.dadosCompra() },
                enabled = podeRegistrarCompra,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("🛒 Registrar compra") }
            if (!podeRegistrarCompra) {
                Text(
                    "Para registrar a compra, preencha o valor pago e escolha o pack.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(
                onClick = { registros.abrirNovaVenda(precoSugerido = estado.precoVenda) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("💰 Registrar venda") }
        }
    }

    confirmandoCompra?.let { compra ->
        AlertDialog(
            onDismissRequest = { confirmandoCompra = null },
            title = { Text("Registrar compra?") },
            text = {
                Text("${compra.totalFotos} fotos por ${Formatador.moeda(compra.valorPago)}, com data e hora de agora. Você pode editar depois em Relatórios.")
            },
            confirmButton = {
                Button(onClick = {
                    registros.registrarCompra(compra.valorPago, compra.totalFotos)
                    confirmandoCompra = null
                }) { Text("Registrar") }
            },
            dismissButton = { TextButton(onClick = { confirmandoCompra = null }) { Text("Cancelar") } },
        )
    }

    estadoRegistros.formulario?.let { formulario ->
        FormularioRegistroDialog(
            formulario = formulario,
            hoje = LocalDate.now(),
            aoMudarQuantidade = registros::aoMudarQuantidade,
            aoSomarQuantidade = registros::aoSomarQuantidade,
            aoMudarValor = registros::aoMudarValor,
            aoMudarDataHora = registros::aoMudarDataHora,
            aoMudarForma = registros::aoMudarFormaPagamento,
            aoSalvar = registros::salvar,
            aoCancelar = registros::cancelar,
        )
    }
}

@Composable
private fun CampoValor(
    rotulo: String,
    texto: String,
    erro: String?,
    aoDigitar: (String) -> Unit,
    aoSair: () -> Unit,
    aviso: String? = null,
    ultimo: Boolean = false,
) {
    // onFocusChanged dispara "sem foco" ao montar; só conta saída depois de ter tido foco
    var teveFoco by remember { mutableStateOf(false) }
    val mensagem = erro ?: aviso

    OutlinedTextField(
        value = texto,
        onValueChange = aoDigitar,
        label = { Text(rotulo) },
        prefix = { Text("R$ ") },
        isError = mensagem != null,
        supportingText = mensagem?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = if (ultimo) ImeAction.Done else ImeAction.Next,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged {
                if (it.isFocused) {
                    teveFoco = true
                } else if (teveFoco) {
                    aoSair()
                }
            },
    )
}

@Composable
private fun SeletorPack(selecionado: Int?, aoSelecionar: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Tamanho do pack (fotos)", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalculadoraViewModel.TAMANHOS_PACK.forEach { tamanho ->
                FilterChip(
                    selected = tamanho == selecionado,
                    onClick = { aoSelecionar(tamanho) },
                    label = { Text("$tamanho") },
                )
            }
        }
    }
}

@Composable
private fun ContadorPacks(
    quantidade: Int,
    totalFotos: Int?,
    aoAumentar: () -> Unit,
    aoDiminuir: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Quantidade de packs", modifier = Modifier.weight(1f))
        FilledTonalButton(
            onClick = aoDiminuir,
            enabled = quantidade > 1,
            modifier = Modifier.semantics { contentDescription = "Diminuir packs" },
        ) { Text("−") }
        Text(
            "$quantidade",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 32.dp),
        )
        FilledTonalButton(
            onClick = aoAumentar,
            enabled = quantidade < MAXIMO_PACKS,
            modifier = Modifier.semantics { contentDescription = "Aumentar packs" },
        ) { Text("+") }
    }
    if (totalFotos != null) {
        Text(
            "Total: $totalFotos fotos",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CartaoCusto(custo: CustoTela) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LinhaResultado(
                "Custo por foto",
                (if (custo.custoAproximado) APROXIMADO else "") + custo.custoPorFoto,
            )
            LinhaResultado("Preço mínimo sem prejuízo", custo.precoMinimo, destaque = true)
            if (custo.custoAproximado) {
                NotaArredondamento()
            }
        }
    }
}

@Composable
private fun CartaoResultado(resultado: ResultadoTela) {
    val corLucro = if (resultado.prejuizo) MaterialTheme.colorScheme.error else Color.Unspecified

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LinhaResultado(
                "Lucro por foto",
                (if (resultado.lucroPorFotoAproximado) APROXIMADO else "") + resultado.lucroPorFoto,
                corLucro,
            )
            LinhaResultado("Lucro total", resultado.lucroTotal, corLucro, destaque = true)
            LinhaResultado(
                "Margem de lucro",
                (if (resultado.margemAproximada) APROXIMADO else "") + resultado.margem,
                corLucro,
            )
            if (resultado.prejuizo) {
                Text(
                    "Prejuízo: o preço de venda está abaixo do custo",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (resultado.lucroPorFotoAproximado || resultado.margemAproximada) {
                NotaArredondamento()
            }
        }
    }
}

@Composable
private fun NotaArredondamento() {
    Text(
        "≈ valor arredondado",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LinhaResultado(
    rotulo: String,
    valor: String,
    cor: Color = Color.Unspecified,
    destaque: Boolean = false,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(rotulo)
        Text(
            valor,
            color = cor,
            fontWeight = if (destaque) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
