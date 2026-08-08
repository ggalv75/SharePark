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
import com.sharepark.data.local.prefs.AutomationPreferences
import com.sharepark.data.repository.AutomationRuleRepository
import com.sharepark.data.repository.VehicleRepository
import com.sharepark.domain.usecase.SaveParkingUseCase
import com.sharepark.domain.usecase.ShareLocationUseCase
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
import javax.inject.Inject
import kotlin.coroutines.resume

@AndroidEntryPoint
class ParkingDetectionService : Service() {

    @Inject lateinit var vehicleRepository: VehicleRepository
    @Inject lateinit var saveParkingUseCase: SaveParkingUseCase
    @Inject lateinit var shareLocationUseCase: ShareLocationUseCase
    @Inject lateinit var automationPreferences: AutomationPreferences
    @Inject lateinit var automationRuleRepository: AutomationRuleRepository

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
                val automation = automationPreferences.getConfig()
                val rule = automationRuleRepository.getRuleForVehicle(vehicleId)
                if (automation.enabled && rule != null && rule.isConfigured &&
                    WhatsAppAutoSendService.isEnabled(this@ParkingDetectionService)
                ) {
                    WhatsAppAutoSendService.begin(
                        context = this@ParkingDetectionService,
                        mode = rule.mode,
                        phone = rule.phone,
                        groupName = rule.groupName,
                        message = shareText
                    )
                }
            }

            stopSelf()
        }

        return START_NOT_STICKY
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
}
