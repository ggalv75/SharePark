package com.sharepark.ui.screens.history

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sharepark.domain.model.ParkingRecord
import com.sharepark.ui.components.AppCard
import com.sharepark.ui.components.IconBadge
import com.sharepark.ui.components.ScreenHeader
import com.sharepark.ui.components.SectionHeader
import com.sharepark.ui.components.rememberBreathingScale
import com.sharepark.ui.components.simpleVerticalScrollbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val activeVehicle by viewModel.activeVehicle.collectAsState()
    val parkingHistory by viewModel.parkingHistory.collectAsState()
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenHeader(
                title = "היסטוריה",
                subtitle = activeVehicle?.name
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (activeVehicle == null) {
                EmptyHistory(
                    title = "אין רכב פעיל",
                    message = "הגדר רכב פעיל במסך 'רכבים' כדי לראות את ההיסטוריה שלו."
                )
            } else if (parkingHistory.isEmpty()) {
                EmptyHistory(
                    title = "עוד אין חניות",
                    message = "אין היסטוריית חניות שמורה עבור ${activeVehicle?.name}. מיקומים יישמרו אוטומטית בעתיד."
                )
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .simpleVerticalScrollbar(listState, MaterialTheme.colorScheme.onBackground),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SectionHeader(title = "${parkingHistory.size} חניות")
                    }
                    items(parkingHistory) { record ->
                        HistoryCard(
                            record = record,
                            onNavigate = { lat, lng ->
                                // Walking directions — you're on foot looking for the car, not driving to it.
                                val gmmIntentUri = Uri.parse("google.navigation:q=$lat,$lng&mode=w")
                                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                                    setPackage("com.google.android.apps.maps")
                                }
                                context.startActivity(mapIntent)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHistory(title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        val breathingScale = rememberBreathingScale()
        IconBadge(
            icon = Icons.Default.History,
            tint = MaterialTheme.colorScheme.primary,
            size = 64.dp,
            iconSize = 30.dp,
            modifier = Modifier
                .padding(bottom = 20.dp)
                .scale(breathingScale)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun HistoryCard(
    record: ParkingRecord,
    onNavigate: (Double, Double) -> Unit
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    AppCard(
        onClick = { onNavigate(record.latitude, record.longitude) },
        contentPadding = PaddingValues(start = 18.dp, end = 12.dp, top = 16.dp, bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Date column — month over day, like a logbook entry
            val date = Date(record.parkedAt)
            Column(
                modifier = Modifier.width(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = SimpleDateFormat("MMM", Locale.getDefault()).format(date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = SimpleDateFormat("d", Locale.getDefault()).format(date),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.address ?: "מיקום ללא כתובת",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))

                val timeString = DateUtils.getRelativeTimeSpanString(
                    record.parkedAt,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                ).toString()
                val details = buildList {
                    add(timeString)
                    if (record.vehicleName.isNotBlank()) add(record.vehicleName)
                    record.parkedByName?.let { add(it) }
                    add("±${record.accuracy.toInt()} מ׳")
                }
                Text(
                    text = details.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted
                )
            }

            Icon(
                imageVector = Icons.Default.Directions,
                contentDescription = "ניווט",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    .padding(9.dp)
            )
        }
    }
}
