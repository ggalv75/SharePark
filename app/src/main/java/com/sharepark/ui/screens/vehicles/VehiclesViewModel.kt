package com.sharepark.ui.screens.vehicles

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharepark.data.local.prefs.ReservationCalendarStore
import com.sharepark.data.remote.cloud.AuthRepository
import com.sharepark.data.remote.cloud.CloudUser
import com.sharepark.data.remote.cloud.SharedVehicleRepository
import com.sharepark.data.remote.cloud.SharingException
import com.sharepark.data.remote.cloud.SignInResult
import com.sharepark.data.remote.cloud.VehicleMember
import com.sharepark.data.repository.ParkingRepository
import com.sharepark.data.repository.VehicleRepository
import com.sharepark.domain.usecase.PublishSharedParkingUseCase
import com.sharepark.domain.model.Vehicle
import com.sharepark.platform.calendar.DeviceCalendar
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VehiclesViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val parkingRepository: ParkingRepository,
    private val authRepository: AuthRepository,
    private val sharedVehicleRepository: SharedVehicleRepository,
    private val publishSharedParkingUseCase: PublishSharedParkingUseCase,
    private val calendarStore: ReservationCalendarStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** What the sharing dialogs on the vehicles screen are currently showing. */
    sealed interface SharingState {
        data object Idle : SharingState
        data class Working(val message: String) : SharingState
        /** Sharing needs an account; [pending] is the car to share once signed in, if any. */
        data class NeedsSignIn(val pending: Vehicle?) : SharingState
        data class InviteReady(val vehicleName: String, val code: String) : SharingState
        data object EnterCode : SharingState
        /** Joined — now decide whether this phone also detects the car or only watches it. */
        data class ChooseLink(val cloudId: String, val name: String, val candidates: List<Vehicle>) : SharingState
        data class Message(val text: String) : SharingState
    }

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
            // Leave the shared car first; if that fails (offline) the car still goes locally,
            // and the other members simply keep it.
            vehicle.cloudId?.let { cloudId ->
                runCatching { sharedVehicleRepository.leave(cloudId) }
            }
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

    // ── Shared vehicles ─────────────────────────────────────────────────────

    val isSharingAvailable: Boolean = authRepository.isAvailable

    val currentUser: StateFlow<CloudUser?> = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.Eagerly, authRepository.currentUserNow())

    private val _sharingState = MutableStateFlow<SharingState>(SharingState.Idle)
    val sharingState: StateFlow<SharingState> = _sharingState.asStateFlow()

    fun dismissSharing() {
        _sharingState.value = SharingState.Idle
    }

    /** [activityContext] must be the Activity: Google's account picker is drawn over it. */
    fun signIn(activityContext: Context) {
        val pending = (_sharingState.value as? SharingState.NeedsSignIn)?.pending
        viewModelScope.launch {
            when (val result = authRepository.signInWithGoogle(activityContext)) {
                is SignInResult.Success ->
                    if (pending != null) shareVehicle(pending) else _sharingState.value = SharingState.EnterCode
                SignInResult.Cancelled -> Unit
                is SignInResult.Failure -> _sharingState.value = SharingState.Message(result.message)
            }
        }
    }

    /** Makes the car shared (first time only) and issues a fresh invite code for it. */
    fun shareVehicle(vehicle: Vehicle) {
        if (authRepository.currentUserNow() == null) {
            _sharingState.value = SharingState.NeedsSignIn(pending = vehicle)
            return
        }
        viewModelScope.launch {
            _sharingState.value = SharingState.Working("יוצר קוד הזמנה...")
            _sharingState.value = try {
                val cloudId = vehicle.cloudId
                    ?: sharedVehicleRepository.createSharedVehicle(vehicle.name).also { newId ->
                        vehicleRepository.setCloudId(vehicle.id, newId)
                        publishCurrentParking(vehicle.copy(cloudId = newId))
                    }
                SharingState.InviteReady(vehicle.name, sharedVehicleRepository.createInvite(cloudId))
            } catch (e: Exception) {
                SharingState.Message(errorText(e))
            }
        }
    }

    fun startJoin() {
        _sharingState.value = if (authRepository.currentUserNow() == null) {
            SharingState.NeedsSignIn(pending = null)
        } else {
            SharingState.EnterCode
        }
    }

    fun joinWithCode(code: String) {
        viewModelScope.launch {
            _sharingState.value = SharingState.Working("מצטרף לרכב...")
            _sharingState.value = try {
                val joined = sharedVehicleRepository.joinWithCode(code)
                if (vehicleRepository.getVehicleByCloudId(joined.cloudId) != null) {
                    SharingState.Message("\"${joined.name}\" כבר משותף איתך")
                } else {
                    val candidates = vehicleRepository.allVehicles.first()
                        .filter { !it.isShared && !it.isViewOnly }
                    SharingState.ChooseLink(joined.cloudId, joined.name, candidates)
                }
            } catch (e: Exception) {
                SharingState.Message(errorText(e))
            }
        }
    }

    /**
     * [localVehicleId] links the shared car to one already registered here, so this phone
     * detects its parkings too; null adds it as a view-only car.
     */
    fun completeJoin(cloudId: String, name: String, localVehicleId: Long?) {
        viewModelScope.launch {
            if (localVehicleId != null) {
                vehicleRepository.setCloudId(localVehicleId, cloudId)
                vehicleRepository.getVehicleById(localVehicleId)?.let { publishCurrentParking(it) }
            } else {
                vehicleRepository.insertViewOnlyVehicle(name, cloudId)
            }
            _sharingState.value = SharingState.Message("הצטרפת ל\"$name\" — החניות שלו יופיעו כאן בזמן אמת")
        }
    }

    /** Live list of who [cloudId] is shared with, for the shared-car options dialog. */
    fun members(cloudId: String): Flow<List<VehicleMember>> = sharedVehicleRepository.observeMembers(cloudId)

    /** Stops sharing on this phone: leaves the cloud car but keeps it (and its history) locally. */
    fun stopSharing(vehicle: Vehicle) {
        val cloudId = vehicle.cloudId ?: return
        viewModelScope.launch {
            try {
                sharedVehicleRepository.leave(cloudId)
                if (vehicle.isViewOnly) {
                    // Nothing left to see without the cloud feed.
                    vehicleRepository.deleteVehicle(vehicle)
                } else {
                    vehicleRepository.setCloudId(vehicle.id, null)
                }
            } catch (e: Exception) {
                _sharingState.value = SharingState.Message(errorText(e))
            }
        }
    }

    /**
     * Reservations go into each member's calendar, so a member who has a shared car is asked for
     * calendar access once — even if they never open the reservations screen themselves.
     */
    fun shouldAskCalendarPermission(vehicles: List<Vehicle>): Boolean =
        isSharingAvailable && vehicles.any { it.isShared } && calendarStore.enabled.value &&
            !DeviceCalendar.hasPermission(context) && !calendarStore.askedForPermission

    fun onCalendarPermissionAsked() {
        calendarStore.askedForPermission = true
    }

    fun onCalendarPermissionResult(granted: Boolean) {
        if (granted) calendarStore.setEnabled(true) else calendarStore.onPermissionChanged()
    }

    /** So the others see where the car is right away, not only after its next parking. */
    private suspend fun publishCurrentParking(vehicle: Vehicle) {
        val current = parkingRepository.getCurrentParkingOnce(vehicle.id) ?: return
        publishSharedParkingUseCase(vehicle, current)
    }

    private fun errorText(e: Exception): String =
        if (e is SharingException) e.message.orEmpty()
        else "הפעולה נכשלה — בדוק חיבור לאינטרנט ונסה שוב"
}
