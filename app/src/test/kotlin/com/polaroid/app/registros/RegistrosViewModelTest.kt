package com.polaroid.app.registros

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
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

    private fun criar(): RegistrosViewModel = RegistrosViewModel(repositorio, relogio)

    @BeforeTest
    fun preparar() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun limpar() = Dispatchers.resetMain()

    // --- Lista ---

    @Test
    fun `lista do registro mais recente para o mais antigo`() = runTest(dispatcher) {
        val antigo = Registro.Perda(1, instante(agora.minusDays(3)), 1)
        val recente = Registro.Perda(2, instante(agora.minusHours(1)), 1)
        repositorio.registros.value = listOf(antigo, recente)

        val vm = criar()
        advanceUntilIdle()

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
    fun `nova venda comeca agora, com o preco sugerido e Pix`() = runTest(dispatcher) {
        val vm = criar()
        vm.abrirNovaVenda(precoSugerido = "10,00")

        val formulario = assertNotNull(vm.estado.value.formulario)
        assertEquals(TipoRegistro.VENDA, formulario.tipo)
        assertEquals("10,00", formulario.valor)
        assertEquals(agora.withSecond(0), formulario.dataHora)
        assertEquals(FormaPagamento.PIX, formulario.formaPagamento)
        assertNull(formulario.idEditando)
    }

    @Test
    fun `salva venda valida`() = runTest(dispatcher) {
        val vm = criar()
        vm.abrirNovaVenda()
        vm.aoMudarQuantidade("3")
        vm.aoMudarValor("10,00")
        vm.salvar()
        advanceUntilIdle()

        val venda = assertIs<Registro.Venda>(repositorio.registros.value.single())
        assertEquals(3, venda.quantidadeFotos)
        assertEquals(1000L, venda.precoPorFotoCentavos)
        assertEquals(FormaPagamento.PIX, venda.formaPagamento)
        assertEquals(0, venda.taxaPontosBase)
        assertNull(vm.estado.value.formulario)
        assertEquals("Venda registrada", vm.estado.value.mensagem)
    }

    @Test
    fun `venda no credito grava a taxa do credito`() = runTest(dispatcher) {
        val vm = criar()
        vm.abrirNovaVenda("10")
        vm.aoMudarQuantidade("1")
        vm.aoMudarFormaPagamento(FormaPagamento.CREDITO)
        vm.salvar()
        advanceUntilIdle()

        assertEquals(499, assertIs<Registro.Venda>(repositorio.registros.value.single()).taxaPontosBase)
    }

    // --- Datas ---

    @Test
    fun `bloqueia data futura`() = runTest(dispatcher) {
        val vm = criar()
        vm.abrirNovaVenda("10")
        vm.aoMudarQuantidade("1")
        vm.aoMudarDataHora(agora.plusMinutes(5))
        vm.salvar()
        advanceUntilIdle()

        assertEquals("Não é possível registrar em data futura", vm.estado.value.formulario?.erros?.get(CampoRegistro.DATA_HORA))
        assertTrue(repositorio.registros.value.isEmpty())
    }

    @Test
    fun `bloqueia amanha`() = runTest(dispatcher) {
        val vm = criar()
        vm.abrirNovaPerda()
        vm.aoMudarQuantidade("1")
        vm.aoMudarDataHora(agora.plusDays(1).withHour(8))
        vm.salvar()
        advanceUntilIdle()

        assertTrue(repositorio.registros.value.isEmpty())
    }

    @Test
    fun `aceita o minuto atual`() = runTest(dispatcher) {
        val vm = criar()
        vm.abrirNovaVenda("10")
        vm.aoMudarQuantidade("1")
        vm.salvar()
        advanceUntilIdle()

        assertEquals(1, repositorio.registros.value.size)
    }

    @Test
    fun `aceita dias anteriores`() = runTest(dispatcher) {
        val vm = criar()
        vm.abrirNovaVenda("10")
        vm.aoMudarQuantidade("1")
        vm.aoMudarDataHora(agora.minusDays(40))
        vm.salvar()
        advanceUntilIdle()

        // Datas escolhidas na tela vão até o minuto (sem segundos)
        assertEquals(instante(agora.minusDays(40).withSecond(0)), repositorio.registros.value.single().dataHora)
    }

    // --- Validação de campos ---

    @Test
    fun `quantidade e preco obrigatorios`() = runTest(dispatcher) {
        val vm = criar()
        vm.abrirNovaVenda()
        vm.salvar()
        advanceUntilIdle()

        val erros = assertNotNull(vm.estado.value.formulario).erros
        assertEquals("Informe a quantidade de fotos", erros[CampoRegistro.QUANTIDADE])
        assertEquals("Informe o preço por foto", erros[CampoRegistro.VALOR])
        assertTrue(repositorio.registros.value.isEmpty())
    }

    @Test
    fun `quantidade zero e preco zero sao invalidos`() = runTest(dispatcher) {
        val vm = criar()
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
        val vm = criar()
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
        val vm = criar()
        vm.abrirNovaVenda("10")
        vm.salvar()
        advanceUntilIdle()
        vm.aoMudarQuantidade("2")

        assertNull(vm.estado.value.formulario?.erros?.get(CampoRegistro.QUANTIDADE))
    }

    // --- Perda ---

    @Test
    fun `registra perda so com quantidade e data`() = runTest(dispatcher) {
        val vm = criar()
        vm.abrirNovaPerda()
        vm.aoMudarQuantidade("2")
        vm.salvar()
        advanceUntilIdle()

        val perda = assertIs<Registro.Perda>(repositorio.registros.value.single())
        assertEquals(2, perda.quantidadeFotos)
        assertEquals("Perda registrada", vm.estado.value.mensagem)
    }

    // --- Edição ---

    @Test
    fun `editar preenche o formulario com os dados da venda`() = runTest(dispatcher) {
        val ontem = agora.minusDays(1).withSecond(0)
        val venda = Registro.Venda(7, instante(ontem), 3, 1050, FormaPagamento.DEBITO, 199)
        repositorio.registros.value = listOf(venda)
        val vm = criar()
        advanceUntilIdle()

        vm.abrirEdicao(venda)

        val formulario = assertNotNull(vm.estado.value.formulario)
        assertEquals(TipoRegistro.VENDA, formulario.tipo)
        assertEquals(7L, formulario.idEditando)
        assertEquals("3", formulario.quantidade)
        assertEquals("10,50", formulario.valor)
        assertEquals(FormaPagamento.DEBITO, formulario.formaPagamento)
        assertEquals(ontem, formulario.dataHora)
    }

    @Test
    fun `salvar edicao atualiza o mesmo registro`() = runTest(dispatcher) {
        val venda = Registro.Venda(7, instante(agora.minusDays(1)), 3, 1000, FormaPagamento.PIX, 0)
        repositorio.registros.value = listOf(venda)
        val vm = criar()
        advanceUntilIdle()

        vm.abrirEdicao(venda)
        vm.aoMudarQuantidade("5")
        vm.salvar()
        advanceUntilIdle()

        val editada = assertIs<Registro.Venda>(repositorio.registros.value.single())
        assertEquals(7L, editada.id)
        assertEquals(5, editada.quantidadeFotos)
        assertEquals("Venda atualizada", vm.estado.value.mensagem)
    }

    @Test
    fun `editar para data futura e bloqueado`() = runTest(dispatcher) {
        val venda = Registro.Venda(7, instante(agora.minusDays(1)), 3, 1000, FormaPagamento.PIX, 0)
        repositorio.registros.value = listOf(venda)
        val vm = criar()
        advanceUntilIdle()

        vm.abrirEdicao(venda)
        vm.aoMudarDataHora(agora.plusDays(2))
        vm.salvar()
        advanceUntilIdle()

        assertEquals(venda, repositorio.registros.value.single())
        assertNotNull(vm.estado.value.formulario?.erros?.get(CampoRegistro.DATA_HORA))
    }

    @Test
    fun `editar mantendo a forma de pagamento preserva a taxa original`() = runTest(dispatcher) {
        val venda = Registro.Venda(7, instante(agora.minusDays(1)), 3, 1000, FormaPagamento.CREDITO, 350)
        repositorio.registros.value = listOf(venda)
        val vm = criar()
        advanceUntilIdle()

        vm.abrirEdicao(venda)
        vm.aoMudarQuantidade("4")
        vm.salvar()
        advanceUntilIdle()

        assertEquals(350, assertIs<Registro.Venda>(repositorio.registros.value.single()).taxaPontosBase)
    }

    @Test
    fun `trocar a forma de pagamento aplica a taxa da nova forma`() = runTest(dispatcher) {
        val venda = Registro.Venda(7, instante(agora.minusDays(1)), 3, 1000, FormaPagamento.CREDITO, 350)
        repositorio.registros.value = listOf(venda)
        val vm = criar()
        advanceUntilIdle()

        vm.abrirEdicao(venda)
        vm.aoMudarFormaPagamento(FormaPagamento.DEBITO)
        vm.salvar()
        advanceUntilIdle()

        assertEquals(199, assertIs<Registro.Venda>(repositorio.registros.value.single()).taxaPontosBase)
    }

    @Test
    fun `editar compra altera valor pago e quantidade`() = runTest(dispatcher) {
        val compra = Registro.Compra(3, instante(agora.minusDays(5)), 20, 12000)
        repositorio.registros.value = listOf(compra)
        val vm = criar()
        advanceUntilIdle()

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
        repositorio.registros.value = listOf(compra)
        val vm = criar()
        advanceUntilIdle()

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
        repositorio.registros.value = listOf(perda)
        val vm = criar()
        advanceUntilIdle()

        vm.apagar(perda)
        advanceUntilIdle()

        assertTrue(repositorio.registros.value.isEmpty())
        assertTrue(vm.estado.value.registros.isEmpty())
    }

    @Test
    fun `cancelar fecha o formulario sem salvar`() = runTest(dispatcher) {
        val vm = criar()
        vm.abrirNovaVenda("10")
        vm.aoMudarQuantidade("1")
        vm.cancelar()
        advanceUntilIdle()

        assertNull(vm.estado.value.formulario)
        assertTrue(repositorio.registros.value.isEmpty())
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
            registros.update { lista -> lista.filterNot { it.id == comId.id && it::class == comId::class } + comId }
        }

        override suspend fun apagar(registro: Registro) {
            registros.update { lista -> lista.filterNot { it.id == registro.id && it::class == registro::class } }
        }
    }
}
