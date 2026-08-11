package com.sharepark.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharepark.data.local.prefs.AutomationConfig
import com.sharepark.data.local.prefs.AutomationPreferences
import com.sharepark.data.repository.AutomationRule
import com.sharepark.data.repository.AutomationRuleRepository
import com.sharepark.data.repository.AutomationZoneRepository
import com.sharepark.data.repository.TrustedContactRepository
import com.sharepark.data.repository.VehicleRepository
import com.sharepark.domain.model.AutomationZone
import com.sharepark.domain.model.TrustedContact
import com.sharepark.domain.model.Vehicle
import com.sharepark.platform.automation.AutomationStatusStore
import com.sharepark.platform.automation.WhatsAppAutoSendService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WhatsAppAutomationViewModel @Inject constructor(
    private val automationPreferences: AutomationPreferences,
    private val automationRuleRepository: AutomationRuleRepository,
    private val vehicleRepository: VehicleRepository,
    automationZoneRepository: AutomationZoneRepository,
    trustedContactRepository: TrustedContactRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    init {
        AutomationStatusStore.refresh(context)
    }

    val config: StateFlow<AutomationConfig> = automationPreferences.config
        .stateIn(viewModelScope, SharingStarted.Lazily, AutomationConfig())

    val vehicles: StateFlow<List<Vehicle>> = vehicleRepository.allVehicles
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val contacts: StateFlow<List<TrustedContact>> = trustedContactRepository.allContacts
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val rules: StateFlow<Map<Long, AutomationRule>> = automationRuleRepository.allRules
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val zones: StateFlow<List<AutomationZone>> = automationZoneRepository.allZones
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val status: StateFlow<AutomationStatusStore.Status?> = AutomationStatusStore.status

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { automationPreferences.setEnabled(enabled) }
    }

    fun setContactTarget(vehicleId: Long, contact: TrustedContact) {
        viewModelScope.launch {
            automationRuleRepository.setContactTarget(
                vehicleId = vehicleId,
                phone = contact.phoneNumber,
                label = contact.name
            )
        }
    }

    fun clearTarget(vehicleId: Long) {
        viewModelScope.launch { automationRuleRepository.clearTarget(vehicleId) }
    }

    /**
     * Runs the real automation path with a test message, so the user can see whether the
     * WhatsApp flow works without having to drive somewhere and park first.
     */
    fun sendTestMessage(vehicleId: Long) {
        viewModelScope.launch {
            val rule = automationRuleRepository.getRuleForVehicle(vehicleId)
            if (rule == null || !rule.isConfigured) {
                AutomationStatusStore.record(
                    context,
                    AutomationStatusStore.Outcome.SKIPPED,
                    "לא הוגדר יעד WhatsApp עבור הרכב הזה"
                )
                return@launch
            }
            if (!WhatsAppAutoSendService.isEnabled(context)) {
                AutomationStatusStore.record(
                    context,
                    AutomationStatusStore.Outcome.SKIPPED,
                    "שירות הנגישות כבוי — לא ניתן לשלוח אוטומטית"
                )
                return@launch
            }
            val vehicleName = vehicleRepository.getVehicleById(vehicleId)?.name ?: "הרכב שלי"
            WhatsAppAutoSendService.begin(
                context = context,
                phone = rule.phone,
                message = "בדיקת SharePark — כך תיראה ההודעה כש$vehicleName יחנה. " +
                        "אפשר להתעלם מההודעה הזו."
            )
        }
    }
}
