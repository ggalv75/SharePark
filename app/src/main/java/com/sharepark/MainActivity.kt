package com.sharepark

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.sharepark.data.repository.VehicleRepository
import com.sharepark.ui.navigation.AppNavigation
import com.sharepark.ui.theme.ShareParkTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var vehicleRepository: VehicleRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        switchToVehicleFromIntent(intent)
        setContent {
            ShareParkTheme {
                AppNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        switchToVehicleFromIntent(intent)
    }

    // When opened from a parking notification, make that vehicle the active one so the
    // Map tab shows the parking that was just detected — even if it isn't the currently
    // selected vehicle (e.g. you drove the family's other car).
    private fun switchToVehicleFromIntent(intent: Intent?) {
        val vehicleId = intent?.getLongExtra(EXTRA_VEHICLE_ID, -1L) ?: -1L
        if (vehicleId != -1L) {
            lifecycleScope.launch {
                if (vehicleRepository.getVehicleById(vehicleId) != null) {
                    vehicleRepository.setActiveVehicle(vehicleId)
                }
            }
        }
    }

    companion object {
        const val EXTRA_VEHICLE_ID = "vehicle_id"
    }
}
