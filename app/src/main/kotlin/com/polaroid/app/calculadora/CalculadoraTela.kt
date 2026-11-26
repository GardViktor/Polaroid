package com.polaroid.app.calculadora

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CalculadoraTela(viewModel: CalculadoraViewModel = viewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    Scaffold { padding ->
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

            CampoNumerico(
                rotulo = "Valor pago (filmes + frete)",
                texto = estado.valorPago,
                erro = estado.erros[Campo.VALOR_PAGO],
                monetario = true,
                aoDigitar = { viewModel.aoDigitar(Campo.VALOR_PAGO, it) },
                aoSair = { viewModel.aoSairDoCampo(Campo.VALOR_PAGO) },
            )
            CampoNumerico(
                rotulo = "Quantidade de fotos",
                texto = estado.quantidade,
                erro = estado.erros[Campo.QUANTIDADE],
                monetario = false,
                aoDigitar = { viewModel.aoDigitar(Campo.QUANTIDADE, it) },
                aoSair = { viewModel.aoSairDoCampo(Campo.QUANTIDADE) },
            )
            CampoNumerico(
                rotulo = "Preço de venda por foto",
                texto = estado.precoVenda,
                erro = estado.erros[Campo.PRECO_VENDA],
                monetario = true,
                ultimo = true,
                aoDigitar = { viewModel.aoDigitar(Campo.PRECO_VENDA, it) },
                aoSair = { viewModel.aoSairDoCampo(Campo.PRECO_VENDA) },
            )

            estado.resultado?.let { CartaoResultado(it) }
        }
    }
}

@Composable
private fun CampoNumerico(
    rotulo: String,
    texto: String,
    erro: String?,
    monetario: Boolean,
    aoDigitar: (String) -> Unit,
    aoSair: () -> Unit,
    ultimo: Boolean = false,
) {
    // onFocusChanged dispara "sem foco" ao montar; só conta saída depois de ter tido foco
    var teveFoco by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = texto,
        onValueChange = aoDigitar,
        label = { Text(rotulo) },
        prefix = if (monetario) ({ Text("R$ ") }) else null,
        isError = erro != null,
        supportingText = erro?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (monetario) KeyboardType.Decimal else KeyboardType.Number,
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
private fun CartaoResultado(resultado: ResultadoTela) {
    val corLucro = if (resultado.prejuizo) MaterialTheme.colorScheme.error else Color.Unspecified
    val aproximado = "≈ "
    val prefixoPorFoto = if (resultado.valoresPorFotoAproximados) aproximado else ""
    val prefixoMargem = if (resultado.margemAproximada) aproximado else ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LinhaResultado("Custo por foto", prefixoPorFoto + resultado.custoPorFoto)
            LinhaResultado("Lucro por foto", prefixoPorFoto + resultado.lucroPorFoto, corLucro)
            LinhaResultado("Lucro total", resultado.lucroTotal, corLucro, destaque = true)
            LinhaResultado("Margem de lucro", prefixoMargem + resultado.margem, corLucro)

            if (resultado.prejuizo) {
                Text(
                    "Prejuízo: o preço de venda está abaixo do custo",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (resultado.valoresPorFotoAproximados || resultado.margemAproximada) {
                Text(
                    "≈ valor arredondado",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
