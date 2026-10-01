package com.sharepark.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharepark.data.repository.VehicleRepository
import com.sharepark.domain.model.Vehicle
import com.sharepark.platform.service.ParkingDetectionService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val activeVehicle: StateFlow<Vehicle?> = vehicleRepository.activeVehicle
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun simulateParkingDisconnect() {
        val vehicle = activeVehicle.value ?: return
        val serviceIntent = Intent(context, ParkingDetectionService::class.java).apply {
            putExtra("bt_address", vehicle.btAddress)
            putExtra("vehicle_id", vehicle.id)
        }
        context.startForegroundService(serviceIntent)
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ColorOS/OPPO-style ROMs block background auto-starts and notification-triggered actions
    // unless the app is explicitly whitelisted in a manufacturer-specific "auto-start" screen —
    // there's no standard Android API for it, so we try the known screens for this ROM family
    // and fall back to the generic app details page.
    fun openAutoStartSettings(context: Context) {
        val candidates = listOf(
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.oppoguardelf", "com.coloros.oppoguardelf.activity.privacypermissions.StartupManagerActivity")
        )
        for (component in candidates) {
            try {
                context.startActivity(
                    Intent().apply {
                        this.component = component
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
                return
            } catch (e: ActivityNotFoundException) {
                // try the next known screen
            }
        }
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
