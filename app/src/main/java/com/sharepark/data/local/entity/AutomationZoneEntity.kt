package com.sharepark.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A place the user marked on the map plus the radius around it. Parking inside one of these
 * circles is what lets the WhatsApp automation fire — parking anywhere else stays private.
 */
@Entity(tableName = "automation_zones")
data class AutomationZoneEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "address") val address: String = "",
    @ColumnInfo(name = "latitude") val latitude: Double,
    @ColumnInfo(name = "longitude") val longitude: Double,
    @ColumnInfo(name = "radius_meters") val radiusMeters: Int,
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
