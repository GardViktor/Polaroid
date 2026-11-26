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

    private fun repetir(vezes: Int, acao: () -> Unit) = repeat(vezes) { acao() }

    // --- Estado inicial ---

    @Test
    fun `comeca sem pack, com 1 pack no contador e sem resultados`() {
        assertEquals("", estado.valorPago)
        assertNull(estado.tamanhoPack)
        assertEquals(1, estado.quantidadePacks)
        assertNull(estado.totalFotos)
        assertEquals("", estado.precoVenda)
        assertNull(estado.custo)
        assertNull(estado.resultado)
        assertNull(estado.avisoPreco)
        assertTrue(estado.erros.isEmpty())
    }

    @Test
    fun `oferece os tamanhos de pack do mercado`() {
        assertEquals(listOf(10, 20, 40, 50, 60, 100), CalculadoraViewModel.TAMANHOS_PACK)
    }

    // --- Packs e contador ---

    @Test
    fun `selecionar pack define o total de fotos`() {
        viewModel.aoSelecionarPack(20)

        assertEquals(20, estado.tamanhoPack)
        assertEquals(20, estado.totalFotos)
    }

    @Test
    fun `contador multiplica o total de fotos`() {
        viewModel.aoSelecionarPack(20)
        repetir(2) { viewModel.aoAumentarPacks() }

        assertEquals(3, estado.quantidadePacks)
        assertEquals(60, estado.totalFotos)
    }

    @Test
    fun `contador nao desce abaixo de 1`() {
        viewModel.aoDiminuirPacks()

        assertEquals(1, estado.quantidadePacks)
    }

    @Test
    fun `contador nao passa de 99`() {
        repetir(120) { viewModel.aoAumentarPacks() }

        assertEquals(99, estado.quantidadePacks)
    }

    @Test
    fun `trocar o pack mantem a quantidade de packs`() {
        viewModel.aoSelecionarPack(20)
        repetir(2) { viewModel.aoAumentarPacks() }
        viewModel.aoSelecionarPack(40)

        assertEquals(3, estado.quantidadePacks)
        assertEquals(120, estado.totalFotos)
    }

    @Test
    fun `ignora tamanho de pack que nao existe`() {
        viewModel.aoSelecionarPack(20)
        viewModel.aoSelecionarPack(7)

        assertEquals(20, estado.tamanhoPack)
    }

    // --- Custo antes do preço de venda ---

    @Test
    fun `mostra custo e preco minimo antes do preco de venda`() {
        viewModel.aoDigitar(Campo.VALOR_PAGO, "120")
        viewModel.aoSelecionarPack(20)

        val custo = assertNotNull(estado.custo)
        assertEquals("R$ 6,00", custo.custoPorFoto)
        assertEquals("R$ 6,00", custo.precoMinimo)
        assertFalse(custo.custoAproximado)
        assertNull(estado.resultado)
    }

    @Test
    fun `preco minimo arredondado para cima quando a divisao nao e exata`() {
        viewModel.aoDigitar(Campo.VALOR_PAGO, "100")
        viewModel.aoSelecionarPack(10)
        repetir(2) { viewModel.aoAumentarPacks() } // 30 fotos

        val custo = assertNotNull(estado.custo)
        assertEquals("R$ 3,33", custo.custoPorFoto)
        assertEquals("R$ 3,34", custo.precoMinimo)
        assertTrue(custo.custoAproximado)
    }

    @Test
    fun `sem custo enquanto nao escolher o pack`() {
        viewModel.aoDigitar(Campo.VALOR_PAGO, "120")

        assertNull(estado.custo)
    }

    @Test
    fun `sem custo enquanto nao informar o valor pago`() {
        viewModel.aoSelecionarPack(20)

        assertNull(estado.custo)
    }

    @Test
    fun `custo atualiza ao mexer no contador`() {
        viewModel.aoDigitar(Campo.VALOR_PAGO, "120")
        viewModel.aoSelecionarPack(20)
        viewModel.aoAumentarPacks() // 40 fotos

        assertEquals("R$ 3,00", assertNotNull(estado.custo).custoPorFoto)
    }

    // --- Lucro com preço de venda ---

    @Test
    fun `calcula lucro quando o preco de venda e informado`() {
        viewModel.aoDigitar(Campo.VALOR_PAGO, "120,00")
        viewModel.aoSelecionarPack(20)
        viewModel.aoDigitar(Campo.PRECO_VENDA, "10,00")

        val resultado = assertNotNull(estado.resultado)
        assertEquals("R$ 4,00", resultado.lucroPorFoto)
        assertEquals("R$ 80,00", resultado.lucroTotal)
        assertEquals("40,00%", resultado.margem)
        assertFalse(resultado.lucroPorFotoAproximado)
        assertFalse(resultado.margemAproximada)
        assertFalse(resultado.prejuizo)
        assertNull(estado.avisoPreco)
    }

    @Test
    fun `sinaliza valores aproximados quando ha arredondamento`() {
        viewModel.aoDigitar(Campo.VALOR_PAGO, "100")
        viewModel.aoSelecionarPack(10)
        repetir(2) { viewModel.aoAumentarPacks() } // 30 fotos
        viewModel.aoDigitar(Campo.PRECO_VENDA, "5")

        val resultado = assertNotNull(estado.resultado)
        assertEquals("R$ 50,00", resultado.lucroTotal)
        assertEquals("33,33%", resultado.margem)
        assertTrue(resultado.lucroPorFotoAproximado)
        assertTrue(resultado.margemAproximada)
    }

    @Test
    fun `avisa e mostra prejuizo quando o preco fica abaixo do minimo`() {
        viewModel.aoDigitar(Campo.VALOR_PAGO, "100")
        viewModel.aoSelecionarPack(10)
        repetir(2) { viewModel.aoAumentarPacks() } // 30 fotos, mínimo 3,34
        viewModel.aoDigitar(Campo.PRECO_VENDA, "3,33")

        assertEquals("Abaixo do preço mínimo (R$ 3,34)", estado.avisoPreco)
        val resultado = assertNotNull(estado.resultado)
        assertEquals("-R$ 0,10", resultado.lucroTotal)
        assertTrue(resultado.prejuizo)
    }

    @Test
    fun `preco igual ao minimo nao tem aviso nem prejuizo`() {
        viewModel.aoDigitar(Campo.VALOR_PAGO, "100")
        viewModel.aoSelecionarPack(10)
        repetir(2) { viewModel.aoAumentarPacks() }
        viewModel.aoDigitar(Campo.PRECO_VENDA, "3,34")

        assertNull(estado.avisoPreco)
        assertFalse(assertNotNull(estado.resultado).prejuizo)
    }

    @Test
    fun `aviso some quando o preco sobe`() {
        viewModel.aoDigitar(Campo.VALOR_PAGO, "120")
        viewModel.aoSelecionarPack(20)
        viewModel.aoDigitar(Campo.PRECO_VENDA, "5")
        viewModel.aoDigitar(Campo.PRECO_VENDA, "8")

        assertNull(estado.avisoPreco)
    }

    @Test
    fun `apagar o preco remove o lucro mas mantem o custo`() {
        viewModel.aoDigitar(Campo.VALOR_PAGO, "120")
        viewModel.aoSelecionarPack(20)
        viewModel.aoDigitar(Campo.PRECO_VENDA, "10")
        viewModel.aoDigitar(Campo.PRECO_VENDA, "")

        assertNull(estado.resultado)
        assertNotNull(estado.custo)
    }

    // --- Digitação e erros ---

    @Test
    fun `ignora caractere invalido mantendo o texto anterior`() {
        viewModel.aoDigitar(Campo.VALOR_PAGO, "12")
        viewModel.aoDigitar(Campo.VALOR_PAGO, "12a")

        assertEquals("12", estado.valorPago)
    }

    @Test
    fun `nao mostra erro enquanto o usuario esta digitando`() {
        viewModel.aoDigitar(Campo.PRECO_VENDA, "0")

        assertNull(estado.erros[Campo.PRECO_VENDA])
    }

    @Test
    fun `mostra erro ao sair de campo vazio`() {
        viewModel.aoSairDoCampo(Campo.VALOR_PAGO)
        viewModel.aoSairDoCampo(Campo.PRECO_VENDA)

        assertEquals("Informe o valor pago", estado.erros[Campo.VALOR_PAGO])
        assertEquals("Informe o preço de venda", estado.erros[Campo.PRECO_VENDA])
    }

    @Test
    fun `preco de venda zero da erro e nao calcula lucro`() {
        viewModel.aoDigitar(Campo.VALOR_PAGO, "120")
        viewModel.aoSelecionarPack(20)
        viewModel.aoDigitar(Campo.PRECO_VENDA, "0")
        viewModel.aoSairDoCampo(Campo.PRECO_VENDA)

        assertEquals("O preço de venda deve ser maior que zero", estado.erros[Campo.PRECO_VENDA])
        assertNull(estado.resultado)
        assertNull(estado.avisoPreco)
    }

    @Test
    fun `separador sozinho e valor invalido`() {
        viewModel.aoDigitar(Campo.VALOR_PAGO, ",")
        viewModel.aoSairDoCampo(Campo.VALOR_PAGO)

        assertEquals("Valor inválido", estado.erros[Campo.VALOR_PAGO])
    }

    @Test
    fun `valor pago zero e permitido`() {
        viewModel.aoDigitar(Campo.VALOR_PAGO, "0")
        viewModel.aoSelecionarPack(10)
        viewModel.aoDigitar(Campo.PRECO_VENDA, "8")
        viewModel.aoSairDoCampo(Campo.VALOR_PAGO)

        assertNull(estado.erros[Campo.VALOR_PAGO])
        assertEquals("R$ 80,00", assertNotNull(estado.resultado).lucroTotal)
    }

    @Test
    fun `erro some quando o campo e corrigido`() {
        viewModel.aoDigitar(Campo.PRECO_VENDA, "0")
        viewModel.aoSairDoCampo(Campo.PRECO_VENDA)
        viewModel.aoDigitar(Campo.PRECO_VENDA, "10")

        assertNull(estado.erros[Campo.PRECO_VENDA])
    }

    @Test
    fun `erro reaparece ao apagar campo ja visitado`() {
        viewModel.aoDigitar(Campo.VALOR_PAGO, "120")
        viewModel.aoSairDoCampo(Campo.VALOR_PAGO)
        viewModel.aoDigitar(Campo.VALOR_PAGO, "")

        assertEquals("Informe o valor pago", estado.erros[Campo.VALOR_PAGO])
    }
}
