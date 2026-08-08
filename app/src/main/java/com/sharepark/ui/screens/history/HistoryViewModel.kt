package com.sharepark.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharepark.data.repository.ParkingRepository
import com.sharepark.data.repository.VehicleRepository
import com.sharepark.domain.model.ParkingRecord
import com.sharepark.domain.model.Vehicle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val parkingRepository: ParkingRepository
) : ViewModel() {

    val activeVehicle: StateFlow<Vehicle?> = vehicleRepository.activeVehicle
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val parkingHistory: StateFlow<List<ParkingRecord>> = activeVehicle.flatMapLatest { vehicle ->
        if (vehicle == null) flowOf(emptyList())
        else parkingRepository.getHistoryForVehicle(vehicle.id)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
