package com.sharepark.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sharepark.data.local.entity.AutomationRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationRuleDao {

    @Query("SELECT * FROM automation_rules")
    fun getAllRules(): Flow<List<AutomationRuleEntity>>

    @Query("SELECT * FROM automation_rules WHERE vehicle_id = :vehicleId LIMIT 1")
    suspend fun getRuleForVehicle(vehicleId: Long): AutomationRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: AutomationRuleEntity)

    @Query("DELETE FROM automation_rules WHERE vehicle_id = :vehicleId")
    suspend fun deleteForVehicle(vehicleId: Long)
}
