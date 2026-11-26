package com.polaroid.app.registros

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrosViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val fuso = ZoneId.of("America/Sao_Paulo")
    private val agora = LocalDateTime.of(2026, 11, 26, 14, 32, 40)
    private val relogio = Clock.fixed(agora.atZone(fuso).toInstant(), fuso)
    private val repositorio = RepositorioFalso()

    private fun instante(dataHora: LocalDateTime) = dataHora.atZone(fuso).toInstant()

    /** Compra antiga com bastante estoque, para as vendas poderem ser salvas. */
    private val compraBase = Registro.Compra(99, instante(agora.minusDays(100)), 1000, 100000)

    private fun vendas() = repositorio.registros.value.filterIsInstance<Registro.Venda>()
    private fun perdas() = repositorio.registros.value.filterIsInstance<Registro.Perda>()

    private fun TestScope.criar(vararg registros: Registro): RegistrosViewModel {
        repositorio.registros.value = registros.toList()
        return RegistrosViewModel(repositorio, relogio).also { advanceUntilIdle() }
    }

    @BeforeTest
    fun preparar() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun limpar() = Dispatchers.resetMain()

    // --- Lista ---

    @Test
    fun `lista do registro mais recente para o mais antigo`() = runTest(dispatcher) {
        val antigo = Registro.Compra(1, instante(agora.minusDays(3)), 10, 1000)
        val recente = Registro.Perda(2, instante(agora.minusHours(1)), 1)
        val vm = criar(antigo, recente)

        assertEquals(listOf(recente, antigo), vm.estado.value.registros)
    }

    // --- Compra pela calculadora ---

    @Test
    fun `registra compra vinda da calculadora com data e hora de agora`() = runTest(dispatcher) {
        val vm = criar()
        vm.registrarCompra(BigDecimal("120.00"), 20)
        advanceUntilIdle()

        val compra = assertIs<Registro.Compra>(repositorio.registros.value.single())
        assertEquals(12000L, compra.valorPagoCentavos)
        assertEquals(20, compra.quantidadeFotos)
        assertEquals(relogio.instant(), compra.dataHora)
        assertEquals("Compra registrada", vm.estado.value.mensagem)
    }

    // --- Nova venda ---

    @Test
    fun `nova venda comeca agora, com 1 foto, o preco sugerido e Pix`() = runTest(dispatcher) {
        val vm = criar(compraBase)
        vm.abrirNovaVenda(precoSugerido = "10,00")

        val formulario = assertNotNull(vm.estado.value.formulario)
        assertEquals(TipoRegistro.VENDA, formulario.tipo)
        assertEquals("1", formulario.quantidade)
        assertEquals("10,00", formulario.valor)
        assertEquals(agora.withSecond(0), formulario.dataHora)
        assertEquals(FormaPagamento.PIX, formulario.formaPagamento)
        assertNull(formulario.idEditando)
    }

    @Test
    fun `salva venda valida`() = runTest(dispatcher) {
        val vm = criar(compraBase)
        vm.abrirNovaVenda()
        vm.aoSelecionarQuantidade(3)
        vm.aoMudarValor("10,00")
        vm.salvar()
        advanceUntilIdle()

        val venda = vendas().single()
        assertEquals(3, venda.quantidadeFotos)
        assertEquals(1000L, venda.precoPorFotoCentavos)
        assertEquals(FormaPagamento.PIX, venda.formaPagamento)
        assertEquals(0, venda.taxaPontosBase)
        assertNull(vm.estado.value.formulario)
        assertEquals("Venda registrada", vm.estado.value.mensagem)
    }

    @Test
    fun `venda no credito grava a taxa do credito`() = runTest(dispatcher) {
        val vm = criar(compraBase)
        vm.abrirNovaVenda("10")
        vm.aoMudarFormaPagamento(FormaPagamento.CREDITO)
        vm.salvar()
        advanceUntilIdle()

        assertEquals(499, vendas().single().taxaPontosBase)
    }

    // --- Botões de quantidade ---

    @Test
    fun `botoes definem a quantidade`() = runTest(dispatcher) {
        val vm = criar(compraBase)
        vm.abrirNovaVenda()

        vm.aoSelecionarQuantidade(10)
        assertEquals("10", vm.estado.value.formulario?.quantidade)
        vm.aoSelecionarQuantidade(5)
        assertEquals("5", vm.estado.value.formulario?.quantidade)
    }

    @Test
    fun `mais e menos ajustam de 1 em 1 com minimo 1`() = runTest(dispatcher) {
        val vm = criar(compraBase)
        vm.abrirNovaVenda()

        vm.aoAumentarQuantidade()
        vm.aoAumentarQuantidade()
        assertEquals("3", vm.estado.value.formulario?.quantidade)

        repeat(5) { vm.aoDiminuirQuantidade() }
        assertEquals("1", vm.estado.value.formulario?.quantidade)
    }

    @Test
    fun `o botao escolhido define o passo do mais e do menos`() = runTest(dispatcher) {
        val vm = criar(compraBase)
        vm.abrirNovaVenda()

        vm.aoSelecionarQuantidade(3)
        vm.aoAumentarQuantidade()
        vm.aoAumentarQuantidade()
        assertEquals("9", vm.estado.value.formulario?.quantidade)

        vm.aoDiminuirQuantidade()
        assertEquals("6", vm.estado.value.formulario?.quantidade)
    }

    @Test
    fun `trocar de botao troca o passo`() = runTest(dispatcher) {
        val vm = criar(compraBase)
        vm.abrirNovaVenda()
        vm.aoSelecionarQuantidade(10)
        vm.aoAumentarQuantidade()
        assertEquals("20", vm.estado.value.formulario?.quantidade)

        vm.aoSelecionarQuantidade(1)
        vm.aoAumentarQuantidade()
        assertEquals("2", vm.estado.value.formulario?.quantidade)
    }

    @Test
    fun `menos nao deixa ficar abaixo de 1 foto`() = runTest(dispatcher) {
        val vm = criar(compraBase)
        vm.abrirNovaVenda()
        vm.aoSelecionarQuantidade(3)

        vm.aoDiminuirQuantidade()

        assertEquals("3", vm.estado.value.formulario?.quantidade)
    }

    @Test
    fun `mais nao passa do disponivel mesmo com passo grande`() = runTest(dispatcher) {
        val vm = criar(Registro.Compra(1, instante(agora.minusDays(1)), 7, 7000))
        vm.abrirNovaVenda()
        vm.aoSelecionarQuantidade(5)

        vm.aoAumentarQuantidade()

        assertEquals("5", vm.estado.value.formulario?.quantidade)
    }

    @Test
    fun `edicao comeca com passo 1`() = runTest(dispatcher) {
        val venda = Registro.Venda(7, instante(agora.minusDays(1)), 7, 1000, FormaPagamento.PIX, 0)
        val vm = criar(compraBase, venda)

        vm.abrirEdicao(venda)
        vm.aoAumentarQuantidade()

        assertEquals("8", vm.estado.value.formulario?.quantidade)
    }

    @Test
    fun `mais para no estoque disponivel`() = runTest(dispatcher) {
        val vm = criar(Registro.Compra(1, instante(agora.minusDays(1)), 4, 4000))
        vm.abrirNovaVenda()

        repeat(10) { vm.aoAumentarQuantidade() }

        assertEquals("4", vm.estado.value.formulario?.quantidade)
    }

    @Test
    fun `formulario mostra o estoque disponivel na data`() = runTest(dispatcher) {
        val vm = criar(
            Registro.Compra(1, instante(agora.minusDays(10)), 140, 14000),
            Registro.Venda(2, instante(agora.minusDays(2)), 30, 1000, FormaPagamento.PIX, 0),
        )
        vm.abrirNovaVenda()
        assertEquals(110, vm.estado.value.formulario?.disponivel)

        // Antes da venda de 30, ainda assim só 110 podem sair (a venda posterior precisa delas)
        vm.aoMudarDataHora(agora.minusDays(5))
        assertEquals(110, vm.estado.value.formulario?.disponivel)

        // Antes da compra não há nada
        vm.aoMudarDataHora(agora.minusDays(20))
        assertEquals(0, vm.estado.value.formulario?.disponivel)
    }

    // --- Estoque ---

    @Test
    fun `bloqueia venda maior que o estoque`() = runTest(dispatcher) {
        val vm = criar(Registro.Compra(1, instante(agora.minusDays(1)), 140, 14000))
        vm.abrirNovaVenda("10")
        vm.aoMudarQuantidade("150")
        vm.salvar()
        advanceUntilIdle()

        assertEquals(
            "Estoque insuficiente: só há 140 fotos disponíveis nessa data",
            vm.estado.value.formulario?.erros?.get(CampoRegistro.QUANTIDADE),
        )
        assertTrue(vendas().isEmpty())
    }

    @Test
    fun `bloqueia perda maior que o estoque`() = runTest(dispatcher) {
        val vm = criar(Registro.Compra(1, instante(agora.minusDays(1)), 2, 2000))
        vm.abrirNovaPerda()
        vm.aoSelecionarQuantidade(3)
        vm.salvar()
        advanceUntilIdle()

        assertTrue(perdas().isEmpty())
        assertNotNull(vm.estado.value.formulario?.erros?.get(CampoRegistro.QUANTIDADE))
    }

    @Test
    fun `bloqueia venda antes da primeira compra`() = runTest(dispatcher) {
        val vm = criar(Registro.Compra(1, instante(agora.minusDays(1)), 140, 14000))
        vm.abrirNovaVenda("10")
        vm.aoMudarDataHora(agora.minusDays(3))
        vm.salvar()
        advanceUntilIdle()

        assertTrue(vendas().isEmpty())
    }

    @Test
    fun `bloqueia mover venda para antes da compra`() = runTest(dispatcher) {
        val compra = Registro.Compra(1, instante(agora.minusDays(5)), 10, 1000)
        val venda = Registro.Venda(2, instante(agora.minusDays(1)), 10, 1000, FormaPagamento.PIX, 0)
        val vm = criar(compra, venda)

        vm.abrirEdicao(venda)
        vm.aoMudarDataHora(agora.minusDays(10))
        vm.salvar()
        advanceUntilIdle()

        assertEquals(venda, vendas().single())
    }

    @Test
    fun `bloqueia diminuir compra abaixo do que ja foi vendido`() = runTest(dispatcher) {
        val compra = Registro.Compra(1, instante(agora.minusDays(5)), 140, 14000)
        val venda = Registro.Venda(2, instante(agora.minusDays(1)), 100, 1000, FormaPagamento.PIX, 0)
        val vm = criar(compra, venda)

        vm.abrirEdicao(compra)
        vm.aoMudarQuantidade("50")
        vm.salvar()
        advanceUntilIdle()

        assertEquals(
            "Essa mudança deixaria o estoque negativo em 25/11/2026",
            vm.estado.value.formulario?.erros?.get(CampoRegistro.QUANTIDADE),
        )
        assertEquals(compra, repositorio.registros.value.filterIsInstance<Registro.Compra>().single())
    }

    @Test
    fun `bloqueia apagar compra que ja foi vendida`() = runTest(dispatcher) {
        val compra = Registro.Compra(1, instante(agora.minusDays(5)), 140, 14000)
        val venda = Registro.Venda(2, instante(agora.minusDays(1)), 100, 1000, FormaPagamento.PIX, 0)
        val vm = criar(compra, venda)

        vm.apagar(compra)
        advanceUntilIdle()

        assertEquals(2, repositorio.registros.value.size)
        assertEquals(
            "Não é possível apagar: o estoque ficaria negativo em 25/11/2026",
            vm.estado.value.mensagem,
        )
    }

    @Test
    fun `apagar venda sempre e permitido`() = runTest(dispatcher) {
        val venda = Registro.Venda(2, instante(agora.minusDays(1)), 100, 1000, FormaPagamento.PIX, 0)
        val vm = criar(compraBase, venda)

        vm.apagar(venda)
        advanceUntilIdle()

        assertTrue(vendas().isEmpty())
    }

    @Test
    fun `avisa quando os dados ja estao com estoque negativo`() = runTest(dispatcher) {
        val vm = criar(
            Registro.Compra(1, instante(agora.minusDays(5)), 140, 14000),
            Registro.Venda(2, instante(agora.minusDays(1)), 150, 1000, FormaPagamento.PIX, 0),
        )

        assertTrue(vm.estado.value.estoqueNegativo)
    }

    @Test
    fun `com dados ja negativos ainda da para corrigir`() = runTest(dispatcher) {
        val venda = Registro.Venda(2, instante(agora.minusDays(1)), 150, 1000, FormaPagamento.PIX, 0)
        val vm = criar(Registro.Compra(1, instante(agora.minusDays(5)), 140, 14000), venda)

        vm.abrirEdicao(venda)
        vm.aoMudarQuantidade("140")
        vm.salvar()
        advanceUntilIdle()

        assertEquals(140, vendas().single().quantidadeFotos)
        assertFalse(vm.estado.value.estoqueNegativo)
    }

    @Test
    fun `com dados ja negativos nao deixa piorar`() = runTest(dispatcher) {
        val vm = criar(
            Registro.Compra(1, instante(agora.minusDays(5)), 140, 14000),
            Registro.Venda(2, instante(agora.minusDays(1)), 150, 1000, FormaPagamento.PIX, 0),
        )
        vm.abrirNovaPerda()
        vm.salvar()
        advanceUntilIdle()

        assertTrue(perdas().isEmpty())
    }

    @Test
    fun `sem aviso quando o estoque esta correto`() = runTest(dispatcher) {
        val vm = criar(compraBase)

        assertFalse(vm.estado.value.estoqueNegativo)
    }

    // --- Datas ---

    @Test
    fun `bloqueia data futura`() = runTest(dispatcher) {
        val vm = criar(compraBase)
        vm.abrirNovaVenda("10")
        vm.aoMudarDataHora(agora.plusMinutes(5))
        vm.salvar()
        advanceUntilIdle()

        assertEquals("Não é possível registrar em data futura", vm.estado.value.formulario?.erros?.get(CampoRegistro.DATA_HORA))
        assertTrue(vendas().isEmpty())
    }

    @Test
    fun `bloqueia amanha`() = runTest(dispatcher) {
        val vm = criar(compraBase)
        vm.abrirNovaPerda()
        vm.aoMudarDataHora(agora.plusDays(1).withHour(8))
        vm.salvar()
        advanceUntilIdle()

        assertTrue(perdas().isEmpty())
    }

    @Test
    fun `aceita o minuto atual`() = runTest(dispatcher) {
        val vm = criar(compraBase)
        vm.abrirNovaVenda("10")
        vm.salvar()
        advanceUntilIdle()

        assertEquals(1, vendas().size)
    }

    @Test
    fun `aceita dias anteriores`() = runTest(dispatcher) {
        val vm = criar(compraBase)
        vm.abrirNovaVenda("10")
        vm.aoMudarDataHora(agora.minusDays(40))
        vm.salvar()
        advanceUntilIdle()

        // Datas escolhidas na tela vão até o minuto (sem segundos)
        assertEquals(instante(agora.minusDays(40).withSecond(0)), vendas().single().dataHora)
    }

    // --- Validação de campos ---

    @Test
    fun `quantidade e preco obrigatorios`() = runTest(dispatcher) {
        val vm = criar(compraBase)
        vm.abrirNovaVenda()
        vm.aoMudarQuantidade("")
        vm.salvar()
        advanceUntilIdle()

        val erros = assertNotNull(vm.estado.value.formulario).erros
        assertEquals("Informe a quantidade de fotos", erros[CampoRegistro.QUANTIDADE])
        assertEquals("Informe o preço por foto", erros[CampoRegistro.VALOR])
        assertTrue(vendas().isEmpty())
    }

    @Test
    fun `quantidade zero e preco zero sao invalidos`() = runTest(dispatcher) {
        val vm = criar(compraBase)
        vm.abrirNovaVenda()
        vm.aoMudarQuantidade("0")
        vm.aoMudarValor("0")
        vm.salvar()
        advanceUntilIdle()

        val erros = assertNotNull(vm.estado.value.formulario).erros
        assertEquals("A quantidade deve ser maior que zero", erros[CampoRegistro.QUANTIDADE])
        assertEquals("O preço deve ser maior que zero", erros[CampoRegistro.VALOR])
    }

    @Test
    fun `ignora caracteres invalidos`() = runTest(dispatcher) {
        val vm = criar(compraBase)
        vm.abrirNovaVenda()
        vm.aoMudarQuantidade("3")
        vm.aoMudarQuantidade("3a")
        vm.aoMudarValor("10,00")
        vm.aoMudarValor("10,005")

        val formulario = assertNotNull(vm.estado.value.formulario)
        assertEquals("3", formulario.quantidade)
        assertEquals("10,00", formulario.valor)
    }

    @Test
    fun `erro some ao corrigir o campo`() = runTest(dispatcher) {
        val vm = criar(compraBase)
        vm.abrirNovaVenda("10")
        vm.aoMudarQuantidade("")
        vm.salvar()
        advanceUntilIdle()
        vm.aoSelecionarQuantidade(2)

        assertNull(vm.estado.value.formulario?.erros?.get(CampoRegistro.QUANTIDADE))
    }

    // --- Perda ---

    @Test
    fun `registra perda com quantidade e data`() = runTest(dispatcher) {
        val vm = criar(compraBase)
        vm.abrirNovaPerda()
        assertEquals("1", vm.estado.value.formulario?.quantidade)
        vm.aoSelecionarQuantidade(3)
        vm.salvar()
        advanceUntilIdle()

        assertEquals(3, perdas().single().quantidadeFotos)
        assertEquals("Perda registrada", vm.estado.value.mensagem)
    }

    // --- Edição ---

    @Test
    fun `editar preenche o formulario com os dados da venda`() = runTest(dispatcher) {
        val ontem = agora.minusDays(1).withSecond(0)
        val venda = Registro.Venda(7, instante(ontem), 3, 1050, FormaPagamento.DEBITO, 199)
        val vm = criar(compraBase, venda)

        vm.abrirEdicao(venda)

        val formulario = assertNotNull(vm.estado.value.formulario)
        assertEquals(TipoRegistro.VENDA, formulario.tipo)
        assertEquals(7L, formulario.idEditando)
        assertEquals("3", formulario.quantidade)
        assertEquals("10,50", formulario.valor)
        assertEquals(FormaPagamento.DEBITO, formulario.formaPagamento)
        assertEquals(ontem, formulario.dataHora)
        // A própria venda volta a ficar disponível durante a edição
        assertEquals(1000, formulario.disponivel)
    }

    @Test
    fun `salvar edicao atualiza o mesmo registro`() = runTest(dispatcher) {
        val venda = Registro.Venda(7, instante(agora.minusDays(1)), 3, 1000, FormaPagamento.PIX, 0)
        val vm = criar(compraBase, venda)

        vm.abrirEdicao(venda)
        vm.aoSelecionarQuantidade(5)
        vm.salvar()
        advanceUntilIdle()

        val editada = vendas().single()
        assertEquals(7L, editada.id)
        assertEquals(5, editada.quantidadeFotos)
        assertEquals("Venda atualizada", vm.estado.value.mensagem)
    }

    @Test
    fun `editar para data futura e bloqueado`() = runTest(dispatcher) {
        val venda = Registro.Venda(7, instante(agora.minusDays(1)), 3, 1000, FormaPagamento.PIX, 0)
        val vm = criar(compraBase, venda)

        vm.abrirEdicao(venda)
        vm.aoMudarDataHora(agora.plusDays(2))
        vm.salvar()
        advanceUntilIdle()

        assertEquals(venda, vendas().single())
        assertNotNull(vm.estado.value.formulario?.erros?.get(CampoRegistro.DATA_HORA))
    }

    @Test
    fun `editar mantendo a forma de pagamento preserva a taxa original`() = runTest(dispatcher) {
        val venda = Registro.Venda(7, instante(agora.minusDays(1)), 3, 1000, FormaPagamento.CREDITO, 350)
        val vm = criar(compraBase, venda)

        vm.abrirEdicao(venda)
        vm.aoSelecionarQuantidade(4)
        vm.salvar()
        advanceUntilIdle()

        assertEquals(350, vendas().single().taxaPontosBase)
    }

    @Test
    fun `trocar a forma de pagamento aplica a taxa da nova forma`() = runTest(dispatcher) {
        val venda = Registro.Venda(7, instante(agora.minusDays(1)), 3, 1000, FormaPagamento.CREDITO, 350)
        val vm = criar(compraBase, venda)

        vm.abrirEdicao(venda)
        vm.aoMudarFormaPagamento(FormaPagamento.DEBITO)
        vm.salvar()
        advanceUntilIdle()

        assertEquals(199, vendas().single().taxaPontosBase)
    }

    @Test
    fun `editar compra altera valor pago e quantidade`() = runTest(dispatcher) {
        val compra = Registro.Compra(3, instante(agora.minusDays(5)), 20, 12000)
        val vm = criar(compra)

        vm.abrirEdicao(compra)
        assertEquals("120,00", vm.estado.value.formulario?.valor)
        vm.aoMudarValor("110,50")
        vm.aoMudarQuantidade("40")
        vm.salvar()
        advanceUntilIdle()

        val editada = assertIs<Registro.Compra>(repositorio.registros.value.single())
        assertEquals(11050L, editada.valorPagoCentavos)
        assertEquals(40, editada.quantidadeFotos)
    }

    @Test
    fun `compra aceita valor pago zero`() = runTest(dispatcher) {
        val compra = Registro.Compra(3, instante(agora.minusDays(5)), 20, 12000)
        val vm = criar(compra)

        vm.abrirEdicao(compra)
        vm.aoMudarValor("0")
        vm.salvar()
        advanceUntilIdle()

        assertEquals(0L, assertIs<Registro.Compra>(repositorio.registros.value.single()).valorPagoCentavos)
    }

    // --- Apagar e cancelar ---

    @Test
    fun `apagar remove o registro`() = runTest(dispatcher) {
        val perda = Registro.Perda(1, instante(agora.minusDays(1)), 1)
        val vm = criar(compraBase, perda)

        vm.apagar(perda)
        advanceUntilIdle()

        assertTrue(perdas().isEmpty())
        assertEquals(listOf<Registro>(compraBase), vm.estado.value.registros)
    }

    @Test
    fun `cancelar fecha o formulario sem salvar`() = runTest(dispatcher) {
        val vm = criar(compraBase)
        vm.abrirNovaVenda("10")
        vm.cancelar()
        advanceUntilIdle()

        assertNull(vm.estado.value.formulario)
        assertTrue(vendas().isEmpty())
    }

    // --- Repositório em memória ---

    private class RepositorioFalso : RepositorioRegistros {
        val registros = MutableStateFlow<List<Registro>>(emptyList())
        private var proximoId = 100L

        override fun observar() = registros

        override suspend fun salvar(registro: Registro) {
            val comId = if (registro.id != 0L) registro else when (registro) {
                is Registro.Compra -> registro.copy(id = proximoId++)
                is Registro.Venda -> registro.copy(id = proximoId++)
                is Registro.Perda -> registro.copy(id = proximoId++)
            }
            registros.update { lista -> lista.filterNot { it.id == comId.id } + comId }
        }

        override suspend fun apagar(registro: Registro) {
            registros.update { lista -> lista.filterNot { it.id == registro.id } }
        }
    }
}
