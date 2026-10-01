package com.sharepark.ui.screens.vehicles

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharepark.data.local.prefs.ReservationCalendarStore
import com.sharepark.data.remote.cloud.AuthRepository
import com.sharepark.data.remote.cloud.SharedVehicleRepository
import com.sharepark.data.remote.cloud.SharingException
import com.sharepark.data.repository.VehicleRepository
import com.sharepark.domain.model.Reservation
import com.sharepark.domain.model.Vehicle
import com.sharepark.platform.calendar.DeviceCalendar
import com.sharepark.platform.calendar.ReservationCalendarSync
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReservationsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    vehicleRepository: VehicleRepository,
    private val authRepository: AuthRepository,
    private val sharedVehicleRepository: SharedVehicleRepository,
    private val calendarStore: ReservationCalendarStore,
    private val calendarSync: ReservationCalendarSync,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val vehicleId: Long = checkNotNull(savedStateHandle["vehicleId"])

    val vehicle: StateFlow<Vehicle?> = vehicleRepository.allVehicles
        .map { vehicles -> vehicles.firstOrNull { it.id == vehicleId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** null until the first snapshot arrives. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val reservations: StateFlow<List<Reservation>?> = vehicle
        .map { it?.cloudId }
        .distinctUntilChanged()
        .flatMapLatest { cloudId ->
            if (cloudId == null) flowOf(emptyList())
            else sharedVehicleRepository.observeReservations(cloudId).catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val myUid: String? get() = authRepository.currentUserNow()?.uid

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun dismissMessage() {
        _message.value = null
    }

    /** [onDone] runs only when the slot was booked, so the form stays open on a conflict. */
    fun reserve(startAt: Long, endAt: Long, note: String, onDone: () -> Unit) {
        val cloudId = vehicle.value?.cloudId ?: return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                sharedVehicleRepository.addReservation(cloudId, startAt, endAt, note)
                onDone()
            } catch (e: Exception) {
                _message.value = errorText(e)
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun cancel(reservation: Reservation) {
        val cloudId = vehicle.value?.cloudId ?: return
        viewModelScope.launch {
            try {
                sharedVehicleRepository.cancelReservation(cloudId, reservation.id)
            } catch (e: Exception) {
                _message.value = errorText(e)
            }
        }
    }

    // ── Device calendar ─────────────────────────────────────────────────────

    private val _hasCalendarPermission = MutableStateFlow(DeviceCalendar.hasPermission(context))

    /** On only when switched on *and* allowed — what the switch on screen shows. */
    val calendarSyncOn: StateFlow<Boolean> = combine(calendarStore.enabled, _hasCalendarPermission) { enabled, allowed ->
        enabled && allowed
    }.stateIn(viewModelScope, SharingStarted.Eagerly, calendarStore.enabled.value && _hasCalendarPermission.value)

    /** First visit with the feature on but no access yet: ask once without waiting for the switch. */
    fun shouldAskCalendarPermission(): Boolean =
        calendarStore.enabled.value && !_hasCalendarPermission.value && !calendarStore.askedForPermission

    fun onCalendarPermissionAsked() {
        calendarStore.askedForPermission = true
    }

    fun onCalendarPermissionResult(granted: Boolean) {
        _hasCalendarPermission.value = granted
        if (granted) calendarStore.setEnabled(true) else calendarStore.onPermissionChanged()
    }

    /** Switching off takes this phone's upcoming bookings back out of the calendar. */
    fun turnOffCalendarSync() {
        calendarStore.setEnabled(false)
        viewModelScope.launch { calendarSync.removeUpcoming(keepCloudIds = emptySet()) }
    }

    private fun errorText(e: Exception): String =
        if (e is SharingException) e.message.orEmpty()
        else "הפעולה נכשלה — בדוק חיבור לאינטרנט ונסה שוב"
}
