package com.sharepark.domain.usecase

import com.sharepark.data.remote.cloud.AuthRepository
import com.sharepark.data.remote.cloud.CloudParking
import com.sharepark.data.remote.cloud.SharedVehicleRepository
import com.sharepark.data.repository.ParkingRepository
import com.sharepark.domain.model.ParkingRecord
import com.sharepark.domain.model.Vehicle
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puts a parking this phone detected into the shared vehicle's cloud history, so every other
 * member sees it. A no-op for private cars or while signed out.
 */
@Singleton
class PublishSharedParkingUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sharedVehicleRepository: SharedVehicleRepository,
    private val parkingRepository: ParkingRepository
) {
    /** Returns true once the server has confirmed the write. */
    suspend operator fun invoke(vehicle: Vehicle, record: ParkingRecord): Boolean {
        val cloudId = vehicle.cloudId ?: return false
        val user = authRepository.currentUserNow() ?: return false

        // The key goes on the local record before the upload: Firestore echoes our own write
        // back through the listener at once, and the sync recognises it by this key.
        val key = UUID.randomUUID().toString()
        parkingRepository.setRemoteKey(record.id, key)

        val parking = CloudParking(
            key = key,
            latitude = record.latitude,
            longitude = record.longitude,
            accuracy = record.accuracy,
            address = record.address,
            parkedAt = record.parkedAt,
            parkedByUid = user.uid,
            parkedByName = user.displayName
        )
        // Offline, Firestore keeps the write queued on disk and sends it later, so giving up on
        // the confirmation here doesn't lose the parking.
        return runCatching {
            withTimeoutOrNull(UPLOAD_TIMEOUT_MS) {
                sharedVehicleRepository.uploadParking(cloudId, parking)
            } != null
        }.getOrDefault(false)
    }

    private companion object {
        const val UPLOAD_TIMEOUT_MS = 15_000L
    }
}
