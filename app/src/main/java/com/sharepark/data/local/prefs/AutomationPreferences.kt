package com.sharepark.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.automationDataStore by preferencesDataStore(name = "automation_prefs")

/** App-wide automation switches. The target itself is per vehicle, in `automation_rules`. */
data class AutomationConfig(
    val enabled: Boolean = false,
    /** Restrict automated sends to parkings inside a defined zone. Ignored while no zone exists. */
    val zonesOnly: Boolean = true
)

@Singleton
class AutomationPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("wa_auto_enabled")
        val ZONES_ONLY = booleanPreferencesKey("wa_auto_zones_only")
    }

    val config: Flow<AutomationConfig> = context.automationDataStore.data.map { prefs ->
        AutomationConfig(
            enabled = prefs[Keys.ENABLED] ?: false,
            zonesOnly = prefs[Keys.ZONES_ONLY] ?: true
        )
    }

    suspend fun getConfig(): AutomationConfig = config.first()

    suspend fun setEnabled(enabled: Boolean) {
        context.automationDataStore.edit { it[Keys.ENABLED] = enabled }
    }

    suspend fun setZonesOnly(zonesOnly: Boolean) {
        context.automationDataStore.edit { it[Keys.ZONES_ONLY] = zonesOnly }
    }
}
