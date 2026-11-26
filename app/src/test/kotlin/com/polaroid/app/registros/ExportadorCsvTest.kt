package com.polaroid.app.registros

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportadorCsvTest {

    private val fuso = ZoneId.of("America/Sao_Paulo")
    private fun em(mes: Int, dia: Int, hora: Int = 12, minuto: Int = 0) =
        LocalDateTime.of(2026, mes, dia, hora, minuto).atZone(fuso).toInstant()
    private val hoje = LocalDate.of(2026, 11, 26)
    private val novembro = Periodo.atual(TipoPeriodo.MES, hoje)

    // Compra 20 fotos por R$ 120 (R$ 6/foto); vende 5 a R$ 10 no Pix; 2 a R$ 10 no crédito 4,99%; perde 1
    private val registros = listOf(
        Registro.Compra(1, em(11, 1, 9, 30), 20, 12000),
        Registro.Venda(2, em(11, 2, 14, 5), 5, 1000, FormaPagamento.PIX, 0),
        Registro.Venda(3, em(11, 3), 2, 1000, FormaPagamento.CREDITO, 499),
        Registro.Perda(4, em(11, 3, 15), 1),
        // Outubro (fora do período): compra 1 foto e vende 1, zerando o estoque antes de novembro
        Registro.Compra(6, em(10, 1), 1, 500),
        Registro.Venda(5, em(10, 20), 1, 1000, FormaPagamento.PIX, 0),
    )

    private fun linhas(csv: String) = csv.removePrefix("﻿").trimEnd('\r', '\n').split("\r\n")

    @Test
    fun `comeca com BOM para o Excel reconhecer os acentos`() {
        assertTrue(ExportadorCsv.gerar(registros, novembro, fuso).startsWith("﻿"))
    }

    @Test
    fun `cabecalho com ponto e virgula`() {
        assertEquals(
            "Data;Hora;Tipo;Fotos;Preço por foto (R$);Valor total (R$);Forma de pagamento;" +
                "Taxa (%);Taxa (R$);Custo das fotos (R$);Lucro (R$)",
            linhas(ExportadorCsv.gerar(registros, novembro, fuso)).first(),
        )
    }

    @Test
    fun `uma linha por registro do periodo em ordem de data`() {
        val linhas = linhas(ExportadorCsv.gerar(registros, novembro, fuso)).drop(1)

        assertEquals(4, linhas.size) // a venda de outubro fica de fora
        assertEquals(listOf("Compra", "Venda", "Venda", "Perda"), linhas.map { it.split(";")[2] })
    }

    @Test
    fun `linha de compra`() {
        val compra = linhas(ExportadorCsv.gerar(registros, novembro, fuso))[1]
        assertEquals("01/11/2026;09:30;Compra;20;;120,00;;;;;", compra)
    }

    @Test
    fun `linha de venda no Pix`() {
        val venda = linhas(ExportadorCsv.gerar(registros, novembro, fuso))[2]
        // 5 × R$ 10 = R$ 50; custo 5 × R$ 6 = R$ 30; lucro R$ 20
        assertEquals("02/11/2026;14:05;Venda;5;10,00;50,00;Pix;0,00;0,00;30,00;20,00", venda)
    }

    @Test
    fun `taxa com fracao de centavo sai exata`() {
        val venda = linhas(ExportadorCsv.gerar(registros, novembro, fuso))[3]
        // 4,99% de R$ 20 = R$ 0,998; lucro = 20 − 0,998 − 12 = 7,002
        assertEquals("03/11/2026;12:00;Venda;2;10,00;20,00;Crédito;4,99;0,998;12,00;7,002", venda)
    }

    @Test
    fun `perda entra com lucro negativo igual ao custo`() {
        val perda = linhas(ExportadorCsv.gerar(registros, novembro, fuso))[4]
        assertEquals("03/11/2026;15:00;Perda;1;;;;;;6,00;-6,00", perda)
    }

    @Test
    fun `soma da coluna lucro bate com o lucro do painel`() {
        val somaLucro = linhas(ExportadorCsv.gerar(registros, novembro, fuso)).drop(1)
            .mapNotNull { it.split(";")[10].takeIf(String::isNotEmpty) }
            .fold(BigDecimal.ZERO) { soma, valor -> soma + BigDecimal(valor.replace(',', '.')) }

        assertEquals(0, Painel.resumo(registros, novembro, fuso).lucro.compareTo(somaLucro))
    }

    @Test
    fun `periodo sem registros tem so o cabecalho`() {
        assertEquals(1, linhas(ExportadorCsv.gerar(registros, novembro.proximo(), fuso)).size)
    }

    @Test
    fun `nome do arquivo segue o periodo`() {
        assertEquals("relatorio_dia_2026-11-26.csv", ExportadorCsv.nomeArquivo(Periodo.atual(TipoPeriodo.DIA, hoje)))
        assertEquals("relatorio_semana_2026-11-23.csv", ExportadorCsv.nomeArquivo(Periodo.atual(TipoPeriodo.SEMANA, hoje)))
        assertEquals("relatorio_mes_2026-11.csv", ExportadorCsv.nomeArquivo(novembro))
        assertEquals("relatorio_trimestre_2026-T4.csv", ExportadorCsv.nomeArquivo(Periodo.atual(TipoPeriodo.TRIMESTRE, hoje)))
        assertEquals("relatorio_ano_2026.csv", ExportadorCsv.nomeArquivo(Periodo.atual(TipoPeriodo.ANO, hoje)))
    }
}
