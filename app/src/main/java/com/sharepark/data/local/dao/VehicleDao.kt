package com.sharepark.data.local.dao

import androidx.room.*
import com.sharepark.data.local.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {

    @Query("SELECT * FROM vehicles ORDER BY created_at DESC")
    fun getAllVehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE id = :id LIMIT 1")
    suspend fun getVehicleById(id: Long): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE bt_address = :address LIMIT 1")
    suspend fun getVehicleByBtAddress(address: String): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE is_active = 1 LIMIT 1")
    fun getActiveVehicle(): Flow<VehicleEntity?>

    @Query("SELECT * FROM vehicles WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveVehicleOnce(): VehicleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vehicle: VehicleEntity): Long

    @Update
    suspend fun update(vehicle: VehicleEntity)

    @Delete
    suspend fun delete(vehicle: VehicleEntity)

    @Query("UPDATE vehicles SET name = :name WHERE id = :vehicleId")
    suspend fun renameVehicle(vehicleId: Long, name: String)

    @Query("UPDATE vehicles SET is_active = 0")
    suspend fun clearActiveVehicle()

    @Query("UPDATE vehicles SET is_active = 1 WHERE id = :vehicleId")
    suspend fun setActiveVehicle(vehicleId: Long)

    @Transaction
    suspend fun setActiveVehicleTransaction(vehicleId: Long) {
        clearActiveVehicle()
        setActiveVehicle(vehicleId)
    }

    @Query("SELECT COUNT(*) FROM vehicles")
    suspend fun getVehicleCount(): Int
}
