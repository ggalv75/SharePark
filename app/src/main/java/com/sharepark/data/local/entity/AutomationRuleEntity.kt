package com.sharepark.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Per-vehicle WhatsApp automation target: which chat gets the message when this car parks. */
@Entity(tableName = "automation_rules")
data class AutomationRuleEntity(
    @PrimaryKey
    @ColumnInfo(name = "vehicle_id") val vehicleId: Long,
    @ColumnInfo(name = "mode") val mode: String,
    @ColumnInfo(name = "phone") val phone: String = "",
    @ColumnInfo(name = "group_name") val groupName: String = "",
    @ColumnInfo(name = "target_label") val targetLabel: String = ""
)
