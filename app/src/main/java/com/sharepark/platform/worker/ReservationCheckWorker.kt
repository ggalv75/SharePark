package com.sharepark.platform.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sharepark.data.remote.cloud.AuthRepository
import com.sharepark.data.remote.cloud.SharedVehicleRepository
import com.sharepark.data.repository.VehicleRepository
import com.sharepark.platform.notification.ReservationNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * The live listener only runs while the app's process is alive. This periodic check covers the
 * rest of the time, so a member hears about a booking even if they haven't opened the app.
 */
@HiltWorker
class ReservationCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val authRepository: AuthRepository,
    private val sharedVehicleRepository: SharedVehicleRepository,
    private val vehicleRepository: VehicleRepository,
    private val reservationNotifier: ReservationNotifier
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!authRepository.isAvailable) return Result.success()
        val user = authRepository.currentUserNow() ?: return Result.success()
        vehicleRepository.getLinkedVehicles().forEach { vehicle ->
            val cloudId = vehicle.cloudId ?: return@forEach
            try {
                val reservations = sharedVehicleRepository.upcomingReservations(cloudId)
                reservationNotifier.onReservations(vehicle.id, cloudId, vehicle.name, user.uid, reservations)
            } catch (e: Exception) {
                // Offline or no longer a member — the next run (or the live listener) catches up.
                Log.w(TAG, "Reservation check for $cloudId failed", e)
            }
        }
        return Result.success()
    }

    private companion object {
        const val TAG = "ReservationCheckWorker"
    }
}
