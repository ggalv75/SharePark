package com.sharepark.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sharepark.data.local.entity.AutomationZoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationZoneDao {

    @Query("SELECT * FROM automation_zones ORDER BY created_at ASC")
    fun getAllZones(): Flow<List<AutomationZoneEntity>>

    /** One-shot read for the parking flow, which runs outside any UI lifecycle. */
    @Query("SELECT * FROM automation_zones WHERE is_enabled = 1")
    suspend fun getEnabledZones(): List<AutomationZoneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(zone: AutomationZoneEntity): Long

    @Query("UPDATE automation_zones SET is_enabled = :enabled WHERE id = :zoneId")
    suspend fun setEnabled(zoneId: Long, enabled: Boolean)

    @Query("DELETE FROM automation_zones WHERE id = :zoneId")
    suspend fun deleteById(zoneId: Long)
}
