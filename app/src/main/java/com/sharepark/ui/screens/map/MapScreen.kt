package com.sharepark.ui.screens.map

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharepark.R
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
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
import com.sharepark.domain.model.Vehicle
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MapScreen(
    onFullScreenChange: (Boolean) -> Unit = {},
    viewModel: MapViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val cars by viewModel.cars.collectAsState()
    val trustedContacts by viewModel.trustedContacts.collectAsState()

    var permissionsGranted by remember {
        mutableStateOf(PermissionManager.hasAllRequiredPermissions(context))
    }
    // The car whose parking the share dialogs are about — the one on screen when Share was tapped.
    var shareOptionsFor by remember { mutableStateOf<Long?>(null) }
    var familyShareFor by remember { mutableStateOf<Long?>(null) }
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

    shareOptionsFor?.let { vehicleId ->
        ShareOptionsDialog(
            onGeneralShare = {
                shareOptionsFor = null
                viewModel.shareParkingLocation(vehicleId)
            },
            onFamilyShare = {
                shareOptionsFor = null
                familyShareFor = vehicleId
            },
            onDismiss = { shareOptionsFor = null }
        )
    }

    familyShareFor?.let { vehicleId ->
        FamilyShareDialog(
            contacts = trustedContacts,
            onSendToContact = { contact ->
                val message = viewModel.buildShareText(vehicleId)
                if (message != null) {
                    val url = WhatsAppLinkBuilder.buildUrl(contact.phoneNumber, message)
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, "WhatsApp אינו מותקן במכשיר", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { familyShareFor = null }
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
            cars?.let { state ->
                MapContent(
                    cars = state,
                    isFullScreen = isFullScreen,
                    onToggleFullScreen = { isFullScreen = !isFullScreen },
                    onSelectVehicle = viewModel::selectVehicle,
                    onShare = { vehicleId -> shareOptionsFor = vehicleId },
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

@SuppressLint("MissingPermission")
@Composable
fun MapContent(
    cars: MapCars,
    isFullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
    onSelectVehicle: (Long) -> Unit,
    onShare: (Long) -> Unit,
    onNavigate: (Double, Double) -> Unit
) {
    if (cars.parkings.isEmpty()) {
        EmptyMapState(activeVehicleName = cars.activeVehicle?.name)
        return
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val vehicles = cars.vehicles
    val pagerState = rememberPagerState(initialPage = cars.activeIndex) { vehicles.size }
    val selectedIndex = pagerState.settledPage.coerceIn(0, vehicles.lastIndex)
    val selectedVehicle = vehicles[selectedIndex]
    val selectedParking = cars.parkings[selectedVehicle.id]

    // Paging and the active car follow each other: swiping to a car makes it active, and a car
    // made active elsewhere (Vehicles tab, a parking notification) scrolls the pager to it.
    // While a swipe's selection is still on its way to the database, the older active car it
    // reports mustn't drag the pager back.
    var pendingSelection by remember { mutableStateOf<Long?>(null) }
    val activeId = cars.activeVehicle?.id
    val currentVehicles by rememberUpdatedState(vehicles)
    val currentActiveId by rememberUpdatedState(activeId)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val id = currentVehicles.getOrNull(page)?.id ?: return@collect
            if (id != currentActiveId) {
                pendingSelection = id
                onSelectVehicle(id)
            }
        }
    }
    LaunchedEffect(activeId, cars.activeIndex) {
        val pending = pendingSelection
        if (pending != null && pending != activeId) return@LaunchedEffect
        pendingSelection = null
        if (pagerState.settledPage != cars.activeIndex) pagerState.animateScrollToPage(cars.activeIndex)
    }

    val cameraPositionState = rememberCameraPositionState {
        val focus = selectedParking ?: cars.parkings.values.first()
        position = CameraPosition.fromLatLngZoom(LatLng(focus.latitude, focus.longitude), 16f)
    }
    // Glide to the car that was swiped to (or to its new spot when it's parked again).
    LaunchedEffect(selectedParking?.latitude, selectedParking?.longitude) {
        val parking = selectedParking ?: return@LaunchedEffect
        val target = LatLng(parking.latitude, parking.longitude)
        if (cameraPositionState.position.target == target) return@LaunchedEffect
        try {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target, 16f))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(target, 16f)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = true),
            uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false),
            onMapClick = { if (!isFullScreen) onToggleFullScreen() }
        ) {
            // Every parked car gets a pin; the one in the sheet stands out, tapping another pages to it.
            vehicles.forEachIndexed { index, vehicle ->
                val parking = cars.parkings[vehicle.id] ?: return@forEachIndexed
                key(parking.id) {
                    val isSelected = index == selectedIndex
                    Marker(
                        state = remember { MarkerState(position = LatLng(parking.latitude, parking.longitude)) },
                        title = vehicle.name,
                        snippet = parking.address ?: "",
                        alpha = if (isSelected) 1f else UNSELECTED_MARKER_ALPHA,
                        zIndex = if (isSelected) 1f else 0f,
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            false
                        }
                    )
                }
            }
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

            if (selectedParking != null) {
                CircleIconButton(
                    icon = Icons.Default.DirectionsCar,
                    contentDescription = "מיקום הרכב",
                    onClick = {
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(
                            LatLng(selectedParking.latitude, selectedParking.longitude),
                            16f
                        )
                    },
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Bottom sheet with the parking details. Swiping it down hands the whole screen to
        // the map; the peek bar left at the bottom brings it back (swipe up or tap).
        // Swiping it sideways pages between the cars.
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
                vehicles = vehicles,
                parkings = cars.parkings,
                pagerState = pagerState,
                onShare = onShare,
                onNavigate = onNavigate,
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
                vehicleName = selectedVehicle.name,
                address = selectedParking?.address,
                pageCount = vehicles.size,
                currentPage = pagerState.currentPage,
                onExpand = onToggleFullScreen,
                onSwipe = { step ->
                    val target = (pagerState.currentPage + step).coerceIn(0, vehicles.lastIndex)
                    coroutineScope.launch { pagerState.animateScrollToPage(target) }
                }
            )
        }
    }
}

/** Nothing parked yet — soft gradient canvas with a bold, left-weighted message. */
@Composable
private fun EmptyMapState(activeVehicleName: String?) {
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

/** What's left of the parking sheet while the map is full screen. */
@Composable
private fun CollapsedSheetBar(
    vehicleName: String,
    address: String?,
    pageCount: Int,
    currentPage: Int,
    onExpand: () -> Unit,
    onSwipe: (step: Int) -> Unit
) {
    var dragged by remember { mutableFloatStateOf(0f) }
    var draggedSideways by remember { mutableFloatStateOf(0f) }
    // The pager runs with the layout: in Hebrew the next car comes in from the left.
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
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
            )
            .draggable(
                orientation = Orientation.Horizontal,
                enabled = pageCount > 1,
                state = rememberDraggableState { delta -> draggedSideways += delta },
                onDragStarted = { draggedSideways = 0f },
                onDragStopped = {
                    if (abs(draggedSideways) > PAGE_DRAG_PX) {
                        val leftwards = draggedSideways < 0
                        onSwipe(if (leftwards != isRtl) 1 else -1)
                    }
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
                    Text(
                        text = address ?: "אין חנייה שמורה",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (pageCount > 1) {
                    PageDots(
                        pageCount = pageCount,
                        currentPage = currentPage,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
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
    vehicles: List<Vehicle>,
    parkings: Map<Long, ParkingRecord>,
    pagerState: PagerState,
    onShare: (Long) -> Unit,
    onNavigate: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp
    ) {
        Column(modifier = Modifier.padding(top = 10.dp, bottom = 20.dp)) {
            SheetHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
            if (vehicles.size > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                PageDots(
                    pageCount = vehicles.size,
                    currentPage = pagerState.currentPage,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            HorizontalPager(
                state = pagerState,
                key = { vehicles[it].id },
                verticalAlignment = Alignment.Top
            ) { page ->
                val vehicle = vehicles[page]
                val parking = parkings[vehicle.id]
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = vehicle.name,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (parking != null) {
                        ParkingDetails(
                            parking = parking,
                            parkedBy = parkedByLabel(parking, vehicle),
                            onShare = { onShare(vehicle.id) },
                            onNavigate = { onNavigate(parking.latitude, parking.longitude) }
                        )
                    } else {
                        Text(
                            text = "אין חנייה שמורה לרכב הזה. המיקום יישמר אוטומטית בחנייה הבאה.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParkingDetails(
    parking: ParkingRecord,
    parkedBy: String?,
    onShare: () -> Unit,
    onNavigate: () -> Unit
) {
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
        parkedBy?.let {
            StatDivider()
            StatItem(
                value = it,
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

/**
 * Who parked, worth saying only on a shared car. This phone's own parkings carry no name
 * (see SharedParkingSync), so on a shared car a missing name means it was me.
 */
fun parkedByLabel(parking: ParkingRecord, vehicle: Vehicle): String? =
    parking.parkedByName ?: if (vehicle.isShared) "אני" else null

/** One dot per car; the one on screen is filled in. */
@Composable
private fun PageDots(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(pageCount) { page ->
            val color by animateColorAsState(
                targetValue = if (page == currentPage) MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.outline,
                label = "pageDot"
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(color, CircleShape)
            )
        }
    }
}

// Sheet gestures: past this share of its height (or flung down) the sheet collapses.
private const val COLLAPSE_FRACTION = 0.3f
private const val FLING_VELOCITY = 1200f
private const val EXPAND_DRAG_PX = 40f
private const val PAGE_DRAG_PX = 60f

// Parked cars other than the one in the sheet stay on the map, just quieter.
private const val UNSELECTED_MARKER_ALPHA = 0.55f

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
