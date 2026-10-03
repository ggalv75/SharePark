package com.sharepark.platform.notification

import android.content.Context
import com.sharepark.data.local.prefs.ReservationAlertStore
import com.sharepark.domain.model.Reservation
import com.sharepark.domain.usecase.ReservationFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Announces bookings other members made on a shared car — a system notification plus a banner
 * if the app is open. Fed both by the live listener and by the periodic background check; the
 * seen-list makes sure each booking is announced once whichever gets there first.
 */
@Singleton
class ReservationNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: ReservationAlertStore,
    private val inAppAlerts: InAppAlerts
) {
    private val mutex = Mutex()

    /** [reservations] must be the car's complete list of slots that haven't ended, from the server. */
    suspend fun onReservations(
        vehicleId: Long,
        cloudId: String,
        vehicleName: String,
        myUid: String,
        reservations: List<Reservation>
    ) = mutex.withLock {
        val now = System.currentTimeMillis()
        val seen = store.seen(cloudId)
        val fresh = if (seen == null) {
            // First look at this car (just joined, or just updated the app): nothing is news.
            emptyList()
        } else {
            reservations.filter { it.id !in seen && it.reservedByUid != myUid && it.endAt > now }
        }

        val stillRelevant = seen.orEmpty().filterValues { it > now }
        store.setSeen(cloudId, stillRelevant + reservations.associate { it.id to it.endAt })

        fresh.sortedBy { it.startAt }.forEach { announce(vehicleId, vehicleName, it) }
    }

    private fun announce(vehicleId: Long, vehicleName: String, reservation: Reservation) {
        val title = "${reservation.reservedByName} שריין את $vehicleName 📅"
        val slot = ReservationFormat.slot(reservation.startAt, reservation.endAt)
        val text = reservation.note?.let { "$slot · $it" } ?: slot
        NotificationHelper.showReservationNotification(
            context = context,
            vehicleId = vehicleId,
            reservationId = reservation.id,
            title = title,
            text = text
        )
        inAppAlerts.post(InAppAlert(title = title, text = text, openReservationsFor = vehicleId))
    }
}
