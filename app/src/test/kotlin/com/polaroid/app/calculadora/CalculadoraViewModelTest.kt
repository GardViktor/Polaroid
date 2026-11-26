package com.polaroid.app.calculadora

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalculadoraViewModelTest {

    private val viewModel = CalculadoraViewModel()
    private val estado get() = viewModel.estado.value

    private fun preencher(valorPago: String, quantidade: String, precoVenda: String) {
        viewModel.aoDigitar(Campo.VALOR_PAGO, valorPago)
        viewModel.aoDigitar(Campo.QUANTIDADE, quantidade)
        viewModel.aoDigitar(Campo.PRECO_VENDA, precoVenda)
    }

    @Test
    fun `comeca com campos vazios, sem resultado e sem erros`() {
        assertEquals("", estado.valorPago)
        assertEquals("", estado.quantidade)
        assertEquals("", estado.precoVenda)
        assertNull(estado.resultado)
        assertTrue(estado.erros.isEmpty())
    }

    @Test
    fun `calcula enquanto digita quando os 3 campos sao validos`() {
        preencher("120,00", "20", "10,00")

        val resultado = assertNotNull(estado.resultado)
        assertEquals("R$ 6,00", resultado.custoPorFoto)
        assertEquals("R$ 4,00", resultado.lucroPorFoto)
        assertEquals("R$ 80,00", resultado.lucroTotal)
        assertEquals("40,00%", resultado.margem)
        assertFalse(resultado.valoresPorFotoAproximados)
        assertFalse(resultado.margemAproximada)
        assertFalse(resultado.prejuizo)
    }

    @Test
    fun `sem resultado enquanto algum campo estiver vazio`() {
        viewModel.aoDigitar(Campo.VALOR_PAGO, "120")
        viewModel.aoDigitar(Campo.QUANTIDADE, "20")

        assertNull(estado.resultado)
    }

    @Test
    fun `sinaliza valores aproximados quando ha arredondamento`() {
        preencher("100", "30", "5")

        val resultado = assertNotNull(estado.resultado)
        assertEquals("R$ 3,33", resultado.custoPorFoto)
        assertEquals("R$ 50,00", resultado.lucroTotal)
        assertEquals("33,33%", resultado.margem)
        assertTrue(resultado.valoresPorFotoAproximados)
        assertTrue(resultado.margemAproximada)
    }

    @Test
    fun `sinaliza prejuizo`() {
        preencher("120", "20", "5")

        val resultado = assertNotNull(estado.resultado)
        assertEquals("-R$ 20,00", resultado.lucroTotal)
        assertTrue(resultado.prejuizo)
    }

    @Test
    fun `ignora caractere invalido mantendo o texto anterior`() {
        viewModel.aoDigitar(Campo.VALOR_PAGO, "12")
        viewModel.aoDigitar(Campo.VALOR_PAGO, "12a")
        viewModel.aoDigitar(Campo.QUANTIDADE, "5")
        viewModel.aoDigitar(Campo.QUANTIDADE, "5,")

        assertEquals("12", estado.valorPago)
        assertEquals("5", estado.quantidade)
    }

    @Test
    fun `nao mostra erro enquanto o usuario esta digitando`() {
        viewModel.aoDigitar(Campo.QUANTIDADE, "0")

        assertNull(estado.erros[Campo.QUANTIDADE])
    }

    @Test
    fun `mostra erro ao sair do campo com valor invalido`() {
        viewModel.aoDigitar(Campo.QUANTIDADE, "0")
        viewModel.aoSairDoCampo(Campo.QUANTIDADE)

        assertEquals("A quantidade deve ser maior que zero", estado.erros[Campo.QUANTIDADE])
    }

    @Test
    fun `mostra erro ao sair de campo vazio`() {
        viewModel.aoSairDoCampo(Campo.VALOR_PAGO)
        viewModel.aoSairDoCampo(Campo.QUANTIDADE)
        viewModel.aoSairDoCampo(Campo.PRECO_VENDA)

        assertEquals("Informe o valor pago", estado.erros[Campo.VALOR_PAGO])
        assertEquals("Informe a quantidade de fotos", estado.erros[Campo.QUANTIDADE])
        assertEquals("Informe o preço de venda", estado.erros[Campo.PRECO_VENDA])
    }

    @Test
    fun `preco de venda zero da erro e nao calcula`() {
        preencher("120", "20", "0")
        viewModel.aoSairDoCampo(Campo.PRECO_VENDA)

        assertEquals("O preço de venda deve ser maior que zero", estado.erros[Campo.PRECO_VENDA])
        assertNull(estado.resultado)
    }

    @Test
    fun `separador sozinho e valor invalido`() {
        viewModel.aoDigitar(Campo.VALOR_PAGO, ",")
        viewModel.aoSairDoCampo(Campo.VALOR_PAGO)

        assertEquals("Valor inválido", estado.erros[Campo.VALOR_PAGO])
    }

    @Test
    fun `valor pago zero e permitido`() {
        preencher("0", "10", "8")
        viewModel.aoSairDoCampo(Campo.VALOR_PAGO)

        assertNull(estado.erros[Campo.VALOR_PAGO])
        assertEquals("R$ 80,00", assertNotNull(estado.resultado).lucroTotal)
    }

    @Test
    fun `erro some quando o campo e corrigido`() {
        viewModel.aoDigitar(Campo.QUANTIDADE, "0")
        viewModel.aoSairDoCampo(Campo.QUANTIDADE)
        viewModel.aoDigitar(Campo.QUANTIDADE, "20")

        assertNull(estado.erros[Campo.QUANTIDADE])
    }

    @Test
    fun `erro reaparece ao apagar campo ja visitado`() {
        viewModel.aoDigitar(Campo.QUANTIDADE, "20")
        viewModel.aoSairDoCampo(Campo.QUANTIDADE)
        viewModel.aoDigitar(Campo.QUANTIDADE, "")

        assertEquals("Informe a quantidade de fotos", estado.erros[Campo.QUANTIDADE])
    }

    @Test
    fun `resultado some quando um campo fica invalido`() {
        preencher("120", "20", "10")
        assertNotNull(estado.resultado)

        viewModel.aoDigitar(Campo.QUANTIDADE, "0")

        assertNull(estado.resultado)
    }
}
