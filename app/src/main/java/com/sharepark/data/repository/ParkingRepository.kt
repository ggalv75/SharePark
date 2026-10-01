package com.sharepark.data.repository

import com.sharepark.data.local.dao.ParkingRecordDao
import com.sharepark.data.local.dao.VehicleDao
import com.sharepark.data.local.entity.ParkingRecordEntity
import com.sharepark.domain.model.ParkingRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParkingRepository @Inject constructor(
    private val parkingRecordDao: ParkingRecordDao,
    private val vehicleDao: VehicleDao
) {
    val allCurrentParkings: Flow<List<ParkingRecord>> = parkingRecordDao.getAllCurrentParkings().flatMapLatest { records ->
        if (records.isEmpty()) return@flatMapLatest flowOf(emptyList())
        vehicleDao.getAllVehicles().map { vehicles ->
            val vehicleMap = vehicles.associateBy { it.id }
            records.map { entity ->
                entity.toDomain(vehicleMap[entity.vehicleId]?.name ?: "Unknown")
            }
        }
    }

    fun getCurrentParkingForVehicle(vehicleId: Long): Flow<ParkingRecord?> {
        return parkingRecordDao.getCurrentParkingForVehicle(vehicleId).flatMapLatest { record ->
            if (record == null) return@flatMapLatest flowOf(null)
            val vehicle = vehicleDao.getVehicleById(vehicleId)
            flowOf(record.toDomain(vehicle?.name ?: "Unknown"))
        }
    }

    fun getHistoryForVehicle(vehicleId: Long): Flow<List<ParkingRecord>> {
        return parkingRecordDao.getHistoryForVehicle(vehicleId).flatMapLatest { records ->
            if (records.isEmpty()) return@flatMapLatest flowOf(emptyList())
            val vehicle = vehicleDao.getVehicleById(vehicleId)
            val vehicleName = vehicle?.name ?: "Unknown"
            flowOf(records.map { it.toDomain(vehicleName) })
        }
    }

    suspend fun saveParkingRecord(
        vehicleId: Long,
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        address: String?,
        mapUrl: String
    ): Long {
        val entity = ParkingRecordEntity(
            vehicleId = vehicleId,
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            address = address,
            mapUrl = mapUrl,
            isCurrent = true
        )
        return parkingRecordDao.insertAndSetCurrent(entity)
    }

    suspend fun getCurrentParkingOnce(vehicleId: Long): ParkingRecord? {
        val record = parkingRecordDao.getCurrentParkingOnce(vehicleId) ?: return null
        val vehicle = vehicleDao.getVehicleById(vehicleId)
        return record.toDomain(vehicle?.name ?: "Unknown")
    }

    suspend fun setRemoteKey(recordId: Long, remoteKey: String) {
        parkingRecordDao.setRemoteKey(recordId, remoteKey)
    }

    /** See [ParkingRecordDao.insertSynced] for the meaning of the result. */
    suspend fun insertSyncedParking(
        vehicleId: Long,
        remoteKey: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        address: String?,
        parkedAt: Long,
        parkedByName: String?
    ): Boolean? = parkingRecordDao.insertSynced(
        ParkingRecordEntity(
            vehicleId = vehicleId,
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            address = address,
            mapUrl = "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude",
            parkedAt = parkedAt,
            remoteKey = remoteKey,
            parkedByName = parkedByName
        )
    )

    suspend fun updateAddress(recordId: Long, address: String) {
        parkingRecordDao.updateAddress(recordId, address)
    }

    suspend fun deleteOlderThan(cutoffMillis: Long): Int {
        return parkingRecordDao.deleteOlderThan(cutoffMillis)
    }

    suspend fun deleteByVehicleId(vehicleId: Long) {
        parkingRecordDao.deleteByVehicleId(vehicleId)
    }

    private fun ParkingRecordEntity.toDomain(vehicleName: String) = ParkingRecord(
        id = id,
        vehicleId = vehicleId,
        vehicleName = vehicleName,
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        address = address,
        mapUrl = mapUrl,
        isCurrent = isCurrent,
        parkedAt = parkedAt,
        parkedByName = parkedByName
    )
}
