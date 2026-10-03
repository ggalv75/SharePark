package com.sharepark.platform.notification

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Something worth a banner inside the app while it's open. */
data class InAppAlert(val title: String, val text: String, val openReservationsFor: Long?)

/**
 * Banners for the open app. Nothing is replayed: an alert raised while no screen is showing is
 * dropped, the system notification already covers that case.
 */
@Singleton
class InAppAlerts @Inject constructor() {
    private val _alerts = MutableSharedFlow<InAppAlert>(extraBufferCapacity = 8)
    val alerts: SharedFlow<InAppAlert> = _alerts.asSharedFlow()

    fun post(alert: InAppAlert) {
        _alerts.tryEmit(alert)
    }
}
