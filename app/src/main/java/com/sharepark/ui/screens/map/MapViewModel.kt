package com.sharepark.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharepark.data.repository.ParkingRepository
import com.sharepark.data.repository.TrustedContactRepository
import com.sharepark.data.repository.VehicleRepository
import com.sharepark.domain.model.ParkingRecord
import com.sharepark.domain.model.TrustedContact
import com.sharepark.domain.model.Vehicle
import com.sharepark.domain.usecase.ShareLocationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val parkingRepository: ParkingRepository,
    private val trustedContactRepository: TrustedContactRepository,
    private val shareLocationUseCase: ShareLocationUseCase
) : ViewModel() {

    val activeVehicle: StateFlow<Vehicle?> = vehicleRepository.activeVehicle
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val currentParking: StateFlow<ParkingRecord?> = activeVehicle.flatMapLatest { vehicle ->
        if (vehicle == null) flowOf(null)
        else parkingRepository.getCurrentParkingForVehicle(vehicle.id)
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val trustedContacts: StateFlow<List<TrustedContact>> = trustedContactRepository.allContacts
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _shareTextEvent = MutableSharedFlow<String>()
    val shareTextEvent: SharedFlow<String> = _shareTextEvent.asSharedFlow()

    fun shareParkingLocation() {
        val vehicle = activeVehicle.value ?: return
        val record = currentParking.value ?: return

        viewModelScope.launch {
            val shareText = shareLocationUseCase(
                vehicleName = vehicle.name,
                address = record.address,
                latitude = record.latitude,
                longitude = record.longitude
            )
            _shareTextEvent.emit(shareText)
        }
    }

    fun buildShareText(): String? {
        val vehicle = activeVehicle.value ?: return null
        val record = currentParking.value ?: return null
        return shareLocationUseCase(
            vehicleName = vehicle.name,
            address = record.address,
            latitude = record.latitude,
            longitude = record.longitude
        )
    }
}
