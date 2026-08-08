package com.sharepark.data.repository

import com.sharepark.data.local.dao.VehicleDao
import com.sharepark.data.local.entity.VehicleEntity
import com.sharepark.domain.model.Vehicle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleRepository @Inject constructor(
    private val vehicleDao: VehicleDao
) {
    val allVehicles: Flow<List<Vehicle>> = vehicleDao.getAllVehicles().map { entities ->
        entities.map { it.toDomain() }
    }

    val activeVehicle: Flow<Vehicle?> = vehicleDao.getActiveVehicle().map { it?.toDomain() }

    suspend fun getVehicleById(id: Long): Vehicle? {
        return vehicleDao.getVehicleById(id)?.toDomain()
    }

    suspend fun getVehicleByBtAddress(address: String): Vehicle? {
        return vehicleDao.getVehicleByBtAddress(address)?.toDomain()
    }

    suspend fun insertVehicle(name: String, btAddress: String, btName: String): Long {
        val entity = VehicleEntity(name = name, btAddress = btAddress, btName = btName)
        val id = vehicleDao.insert(entity)
        if (vehicleDao.getVehicleCount() == 1) {
            vehicleDao.setActiveVehicleTransaction(id)
        }
        return id
    }

    suspend fun deleteVehicle(vehicle: Vehicle) {
        vehicleDao.delete(vehicle.toEntity())
    }

    suspend fun renameVehicle(vehicleId: Long, newName: String) {
        vehicleDao.renameVehicle(vehicleId, newName)
    }

    suspend fun setActiveVehicle(vehicleId: Long) {
        vehicleDao.setActiveVehicleTransaction(vehicleId)
    }

    private fun VehicleEntity.toDomain() = Vehicle(
        id = id,
        name = name,
        btAddress = btAddress,
        btName = btName,
        isActive = isActive,
        createdAt = createdAt
    )

    private fun Vehicle.toEntity() = VehicleEntity(
        id = id,
        name = name,
        btAddress = btAddress,
        btName = btName,
        isActive = isActive,
        createdAt = createdAt
    )
}
