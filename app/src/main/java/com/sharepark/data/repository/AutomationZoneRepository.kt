package com.sharepark.data.repository

import android.location.Location
import com.sharepark.data.local.dao.AutomationZoneDao
import com.sharepark.data.local.entity.AutomationZoneEntity
import com.sharepark.domain.model.AutomationZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationZoneRepository @Inject constructor(
    private val automationZoneDao: AutomationZoneDao
) {
    val allZones: Flow<List<AutomationZone>> = automationZoneDao.getAllZones().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun getEnabledZones(): List<AutomationZone> =
        automationZoneDao.getEnabledZones().map { it.toDomain() }

    /**
     * The zone a parking spot falls inside, or null when it's outside every one of them.
     * The smallest matching zone wins, so a tight "home parking" circle drawn inside a wider
     * "neighbourhood" circle is the one reported.
     */
    suspend fun findMatchingZone(latitude: Double, longitude: Double): AutomationZone? =
        getEnabledZones()
            .filter { it.contains(latitude, longitude) }
            .minByOrNull { it.radiusMeters }

    suspend fun saveZone(zone: AutomationZone): Long =
        automationZoneDao.upsert(
            AutomationZoneEntity(
                id = zone.id,
                label = zone.label,
                address = zone.address,
                latitude = zone.latitude,
                longitude = zone.longitude,
                radiusMeters = zone.radiusMeters,
                isEnabled = zone.isEnabled,
                createdAt = zone.createdAt
            )
        )

    suspend fun setEnabled(zoneId: Long, enabled: Boolean) =
        automationZoneDao.setEnabled(zoneId, enabled)

    suspend fun deleteZone(zoneId: Long) = automationZoneDao.deleteById(zoneId)

    private fun AutomationZoneEntity.toDomain() = AutomationZone(
        id = id,
        label = label,
        address = address,
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters,
        isEnabled = isEnabled,
        createdAt = createdAt
    )
}

/** Metres between the parking spot and the zone centre, on the WGS84 ellipsoid. */
fun AutomationZone.distanceTo(latitude: Double, longitude: Double): Float {
    val results = FloatArray(1)
    Location.distanceBetween(this.latitude, this.longitude, latitude, longitude, results)
    return results[0]
}

fun AutomationZone.contains(latitude: Double, longitude: Double): Boolean =
    distanceTo(latitude, longitude) <= radiusMeters
