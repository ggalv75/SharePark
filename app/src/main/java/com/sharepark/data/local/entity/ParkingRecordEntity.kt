package com.sharepark.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "parking_records",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicle_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("vehicle_id"), Index("is_current"), Index(value = ["remote_key"], unique = true)]
)
data class ParkingRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "vehicle_id") val vehicleId: Long,
    @ColumnInfo(name = "latitude") val latitude: Double,
    @ColumnInfo(name = "longitude") val longitude: Double,
    @ColumnInfo(name = "accuracy") val accuracy: Float = 0f,
    @ColumnInfo(name = "address") val address: String? = null,
    @ColumnInfo(name = "map_url") val mapUrl: String,
    @ColumnInfo(name = "is_current") val isCurrent: Boolean = true,
    @ColumnInfo(name = "parked_at") val parkedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    /**
     * Id of this parking in the shared vehicle's cloud history. Set on upload and on sync, so a
     * parking this phone uploaded isn't inserted a second time when the listener echoes it back.
     */
    @ColumnInfo(name = "remote_key") val remoteKey: String? = null,
    /** Who parked, for parkings synced from another member; null for this phone's own. */
    @ColumnInfo(name = "parked_by_name") val parkedByName: String? = null
)
