package com.sharepark.platform.calendar

import android.content.Context
import com.sharepark.data.local.prefs.CalendarEntry
import com.sharepark.data.local.prefs.ReservationCalendarStore
import com.sharepark.domain.model.Reservation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors the reservations of shared cars into this phone's calendar: every member's phone does
 * the same for itself, so each member's calendar shows every booking — their own and others'.
 * Cancelled bookings are removed; bookings that already ended stay as history.
 */
@Singleton
class ReservationCalendarSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: ReservationCalendarStore
) {
    // Several cars reconcile concurrently against the one mapping.
    private val mutex = Mutex()

    private val isActive: Boolean
        get() = store.enabled.value && DeviceCalendar.hasPermission(context)

    /**
     * [reservations] must be the car's complete list of slots that haven't ended, straight from
     * the server — anything mapped but missing from it is treated as cancelled.
     */
    suspend fun reconcile(cloudId: String, vehicleName: String, myUid: String, reservations: List<Reservation>) =
        mutex.withLock {
            if (!isActive) return@withLock
            val now = System.currentTimeMillis()
            val mapped = store.entries().filter { it.cloudId == cloudId }
            val mappedIds = mapped.mapTo(HashSet()) { it.reservationId }
            val liveIds = reservations.mapTo(HashSet()) { it.id }

            mapped.filter { it.reservationId !in liveIds }.forEach { entry ->
                if (entry.endAt > now) DeviceCalendar.deleteEvent(context, entry.eventId)
                store.remove(entry.reservationId)
            }

            val toAdd = reservations.filter { it.id !in mappedIds && it.endAt > now }
            if (toAdd.isEmpty()) return@withLock
            val calendarId = DeviceCalendar.defaultCalendarId(context) ?: return@withLock
            toAdd.forEach { reservation ->
                val eventId = DeviceCalendar.insertEvent(
                    context = context,
                    calendarId = calendarId,
                    title = title(vehicleName, reservation, myUid),
                    description = description(reservation),
                    startAt = reservation.startAt,
                    endAt = reservation.endAt
                ) ?: return@forEach
                store.put(CalendarEntry(reservation.id, eventId, cloudId, reservation.endAt))
            }
        }

    /** After leaving a car (or switching the feature off), its upcoming bookings leave the calendar. */
    suspend fun removeUpcoming(keepCloudIds: Set<String>) = mutex.withLock {
        if (!DeviceCalendar.hasPermission(context)) return@withLock
        val now = System.currentTimeMillis()
        store.entries().filter { it.cloudId !in keepCloudIds }.forEach { entry ->
            if (entry.endAt > now) DeviceCalendar.deleteEvent(context, entry.eventId)
            store.remove(entry.reservationId)
        }
    }

    private fun title(vehicleName: String, reservation: Reservation, myUid: String): String =
        if (reservation.reservedByUid == myUid) "🚗 $vehicleName — שריון שלי"
        else "🚗 $vehicleName — ${reservation.reservedByName}"

    private fun description(reservation: Reservation): String = buildString {
        append("שריון רכב משותף של ${reservation.reservedByName}")
        reservation.note?.let { append("\n").append(it) }
        append("\n\nנוסף אוטומטית ע\"י SharePark")
    }
}
