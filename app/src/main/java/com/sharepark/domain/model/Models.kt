package com.sharepark.domain.model

data class Vehicle(
    val id: Long = 0,
    val name: String,
    val btAddress: String,
    val btName: String,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class ParkingRecord(
    val id: Long = 0,
    val vehicleId: Long,
    val vehicleName: String = "",
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float = 0f,
    val address: String? = null,
    val mapUrl: String,
    val isCurrent: Boolean = true,
    val parkedAt: Long = System.currentTimeMillis()
)

data class TrustedContact(
    val id: Long = 0,
    val name: String,
    val phoneNumber: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A circle on the map — an address the user cares about plus how far around it still counts.
 * Automation only fires for parkings that land inside one of these.
 */
data class AutomationZone(
    val id: Long = 0,
    val label: String,
    val address: String = "",
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = DEFAULT_RADIUS_METERS,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val DEFAULT_RADIUS_METERS = 200
        const val MIN_RADIUS_METERS = 50
        const val MAX_RADIUS_METERS = 500
    }
}
