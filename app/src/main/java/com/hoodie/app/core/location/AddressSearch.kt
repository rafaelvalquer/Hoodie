package com.hoodie.app.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class AddressResult(val label: String, val latitude: Double, val longitude: Double)

/** Endereço ↔ coordenada. Precisa de conexão; sem ela, devolve vazio/null. */
interface AddressSearch {
    suspend fun search(query: String): List<AddressResult>
    suspend fun reverse(latitude: Double, longitude: Double): String?
}

/**
 * Geocoder do próprio Android (sem chave de API). Só é chamado na tela de
 * escolher um lugar: o texto digitado vai para o serviço de geocodificação do
 * sistema e nada mais sai do aparelho.
 */
@Singleton
class GeocoderAddressSearch @Inject constructor(@ApplicationContext private val context: Context) : AddressSearch {

    private val geocoder by lazy { Geocoder(context, Locale.forLanguageTag("pt-BR")) }

    override suspend fun search(query: String): List<AddressResult> {
        if (query.isBlank() || !Geocoder.isPresent()) return emptyList()
        val q = query.trim()
        val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            async { l -> geocoder.getFromLocationName(q, MAX_RESULTS, l) }
        } else {
            blocking { geocoder.getFromLocationName(q, MAX_RESULTS) }
        }
        return list.orEmpty().mapNotNull { a ->
            if (a.hasLatitude() && a.hasLongitude()) AddressResult(a.getAddressLine(0) ?: a.featureName ?: q, a.latitude, a.longitude) else null
        }
    }

    override suspend fun reverse(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            async { l -> geocoder.getFromLocation(latitude, longitude, 1, l) }
        } else {
            blocking { geocoder.getFromLocation(latitude, longitude, 1) }
        }
        return list?.firstOrNull()?.getAddressLine(0)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun async(call: (Geocoder.GeocodeListener) -> Unit): List<Address>? = withTimeoutOrNull(TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            runCatching {
                call(object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) { if (cont.isActive) cont.resume(addresses) }
                    override fun onError(errorMessage: String?) { if (cont.isActive) cont.resume(emptyList()) }
                })
            }.onFailure { if (cont.isActive) cont.resume(emptyList()) }
        }
    }

    /** Até a API 32 o Geocoder bloqueia (rede): sempre fora da main thread. */
    @SuppressLint("NewApi")
    private suspend fun blocking(call: () -> List<Address>?): List<Address>? = withTimeoutOrNull(TIMEOUT_MS) {
        withContext(Dispatchers.IO) { runCatching(call).getOrNull() }
    }

    companion object {
        const val MAX_RESULTS = 5
        const val TIMEOUT_MS = 12_000L
    }
}
