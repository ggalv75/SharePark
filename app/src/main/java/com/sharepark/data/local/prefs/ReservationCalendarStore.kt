package com.sharepark.data.local.prefs

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A calendar event this phone created for a reservation, so it can be removed if cancelled. */
data class CalendarEntry(val reservationId: String, val eventId: Long, val cloudId: String, val endAt: Long)

/**
 * Whether reservations of shared cars go into the device calendar, and which event belongs to
 * which reservation. Plain SharedPreferences: the sync reads it from a background thread and
 * the mapping is a handful of rows.
 */
@Singleton
class ReservationCalendarStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("reservation_calendar", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** Bumped whenever the calendar becomes usable (switched on, permission granted) to re-sync. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
        _enabled.value = enabled
        _version.value++
    }

    fun onPermissionChanged() {
        _version.value++
    }

    /** The app asks for calendar access by itself only once; after that it's the switch's job. */
    var askedForPermission: Boolean
        get() = prefs.getBoolean(KEY_ASKED, false)
        set(value) = prefs.edit { putBoolean(KEY_ASKED, value) }

    fun entries(): List<CalendarEntry> = prefs.all.mapNotNull { (key, value) ->
        if (!key.startsWith(ENTRY_PREFIX) || value !is String) return@mapNotNull null
        val parts = value.split(';')
        if (parts.size != 3) return@mapNotNull null
        CalendarEntry(
            reservationId = key.removePrefix(ENTRY_PREFIX),
            eventId = parts[0].toLongOrNull() ?: return@mapNotNull null,
            cloudId = parts[1],
            endAt = parts[2].toLongOrNull() ?: return@mapNotNull null
        )
    }

    fun put(entry: CalendarEntry) = prefs.edit {
        putString(ENTRY_PREFIX + entry.reservationId, "${entry.eventId};${entry.cloudId};${entry.endAt}")
    }

    fun remove(reservationId: String) = prefs.edit { remove(ENTRY_PREFIX + reservationId) }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_ASKED = "asked_permission"
        const val ENTRY_PREFIX = "event_"
    }
}
