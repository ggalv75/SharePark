package com.sharepark.data.repository

import com.sharepark.data.local.dao.AutomationRuleDao
import com.sharepark.data.local.entity.AutomationRuleEntity
import com.sharepark.data.local.prefs.AutomationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Per-vehicle automation target, e.g. "RAV4 → Dad", "Yaris Cross → Sister". */
data class AutomationRule(
    val vehicleId: Long,
    val mode: String,
    val phone: String = "",
    val groupName: String = "",
    val targetLabel: String = ""
) {
    val isConfigured: Boolean
        get() = if (mode == AutomationConfig.MODE_CONTACT) phone.isNotBlank() else groupName.isNotBlank()
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
                mode = AutomationConfig.MODE_CONTACT,
                phone = phone,
                groupName = "",
                targetLabel = label
            )
        )
    }

    suspend fun setGroupTarget(vehicleId: Long, groupName: String) {
        automationRuleDao.upsert(
            AutomationRuleEntity(
                vehicleId = vehicleId,
                mode = AutomationConfig.MODE_GROUP,
                phone = "",
                groupName = groupName,
                targetLabel = groupName
            )
        )
    }

    suspend fun clearTarget(vehicleId: Long) {
        automationRuleDao.deleteForVehicle(vehicleId)
    }

    private fun AutomationRuleEntity.toDomain() = AutomationRule(
        vehicleId = vehicleId,
        mode = mode,
        phone = phone,
        groupName = groupName,
        targetLabel = targetLabel
    )
}
