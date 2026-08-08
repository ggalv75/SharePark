package com.sharepark.ui.screens.vehicles

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharepark.data.repository.VehicleRepository
import com.sharepark.domain.model.Vehicle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VehiclesViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val vehicles: StateFlow<List<Vehicle>> = vehicleRepository.allVehicles
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val activeVehicle: StateFlow<Vehicle?> = vehicleRepository.activeVehicle
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _bondedDevices = MutableStateFlow<List<BondedDevice>>(emptyList())
    val bondedDevices: StateFlow<List<BondedDevice>> = _bondedDevices.asStateFlow()

    data class BondedDevice(val name: String, val address: String)

    @SuppressLint("MissingPermission")
    fun loadBondedDevices() {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: return
        
        try {
            val devices = adapter.bondedDevices.map { device ->
                BondedDevice(
                    name = device.name ?: "Unknown Device",
                    address = device.address
                )
            }
            _bondedDevices.value = devices
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun addVehicle(name: String, btAddress: String, btName: String) {
        viewModelScope.launch {
            vehicleRepository.insertVehicle(name, btAddress, btName)
        }
    }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            vehicleRepository.deleteVehicle(vehicle)
        }
    }

    fun renameVehicle(vehicleId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            vehicleRepository.renameVehicle(vehicleId, trimmed)
        }
    }

    fun setActiveVehicle(vehicleId: Long) {
        viewModelScope.launch {
            vehicleRepository.setActiveVehicle(vehicleId)
        }
    }
}
