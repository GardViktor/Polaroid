package com.polaroid.app.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.ZonedDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class EstadoAlbum(
    val carregando: Boolean = true,
    val fotos: List<FotoAlbum> = emptyList(),
    val fotoAberta: DetalhesFoto? = null,
    val confirmandoApagar: Boolean = false,
    val salvando: Boolean = false,
    val erro: String? = null,
)

/**
 * Álbum de fotos com data, hora e local.
 *
 * A localização começa a ser buscada quando a câmera abre (pronta na hora do clique)
 * e nunca trava a foto: se não chegar em [limiteLocalizacao], a foto é salva sem local.
 */
class AlbumViewModel(
    private val repositorio: RepositorioFotos,
    private val localizacao: ProvedorLocalizacao,
    private val nomeLocal: ProvedorNomeLocal,
    private val relogio: Clock,
    private val limiteLocalizacao: Duration = 5.seconds,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoAlbum())
    val estado: StateFlow<EstadoAlbum> = _estado.asStateFlow()

    private var localizacaoEmAndamento: Deferred<ResultadoLocalizacao>? = null

    init {
        viewModelScope.launch {
            val fotos = tentar { repositorio.listar() } ?: emptyList()
            _estado.update { it.copy(carregando = false, fotos = maisRecentesPrimeiro(fotos)) }
        }
    }

    fun aoAbrirCamera() {
        localizacaoEmAndamento?.cancel()
        localizacaoEmAndamento = buscarLocalizacao()
    }

    fun aoFotoCapturada(arquivoTemporario: String) {
        // Data/hora do clique, não do fim do salvamento
        val dataHora = ZonedDateTime.now(relogio)
        val pendente = localizacaoEmAndamento ?: buscarLocalizacao()
        localizacaoEmAndamento = null
        _estado.update { it.copy(salvando = true) }

        viewModelScope.launch {
            val resultado = withTimeoutOrNull(limiteLocalizacao) { pendente.await() }
                ?: ResultadoLocalizacao.Indisponivel(MotivoSemLocal.NAO_ENCONTRADO)
            pendente.cancel()

            val (local, motivo) = when (resultado) {
                is ResultadoLocalizacao.Encontrada ->
                    LocalFoto(resultado.coordenadas, descobrirNome(resultado.coordenadas)) to null
                is ResultadoLocalizacao.Indisponivel -> null to resultado.motivo
            }

            val foto = tentar { repositorio.salvar(arquivoTemporario, dataHora, local, motivo) }
            _estado.update {
                if (foto != null) {
                    it.copy(salvando = false, fotos = maisRecentesPrimeiro(it.fotos + foto))
                } else {
                    it.copy(salvando = false, erro = "Não foi possível salvar a foto")
                }
            }
        }
    }

    fun aoAbrirFoto(foto: FotoAlbum) {
        _estado.update { it.copy(fotoAberta = DetalhesFoto(foto, null), confirmandoApagar = false) }

        viewModelScope.launch {
            var local = tentar { repositorio.lerLocal(foto) }
            // Foto tirada sem internet: tenta descobrir o nome agora e guarda na foto
            if (local != null && local.nome == null) {
                descobrirNome(local.coordenadas)?.let { nome ->
                    local = local.copy(nome = nome)
                    tentar { repositorio.atualizarNomeLocal(foto, nome) }
                }
            }
            val motivo = if (local == null) tentar { repositorio.lerMotivoSemLocal(foto) } else null
            _estado.update {
                if (it.fotoAberta?.foto == foto) it.copy(fotoAberta = DetalhesFoto(foto, local, motivo)) else it
            }
        }
    }

    fun aoFecharFoto() {
        _estado.update { it.copy(fotoAberta = null, confirmandoApagar = false) }
    }

    fun aoPedirApagar() {
        _estado.update { if (it.fotoAberta != null) it.copy(confirmandoApagar = true) else it }
    }

    fun aoCancelarApagar() {
        _estado.update { it.copy(confirmandoApagar = false) }
    }

    fun aoConfirmarApagar() {
        val foto = _estado.value.fotoAberta?.foto ?: return
        _estado.update { it.copy(confirmandoApagar = false) }

        viewModelScope.launch {
            val apagou = tentar { repositorio.apagar(foto) } != null
            _estado.update {
                if (apagou) {
                    it.copy(fotos = it.fotos - foto, fotoAberta = null)
                } else {
                    it.copy(erro = "Não foi possível apagar a foto")
                }
            }
        }
    }

    fun aoDispensarErro() {
        _estado.update { it.copy(erro = null) }
    }

    private fun buscarLocalizacao(): Deferred<ResultadoLocalizacao> =
        viewModelScope.async {
            tentar { localizacao.localizacaoAtual() }
                ?: ResultadoLocalizacao.Indisponivel(MotivoSemLocal.NAO_ENCONTRADO)
        }

    private suspend fun descobrirNome(coordenadas: Coordenadas): String? =
        withTimeoutOrNull(limiteLocalizacao) { tentar { nomeLocal.nomeDoLocal(coordenadas) } }

    private fun maisRecentesPrimeiro(fotos: List<FotoAlbum>) =
        fotos.sortedByDescending { it.dataHora.toInstant() }

    /** Executa e devolve null em caso de erro, sem engolir cancelamento de corrotina. */
    private suspend fun <T> tentar(bloco: suspend () -> T): T? =
        try {
            bloco()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
}
