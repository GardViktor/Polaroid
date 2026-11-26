package com.polaroid.app.album

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Localização PRECISA quando permitida (o usuário pode escolher "aproximada"), pedida uma
 * única vez por foto, sem rastrear em segundo plano.
 *
 * Com permissão precisa: consulta "fused" (Android 12+) e GPS ao mesmo tempo e usa a
 * primeira resposta; a rede (menos precisa, costuma errar o bairro) só entra se eles falharem.
 * Só com aproximada: consulta todos os provedores ligados ao mesmo tempo.
 */
class ProvedorLocalizacaoAndroid(private val context: Context) : ProvedorLocalizacao {

    override suspend fun localizacaoAtual(): ResultadoLocalizacao {
        fun concedida(permissao: String) =
            ContextCompat.checkSelfPermission(context, permissao) == PackageManager.PERMISSION_GRANTED
        val precisa = concedida(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!precisa && !concedida(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            return ResultadoLocalizacao.Indisponivel(MotivoSemLocal.SEM_PERMISSAO)
        }

        val gerenciador = context.getSystemService(LocationManager::class.java)
        if (gerenciador == null || !LocationManagerCompat.isLocationEnabled(gerenciador)) {
            return ResultadoLocalizacao.Indisponivel(MotivoSemLocal.LOCALIZACAO_DESLIGADA)
        }

        val provedores = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }.filter { it in gerenciador.allProviders && gerenciador.isProviderEnabled(it) }

        if (provedores.isEmpty()) {
            return ResultadoLocalizacao.Indisponivel(MotivoSemLocal.LOCALIZACAO_DESLIGADA)
        }

        val fontes = { lista: List<String> -> lista.map { provedor -> suspend { consultar(gerenciador, provedor) } } }
        val coordenadas = if (precisa) {
            val (precisos, rede) = provedores.partition { it != LocationManager.NETWORK_PROVIDER }
            primeiraLocalizacao(fontes(precisos)) ?: primeiraLocalizacao(fontes(rede))
        } else {
            primeiraLocalizacao(fontes(provedores))
        }
        return coordenadas?.let { ResultadoLocalizacao.Encontrada(it) }
            ?: ResultadoLocalizacao.Indisponivel(MotivoSemLocal.NAO_ENCONTRADO)
    }

    @SuppressLint("MissingPermission") // verificada em localizacaoAtual()
    private suspend fun consultar(gerenciador: LocationManager, provedor: String): Coordenadas? =
        suspendCancellableCoroutine { continuacao ->
            val cancelamento = CancellationSignal()
            continuacao.invokeOnCancellation { cancelamento.cancel() }
            LocationManagerCompat.getCurrentLocation(
                gerenciador,
                provedor,
                cancelamento,
                ContextCompat.getMainExecutor(context),
            ) { local ->
                if (continuacao.isActive) {
                    continuacao.resume(local?.let { Coordenadas(it.latitude, it.longitude) })
                }
            }
        }
}

/** Nome do lugar ("Bairro, Cidade") pelo serviço de endereços do Android (precisa de internet). */
class ProvedorNomeLocalGeocoder(private val context: Context) : ProvedorNomeLocal {

    override suspend fun nomeDoLocal(coordenadas: Coordenadas): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.forLanguageTag("pt-BR"))

        val endereco: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuacao ->
                geocoder.getFromLocation(
                    coordenadas.latitude,
                    coordenadas.longitude,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(enderecos: MutableList<Address>) {
                            continuacao.resume(enderecos.firstOrNull())
                        }

                        override fun onError(mensagem: String?) {
                            continuacao.resume(null)
                        }
                    },
                )
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(coordenadas.latitude, coordenadas.longitude, 1)?.firstOrNull()
            }
        }

        return endereco?.let {
            EnderecoBrasil.montarNome(
                bairro = it.subLocality,
                cidade = it.locality,
                municipio = it.subAdminArea,
                linhaEndereco = if (it.maxAddressLineIndex >= 0) it.getAddressLine(0) else null,
            )
        }
    }
}
