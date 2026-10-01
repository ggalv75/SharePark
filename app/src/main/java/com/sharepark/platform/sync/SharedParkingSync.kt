package com.sharepark.platform.sync

import android.content.Context
import android.util.Log
import com.sharepark.data.remote.cloud.AuthRepository
import com.sharepark.data.remote.cloud.CloudParking
import com.sharepark.data.remote.cloud.SharedVehicleRepository
import com.sharepark.data.repository.ParkingRepository
import com.sharepark.data.repository.VehicleRepository
import com.sharepark.platform.notification.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors every shared car's cloud parkings into the local database while the app process is
 * alive. Because they land in Room like any other parking, the map, history and notifications
 * need no idea that a parking came from someone else's phone.
 */
@Singleton
class SharedParkingSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val sharedVehicleRepository: SharedVehicleRepository,
    private val vehicleRepository: VehicleRepository,
    private val parkingRepository: ParkingRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private data class Link(val vehicleId: Long, val cloudId: String, val name: String)

    fun start() {
        if (!authRepository.isAvailable || job != null) return
        job = scope.launch {
            combine(authRepository.currentUser, vehicleRepository.linkedVehicles) { user, vehicles ->
                user to vehicles.map { Link(it.id, it.cloudId!!, it.name) }
            }
                // The vehicles table changes for unrelated reasons (active car, rename);
                // only a different set of links or user should restart the listeners.
                .distinctUntilChanged()
                .collectLatest { (user, links) ->
                    if (user == null || links.isEmpty()) return@collectLatest
                    val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(HISTORY_DAYS)
                    coroutineScope {
                        links.forEach { link ->
                            launch {
                                sharedVehicleRepository.observeParkings(link.cloudId, since)
                                    .collect { parkings ->
                                        parkings.forEach { store(link, it, user.uid) }
                                    }
                            }
                        }
                    }
                }
        }
    }

    private suspend fun store(link: Link, parking: CloudParking, myUid: String) {
        val isMine = parking.parkedByUid == myUid
        val becameCurrent = try {
            parkingRepository.insertSyncedParking(
                vehicleId = link.vehicleId,
                remoteKey = parking.key,
                latitude = parking.latitude,
                longitude = parking.longitude,
                accuracy = parking.accuracy,
                address = parking.address,
                parkedAt = parking.parkedAt,
                parkedByName = if (isMine) null else parking.parkedByName
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not store synced parking ${parking.key}", e)
            null
        } ?: return

        // Only a fresh parking is news. Joining a car pulls in its whole recent history, which
        // must not turn into a burst of "someone parked" notifications.
        val isFresh = System.currentTimeMillis() - parking.parkedAt < FRESH_WINDOW_MS
        if (becameCurrent && !isMine && isFresh) {
            NotificationHelper.showSharedParkingNotification(
                context = context,
                vehicleId = link.vehicleId,
                vehicleName = link.name,
                parkedByName = parking.parkedByName,
                address = parking.address ?: "${parking.latitude}, ${parking.longitude}"
            )
        }
    }

    private companion object {
        const val TAG = "SharedParkingSync"
        const val HISTORY_DAYS = 30L
        val FRESH_WINDOW_MS = TimeUnit.MINUTES.toMillis(15)
    }
}
