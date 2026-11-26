package com.polaroid.app.registros

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm")

/** Criar/editar compra, venda ou perda. Datas futuras não podem nem ser escolhidas. */
@Composable
fun FormularioRegistroDialog(
    formulario: Formulario,
    hoje: LocalDate,
    aoMudarQuantidade: (String) -> Unit,
    aoSelecionarQuantidade: (Int) -> Unit,
    aoAumentarQuantidade: () -> Unit,
    aoDiminuirQuantidade: () -> Unit,
    aoMudarValor: (String) -> Unit,
    aoMudarDataHora: (LocalDateTime) -> Unit,
    aoMudarForma: (FormaPagamento) -> Unit,
    aoSalvar: () -> Unit,
    aoCancelar: () -> Unit,
) {
    var escolhendoData by remember { mutableStateOf(false) }
    var escolhendoHora by remember { mutableStateOf(false) }

    val titulo = when (formulario.tipo) {
        TipoRegistro.COMPRA -> if (formulario.idEditando == null) "Nova compra" else "Editar compra"
        TipoRegistro.VENDA -> if (formulario.idEditando == null) "Nova venda" else "Editar venda"
        TipoRegistro.PERDA -> if (formulario.idEditando == null) "Fotos perdidas" else "Editar perda"
    }

    AlertDialog(
        onDismissRequest = aoCancelar,
        title = { Text(titulo) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { escolhendoData = true }, modifier = Modifier.weight(1f)) {
                        Text("📅 ${FORMATO_DATA.format(formulario.dataHora)}")
                    }
                    OutlinedButton(onClick = { escolhendoHora = true }, modifier = Modifier.weight(1f)) {
                        Text("🕐 ${FORMATO_HORA.format(formulario.dataHora)}")
                    }
                }
                formulario.erros[CampoRegistro.DATA_HORA]?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                if (formulario.tipo == TipoRegistro.COMPRA) {
                    OutlinedTextField(
                        value = formulario.quantidade,
                        onValueChange = aoMudarQuantidade,
                        label = { Text("Quantidade de fotos") },
                        isError = CampoRegistro.QUANTIDADE in formulario.erros,
                        supportingText = formulario.erros[CampoRegistro.QUANTIDADE]?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    SeletorQuantidade(
                        formulario = formulario,
                        aoSelecionar = aoSelecionarQuantidade,
                        aoAumentar = aoAumentarQuantidade,
                        aoDiminuir = aoDiminuirQuantidade,
                    )
                }

                if (formulario.tipo != TipoRegistro.PERDA) {
                    OutlinedTextField(
                        value = formulario.valor,
                        onValueChange = aoMudarValor,
                        label = { Text(if (formulario.tipo == TipoRegistro.VENDA) "Preço por foto" else "Valor pago") },
                        prefix = { Text("R$ ") },
                        isError = CampoRegistro.VALOR in formulario.erros,
                        supportingText = formulario.erros[CampoRegistro.VALOR]?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (formulario.tipo == TipoRegistro.VENDA) {
                    Text("Forma de pagamento", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormaPagamento.entries.forEach { forma ->
                            FilterChip(
                                selected = forma == formulario.formaPagamento,
                                onClick = { aoMudarForma(forma) },
                                label = { Text(forma.rotulo) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = aoSalvar) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = aoCancelar) { Text("Cancelar") } },
    )

    if (escolhendoData) {
        SeletorData(
            atual = formulario.dataHora.toLocalDate(),
            hoje = hoje,
            aoEscolher = { data ->
                aoMudarDataHora(LocalDateTime.of(data, formulario.dataHora.toLocalTime()))
                escolhendoData = false
            },
            aoCancelar = { escolhendoData = false },
        )
    }
    if (escolhendoHora) {
        SeletorHora(
            atual = formulario.dataHora.toLocalTime(),
            aoEscolher = { hora ->
                aoMudarDataHora(LocalDateTime.of(formulario.dataHora.toLocalDate(), hora))
                escolhendoHora = false
            },
            aoCancelar = { escolhendoHora = false },
        )
    }
}

private val ATALHOS_QUANTIDADE = listOf(1, 3, 5, 10)

/** Botões 1/3/5/10 + contador −/+, limitado ao estoque disponível. */
@Composable
private fun SeletorQuantidade(
    formulario: Formulario,
    aoSelecionar: (Int) -> Unit,
    aoAumentar: () -> Unit,
    aoDiminuir: () -> Unit,
) {
    val atual = formulario.quantidade.toIntOrNull() ?: 0
    val disponivel = formulario.disponivel

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Quantidade de fotos", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ATALHOS_QUANTIDADE.forEach { quantidade ->
                FilterChip(
                    selected = quantidade == atual,
                    onClick = { aoSelecionar(quantidade) },
                    enabled = disponivel == null || quantidade <= disponivel,
                    label = { Text("$quantidade") },
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = aoDiminuir,
                enabled = atual > 1,
                modifier = Modifier.semantics { contentDescription = "Diminuir quantidade" },
            ) { Text("−") }
            Text(
                formulario.quantidade.ifEmpty { "—" },
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 40.dp),
            )
            FilledTonalButton(
                onClick = aoAumentar,
                enabled = disponivel == null || atual < disponivel,
                modifier = Modifier.semantics { contentDescription = "Aumentar quantidade" },
            ) { Text("+") }
        }
        if (disponivel != null) {
            Text(
                "Disponível: $disponivel fotos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        formulario.erros[CampoRegistro.QUANTIDADE]?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeletorData(atual: LocalDate, hoje: LocalDate, aoEscolher: (LocalDate) -> Unit, aoCancelar: () -> Unit) {
    // O seletor trabalha com meia-noite UTC de cada dia
    val limite = hoje.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val estado = rememberDatePickerState(
        initialSelectedDateMillis = atual.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= limite
            override fun isSelectableYear(year: Int) = year <= hoje.year
        },
    )
    DatePickerDialog(
        onDismissRequest = aoCancelar,
        confirmButton = {
            TextButton(onClick = {
                estado.selectedDateMillis?.let {
                    aoEscolher(java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                } ?: aoCancelar()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = aoCancelar) { Text("Cancelar") } },
    ) {
        DatePicker(state = estado)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeletorHora(atual: LocalTime, aoEscolher: (LocalTime) -> Unit, aoCancelar: () -> Unit) {
    val estado = rememberTimePickerState(initialHour = atual.hour, initialMinute = atual.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = aoCancelar,
        text = { TimePicker(state = estado) },
        confirmButton = {
            TextButton(onClick = { aoEscolher(LocalTime.of(estado.hour, estado.minute)) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = aoCancelar) { Text("Cancelar") } },
    )
}
