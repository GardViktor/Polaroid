package com.polaroid.app.registros

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ArquivoExportacao(val nome: String, val conteudo: String)

/**
 * Gera o CSV dos registros de um período no formato do Excel brasileiro:
 * separador ";", vírgula decimal, UTF-8 com BOM (acentos corretos) e quebra de linha CRLF.
 *
 * A coluna Lucro fecha com o painel: vendas = faturamento − taxa − custo; perdas = −custo.
 * Valores com fração de centavo saem exatos (até 6 casas), sem arredondar para 2.
 */
object ExportadorCsv {

    private const val BOM = "﻿"
    private const val FIM_LINHA = "\r\n"
    private val DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val HORA = DateTimeFormatter.ofPattern("HH:mm")

    private val CABECALHO = listOf(
        "Data", "Hora", "Tipo", "Fotos", "Preço por foto (R$)", "Valor total (R$)", "Forma de pagamento",
        "Taxa (%)", "Taxa (R$)", "Custo das fotos (R$)", "Lucro (R$)",
    )

    fun gerar(registros: List<Registro>, periodo: Periodo, zona: ZoneId): String {
        val custos = Painel.custosDasSaidas(registros)
        val doPeriodo = Estoque.emOrdem(registros)
            .filter { periodo.contem(it.dataHora.atZone(zona).toLocalDate()) }

        return buildString {
            append(BOM)
            append(linha(CABECALHO))
            doPeriodo.forEach { registro ->
                val quando = LocalDateTime.ofInstant(registro.dataHora, zona)
                val inicio = listOf(DATA.format(quando), HORA.format(quando))
                val custo = custos[registro.id] ?: BigDecimal.ZERO
                val campos = when (registro) {
                    is Registro.Compra -> listOf(
                        "Compra", "${registro.quantidadeFotos}", "",
                        numero(Centavos.paraReais(registro.valorPagoCentavos)), "", "", "", "", "",
                    )
                    is Registro.Venda -> {
                        val total = Centavos.paraReais(registro.precoPorFotoCentavos * registro.quantidadeFotos)
                        val taxa = BigDecimal.valueOf(
                            registro.precoPorFotoCentavos * registro.quantidadeFotos * registro.taxaPontosBase, 6,
                        )
                        listOf(
                            "Venda", "${registro.quantidadeFotos}",
                            numero(Centavos.paraReais(registro.precoPorFotoCentavos)), numero(total),
                            registro.formaPagamento.rotulo,
                            numero(BigDecimal.valueOf(registro.taxaPontosBase.toLong(), 2)), numero(taxa),
                            numero(custo), numero(total - taxa - custo),
                        )
                    }
                    is Registro.Perda -> listOf(
                        "Perda", "${registro.quantidadeFotos}", "", "", "", "", "", numero(custo), numero(custo.negate()),
                    )
                }
                append(linha(inicio + campos))
            }
        }
    }

    fun nomeArquivo(periodo: Periodo): String {
        val inicio = periodo.inicio
        val sufixo = when (periodo.tipo) {
            TipoPeriodo.DIA -> "dia_$inicio"
            TipoPeriodo.SEMANA -> "semana_$inicio"
            TipoPeriodo.MES -> "mes_%04d-%02d".format(inicio.year, inicio.monthValue)
            TipoPeriodo.TRIMESTRE -> "trimestre_${inicio.year}-T${(inicio.monthValue - 1) / 3 + 1}"
            TipoPeriodo.ANO -> "ano_${inicio.year}"
        }
        return "relatorio_$sufixo.csv"
    }

    /** Vírgula decimal, sem separador de milhar; mínimo 2 casas, até 6 se houver fração de centavo. */
    private fun numero(valor: BigDecimal): String {
        val casas = valor.stripTrailingZeros().scale().coerceIn(2, 6)
        return valor.setScale(casas, RoundingMode.HALF_UP).toPlainString().replace('.', ',')
    }

    private fun linha(campos: List<String>): String = campos.joinToString(";") { escapar(it) } + FIM_LINHA

    private fun escapar(campo: String): String =
        if (campo.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + campo.replace("\"", "\"\"") + "\""
        } else {
            campo
        }
}
