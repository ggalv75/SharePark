package com.sharepark.data.repository

import com.sharepark.data.local.dao.AutomationRuleDao
import com.sharepark.data.local.entity.AutomationRuleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Per-vehicle automation target, e.g. "RAV4 → Dad", "Yaris Cross → Sister". */
data class AutomationRule(
    val vehicleId: Long,
    val phone: String = "",
    val targetLabel: String = ""
) {
    val isConfigured: Boolean
        get() = phone.isNotBlank()
}

@Singleton
class AutomationRuleRepository @Inject constructor(
    private val automationRuleDao: AutomationRuleDao
) {
    val allRules: Flow<Map<Long, AutomationRule>> = automationRuleDao.getAllRules().map { entities ->
        entities.associate { it.vehicleId to it.toDomain() }
    }

    suspend fun getRuleForVehicle(vehicleId: Long): AutomationRule? =
        automationRuleDao.getRuleForVehicle(vehicleId)?.toDomain()

    suspend fun setContactTarget(vehicleId: Long, phone: String, label: String) {
        automationRuleDao.upsert(
            AutomationRuleEntity(
                vehicleId = vehicleId,
                phone = phone,
                targetLabel = label
            )
        )
    }

    suspend fun clearTarget(vehicleId: Long) {
        automationRuleDao.deleteForVehicle(vehicleId)
    }

    private fun AutomationRuleEntity.toDomain() = AutomationRule(
        vehicleId = vehicleId,
        phone = phone,
        targetLabel = targetLabel
    )
}
