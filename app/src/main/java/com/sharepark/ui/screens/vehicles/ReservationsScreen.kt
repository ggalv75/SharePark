package com.sharepark.ui.screens.vehicles

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sharepark.domain.model.Reservation
import com.sharepark.domain.usecase.ReservationFormat
import com.sharepark.platform.calendar.DeviceCalendar
import com.sharepark.ui.components.AppCard
import com.sharepark.ui.components.IconBadge
import com.sharepark.ui.components.MutedText
import com.sharepark.ui.components.PrimaryPillButton
import com.sharepark.ui.components.ScreenHeader
import com.sharepark.ui.components.SectionHeader
import com.sharepark.ui.components.simpleVerticalScrollbar
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Who has booked a shared car and when. Every member sees the same list live; each can book a
 * free slot and cancel their own.
 */
@Composable
fun ReservationsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReservationsViewModel = hiltViewModel()
) {
    val vehicle by viewModel.vehicle.collectAsState()
    val reservations by viewModel.reservations.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val message by viewModel.message.collectAsState()
    val calendarSyncOn by viewModel.calendarSyncOn.collectAsState()
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> viewModel.onCalendarPermissionResult(results.values.all { it }) }

    LaunchedEffect(Unit) {
        if (viewModel.shouldAskCalendarPermission()) {
            viewModel.onCalendarPermissionAsked()
            calendarPermissionLauncher.launch(DeviceCalendar.PERMISSIONS)
        }
    }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var pendingCancel by remember { mutableStateOf<Reservation?>(null) }

    val now = System.currentTimeMillis()
    // The listener only filters at subscribe time, so drop slots that ended while we watched.
    val upcoming = reservations.orEmpty().filter { it.endAt > now }
    val myUid = viewModel.myUid

    if (showAdd) {
        AddReservationDialog(
            existing = upcoming,
            isSaving = isSaving,
            onConfirm = { start, end, note ->
                viewModel.reserve(start, end, note) { showAdd = false }
            },
            onDismiss = { showAdd = false }
        )
    }

    pendingCancel?.let { reservation ->
        AlertDialog(
            onDismissRequest = { pendingCancel = null },
            title = { Text("לבטל את השריון?") },
            text = { Text(ReservationFormat.slot(reservation.startAt, reservation.endAt)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cancel(reservation)
                    pendingCancel = null
                }) { Text("בטל שריון", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingCancel = null }) { Text("השאר") } }
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            text = { Text(text) },
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text("אישור") } }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenHeader(
                title = "שריונים",
                subtitle = vehicle?.name,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזור")
                    }
                },
                actions = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, contentDescription = "שריון חדש")
                    }
                }
            )
        },
        bottomBar = {
            PrimaryPillButton(
                text = "שריין את הרכב",
                icon = Icons.Default.CalendarMonth,
                onClick = { showAdd = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (reservations == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                return@Box
            }

            val listState = rememberLazyListState()
            val byDay = upcoming.groupBy { dayOf(maxOf(it.startAt, now)) }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .simpleVerticalScrollbar(listState, MaterialTheme.colorScheme.onBackground),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { NowStatusCard(current = upcoming.firstOrNull { it.isOngoing(now) }, myUid = myUid) }
                item {
                    CalendarSyncCard(
                        isOn = calendarSyncOn,
                        onToggle = { on ->
                            if (!on) viewModel.turnOffCalendarSync()
                            else calendarPermissionLauncher.launch(DeviceCalendar.PERMISSIONS)
                        }
                    )
                }

                if (upcoming.isEmpty()) {
                    item {
                        Text(
                            text = "אין שריונים קרובים. שריון שתוסיף יופיע מיד אצל כל השותפים ברכב.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MutedText,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                        )
                    }
                }

                byDay.forEach { (day, dayReservations) ->
                    item(key = "day-$day") { SectionHeader(title = ReservationFormat.day(day)) }
                    items(dayReservations, key = { it.id }) { reservation ->
                        ReservationCard(
                            reservation = reservation,
                            isMine = reservation.reservedByUid == myUid,
                            isOngoing = reservation.isOngoing(now),
                            onCancel = { pendingCancel = reservation }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NowStatusCard(current: Reservation?, myUid: String?) {
    AppCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = if (current == null) Icons.Default.CheckCircle else Icons.Default.EventBusy,
                tint = if (current == null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(end = 14.dp)
            )
            Column {
                Text(
                    text = when {
                        current == null -> "הרכב פנוי עכשיו"
                        current.reservedByUid == myUid -> "משוריין עכשיו עבורך"
                        else -> "משוריין עכשיו ע\"י ${current.reservedByName}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (current != null) {
                    Text(
                        text = "עד ${ReservationFormat.time(current.endAt)}" +
                            if (dayOf(current.endAt) != LocalDate.now()) " (${ReservationFormat.day(current.endAt)})" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedText
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarSyncCard(isOn: Boolean, onToggle: (Boolean) -> Unit) {
    AppCard(contentPadding = PaddingValues(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = Icons.Default.EventAvailable,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(end = 14.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "הוספה ליומן",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isOn) "כל שריון של הרכב נכנס אוטומטית ליומן בטלפון הזה"
                           else "השריונים לא נכנסים ליומן בטלפון הזה",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
            Switch(checked = isOn, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun ReservationCard(
    reservation: Reservation,
    isMine: Boolean,
    isOngoing: Boolean,
    onCancel: () -> Unit
) {
    AppCard(contentPadding = PaddingValues(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = Icons.Default.Schedule,
                tint = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(end = 14.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ReservationFormat.slot(reservation.startAt, reservation.endAt),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = buildString {
                        append(if (isMine) "אתה" else reservation.reservedByName)
                        if (isOngoing) append(" · עכשיו")
                        reservation.note?.let { append(" · ").append(it) }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isMine) MaterialTheme.colorScheme.primary else MutedText
                )
            }
            if (isMine) {
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "בטל שריון",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * Pick a day, a start and an end time. An end time at or before the start means the slot runs
 * into the next day (e.g. 22:00–07:00). Overlaps with existing slots are flagged before saving;
 * the repository checks again against the cloud.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddReservationDialog(
    existing: List<Reservation>,
    isSaving: Boolean,
    onConfirm: (startAt: Long, endAt: Long, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    val zone = ZoneId.systemDefault()
    val nextHour = remember { LocalTime.now().withMinute(0).withSecond(0).withNano(0).plusHours(1) }
    var date by rememberSaveable {
        // Past 23:00 the next full hour is already tomorrow.
        mutableStateOf(if (nextHour == LocalTime.MIDNIGHT) LocalDate.now().plusDays(1) else LocalDate.now())
    }
    var start by rememberSaveable { mutableStateOf(nextHour) }
    var end by rememberSaveable { mutableStateOf(nextHour.plusHours(2)) }
    var note by rememberSaveable { mutableStateOf("") }
    var picking by remember { mutableStateOf<Picker?>(null) }

    val startAt = date.atTime(start).atZone(zone).toInstant().toEpochMilli()
    val endsNextDay = !end.isAfter(start)
    val endAt = (if (endsNextDay) date.plusDays(1) else date).atTime(end).atZone(zone).toInstant().toEpochMilli()
    val conflict = existing.firstOrNull { it.overlaps(startAt, endAt) }
    val inPast = endAt <= System.currentTimeMillis()

    when (picking) {
        Picker.Date -> DatePickDialog(
            initial = date,
            onPick = { date = it; picking = null },
            onDismiss = { picking = null }
        )
        Picker.Start -> TimePickDialog(
            title = "שעת התחלה",
            initial = start,
            onPick = { picked ->
                // Keep the slot's length when the start moves.
                val length = Duration.between(start, end).let { if (it.isNegative || it.isZero) it.plusDays(1) else it }
                start = picked
                end = picked.plus(length)
                picking = null
            },
            onDismiss = { picking = null }
        )
        Picker.End -> TimePickDialog(
            title = "שעת סיום",
            initial = end,
            onPick = { end = it; picking = null },
            onDismiss = { picking = null }
        )
        null -> Unit
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("שריון הרכב") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PickerRow(Icons.Default.CalendarMonth, "תאריך", ReservationFormat.day(date)) { picking = Picker.Date }
                PickerRow(Icons.Default.Schedule, "מ-", ReservationFormat.time(startAt)) { picking = Picker.Start }
                PickerRow(
                    Icons.Default.Schedule,
                    "עד",
                    ReservationFormat.time(endAt) + if (endsNextDay) " (למחרת)" else ""
                ) { picking = Picker.End }

                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    label = { Text("הערה (לא חובה)") },
                    placeholder = { Text("למשל: נסיעה לסבתא") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                val warning = when {
                    inPast -> "הזמן הזה כבר עבר"
                    conflict != null -> "חופף לשריון של ${conflict.reservedByName}: " +
                        ReservationFormat.slot(conflict.startAt, conflict.endAt)
                    else -> null
                }
                if (warning != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(startAt, endAt, note) },
                enabled = !isSaving && !inPast && conflict == null
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("שריין")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("ביטול") }
        }
    )
}

private enum class Picker { Date, Start, End }

@Composable
private fun PickerRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, color = MutedText, modifier = Modifier.width(56.dp))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // DatePicker speaks UTC midnights regardless of the device's time zone.
    val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                } ?: onDismiss()
            }) { Text("אישור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickDialog(title: String, initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("אישור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}

private fun dayOf(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
