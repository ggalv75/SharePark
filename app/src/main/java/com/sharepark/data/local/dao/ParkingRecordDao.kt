package com.sharepark.data.local.dao

import androidx.room.*
import com.sharepark.data.local.entity.ParkingRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ParkingRecordDao {

    @Query("SELECT * FROM parking_records WHERE vehicle_id = :vehicleId AND is_current = 1 LIMIT 1")
    fun getCurrentParkingForVehicle(vehicleId: Long): Flow<ParkingRecordEntity?>

    @Query("SELECT * FROM parking_records WHERE vehicle_id = :vehicleId AND is_current = 1 LIMIT 1")
    suspend fun getCurrentParkingOnce(vehicleId: Long): ParkingRecordEntity?

    @Query("SELECT * FROM parking_records WHERE is_current = 1 LIMIT 1")
    fun getAnyCurrentParking(): Flow<ParkingRecordEntity?>

    @Query("""
        SELECT * FROM parking_records 
        WHERE vehicle_id = :vehicleId 
        ORDER BY parked_at DESC 
        LIMIT 100
    """)
    fun getHistoryForVehicle(vehicleId: Long): Flow<List<ParkingRecordEntity>>

    @Query("""
        SELECT pr.* FROM parking_records pr
        WHERE pr.is_current = 1
        ORDER BY pr.parked_at DESC
    """)
    fun getAllCurrentParkings(): Flow<List<ParkingRecordEntity>>

    @Insert
    suspend fun insert(record: ParkingRecordEntity): Long

    @Query("UPDATE parking_records SET is_current = 0 WHERE vehicle_id = :vehicleId")
    suspend fun clearCurrentForVehicle(vehicleId: Long)

    @Transaction
    suspend fun insertAndSetCurrent(record: ParkingRecordEntity): Long {
        clearCurrentForVehicle(record.vehicleId)
        return insert(record)
    }

    @Query("SELECT * FROM parking_records WHERE remote_key = :remoteKey LIMIT 1")
    suspend fun getByRemoteKey(remoteKey: String): ParkingRecordEntity?

    @Query("UPDATE parking_records SET remote_key = :remoteKey WHERE id = :recordId")
    suspend fun setRemoteKey(recordId: Long, remoteKey: String)

    /**
     * Stores a parking that arrived from the shared vehicle's cloud history. Returns null if it
     * is already here, true if it became the car's current spot, false if it's an older one
     * that only belongs in the history (listeners don't deliver in parking order).
     */
    @Transaction
    suspend fun insertSynced(record: ParkingRecordEntity): Boolean? {
        val key = record.remoteKey ?: return null
        if (getByRemoteKey(key) != null) return null
        val current = getCurrentParkingOnce(record.vehicleId)
        return if (current == null || record.parkedAt > current.parkedAt) {
            insertAndSetCurrent(record.copy(isCurrent = true))
            true
        } else {
            insert(record.copy(isCurrent = false))
            false
        }
    }

    @Query("UPDATE parking_records SET address = :address WHERE id = :recordId")
    suspend fun updateAddress(recordId: Long, address: String)

    @Query("DELETE FROM parking_records WHERE parked_at < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int

    @Query("DELETE FROM parking_records WHERE vehicle_id = :vehicleId")
    suspend fun deleteByVehicleId(vehicleId: Long)

    @Query("SELECT COUNT(*) FROM parking_records")
    suspend fun getTotalCount(): Int
}
