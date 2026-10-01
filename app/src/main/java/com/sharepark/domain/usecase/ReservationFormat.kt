package com.sharepark.domain.usecase

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Hebrew wording for reservation slots, shared by the screens and the notification. */
object ReservationFormat {

    private val time = DateTimeFormatter.ofPattern("HH:mm")
    private val date = DateTimeFormatter.ofPattern("d.M")
    private val weekday = DateTimeFormatter.ofPattern("EEEE", Locale("iw"))

    /** "היום", "מחר", or e.g. "יום שישי 3.10". */
    fun day(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        day(Instant.ofEpochMilli(millis).atZone(zone).toLocalDate(), zone)

    fun day(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
        val today = LocalDate.now(zone)
        return when (day) {
            today -> "היום"
            today.plusDays(1) -> "מחר"
            else -> "${weekday.format(day)} ${date.format(day)}"
        }
    }

    fun time(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        time.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** "היום 14:00–18:00", or with both days when the slot runs past midnight. */
    fun slot(startAt: Long, endAt: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val start = Instant.ofEpochMilli(startAt).atZone(zone)
        val end = Instant.ofEpochMilli(endAt).atZone(zone)
        return if (start.toLocalDate() == end.toLocalDate()) {
            "${day(startAt, zone)} ${time.format(start)}–${time.format(end)}"
        } else {
            "${day(startAt, zone)} ${time.format(start)} – ${day(endAt, zone)} ${time.format(end)}"
        }
    }
}
