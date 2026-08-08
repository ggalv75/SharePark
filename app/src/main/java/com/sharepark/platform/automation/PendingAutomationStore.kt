package com.sharepark.platform.automation

import android.content.Context
import androidx.core.content.edit

/**
 * Survives process death for a deferred send. Parking usually happens with the phone locked in
 * a pocket, and the automated send has to wait for the next unlock — which can be long enough
 * that the app process is killed in between, so the pending send can't live only in memory.
 */
object PendingAutomationStore {

    private const val PREFS_NAME = "pending_automation"
    private const val KEY_MESSAGE = "message"
    private const val KEY_MODE = "mode"
    private const val KEY_PHONE = "phone"
    private const val KEY_GROUP = "group"
    private const val KEY_DEADLINE = "deadline"

    data class Pending(
        val message: String,
        val mode: String,
        val phone: String,
        val groupName: String,
        val deadline: Long
    )

    fun save(context: Context, pending: Pending) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_MESSAGE, pending.message)
                putString(KEY_MODE, pending.mode)
                putString(KEY_PHONE, pending.phone)
                putString(KEY_GROUP, pending.groupName)
                putLong(KEY_DEADLINE, pending.deadline)
            }
    }

    fun load(context: Context): Pending? {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val message = prefs.getString(KEY_MESSAGE, null) ?: return null
        val deadline = prefs.getLong(KEY_DEADLINE, 0L)
        if (System.currentTimeMillis() > deadline) {
            clear(context)
            return null
        }
        return Pending(
            message = message,
            mode = prefs.getString(KEY_MODE, "").orEmpty(),
            phone = prefs.getString(KEY_PHONE, "").orEmpty(),
            groupName = prefs.getString(KEY_GROUP, "").orEmpty(),
            deadline = deadline
        )
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { clear() }
    }
}
