package com.sharepark.domain.usecase

import com.sharepark.data.repository.ParkingRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class CleanupOldRecordsUseCase @Inject constructor(
    private val parkingRepository: ParkingRepository
) {
    suspend operator fun invoke(): Int {
        val thirtyDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        return parkingRepository.deleteOlderThan(thirtyDaysAgo)
    }
}
