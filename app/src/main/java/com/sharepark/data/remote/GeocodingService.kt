package com.sharepark.data.remote

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.Locale
import kotlin.coroutines.resume

// ── Retrofit API interface (fallback path) ──────────────────────────────────
interface GeocodingApi {
    @GET("maps/api/geocode/json")
    suspend fun reverseGeocode(
        @Query("latlng") latLng: String,
        @Query("key") apiKey: String,
        @Query("language") language: String = "he"
    ): GeocodingResponse
}

// ── Response DTOs ───────────────────────────────────────────────────────────
data class GeocodingResponse(
    val status: String,
    val results: List<GeocodingResult>,
    val error_message: String? = null
)

data class GeocodingResult(
    val formatted_address: String,
    val address_components: List<AddressComponent>
)

data class AddressComponent(
    val long_name: String,
    val short_name: String,
    val types: List<String>
)

// ── Service wrapper ─────────────────────────────────────────────────────────
class GeocodingService(
    private val context: Context,
    private val api: GeocodingApi,
    private val apiKey: String
) {
    data class GeoResult(val address: String, val mapUrl: String)

    suspend fun reverseGeocode(latitude: Double, longitude: Double): GeoResult {
        val mapUrl = "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"

        // 1) Platform Geocoder — no API key, unaffected by Maps key restrictions
        platformGeocode(latitude, longitude)?.let { return GeoResult(it, mapUrl) }

        // 2) Google Geocoding web API — only works if the key allows web-service calls
        httpGeocode(latitude, longitude)?.let { return GeoResult(it, mapUrl) }

        return GeoResult(FALLBACK_ADDRESS, mapUrl)
    }

    private suspend fun platformGeocode(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale("he", "IL"))
        return try {
            val addresses: List<Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // The listener isn't guaranteed to fire on every device state — cap the wait.
                withTimeoutOrNull(8000L) {
                    suspendCancellableCoroutine { continuation ->
                        geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(result: MutableList<Address>) {
                                if (continuation.isActive) continuation.resume(result)
                            }

                            override fun onError(errorMessage: String?) {
                                Log.w(TAG, "Platform geocoder error: $errorMessage")
                                if (continuation.isActive) continuation.resume(emptyList())
                            }
                        })
                    }
                } ?: emptyList()
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1) ?: emptyList()
            }
            addresses.firstOrNull()?.let { formatAddress(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Platform geocoder threw", e)
            null
        }
    }

    private fun formatAddress(address: Address): String {
        address.getAddressLine(0)?.let { return it }
        val street = listOfNotNull(address.thoroughfare, address.subThoroughfare).joinToString(" ")
        return listOf(street, address.locality.orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .ifBlank { FALLBACK_ADDRESS }
    }

    private suspend fun httpGeocode(latitude: Double, longitude: Double): String? {
        if (apiKey.isBlank()) return null
        return try {
            val response = api.reverseGeocode(
                latLng = "$latitude,$longitude",
                apiKey = apiKey,
                language = "he"
            )
            if (response.status == "OK" && response.results.isNotEmpty()) {
                response.results.first().formatted_address
            } else {
                Log.w(TAG, "HTTP geocode failed: status=${response.status} error=${response.error_message}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTP geocode request threw", e)
            null
        }
    }

    private companion object {
        const val TAG = "GeocodingService"
        const val FALLBACK_ADDRESS = "כתובת לא זוהתה"
    }
}
