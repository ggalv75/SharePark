package com.sharepark.platform.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object LocationHelper {

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context, timeoutMillis: Long = 15000L): Location? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        
        // Try getting last known location first (quick check)
        try {
            val lastLocation = suspendCancellableCoroutine<Location?> { continuation ->
                client.lastLocation.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        continuation.resume(task.result)
                    } else {
                        continuation.resume(null)
                    }
                }
            }
            // If last location is recent (less than 1 minute old) and accurate enough, return it
            if (lastLocation != null && (System.currentTimeMillis() - lastLocation.time) < 60000L && lastLocation.accuracy < 20f) {
                return lastLocation
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Request a fresh, high-accuracy single fix
        return withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val request = LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    1000L
                ).setMaxUpdates(1)
                 .setDurationMillis(timeoutMillis)
                 .build()

                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        val location = result.lastLocation
                        client.removeLocationUpdates(this)
                        if (continuation.isActive) {
                            continuation.resume(location)
                        }
                    }
                }

                client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                    .addOnFailureListener {
                        client.removeLocationUpdates(callback)
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }

                continuation.invokeOnCancellation {
                    client.removeLocationUpdates(callback)
                }
            }
        }
    }
}
