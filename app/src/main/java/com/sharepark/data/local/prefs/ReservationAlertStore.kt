package com.sharepark.data.local.prefs

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which reservations of each shared car this phone has already announced. Keyed by reservation
 * id rather than by time, so a booking made while the app was closed is still announced the
 * next time it looks, and a member's wrong clock can't hide one.
 */
@Singleton
class ReservationAlertStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("reservation_alerts", Context.MODE_PRIVATE)

    /** Null until the car has been looked at once — its existing bookings aren't news. */
    fun seen(cloudId: String): Map<String, Long>? =
        prefs.getStringSet(KEY_PREFIX + cloudId, null)?.mapNotNull { row ->
            val parts = row.split(';')
            if (parts.size != 2) return@mapNotNull null
            parts[0] to (parts[1].toLongOrNull() ?: return@mapNotNull null)
        }?.toMap()

    /** Reservation id → its end time, so ended ones can be pruned. */
    fun setSeen(cloudId: String, seen: Map<String, Long>) = prefs.edit {
        putStringSet(KEY_PREFIX + cloudId, seen.mapTo(HashSet()) { (id, endAt) -> "$id;$endAt" })
    }

    private companion object {
        const val KEY_PREFIX = "seen_"
    }
}
