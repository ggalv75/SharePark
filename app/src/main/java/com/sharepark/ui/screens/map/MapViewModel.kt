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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Every car the map can page through, each with its current parking (if any). */
data class MapCars(
    val vehicles: List<Vehicle>,
    val parkings: Map<Long, ParkingRecord>
) {
    val activeIndex: Int get() = vehicles.indexOfFirst { it.isActive }.coerceAtLeast(0)
    val activeVehicle: Vehicle? get() = vehicles.getOrNull(activeIndex)
}

@HiltViewModel
class MapViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    parkingRepository: ParkingRepository,
    trustedContactRepository: TrustedContactRepository,
    private val shareLocationUseCase: ShareLocationUseCase
) : ViewModel() {

    /** Null until the database has answered, so the screen doesn't flash an empty state. */
    val cars: StateFlow<MapCars?> = combine(
        vehicleRepository.allVehicles,
        parkingRepository.allCurrentParkings
    ) { vehicles, parkings ->
        MapCars(vehicles, parkings.associateBy { it.vehicleId })
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val trustedContacts: StateFlow<List<TrustedContact>> = trustedContactRepository.allContacts
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _shareTextEvent = MutableSharedFlow<String>()
    val shareTextEvent: SharedFlow<String> = _shareTextEvent.asSharedFlow()

    /** Swiping to a car makes it the active one, so History and the rest of the app follow. */
    fun selectVehicle(vehicleId: Long) {
        if (cars.value?.activeVehicle?.id == vehicleId) return
        viewModelScope.launch { vehicleRepository.setActiveVehicle(vehicleId) }
    }

    fun shareParkingLocation(vehicleId: Long) {
        val shareText = buildShareText(vehicleId) ?: return
        viewModelScope.launch { _shareTextEvent.emit(shareText) }
    }

    fun buildShareText(vehicleId: Long): String? {
        val state = cars.value ?: return null
        val vehicle = state.vehicles.firstOrNull { it.id == vehicleId } ?: return null
        val record = state.parkings[vehicleId] ?: return null
        return shareLocationUseCase(
            vehicleName = vehicle.name,
            address = record.address,
            latitude = record.latitude,
            longitude = record.longitude
        )
    }
}
