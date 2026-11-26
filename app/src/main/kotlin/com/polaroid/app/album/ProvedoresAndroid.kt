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
 * Localização APROXIMADA (rede/Wi-Fi primeiro): rápida e econômica em bateria.
 * Pedida uma única vez por foto, sem rastrear em segundo plano.
 */
class ProvedorLocalizacaoAndroid(private val context: Context) : ProvedorLocalizacao {

    @SuppressLint("MissingPermission") // verificada logo abaixo
    override suspend fun localizacaoAtual(): Coordenadas? {
        val permitido = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!permitido) return null

        val gerenciador = context.getSystemService(LocationManager::class.java) ?: return null
        val provedor = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { gerenciador.isProviderEnabled(it) } ?: return null

        return suspendCancellableCoroutine { continuacao ->
            val cancelamento = CancellationSignal()
            continuacao.invokeOnCancellation { cancelamento.cancel() }
            LocationManagerCompat.getCurrentLocation(
                gerenciador,
                provedor,
                cancelamento,
                ContextCompat.getMainExecutor(context),
            ) { local ->
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
            listOfNotNull(it.subLocality, it.locality ?: it.subAdminArea)
                .distinct()
                .joinToString(", ")
                .ifBlank { null }
        }
    }
}
