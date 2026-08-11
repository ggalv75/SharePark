package com.sharepark.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharepark.data.local.prefs.AutomationConfig
import com.sharepark.data.local.prefs.AutomationPreferences
import com.sharepark.data.remote.GeocodingService
import com.sharepark.data.repository.AutomationZoneRepository
import com.sharepark.domain.model.AutomationZone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutomationZonesViewModel @Inject constructor(
    private val automationZoneRepository: AutomationZoneRepository,
    private val automationPreferences: AutomationPreferences,
    private val geocodingService: GeocodingService
) : ViewModel() {

    /** The zone being created or edited, with whatever the user has filled in so far. */
    data class ZoneDraft(
        val id: Long = 0,
        val label: String = "",
        val address: String = "",
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Int = AutomationZone.DEFAULT_RADIUS_METERS,
        val isEnabled: Boolean = true,
        val createdAt: Long = System.currentTimeMillis(),
        val isResolvingAddress: Boolean = false
    ) {
        val isNew: Boolean get() = id == 0L
    }

    val zones: StateFlow<List<AutomationZone>> = automationZoneRepository.allZones
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val config: StateFlow<AutomationConfig> = automationPreferences.config
        .stateIn(viewModelScope, SharingStarted.Lazily, AutomationConfig())

    private val _draft = MutableStateFlow<ZoneDraft?>(null)
    val draft: StateFlow<ZoneDraft?> = _draft.asStateFlow()

    private val _searchResults = MutableStateFlow<List<GeocodingService.Place>>(emptyList())
    val searchResults: StateFlow<List<GeocodingService.Place>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private var addressJob: Job? = null
    private var searchJob: Job? = null

    // ── Draft lifecycle ─────────────────────────────────────────────────────

    /** Starts a new zone at a point the user tapped on the map, then names it from its address. */
    fun startDraftAt(latitude: Double, longitude: Double) {
        _draft.value = ZoneDraft(
            latitude = latitude,
            longitude = longitude,
            isResolvingAddress = true
        )
        resolveAddress(latitude, longitude)
    }

    fun startDraftFromPlace(place: GeocodingService.Place) {
        _draft.value = ZoneDraft(
            label = place.address.substringBefore(',').trim(),
            address = place.address,
            latitude = place.latitude,
            longitude = place.longitude
        )
        clearSearch()
    }

    fun editZone(zone: AutomationZone) {
        addressJob?.cancel()
        _draft.value = ZoneDraft(
            id = zone.id,
            label = zone.label,
            address = zone.address,
            latitude = zone.latitude,
            longitude = zone.longitude,
            // A zone saved under a wider limit would otherwise show a number the slider can't
            // reach, so the editor would display one radius and save another.
            radiusMeters = zone.radiusMeters.coerceIn(
                AutomationZone.MIN_RADIUS_METERS,
                AutomationZone.MAX_RADIUS_METERS
            ),
            isEnabled = zone.isEnabled,
            createdAt = zone.createdAt
        )
    }

    fun moveDraftTo(latitude: Double, longitude: Double) {
        val current = _draft.value ?: return
        _draft.value = current.copy(
            latitude = latitude,
            longitude = longitude,
            isResolvingAddress = true
        )
        resolveAddress(latitude, longitude)
    }

    fun updateDraftLabel(label: String) {
        _draft.value = _draft.value?.copy(label = label)
    }

    fun updateDraftRadius(radiusMeters: Int) {
        _draft.value = _draft.value?.copy(radiusMeters = radiusMeters)
    }

    fun cancelDraft() {
        addressJob?.cancel()
        _draft.value = null
    }

    fun saveDraft() {
        val draft = _draft.value ?: return
        val label = draft.label.trim().ifBlank {
            draft.address.substringBefore(',').trim().ifBlank { "אזור ללא שם" }
        }
        viewModelScope.launch {
            automationZoneRepository.saveZone(
                AutomationZone(
                    id = draft.id,
                    label = label,
                    address = draft.address,
                    latitude = draft.latitude,
                    longitude = draft.longitude,
                    radiusMeters = draft.radiusMeters,
                    isEnabled = draft.isEnabled,
                    createdAt = draft.createdAt
                )
            )
            _draft.value = null
        }
    }

    private fun resolveAddress(latitude: Double, longitude: Double) {
        addressJob?.cancel()
        addressJob = viewModelScope.launch {
            val result = geocodingService.reverseGeocode(latitude, longitude)
            val current = _draft.value ?: return@launch
            // The pin may have moved again while we were geocoding — only apply if it didn't.
            if (current.latitude != latitude || current.longitude != longitude) return@launch
            _draft.value = current.copy(
                address = result.address,
                label = current.label.ifBlank { result.address.substringBefore(',').trim() },
                isResolvingAddress = false
            )
        }
    }

    // ── Address search ──────────────────────────────────────────────────────

    fun searchAddress(query: String) {
        searchJob?.cancel()
        _searchError.value = null
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            val results = geocodingService.searchAddress(query)
            _searchResults.value = results
            _searchError.value = if (results.isEmpty()) "לא נמצאה כתובת מתאימה" else null
            _isSearching.value = false
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
        _searchError.value = null
        _isSearching.value = false
    }

    // ── Saved zones ─────────────────────────────────────────────────────────

    fun setZoneEnabled(zoneId: Long, enabled: Boolean) {
        viewModelScope.launch { automationZoneRepository.setEnabled(zoneId, enabled) }
    }

    fun deleteZone(zoneId: Long) {
        viewModelScope.launch { automationZoneRepository.deleteZone(zoneId) }
    }

    fun setZonesOnly(zonesOnly: Boolean) {
        viewModelScope.launch { automationPreferences.setZonesOnly(zonesOnly) }
    }
}
