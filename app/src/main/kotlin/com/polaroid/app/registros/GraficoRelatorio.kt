package com.polaroid.app.registros

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val SIMBOLOS_BR = DecimalFormatSymbols(Locale.forLanguageTag("pt-BR"))

/**
 * Nomes das barras guardados JUNTO com os dados do gráfico (mesma transação), para nunca
 * ficarem dessincronizados ao trocar de período (ex.: outubro com 31 barras → novembro com 30).
 */
private val CHAVE_ROTULOS = ExtraStore.Key<List<String>>()

/** Valores dos eixos: inteiros para fotos, "R$ 1.234" para dinheiro (sem centavos, para caber). */
private fun formatarEixo(valor: Double, emReais: Boolean): String {
    val numero = DecimalFormat("#,##0", SIMBOLOS_BR).format(valor)
    return if (emReais) "R$ $numero" else numero
}

/**
 * Barras = métrica escolhida (fotos, faturamento ou lucro), eixo da esquerda.
 * Linha = preço médio por foto, eixo da direita (só nas barras que tiveram venda).
 */
@Composable
fun GraficoRelatorio(
    pontos: List<PontoGrafico>,
    metrica: MetricaGrafico,
    tipoPeriodo: TipoPeriodo,
    aoEscolherMetrica: (MetricaGrafico) -> Unit,
) {
    val produtor = remember { CartesianChartModelProducer() }
    val comPreco = pontos.withIndex().filter { it.value.precoMedioPorFoto != null }
    val temPreco = comPreco.isNotEmpty()
    val semVendas = pontos.all { it.fotosVendidas == 0 }

    LaunchedEffect(pontos, metrica) {
        if (pontos.isEmpty()) return@LaunchedEffect
        produtor.runTransaction {
            columnModel { series(pontos.map { it.valor(metrica).toDouble() }) }
            lineModel {
                // Sem nenhuma venda: linha invisível (transparente) só para manter o gráfico estável
                if (temPreco) {
                    series(x = comPreco.map { it.index }, y = comPreco.map { it.value.precoMedioPorFoto!!.toDouble() })
                } else {
                    series(pontos.map { 0.0 })
                }
            }
            extras { it[CHAVE_ROTULOS] = pontos.map(PontoGrafico::rotulo) }
        }
    }

    val corBarra = MaterialTheme.colorScheme.primary
    val corLinha = if (temPreco) MaterialTheme.colorScheme.tertiary else Color.Transparent
    val espacoRotulos = when (tipoPeriodo) {
        TipoPeriodo.DIA -> 3
        TipoPeriodo.MES -> 5
        TipoPeriodo.TRIMESTRE -> 2
        else -> 1
    }
    val corTexto = MaterialTheme.colorScheme.onSurface
    val textoEixo = rememberTextComponent(style = TextStyle(color = corTexto, fontSize = 10.sp))

    val grafico = rememberCartesianChart(
        rememberColumnCartesianLayer(
            columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                rememberLineComponent(fill = Fill(corBarra), thickness = 10.dp),
            ),
            verticalAxisPosition = Axis.Position.Vertical.Start,
        ),
        rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(
                LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(Fill(corLinha)),
                    // Curva suave (sem picos inventados) + um ponto em cada barra que teve venda
                    interpolator = LineCartesianLayer.Interpolator.cubic(),
                    pointProvider = LineCartesianLayer.PointProvider.single(
                        LineCartesianLayer.Point(rememberShapeComponent(fill = Fill(corLinha), shape = CircleShape), 7.dp),
                    ),
                ),
            ),
            verticalAxisPosition = Axis.Position.Vertical.End,
        ),
        startAxis = VerticalAxis.rememberStart(
            label = textoEixo,
            valueFormatter = remember(metrica) {
                CartesianValueFormatter { _, valor, _ -> formatarEixo(valor, emReais = metrica != MetricaGrafico.FOTOS) }
            },
        ),
        endAxis = if (temPreco) {
            VerticalAxis.rememberEnd(
                label = textoEixo,
                valueFormatter = remember { CartesianValueFormatter { _, valor, _ -> formatarEixo(valor, emReais = true) } },
            )
        } else {
            null
        },
        bottomAxis = HorizontalAxis.rememberBottom(
            label = textoEixo,
            valueFormatter = remember {
                CartesianValueFormatter { contexto, x, _ ->
                    Grafico.rotuloDaBarra(contexto.model.extraStore.getOrNull(CHAVE_ROTULOS), x)
                }
            },
            itemPlacer = remember(espacoRotulos) { HorizontalAxis.ItemPlacer.aligned(spacing = { espacoRotulos }) },
        ),
        // Tocar numa barra mostra os valores exatos dela
        marker = rememberDefaultCartesianMarker(
            label = rememberTextComponent(style = TextStyle(color = corTexto, fontSize = 12.sp)),
            valueFormatter = remember {
                DefaultCartesianMarker.ValueFormatter.default(decimalSeparator = ",", thousandsSeparator = ".")
            },
        ),
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MetricaGrafico.entries.forEach { opcao ->
                    FilterChip(
                        selected = opcao == metrica,
                        onClick = { aoEscolherMetrica(opcao) },
                        label = { Text(opcao.rotulo) },
                    )
                }
            }

            if (pontos.isNotEmpty()) {
                CartesianChartHost(
                    chart = grafico,
                    modelProducer = produtor,
                    // Cabe todas as barras na largura da tela (sem precisar rolar)
                    zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Legenda(corBarra, "${metrica.rotulo} (barras)")
                if (temPreco) Legenda(corLinha, "Preço médio por foto (linha)")
            }
            if (semVendas) {
                Text(
                    "Nenhuma venda neste período.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Legenda(cor: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(10.dp).background(cor))
        Text(texto, style = MaterialTheme.typography.bodySmall)
    }
}
