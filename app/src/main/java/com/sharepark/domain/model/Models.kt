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
