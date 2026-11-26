package com.polaroid.app.album

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val fuso = ZoneId.of("America/Sao_Paulo")
    private val agora = ZonedDateTime.of(2026, 11, 26, 14, 32, 0, 0, fuso)
    private val relogio = Clock.fixed(agora.toInstant(), fuso)

    private val pinheiros = Coordenadas(-23.5613, -46.6565)

    private val repositorio = RepositorioFalso()
    private var resultadoLocalizacao: ResultadoLocalizacao = ResultadoLocalizacao.Encontrada(pinheiros)
    private var localizacaoTrava = false
    private var atrasoLocalizacaoMs = 0L
    private var nome: String? = "Pinheiros, São Paulo"
    private var chamadasNome = 0

    private val localizacao = ProvedorLocalizacao {
        if (localizacaoTrava) awaitCancellation()
        kotlinx.coroutines.delay(atrasoLocalizacaoMs)
        resultadoLocalizacao
    }
    private val nomeLocal = ProvedorNomeLocal {
        chamadasNome++
        nome
    }

    private fun criarViewModel() = AlbumViewModel(repositorio, localizacao, nomeLocal, relogio)

    @BeforeTest
    fun preparar() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun limpar() = Dispatchers.resetMain()

    // --- Listagem ---

    @Test
    fun `album vazio`() = runTest(dispatcher) {
        val vm = criarViewModel()
        advanceUntilIdle()

        assertFalse(vm.estado.value.carregando)
        assertTrue(vm.estado.value.fotos.isEmpty())
    }

    @Test
    fun `lista da foto mais recente para a mais antiga`() = runTest(dispatcher) {
        val antiga = repositorio.adicionar(agora.minusDays(2))
        val recente = repositorio.adicionar(agora.minusHours(1))
        val meio = repositorio.adicionar(agora.minusDays(1))

        val vm = criarViewModel()
        advanceUntilIdle()

        assertEquals(listOf(recente, meio, antiga), vm.estado.value.fotos)
    }

    // --- Tirar foto ---

    @Test
    fun `salva foto com data, hora e local`() = runTest(dispatcher) {
        val vm = criarViewModel()
        vm.aoAbrirCamera()
        vm.aoFotoCapturada("tmp/foto.jpg")
        advanceUntilIdle()

        val salva = repositorio.salvas.single()
        assertEquals("tmp/foto.jpg", salva.arquivo)
        assertEquals(agora, salva.dataHora)
        assertEquals(LocalFoto(pinheiros, "Pinheiros, São Paulo"), salva.local)
        assertNull(salva.motivoSemLocal)
        assertEquals(salva.foto, vm.estado.value.fotos.first())
        assertFalse(vm.estado.value.salvando)
    }

    @Test
    fun `sem permissao de localizacao salva sem local e com o motivo`() = runTest(dispatcher) {
        resultadoLocalizacao = ResultadoLocalizacao.Indisponivel(MotivoSemLocal.SEM_PERMISSAO)
        val vm = criarViewModel()
        vm.aoAbrirCamera()
        vm.aoFotoCapturada("tmp/foto.jpg")
        advanceUntilIdle()

        val salva = repositorio.salvas.single()
        assertNull(salva.local)
        assertEquals(MotivoSemLocal.SEM_PERMISSAO, salva.motivoSemLocal)
        assertEquals(0, chamadasNome)
    }

    @Test
    fun `localizacao desligada guarda o motivo`() = runTest(dispatcher) {
        resultadoLocalizacao = ResultadoLocalizacao.Indisponivel(MotivoSemLocal.LOCALIZACAO_DESLIGADA)
        val vm = criarViewModel()
        vm.aoFotoCapturada("tmp/foto.jpg")
        advanceUntilIdle()

        assertEquals(MotivoSemLocal.LOCALIZACAO_DESLIGADA, repositorio.salvas.single().motivoSemLocal)
    }

    @Test
    fun `sem internet salva as coordenadas sem nome`() = runTest(dispatcher) {
        nome = null
        val vm = criarViewModel()
        vm.aoFotoCapturada("tmp/foto.jpg")
        advanceUntilIdle()

        assertEquals(LocalFoto(pinheiros, null), repositorio.salvas.single().local)
    }

    @Test
    fun `localizacao demorada nao trava a foto - salva sem local`() = runTest(dispatcher) {
        localizacaoTrava = true
        val vm = criarViewModel()
        vm.aoAbrirCamera()
        vm.aoFotoCapturada("tmp/foto.jpg")
        advanceUntilIdle()

        val salva = repositorio.salvas.single()
        assertNull(salva.local)
        assertEquals(MotivoSemLocal.NAO_ENCONTRADO, salva.motivoSemLocal)
        assertEquals(1, vm.estado.value.fotos.size)
    }

    @Test
    fun `localizacao precisa que demora 8 segundos ainda e aproveitada`() = runTest(dispatcher) {
        // GPS (localização precisa) pode levar alguns segundos: o limite é de 10 s
        atrasoLocalizacaoMs = 8_000
        val vm = criarViewModel()
        vm.aoFotoCapturada("tmp/foto.jpg")
        advanceUntilIdle()

        assertEquals(LocalFoto(pinheiros, "Pinheiros, São Paulo"), repositorio.salvas.single().local)
    }

    @Test
    fun `localizacao que passa de 10 segundos e descartada`() = runTest(dispatcher) {
        atrasoLocalizacaoMs = 11_000
        val vm = criarViewModel()
        vm.aoFotoCapturada("tmp/foto.jpg")
        advanceUntilIdle()

        assertEquals(MotivoSemLocal.NAO_ENCONTRADO, repositorio.salvas.single().motivoSemLocal)
    }

    @Test
    fun `funciona mesmo sem abrir a camera antes`() = runTest(dispatcher) {
        val vm = criarViewModel()
        vm.aoFotoCapturada("tmp/foto.jpg")
        advanceUntilIdle()

        assertNotNull(repositorio.salvas.single().local)
    }

    @Test
    fun `erro ao salvar mostra mensagem`() = runTest(dispatcher) {
        repositorio.falharAoSalvar = true
        val vm = criarViewModel()
        vm.aoFotoCapturada("tmp/foto.jpg")
        advanceUntilIdle()

        assertEquals("Não foi possível salvar a foto", vm.estado.value.erro)
        assertFalse(vm.estado.value.salvando)

        vm.aoDispensarErro()
        assertNull(vm.estado.value.erro)
    }

    // --- Ver foto ---

    @Test
    fun `abrir foto carrega o local`() = runTest(dispatcher) {
        val foto = repositorio.adicionar(agora, LocalFoto(pinheiros, "Pinheiros, São Paulo"))
        val vm = criarViewModel()
        advanceUntilIdle()

        vm.aoAbrirFoto(foto)
        advanceUntilIdle()

        val aberta = assertNotNull(vm.estado.value.fotoAberta)
        assertEquals(foto, aberta.foto)
        assertEquals("Pinheiros, São Paulo", aberta.local?.nome)
    }

    @Test
    fun `abrir foto sem nome do local tenta descobrir e guarda`() = runTest(dispatcher) {
        val foto = repositorio.adicionar(agora, LocalFoto(pinheiros, null))
        val vm = criarViewModel()
        advanceUntilIdle()

        vm.aoAbrirFoto(foto)
        advanceUntilIdle()

        assertEquals("Pinheiros, São Paulo", vm.estado.value.fotoAberta?.local?.nome)
        assertEquals("Pinheiros, São Paulo", repositorio.locais[foto.id]?.nome)
    }

    @Test
    fun `abrir foto sem local mostra o motivo`() = runTest(dispatcher) {
        val foto = repositorio.adicionar(agora, local = null, motivo = MotivoSemLocal.LOCALIZACAO_DESLIGADA)
        val vm = criarViewModel()
        advanceUntilIdle()

        vm.aoAbrirFoto(foto)
        advanceUntilIdle()

        val aberta = assertNotNull(vm.estado.value.fotoAberta)
        assertNull(aberta.local)
        assertEquals(MotivoSemLocal.LOCALIZACAO_DESLIGADA, aberta.motivoSemLocal)
    }

    @Test
    fun `fechar foto`() = runTest(dispatcher) {
        val foto = repositorio.adicionar(agora)
        val vm = criarViewModel()
        advanceUntilIdle()
        vm.aoAbrirFoto(foto)
        advanceUntilIdle()

        vm.aoFecharFoto()

        assertNull(vm.estado.value.fotoAberta)
    }

    // --- Apagar ---

    @Test
    fun `apagar pede confirmacao`() = runTest(dispatcher) {
        val foto = repositorio.adicionar(agora)
        val vm = criarViewModel()
        advanceUntilIdle()
        vm.aoAbrirFoto(foto)
        advanceUntilIdle()

        vm.aoPedirApagar()

        assertTrue(vm.estado.value.confirmandoApagar)
        assertTrue(repositorio.apagadas.isEmpty())
    }

    @Test
    fun `cancelar nao apaga`() = runTest(dispatcher) {
        val foto = repositorio.adicionar(agora)
        val vm = criarViewModel()
        advanceUntilIdle()
        vm.aoAbrirFoto(foto)
        advanceUntilIdle()

        vm.aoPedirApagar()
        vm.aoCancelarApagar()
        advanceUntilIdle()

        assertFalse(vm.estado.value.confirmandoApagar)
        assertTrue(repositorio.apagadas.isEmpty())
        assertEquals(listOf(foto), vm.estado.value.fotos)
    }

    @Test
    fun `confirmar apaga, tira da lista e fecha a foto`() = runTest(dispatcher) {
        val foto = repositorio.adicionar(agora)
        val outra = repositorio.adicionar(agora.minusDays(1))
        val vm = criarViewModel()
        advanceUntilIdle()
        vm.aoAbrirFoto(foto)
        advanceUntilIdle()

        vm.aoPedirApagar()
        vm.aoConfirmarApagar()
        advanceUntilIdle()

        assertEquals(listOf(foto), repositorio.apagadas)
        assertEquals(listOf(outra), vm.estado.value.fotos)
        assertNull(vm.estado.value.fotoAberta)
        assertFalse(vm.estado.value.confirmandoApagar)
    }

    // --- Repositório em memória ---

    private class RepositorioFalso : RepositorioFotos {
        data class Salva(
            val arquivo: String,
            val dataHora: ZonedDateTime,
            val local: LocalFoto?,
            val motivoSemLocal: MotivoSemLocal?,
            val foto: FotoAlbum,
        )

        private val fotos = mutableListOf<FotoAlbum>()
        val locais = mutableMapOf<String, LocalFoto?>()
        private val motivos = mutableMapOf<String, MotivoSemLocal?>()
        val salvas = mutableListOf<Salva>()
        val apagadas = mutableListOf<FotoAlbum>()
        var falharAoSalvar = false
        private var proximoId = 1

        fun adicionar(dataHora: ZonedDateTime, local: LocalFoto? = null, motivo: MotivoSemLocal? = null): FotoAlbum {
            val foto = FotoAlbum("content://foto/${proximoId++}", dataHora)
            fotos += foto
            locais[foto.id] = local
            motivos[foto.id] = motivo
            return foto
        }

        override suspend fun listar() = fotos.toList()

        override suspend fun salvar(
            arquivoTemporario: String,
            dataHora: ZonedDateTime,
            local: LocalFoto?,
            motivoSemLocal: MotivoSemLocal?,
        ): FotoAlbum {
            if (falharAoSalvar) error("disco cheio")
            val foto = adicionar(dataHora, local, motivoSemLocal)
            salvas += Salva(arquivoTemporario, dataHora, local, motivoSemLocal, foto)
            return foto
        }

        override suspend fun lerLocal(foto: FotoAlbum) = locais[foto.id]

        override suspend fun lerMotivoSemLocal(foto: FotoAlbum) = motivos[foto.id]

        override suspend fun atualizarNomeLocal(foto: FotoAlbum, nome: String) {
            locais[foto.id] = locais[foto.id]?.copy(nome = nome)
        }

        override suspend fun apagar(foto: FotoAlbum) {
            fotos -= foto
            apagadas += foto
        }
    }
}
