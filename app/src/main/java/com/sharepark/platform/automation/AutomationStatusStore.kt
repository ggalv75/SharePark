package com.sharepark.platform.automation

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The outcome of the last automated send, persisted so the settings screen can show what
 * actually happened. Without it an automation that quietly fails inside WhatsApp is
 * indistinguishable from one that never ran — which is exactly the state that makes this
 * feature impossible to debug from the user's side.
 */
object AutomationStatusStore {

    enum class Outcome { SENT, DEFERRED, FAILED, SKIPPED }

    data class Status(
        val outcome: Outcome,
        val detail: String,
        val timestamp: Long
    )

    private const val PREFS_NAME = "automation_status"
    private const val KEY_OUTCOME = "outcome"
    private const val KEY_DETAIL = "detail"
    private const val KEY_TIMESTAMP = "timestamp"

    private val _status = MutableStateFlow<Status?>(null)
    val status: StateFlow<Status?> = _status.asStateFlow()

    fun record(context: Context, outcome: Outcome, detail: String) {
        val status = Status(outcome, detail, System.currentTimeMillis())
        _status.value = status
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_OUTCOME, outcome.name)
                putString(KEY_DETAIL, detail)
                putLong(KEY_TIMESTAMP, status.timestamp)
            }
    }

    /** Loads the persisted status into memory — call before observing [status] in the UI. */
    fun refresh(context: Context) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val outcomeName = prefs.getString(KEY_OUTCOME, null) ?: return
        val outcome = runCatching { Outcome.valueOf(outcomeName) }.getOrNull() ?: return
        _status.value = Status(
            outcome = outcome,
            detail = prefs.getString(KEY_DETAIL, "").orEmpty(),
            timestamp = prefs.getLong(KEY_TIMESTAMP, 0L)
        )
    }
}
