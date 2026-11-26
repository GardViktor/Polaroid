package com.polaroid.app.registros

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.polaroid.app.calculadora.Formatador
import java.math.RoundingMode

/** Cor fixa por fatia, para "Pix" ter sempre a mesma cor em qualquer período. */
private val CORES = mapOf(
    "Pix" to Color(0xFF00897B),
    "Dinheiro" to Color(0xFF43A047),
    "Débito" to Color(0xFF1E88E5),
    "Crédito" to Color(0xFF8E24AA),
    "Perdas" to Color(0xFFE53935),
)

/** Rosca "para onde foram as fotos": formas de pagamento + perdas, em % das fotos que saíram. */
@Composable
fun PizzaRelatorio(fatias: List<FatiaPizza>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Para onde foram as fotos", style = MaterialTheme.typography.titleSmall)

            if (fatias.isEmpty()) {
                Text(
                    "Nenhuma foto vendida ou perdida neste período.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            val total = fatias.sumOf { it.fotos }
            val descricao = fatias.joinToString { "${it.rotulo} ${percentual(it)}" }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                    Canvas(
                        modifier = Modifier
                            .size(120.dp)
                            .semantics { contentDescription = "Gráfico: $descricao" },
                    ) {
                        val espessura = size.minDimension * 0.22f
                        val lado = size.minDimension - espessura
                        var inicio = -90f // começa no topo
                        fatias.forEach { fatia ->
                            val angulo = 360f * fatia.fotos / total
                            drawArc(
                                color = CORES[fatia.rotulo] ?: Color.Gray,
                                startAngle = inicio,
                                sweepAngle = angulo,
                                useCenter = false,
                                topLeft = Offset(espessura / 2, espessura / 2),
                                size = Size(lado, lado),
                                style = Stroke(width = espessura),
                            )
                            inicio += angulo
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("fotos", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    fatias.forEach { fatia -> LinhaLegenda(fatia) }
                }
            }
        }
    }
}

@Composable
private fun LinhaLegenda(fatia: FatiaPizza) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(CORES[fatia.rotulo] ?: Color.Gray, CircleShape),
        )
        Column {
            Text("${fatia.rotulo} · ${percentual(fatia)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            val valor = Formatador.moeda(fatia.valor)
            Text(
                "${fatia.fotos} fotos · " + if (fatia.ePerda) "custo $valor" else valor,
                style = MaterialTheme.typography.bodySmall,
                color = if (fatia.ePerda) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Percentual com 1 casa (ex.: "33,3%"); a soma pode não dar 100,0 exato por arredondamento. */
private fun percentual(fatia: FatiaPizza): String =
    fatia.percentual.setScale(1, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + "%"
