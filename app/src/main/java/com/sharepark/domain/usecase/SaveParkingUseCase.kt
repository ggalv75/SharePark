package com.sharepark.domain.usecase

import com.sharepark.data.remote.GeocodingService
import com.sharepark.data.repository.ParkingRepository
import javax.inject.Inject
import javax.inject.Singleton

data class SaveParkingResult(val recordId: Long, val address: String)

@Singleton
class SaveParkingUseCase @Inject constructor(
    private val parkingRepository: ParkingRepository,
    private val geocodingService: GeocodingService
) {
    suspend operator fun invoke(
        vehicleId: Long,
        latitude: Double,
        longitude: Double,
        accuracy: Float
    ): SaveParkingResult {
        val mapUrl = "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"

        // Save initial record with a placeholder address while geocoding resolves
        val recordId = parkingRepository.saveParkingRecord(
            vehicleId = vehicleId,
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            address = "מאתר כתובת...",
            mapUrl = mapUrl
        )

        // Reverse-geocode to a human-readable Hebrew address
        val resolvedAddress = try {
            val geoResult = geocodingService.reverseGeocode(latitude, longitude)
            parkingRepository.updateAddress(recordId, geoResult.address)
            geoResult.address
        } catch (e: Exception) {
            e.printStackTrace()
            "כתובת לא זוהתה"
        }

        return SaveParkingResult(recordId, resolvedAddress)
    }
}
