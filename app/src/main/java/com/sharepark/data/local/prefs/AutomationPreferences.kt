package com.sharepark.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.automationDataStore by preferencesDataStore(name = "automation_prefs")

data class AutomationConfig(
    val enabled: Boolean = false,
    val vehicleId: Long = -1L, // -1 = every vehicle
    val mode: String = MODE_CONTACT,
    val phone: String = "",
    val groupName: String = "",
    /** Restrict automated sends to parkings inside a defined zone. Ignored while no zone exists. */
    val zonesOnly: Boolean = true
) {
    val isTargetConfigured: Boolean
        get() = if (mode == MODE_CONTACT) phone.isNotBlank() else groupName.isNotBlank()

    companion object {
        const val MODE_CONTACT = "contact"
        const val MODE_GROUP = "group"
    }
}

@Singleton
class AutomationPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("wa_auto_enabled")
        val VEHICLE_ID = longPreferencesKey("wa_auto_vehicle_id")
        val MODE = stringPreferencesKey("wa_auto_mode")
        val PHONE = stringPreferencesKey("wa_auto_phone")
        val GROUP_NAME = stringPreferencesKey("wa_auto_group_name")
        val ZONES_ONLY = booleanPreferencesKey("wa_auto_zones_only")
    }

    val config: Flow<AutomationConfig> = context.automationDataStore.data.map { prefs ->
        AutomationConfig(
            enabled = prefs[Keys.ENABLED] ?: false,
            vehicleId = prefs[Keys.VEHICLE_ID] ?: -1L,
            mode = prefs[Keys.MODE] ?: AutomationConfig.MODE_CONTACT,
            phone = prefs[Keys.PHONE] ?: "",
            groupName = prefs[Keys.GROUP_NAME] ?: "",
            zonesOnly = prefs[Keys.ZONES_ONLY] ?: true
        )
    }

    suspend fun getConfig(): AutomationConfig = config.first()

    suspend fun setEnabled(enabled: Boolean) {
        context.automationDataStore.edit { it[Keys.ENABLED] = enabled }
    }

    suspend fun setVehicleId(vehicleId: Long) {
        context.automationDataStore.edit { it[Keys.VEHICLE_ID] = vehicleId }
    }

    suspend fun setMode(mode: String) {
        context.automationDataStore.edit { it[Keys.MODE] = mode }
    }

    suspend fun setPhone(phone: String) {
        context.automationDataStore.edit { it[Keys.PHONE] = phone }
    }

    suspend fun setGroupName(groupName: String) {
        context.automationDataStore.edit { it[Keys.GROUP_NAME] = groupName }
    }

    suspend fun setZonesOnly(zonesOnly: Boolean) {
        context.automationDataStore.edit { it[Keys.ZONES_ONLY] = zonesOnly }
    }
}
