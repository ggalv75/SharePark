package com.sharepark.platform.service

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.sharepark.data.local.prefs.AutomationPreferences
import com.sharepark.data.repository.AutomationRuleRepository
import com.sharepark.data.repository.AutomationZoneRepository
import com.sharepark.data.repository.VehicleRepository
import com.sharepark.data.repository.contains
import com.sharepark.data.repository.distanceTo
import com.sharepark.domain.usecase.SaveParkingUseCase
import com.sharepark.domain.usecase.ShareLocationUseCase
import com.sharepark.platform.automation.AutomationStatusStore
import com.sharepark.platform.automation.WhatsAppAutoSendService
import com.sharepark.platform.location.LocationHelper
import com.sharepark.platform.notification.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.math.roundToInt

@AndroidEntryPoint
class ParkingDetectionService : Service() {

    @Inject lateinit var vehicleRepository: VehicleRepository
    @Inject lateinit var saveParkingUseCase: SaveParkingUseCase
    @Inject lateinit var shareLocationUseCase: ShareLocationUseCase
    @Inject lateinit var automationPreferences: AutomationPreferences
    @Inject lateinit var automationRuleRepository: AutomationRuleRepository
    @Inject lateinit var automationZoneRepository: AutomationZoneRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        
        // Start foreground immediately to comply with Android rules
        val notification = NotificationHelper.buildServiceNotification(
            this,
            "מזהה מיקום חנייה ברקע..."
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.SERVICE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val btAddress = intent?.getStringExtra("bt_address")
        val vehicleId = intent?.getLongExtra("vehicle_id", -1L) ?: -1L

        if (btAddress == null || vehicleId == -1L) {
            stopSelf()
            return START_NOT_STICKY
        }

        serviceScope.launch {
            // 1. Debounce phase: Wait 10 seconds to verify if the disconnection is stable
            // (e.g. not a brief signal drop in a tunnel or quick engine restart)
            delay(10000L)

            if (isDeviceStillConnected(btAddress)) {
                // Device reconnected or never disconnected, abort
                stopSelf()
                return@launch
            }

            // 2. Fetch high-accuracy GPS coordinates
            val location = LocationHelper.getCurrentLocation(this@ParkingDetectionService)
            if (location != null) {
                val vehicle = vehicleRepository.getVehicleById(vehicleId)
                val vehicleName = vehicle?.name ?: "הרכב שלי"

                // 3. Save the record and reverse-geocode to a Hebrew address
                val saveResult = saveParkingUseCase(
                    vehicleId = vehicleId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy
                )

                // 4. Send success notification to user, with a one-tap share action
                val shareText = shareLocationUseCase(
                    vehicleName = vehicleName,
                    address = saveResult.address,
                    latitude = location.latitude,
                    longitude = location.longitude
                )
                NotificationHelper.showParkingDetectedNotification(
                    context = this@ParkingDetectionService,
                    vehicleId = vehicleId,
                    vehicleName = vehicleName,
                    address = saveResult.address,
                    shareText = shareText
                )

                // 5. WhatsApp automation: each vehicle has its own target chat, so look up
                // the rule for the car that just parked.
                runAutomation(
                    vehicleId = vehicleId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    shareText = shareText
                )
            }

            stopSelf()
        }

        return START_NOT_STICKY
    }

    /**
     * Decides whether this parking should trigger an automated WhatsApp message, and fires it.
     *
     * The zone check is the gate the user asked for: with "zones only" on and at least one zone
     * defined, a car parked outside every circle stays private. No zones defined means no
     * restriction — otherwise turning the feature on would silently disable automation.
     */
    private suspend fun runAutomation(
        vehicleId: Long,
        latitude: Double,
        longitude: Double,
        shareText: String
    ) {
        val automation = automationPreferences.getConfig()
        if (!automation.enabled) return

        val rule = automationRuleRepository.getRuleForVehicle(vehicleId)
        if (rule == null || !rule.isConfigured) {
            AutomationStatusStore.record(
                this,
                AutomationStatusStore.Outcome.SKIPPED,
                "לא הוגדר יעד WhatsApp עבור הרכב הזה"
            )
            return
        }

        if (!WhatsAppAutoSendService.isEnabled(this)) {
            AutomationStatusStore.record(
                this,
                AutomationStatusStore.Outcome.SKIPPED,
                "שירות הנגישות כבוי — לא ניתן לשלוח אוטומטית"
            )
            return
        }

        if (automation.zonesOnly) {
            val zones = automationZoneRepository.getEnabledZones()
            if (zones.isNotEmpty()) {
                val matched = zones
                    .filter { it.contains(latitude, longitude) }
                    .minByOrNull { it.radiusMeters }
                if (matched == null) {
                    val nearest = zones.minByOrNull { it.distanceTo(latitude, longitude) }
                    val detail = nearest?.let {
                        "החנייה מחוץ לאזורים שהוגדרו (${formatDistance(it.distanceTo(latitude, longitude))} מ\"${it.label}\")"
                    } ?: "החנייה מחוץ לאזורים שהוגדרו"
                    AutomationStatusStore.record(this, AutomationStatusStore.Outcome.SKIPPED, detail)
                    return
                }
                Log.i(TAG, "Parking matched automation zone")
            }
        }

        WhatsAppAutoSendService.begin(
            context = this,
            phone = rule.phone,
            message = shareText
        )
    }

    /** Metres up close, kilometres once "83421 מ'" stops being a number anyone can read. */
    private fun formatDistance(meters: Float): String =
        if (meters < 1000f) {
            "${meters.roundToInt()} מ'"
        } else {
            "${String.format(Locale.getDefault(), "%.1f", meters / 1000f)} ק\"מ"
        }

    private suspend fun isDeviceStillConnected(macAddress: String): Boolean {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
        val adapter = bluetoothManager.adapter ?: return false

        // Bluetooth off means nothing can be connected — and the profile-proxy callbacks
        // below may never fire in that state, so bail out before waiting on them.
        if (!adapter.isEnabled) return false

        // BluetoothManager.getConnectedDevices() only supports GATT/GATT_SERVER — A2DP and
        // HEADSET must be queried through their own profile proxy, or the call throws
        // IllegalArgumentException("Profile not supported"). The proxy callback isn't
        // guaranteed to fire on every device state, so cap the wait.
        val connectedDevices = withTimeoutOrNull(5000L) {
            getConnectedDevicesForProfile(adapter, BluetoothProfile.A2DP) +
                    getConnectedDevicesForProfile(adapter, BluetoothProfile.HEADSET)
        } ?: return false
        return connectedDevices.any { it.address.equals(macAddress, ignoreCase = true) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getConnectedDevicesForProfile(
        adapter: BluetoothAdapter,
        profile: Int
    ): List<BluetoothDevice> = suspendCancellableCoroutine { continuation ->
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profileType: Int, proxy: BluetoothProfile) {
                val devices = try {
                    proxy.connectedDevices
                } catch (e: SecurityException) {
                    emptyList()
                }
                adapter.closeProfileProxy(profile, proxy)
                if (continuation.isActive) continuation.resume(devices)
            }

            override fun onServiceDisconnected(profileType: Int) {
                if (continuation.isActive) continuation.resume(emptyList())
            }
        }

        val started = try {
            adapter.getProfileProxy(applicationContext, listener, profile)
        } catch (e: SecurityException) {
            false
        }
        if (!started && continuation.isActive) {
            continuation.resume(emptyList())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val TAG = "ParkingDetection"
    }
}
