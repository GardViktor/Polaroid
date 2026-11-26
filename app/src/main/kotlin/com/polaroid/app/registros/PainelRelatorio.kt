package com.polaroid.app.registros

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.polaroid.app.calculadora.Formatador
import java.math.BigDecimal

private val VERDE = Color(0xFF2E7D32)

/** Mostra "≈" quando o valor tem fração de centavo e precisa ser arredondado na tela. */
private fun dinheiro(valor: BigDecimal): String {
    val aproximado = valor.stripTrailingZeros().scale() > 2
    return (if (aproximado) "≈ " else "") + Formatador.moeda(valor)
}

@Composable
fun SeletorPeriodo(
    periodo: Periodo,
    podeAvancar: Boolean,
    aoEscolherTipo: (TipoPeriodo) -> Unit,
    aoAnterior: () -> Unit,
    aoProximo: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TipoPeriodo.entries.forEach { tipo ->
                FilterChip(
                    selected = tipo == periodo.tipo,
                    onClick = { aoEscolherTipo(tipo) },
                    label = { Text(tipo.rotulo) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = aoAnterior) { Text("◀") }
            Text(
                periodo.rotulo(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = aoProximo, enabled = podeAvancar) { Text("▶") }
        }
    }
}

@Composable
fun CartoesPainel(painel: DadosPainel, limiteEstoqueBaixo: Int) {
    val resumo = painel.resumo
    val situacao = painel.situacao

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Situação geral (não depende do período)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            CartaoNumero(
                titulo = "Saldo de caixa",
                valor = dinheiro(situacao.saldoCaixa),
                cor = if (situacao.saldoCaixa.signum() < 0) MaterialTheme.colorScheme.error else VERDE,
                modifier = Modifier.weight(1f),
            )
            CartaoNumero(
                titulo = "Estoque",
                valor = "${situacao.estoque} fotos",
                cor = if (painel.estoqueBaixo) MaterialTheme.colorScheme.error else Color.Unspecified,
                modifier = Modifier.weight(1f),
            )
        }
        if (painel.estoqueBaixo) {
            Text(
                if (situacao.estoque <= 0) "⚠ Sem estoque: registre uma compra." else "⚠ Estoque baixo (limite: $limiteEstoqueBaixo fotos)",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // Período escolhido
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinhaPainel("Fotos vendidas", "${resumo.fotosVendidas}", painel.variacaoFotosVendidas)
                LinhaPainel("Faturamento", dinheiro(resumo.faturamento), painel.variacaoFaturamento)
                LinhaPainel("Taxas de cartão", "− " + dinheiro(resumo.taxas))
                LinhaPainel(
                    "Custo das fotos" + if (resumo.fotosPerdidas > 0) " (inclui ${resumo.fotosPerdidas} perdidas)" else "",
                    "− " + dinheiro(resumo.custoFotos),
                )
                LinhaPainel(
                    "Lucro",
                    dinheiro(resumo.lucro),
                    painel.variacaoLucro,
                    destaque = true,
                    cor = if (resumo.lucro.signum() < 0) MaterialTheme.colorScheme.error else VERDE,
                )
                resumo.precoMedioPorFoto?.let { LinhaPainel("Preço médio por foto", dinheiro(it)) }
            }
        }
    }
}

@Composable
private fun CartaoNumero(titulo: String, valor: String, cor: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(titulo, style = MaterialTheme.typography.labelMedium)
            Text(valor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = cor)
        }
    }
}

@Composable
private fun LinhaPainel(
    rotulo: String,
    valor: String,
    variacao: BigDecimal? = null,
    destaque: Boolean = false,
    cor: Color = Color.Unspecified,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(rotulo, modifier = Modifier.weight(1f), fontWeight = if (destaque) FontWeight.Bold else FontWeight.Normal)
        Column(horizontalAlignment = Alignment.End) {
            Text(valor, color = cor, fontWeight = if (destaque) FontWeight.Bold else FontWeight.Normal)
            variacao?.let {
                val sobe = it.signum() >= 0
                Text(
                    (if (sobe) "↑ " else "↓ ") + Formatador.percentual(it.abs()) + " vs. anterior",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sobe) VERDE else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Taxas por forma de pagamento e limite do aviso de estoque baixo. */
@Composable
fun ConfiguracoesDialog(atual: ConfiguracaoTaxas, aoSalvar: (ConfiguracaoTaxas) -> Unit, aoCancelar: () -> Unit) {
    var taxas by remember { mutableStateOf(FormaPagamento.entries.associateWith { TextoTaxa.dePontosBase(atual.taxa(it)) }) }
    var limite by remember { mutableStateOf(atual.limiteEstoqueBaixo.toString()) }

    val taxasValidas = taxas.mapValues { TextoTaxa.paraPontosBase(it.value) }
    val limiteValido = limite.toIntOrNull()?.takeIf { it >= 0 }
    val podeSalvar = taxasValidas.values.all { it != null } && limiteValido != null

    AlertDialog(
        onDismissRequest = aoCancelar,
        title = { Text("Configurações") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Taxas valem para vendas novas. Vendas já registradas mantêm a taxa da época.",
                    style = MaterialTheme.typography.bodySmall,
                )
                FormaPagamento.entries.forEach { forma ->
                    OutlinedTextField(
                        value = taxas.getValue(forma),
                        onValueChange = { texto -> taxas = taxas + (forma to texto) },
                        label = { Text("Taxa ${forma.rotulo}") },
                        suffix = { Text("%") },
                        isError = taxasValidas[forma] == null,
                        supportingText = if (taxasValidas[forma] == null) ({ Text("De 0 a 100, até 2 casas") }) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = limite,
                    onValueChange = { texto -> if (texto.all(Char::isDigit) && texto.length <= 5) limite = texto },
                    label = { Text("Avisar estoque baixo com até") },
                    suffix = { Text("fotos") },
                    isError = limiteValido == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = podeSalvar,
                onClick = {
                    aoSalvar(
                        ConfiguracaoTaxas(
                            pontosBase = taxasValidas.mapValues { it.value!! },
                            limiteEstoqueBaixo = limiteValido!!,
                        ),
                    )
                },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = aoCancelar) { Text("Cancelar") } },
    )
}
