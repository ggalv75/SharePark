package com.sharepark.domain.usecase

import com.sharepark.data.remote.cloud.AuthRepository
import com.sharepark.data.remote.cloud.SharedVehicleRepository
import com.sharepark.data.repository.ParkingRepository
import com.sharepark.data.repository.VehicleRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class CleanupOldRecordsUseCase @Inject constructor(
    private val parkingRepository: ParkingRepository,
    private val vehicleRepository: VehicleRepository,
    private val authRepository: AuthRepository,
    private val sharedVehicleRepository: SharedVehicleRepository
) {
    suspend operator fun invoke(): Int {
        val thirtyDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        val deleted = parkingRepository.deleteOlderThan(thirtyDaysAgo)

        // Shared cars keep the same 30-day window in the cloud. Best effort: any member's
        // phone can do it, so one that's offline or signed out today changes nothing.
        if (authRepository.currentUserNow() != null) {
            vehicleRepository.getLinkedVehicles().forEach { vehicle ->
                runCatching { sharedVehicleRepository.deleteParkings(vehicle.cloudId!!, thirtyDaysAgo) }
            }
        }
        return deleted
    }
}
