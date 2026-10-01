package com.sharepark.platform.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.TimeZone

/** Thin wrapper over the system calendar provider — whatever account the phone syncs with. */
object DeviceCalendar {

    private const val TAG = "DeviceCalendar"

    val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    fun hasPermission(context: Context): Boolean = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * The calendar new events go to: the primary one of a writable, visible calendar if there is
     * one (normally the Google account's own calendar), otherwise the first writable calendar.
     */
    fun defaultCalendarId(context: Context): Long? {
        if (!hasPermission(context)) return null
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.ACCOUNT_TYPE
        )
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND " +
            "${CalendarContract.Calendars.VISIBLE} = 1"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

        return try {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, selection, args, null)
                ?.use { cursor ->
                    var fallback: Long? = null
                    var google: Long? = null
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        if (cursor.getInt(1) == 1) return@use id
                        if (google == null && cursor.getString(2) == "com.google") google = id
                        if (fallback == null) fallback = id
                    }
                    google ?: fallback
                }
        } catch (e: SecurityException) {
            Log.w(TAG, "Calendar query denied", e)
            null
        }
    }

    /** Returns the new event's id, or null if the provider refused it. */
    fun insertEvent(
        context: Context,
        calendarId: Long,
        title: String,
        description: String,
        startAt: Long,
        endAt: Long
    ): Long? {
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, startAt)
            put(CalendarContract.Events.DTEND, endAt)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)
        }
        return try {
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                ?.let { ContentUris.parseId(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not insert calendar event", e)
            null
        }
    }

    /** Deleting an event the user already removed by hand is a harmless no-op. */
    fun deleteEvent(context: Context, eventId: Long) {
        try {
            context.contentResolver.delete(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                null,
                null
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete calendar event $eventId", e)
        }
    }
}
