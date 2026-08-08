package com.sharepark.platform.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sharepark.data.repository.VehicleRepository
import com.sharepark.platform.service.ParkingDetectionService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BluetoothEventReceiver : BroadcastReceiver() {

    @Inject lateinit var vehicleRepository: VehicleRepository

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        } ?: return

        val macAddress = device.address ?: return

        if (action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val registeredVehicle = vehicleRepository.getVehicleByBtAddress(macAddress)
                    if (registeredVehicle != null) {
                        val serviceIntent = Intent(context, ParkingDetectionService::class.java).apply {
                            putExtra("bt_address", macAddress)
                            putExtra("vehicle_id", registeredVehicle.id)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            // On Android 12+, we can only start a foreground service from a BroadcastReceiver under certain circumstances.
                            // Triggering from a Bluetooth broadcast is allowed under system exclusions.
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startForegroundService(serviceIntent)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
