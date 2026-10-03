package com.sharepark.ui.screens.map

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharepark.R
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.sharepark.domain.model.ParkingRecord
import com.sharepark.domain.model.TrustedContact
import com.sharepark.domain.usecase.WhatsAppLinkBuilder
import com.sharepark.platform.location.LocationHelper
import com.sharepark.platform.permissions.PermissionManager
import com.sharepark.ui.components.CircleIconButton
import com.sharepark.ui.components.IconBadge
import com.sharepark.ui.components.PrimaryPillButton
import com.sharepark.ui.components.SecondaryPillButton
import com.sharepark.ui.components.SheetHandle
import com.sharepark.ui.components.StatDivider
import com.sharepark.ui.components.StatItem
import com.sharepark.ui.components.rememberBreathingScale
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MapScreen(
    onFullScreenChange: (Boolean) -> Unit = {},
    viewModel: MapViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activeVehicle by viewModel.activeVehicle.collectAsState()
    val currentParking by viewModel.currentParking.collectAsState()
    val trustedContacts by viewModel.trustedContacts.collectAsState()

    var permissionsGranted by remember {
        mutableStateOf(PermissionManager.hasAllRequiredPermissions(context))
    }
    var showShareOptions by remember { mutableStateOf(false) }
    var showFamilyShare by remember { mutableStateOf(false) }
    var isFullScreen by remember { mutableStateOf(false) }

    LaunchedEffect(isFullScreen) { onFullScreenChange(isFullScreen) }
    if (isFullScreen) {
        BackHandler { isFullScreen = false }
    }

    LaunchedEffect(Unit) {
        viewModel.shareTextEvent.collectLatest { shareText ->
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, null)
            context.startActivity(shareIntent)
        }
    }

    if (showShareOptions) {
        ShareOptionsDialog(
            onGeneralShare = {
                showShareOptions = false
                viewModel.shareParkingLocation()
            },
            onFamilyShare = {
                showShareOptions = false
                showFamilyShare = true
            },
            onDismiss = { showShareOptions = false }
        )
    }

    if (showFamilyShare) {
        FamilyShareDialog(
            contacts = trustedContacts,
            onSendToContact = { contact ->
                val message = viewModel.buildShareText()
                if (message != null) {
                    val url = WhatsAppLinkBuilder.buildUrl(contact.phoneNumber, message)
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, "WhatsApp אינו מותקן במכשיר", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showFamilyShare = false }
        )
    }

    // The map owns the whole tab; everything else floats over it.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!permissionsGranted) {
            PermissionOverlay(
                onPermissionsGranted = { permissionsGranted = true }
            )
        } else {
            MapContent(
                parking = currentParking,
                activeVehicleName = activeVehicle?.name,
                isFullScreen = isFullScreen,
                onToggleFullScreen = { isFullScreen = !isFullScreen },
                onShare = { showShareOptions = true },
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

@SuppressLint("MissingPermission")
@Composable
fun MapContent(
    parking: ParkingRecord?,
    activeVehicleName: String?,
    isFullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
    onShare: () -> Unit,
    onNavigate: (Double, Double) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val cameraPositionState = rememberCameraPositionState()

    LaunchedEffect(parking) {
        parking?.let {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(it.latitude, it.longitude),
                16f
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (parking != null) {
            val position = LatLng(parking.latitude, parking.longitude)
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = true),
                uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false),
                onMapClick = { if (!isFullScreen) onToggleFullScreen() }
            ) {
                Marker(
                    state = MarkerState(position = position),
                    title = activeVehicleName ?: "הרכב שלי",
                    snippet = parking.address ?: ""
                )
            }

            if (isFullScreen) {
                // Close button — exits fullscreen (back button also works via BackHandler)
                CircleIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "צא ממסך מלא",
                    onClick = onToggleFullScreen,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                )
            }

            // Floating map controls — full screen / re-center on my location / on the car
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Tapping the map also expands it, but that's invisible until you try it.
                if (!isFullScreen) {
                    CircleIconButton(
                        icon = Icons.Default.Fullscreen,
                        contentDescription = "מסך מלא",
                        onClick = onToggleFullScreen
                    )
                }

                CircleIconButton(
                    icon = Icons.Default.MyLocation,
                    contentDescription = "המיקום שלי",
                    onClick = {
                        coroutineScope.launch {
                            val myLocation = LocationHelper.getCurrentLocation(context)
                            if (myLocation != null) {
                                cameraPositionState.position = CameraPosition.fromLatLngZoom(
                                    LatLng(myLocation.latitude, myLocation.longitude),
                                    16f
                                )
                            }
                        }
                    }
                )

                CircleIconButton(
                    icon = Icons.Default.DirectionsCar,
                    contentDescription = "מיקום הרכב",
                    onClick = {
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(position, 16f)
                    },
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }

            // Bottom sheet with the parking details. Swiping it down hands the whole screen to
            // the map; the peek bar left at the bottom brings it back (swipe up or tap).
            var sheetHeight by remember { mutableIntStateOf(0) }
            var sheetOffset by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(isFullScreen) { if (!isFullScreen) sheetOffset = 0f }

            AnimatedVisibility(
                visible = !isFullScreen,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ParkingSheet(
                    parking = parking,
                    vehicleName = activeVehicleName ?: "הרכב שלי",
                    onShare = onShare,
                    onNavigate = { onNavigate(parking.latitude, parking.longitude) },
                    modifier = Modifier
                        .onSizeChanged { sheetHeight = it.height }
                        .offset { IntOffset(0, sheetOffset.roundToInt()) }
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                sheetOffset = (sheetOffset + delta).coerceIn(0f, sheetHeight.toFloat())
                            },
                            onDragStopped = { velocity ->
                                val collapse = sheetOffset > sheetHeight * COLLAPSE_FRACTION ||
                                    velocity > FLING_VELOCITY
                                val target = if (collapse) sheetHeight.toFloat() else 0f
                                animate(sheetOffset, target) { value, _ -> sheetOffset = value }
                                if (collapse) onToggleFullScreen()
                            }
                        )
                )
            }

            AnimatedVisibility(
                visible = isFullScreen,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                CollapsedSheetBar(
                    vehicleName = activeVehicleName ?: "הרכב שלי",
                    address = parking.address,
                    onExpand = onToggleFullScreen
                )
            }
        } else {
            // Empty state — soft gradient canvas with a bold, left-weighted message
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            )
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 28.dp)
                ) {
                    val breathingScale = rememberBreathingScale()
                    IconBadge(
                        icon = Icons.Default.Map,
                        tint = MaterialTheme.colorScheme.primary,
                        size = 64.dp,
                        iconSize = 30.dp,
                        modifier = Modifier
                            .padding(bottom = 20.dp)
                            .scale(breathingScale)
                    )
                    Text(
                        text = if (activeVehicleName == null) "אין רכב פעיל" else "אין חנייה שמורה",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (activeVehicleName == null) "פנה לכרטיסיית 'רכבים' והוסף רכב."
                               else "המיקום של $activeVehicleName יישמר אוטומטית בעת ניתוק הבלוטות'.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

/** What's left of the parking sheet while the map is full screen. */
@Composable
private fun CollapsedSheetBar(
    vehicleName: String,
    address: String?,
    onExpand: () -> Unit
) {
    var dragged by remember { mutableFloatStateOf(0f) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onExpand)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> dragged += delta },
                onDragStarted = { dragged = 0f },
                onDragStopped = { velocity ->
                    if (dragged < -EXPAND_DRAG_PX || velocity < -FLING_VELOCITY) onExpand()
                }
            ),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 14.dp)
        ) {
            SheetHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DirectionsCar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 10.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = vehicleName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    address?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "הצג פרטי חנייה",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ParkingSheet(
    parking: ParkingRecord,
    vehicleName: String,
    onShare: () -> Unit,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 20.dp)
        ) {
            SheetHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = vehicleName,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Stats row — how long ago, how accurate, and (on shared cars) who parked it
            val (elapsedValue, elapsedUnit) = elapsedSince(parking.parkedAt)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatItem(
                    value = elapsedValue,
                    unit = elapsedUnit,
                    label = "מאז החנייה",
                    icon = Icons.Default.Schedule
                )
                StatDivider()
                StatItem(
                    value = parking.accuracy.toInt().toString(),
                    unit = "מ׳",
                    label = "דיוק",
                    icon = Icons.Default.GpsFixed
                )
                parking.parkedByName?.let { parkedBy ->
                    StatDivider()
                    StatItem(
                        value = parkedBy,
                        label = "החנה",
                        icon = Icons.Default.Person,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = parking.address ?: "מעבד מיקום...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                PrimaryPillButton(
                    text = "ניווט",
                    icon = Icons.Default.Directions,
                    onClick = onNavigate,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                SecondaryPillButton(
                    text = "שתף",
                    icon = Icons.Default.Share,
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// Sheet gestures: past this share of its height (or flung down) the sheet collapses.
private const val COLLAPSE_FRACTION = 0.3f
private const val FLING_VELOCITY = 1200f
private const val EXPAND_DRAG_PX = 40f

/** Elapsed time since [since] as a bold number + short Hebrew unit. */
private fun elapsedSince(since: Long): Pair<String, String> {
    val minutes = ((System.currentTimeMillis() - since) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 60 -> minutes.toString() to "דק׳"
        minutes < 48 * 60 -> (minutes / 60).toString() to "שע׳"
        else -> (minutes / (24 * 60)).toString() to "ימים"
    }
}

@Composable
fun ShareOptionsDialog(
    onGeneralShare: () -> Unit,
    onFamilyShare: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("שיתוף מיקום חנייה") },
        text = {
            Column {
                ShareOptionRow(
                    icon = Icons.Default.Share,
                    label = "שיתוף כללי",
                    description = "פתח את תפריט השיתוף הרגיל של המכשיר",
                    onClick = onGeneralShare
                )
                Spacer(modifier = Modifier.height(8.dp))
                ShareOptionRow(
                    icon = Icons.Default.People,
                    label = "שלח לאנשי קשר מורשים",
                    description = "שלח ישירות ב-WhatsApp לבני המשפחה שהגדרת",
                    onClick = onFamilyShare
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ביטול")
            }
        }
    )
}

@Composable
private fun ShareOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(
            icon = icon,
            tint = MaterialTheme.colorScheme.primary,
            size = 40.dp,
            iconSize = 20.dp,
            modifier = Modifier.padding(end = 12.dp)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun FamilyShareDialog(
    contacts: List<TrustedContact>,
    onSendToContact: (TrustedContact) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("שלח לאנשי קשר מורשים") },
        text = {
            if (contacts.isEmpty()) {
                Text(
                    text = "עדיין לא הגדרת אנשי קשר מורשים. ניתן להוסיף בני משפחה במסך ההגדרות.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            } else {
                LazyColumn {
                    items(contacts) { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSendToContact(contact) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = contact.phoneNumber,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            IconButton(onClick = { onSendToContact(contact) }) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "שלח",
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("סגור")
            }
        }
    )
}

@Composable
fun PermissionOverlay(
    onPermissionsGranted: () -> Unit
) {
    var requesting by remember { mutableStateOf(false) }

    // Welcome-style screen: soft gradient, oversized headline, one pill CTA at the bottom.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    )
                )
            )
            .padding(horizontal = 28.dp, vertical = 24.dp)
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "ברוכים הבאים ל",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 52.sp, lineHeight = 58.sp),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "על מנת לזהות חנייה אוטומטית ברקע ולשמור את מיקום הרכב, האפליקציה זקוקה להרשאות מיקום (כולל ברקע), בלוטות' והתראות.",
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 27.sp),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.weight(1.4f))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (requesting) {
                CircularProgressIndicator()
            } else {
                PrimaryPillButton(
                    text = "המשך להגדרת הרשאות",
                    onClick = {
                        requesting = true
                        // Permission requests themselves are triggered inside AppNavigation.
                        onPermissionsGranted()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
