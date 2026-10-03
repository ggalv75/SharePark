package com.sharepark

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.sharepark.data.repository.VehicleRepository
import com.sharepark.platform.notification.InAppAlerts
import com.sharepark.ui.navigation.AppNavigation
import com.sharepark.ui.theme.ShareParkTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var vehicleRepository: VehicleRepository
    @Inject lateinit var inAppAlerts: InAppAlerts

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        switchToVehicleFromIntent(intent)
        takeReservationsRequest(intent)
        setContent {
            ShareParkTheme {
                AppNavigation(
                    openReservationsFor = reservationsRequest,
                    onReservationsOpened = { reservationsRequest = null },
                    alerts = inAppAlerts.alerts
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        switchToVehicleFromIntent(intent)
        takeReservationsRequest(intent)
    }

    /** Set by a reservation notification; AppNavigation opens that car's reservations and clears it. */
    private var reservationsRequest by mutableStateOf<Long?>(null)

    private fun takeReservationsRequest(intent: Intent?) {
        val vehicleId = intent?.getLongExtra(EXTRA_OPEN_RESERVATIONS, -1L) ?: -1L
        if (vehicleId == -1L) return
        // Consumed once, so recreating the activity (rotation) doesn't open the screen again.
        intent?.removeExtra(EXTRA_OPEN_RESERVATIONS)
        reservationsRequest = vehicleId
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
        const val EXTRA_OPEN_RESERVATIONS = "open_reservations"
    }
}
