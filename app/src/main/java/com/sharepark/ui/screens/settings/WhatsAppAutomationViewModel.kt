package com.sharepark.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharepark.data.local.prefs.AutomationConfig
import com.sharepark.data.local.prefs.AutomationPreferences
import com.sharepark.data.repository.AutomationRule
import com.sharepark.data.repository.AutomationRuleRepository
import com.sharepark.data.repository.TrustedContactRepository
import com.sharepark.data.repository.VehicleRepository
import com.sharepark.domain.model.TrustedContact
import com.sharepark.domain.model.Vehicle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WhatsAppAutomationViewModel @Inject constructor(
    private val automationPreferences: AutomationPreferences,
    private val automationRuleRepository: AutomationRuleRepository,
    vehicleRepository: VehicleRepository,
    trustedContactRepository: TrustedContactRepository
) : ViewModel() {

    val config: StateFlow<AutomationConfig> = automationPreferences.config
        .stateIn(viewModelScope, SharingStarted.Lazily, AutomationConfig())

    val vehicles: StateFlow<List<Vehicle>> = vehicleRepository.allVehicles
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val contacts: StateFlow<List<TrustedContact>> = trustedContactRepository.allContacts
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val rules: StateFlow<Map<Long, AutomationRule>> = automationRuleRepository.allRules
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

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

    fun setGroupTarget(vehicleId: Long, groupName: String) {
        viewModelScope.launch {
            automationRuleRepository.setGroupTarget(vehicleId, groupName)
        }
    }

    fun clearTarget(vehicleId: Long) {
        viewModelScope.launch { automationRuleRepository.clearTarget(vehicleId) }
    }
}
