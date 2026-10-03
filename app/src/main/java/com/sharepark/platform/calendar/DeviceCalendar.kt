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
    private const val GOOGLE_ACCOUNT_TYPE = "com.google"

    val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    fun hasPermission(context: Context): Boolean = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** A calendar on the phone that events can be added to. */
    data class CalendarInfo(val id: Long, val name: String, val accountName: String, val isGoogle: Boolean) {
        /** "Work (gal@gmail.com)", or just the account when the calendar is named after it. */
        val label: String get() = if (name == accountName || accountName.isBlank()) name else "$name ($accountName)"
    }

    /**
     * Writable, visible calendars, best default first: a Google account's own calendar, then its
     * other calendars, then everything else. Phone-local calendars ("My calendar" on Samsung and
     * others) are often flagged primary too but never reach Google Calendar, so they come last.
     */
    fun writableCalendars(context: Context): List<CalendarInfo> {
        if (!hasPermission(context)) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.IS_PRIMARY
        )
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND " +
            "${CalendarContract.Calendars.VISIBLE} = 1"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

        data class Row(val info: CalendarInfo, val rank: Int)
        return try {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, selection, args, null)
                ?.use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            val accountName = cursor.getString(2).orEmpty()
                            val isGoogle = cursor.getString(3) == GOOGLE_ACCOUNT_TYPE
                            val isPrimary = cursor.getInt(5) == 1
                            // A Google account's own calendar is owned by the account itself.
                            val isOwnCalendar = cursor.getString(4) == accountName
                            val rank = when {
                                isGoogle && (isPrimary || isOwnCalendar) -> 0
                                isGoogle -> 1
                                isPrimary -> 2
                                else -> 3
                            }
                            val info = CalendarInfo(
                                id = cursor.getLong(0),
                                name = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: accountName,
                                accountName = accountName,
                                isGoogle = isGoogle
                            )
                            add(Row(info, rank))
                        }
                    }
                }
                .orEmpty()
                .sortedBy { it.rank }
                .map { it.info }
        } catch (e: SecurityException) {
            Log.w(TAG, "Calendar query denied", e)
            emptyList()
        }
    }

    /** The calendar new events go to: [chosenId] if it's still there, otherwise the best default. */
    fun targetCalendar(context: Context, chosenId: Long?): CalendarInfo? {
        val calendars = writableCalendars(context)
        return calendars.firstOrNull { it.id == chosenId } ?: calendars.firstOrNull()
    }

    /**
     * An event already in [calendarId] with exactly this title and time — one this app wrote
     * before it lost track of it (reinstalled, data cleared). Adopting it avoids a duplicate.
     */
    fun findEvent(context: Context, calendarId: Long, title: String, startAt: Long, endAt: Long): Long? {
        if (!hasPermission(context)) return null
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND " +
            "${CalendarContract.Events.TITLE} = ? AND " +
            "${CalendarContract.Events.DTSTART} = ? AND " +
            "${CalendarContract.Events.DTEND} = ? AND " +
            "${CalendarContract.Events.DELETED} = 0"
        val args = arrayOf(calendarId.toString(), title, startAt.toString(), endAt.toString())
        return try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events._ID),
                selection,
                args,
                null
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
        } catch (e: Exception) {
            Log.w(TAG, "Could not look up calendar event", e)
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
