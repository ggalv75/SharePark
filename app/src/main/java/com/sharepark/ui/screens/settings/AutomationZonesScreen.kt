package com.sharepark.ui.screens.settings

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.sharepark.domain.model.AutomationZone
import com.sharepark.platform.location.LocationHelper
import com.sharepark.platform.permissions.PermissionManager
import com.sharepark.ui.components.CircleIconButton
import com.sharepark.ui.components.IconBadge
import com.sharepark.ui.components.ScreenHeader
import kotlin.math.cos
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun AutomationZonesScreen(
    onNavigateBack: () -> Unit,
    viewModel: AutomationZonesViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val zones by viewModel.zones.collectAsState()
    val config by viewModel.config.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchError by viewModel.searchError.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var zonePendingDeletion by remember { mutableStateOf<AutomationZone?>(null) }
    var isMapFullScreen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    if (isMapFullScreen) {
        BackHandler { isMapFullScreen = false }
    }

    // Placing and moving the pin work the same whichever size the map is showing at.
    fun placeOrMoveDraft(latLng: LatLng) {
        if (draft == null) {
            viewModel.startDraftAt(latLng.latitude, latLng.longitude)
        } else {
            viewModel.moveDraftTo(latLng.latitude, latLng.longitude)
        }
    }
    val hasLocationPermission = remember {
        PermissionManager.hasPermissions(context, PermissionManager.getRequiredPermissions())
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DEFAULT_CENTER, 11f)
    }

    // Open on something meaningful: the first zone, or where the user is standing right now.
    var cameraInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(zones.isNotEmpty()) {
        if (cameraInitialized) return@LaunchedEffect
        val firstZone = zones.firstOrNull()
        if (firstZone != null) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(firstZone.latitude, firstZone.longitude),
                zoomForRadius(firstZone.radiusMeters)
            )
            cameraInitialized = true
        } else if (hasLocationPermission) {
            LocationHelper.getCurrentLocation(context)?.let { location ->
                cameraPositionState.position = CameraPosition.fromLatLngZoom(
                    LatLng(location.latitude, location.longitude),
                    15f
                )
                cameraInitialized = true
            }
        }
    }

    // The editor is inserted above the list, and LazyColumn keeps its existing item anchored —
    // which leaves the card the user just summoned scrolled off the top. Bring it into view.
    LaunchedEffect(draft?.id, draft != null) {
        if (draft != null) listState.animateScrollToItem(0)
    }

    // Keep the whole circle framed while it's being edited — including as the radius grows,
    // otherwise widening it just pushes the edge off-screen and the map stops showing you
    // the very thing you're setting.
    val cameraPaddingPx = with(LocalDensity.current) { CAMERA_PADDING.roundToPx() }
    LaunchedEffect(draft?.latitude, draft?.longitude, draft?.radiusMeters) {
        val current = draft ?: return@LaunchedEffect
        val bounds = boundsAround(current.latitude, current.longitude, current.radiusMeters)
        // newLatLngBounds needs a laid-out map; fall back to a plain zoom if it isn't ready.
        runCatching {
            cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, cameraPaddingPx))
        }.onFailure {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(current.latitude, current.longitude),
                zoomForRadius(current.radiusMeters)
            )
        }
    }

    zonePendingDeletion?.let { zone ->
        AlertDialog(
            onDismissRequest = { zonePendingDeletion = null },
            title = { Text("מחיקת אזור") },
            text = { Text("למחוק את האזור \"${zone.label}\"? האוטומציה לא תופעל יותר מהמקום הזה.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteZone(zone.id)
                        zonePendingDeletion = null
                    }
                ) {
                    Text("מחק", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { zonePendingDeletion = null }) { Text("ביטול") }
            }
        )
    }

    if (isMapFullScreen) {
        FullScreenZoneMap(
            cameraPositionState = cameraPositionState,
            zones = zones,
            draft = draft,
            hasLocationPermission = hasLocationPermission,
            onMapClick = ::placeOrMoveDraft,
            onRadiusChange = viewModel::updateDraftRadius,
            onSave = viewModel::saveDraft,
            onCancelDraft = viewModel::cancelDraft,
            onExitFullScreen = { isMapFullScreen = false }
        )
        return
    }

    Scaffold(
        topBar = {
            ScreenHeader(
                title = "אזורי אוטומציה",
                navigationIcon = {
                    CircleIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "חזור",
                        onClick = onNavigateBack,
                        size = 44.dp
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Address search ──────────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("חפשו כתובת") },
                    placeholder = { Text("לדוגמה: הרצל 12, תל אביב") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                keyboardController?.hide()
                                viewModel.searchAddress(searchQuery)
                            }
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "חפש")
                        }
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = {
                            keyboardController?.hide()
                            viewModel.searchAddress(searchQuery)
                        }
                    )
                )

                if (isSearching) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("מחפש...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                searchError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                searchResults.forEach { place ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.startDraftFromPlace(place)
                                searchQuery = ""
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = place.address,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Map ─────────────────────────────────────────────────────────
            // A fixed-height map leaves the editor below the fold on shorter screens, so while
            // a zone is being edited the map gives up height to the controls that need it.
            val mapHeight by animateDpAsState(
                targetValue = if (draft != null) MAP_HEIGHT_EDITING else MAP_HEIGHT_BROWSING,
                label = "mapHeight"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(mapHeight)
            ) {
                ZonesMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    zones = zones,
                    draft = draft,
                    hasLocationPermission = hasLocationPermission,
                    onMapClick = ::placeOrMoveDraft
                )

                MapHintChip(
                    isEditing = draft != null,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                FloatingActionButton(
                    shape = CircleShape,
                    onClick = { isMapFullScreen = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Icon(Icons.Default.Fullscreen, contentDescription = "מסך מלא")
                }
            }

            // ── Draft editor + saved zone list ──────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                draft?.let { current ->
                    item(key = "draft") {
                        ZoneEditorCard(
                            draft = current,
                            onLabelChange = viewModel::updateDraftLabel,
                            onRadiusChange = viewModel::updateDraftRadius,
                            onSave = {
                                keyboardController?.hide()
                                viewModel.saveDraft()
                            },
                            onCancel = viewModel::cancelDraft
                        )
                    }
                }

                item(key = "zones_only") {
                    ZonesOnlySwitchCard(
                        zonesOnly = config.zonesOnly,
                        zoneCount = zones.count { it.isEnabled },
                        onToggle = viewModel::setZonesOnly
                    )
                }

                if (zones.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = "עדיין לא הוגדרו אזורים. חפשו כתובת או הקישו על המפה כדי להוסיף את הראשון.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    items(zones, key = { it.id }) { zone ->
                        ZoneRow(
                            zone = zone,
                            isEditing = draft?.id == zone.id,
                            onClick = { viewModel.editZone(zone) },
                            onToggle = { enabled -> viewModel.setZoneEnabled(zone.id, enabled) },
                            onDelete = { zonePendingDeletion = zone }
                        )
                    }
                }
            }
        }
    }
}

/** The map itself — identical content whether it's the inline strip or the full screen. */
@SuppressLint("MissingPermission")
@Composable
private fun ZonesMap(
    modifier: Modifier,
    cameraPositionState: com.google.maps.android.compose.CameraPositionState,
    zones: List<AutomationZone>,
    draft: AutomationZonesViewModel.ZoneDraft?,
    hasLocationPermission: Boolean,
    onMapClick: (LatLng) -> Unit
) {
    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
        uiSettings = MapUiSettings(myLocationButtonEnabled = false),
        onMapClick = onMapClick
    ) {
        // Saved zones — the one being edited is drawn from the draft instead, so skip it here.
        zones.filter { it.id != draft?.id }.forEach { zone ->
            val center = LatLng(zone.latitude, zone.longitude)
            val tint = if (zone.isEnabled) ZONE_ACTIVE_COLOR else ZONE_INACTIVE_COLOR
            Circle(
                center = center,
                radius = zone.radiusMeters.toDouble(),
                fillColor = tint.copy(alpha = 0.15f),
                strokeColor = tint,
                strokeWidth = 4f
            )
            Marker(
                state = MarkerState(position = center),
                title = zone.label,
                snippet = "${zone.radiusMeters} מ'"
            )
        }

        draft?.let { current ->
            val center = LatLng(current.latitude, current.longitude)
            Circle(
                center = center,
                radius = current.radiusMeters.toDouble(),
                fillColor = ZONE_DRAFT_COLOR.copy(alpha = 0.2f),
                strokeColor = ZONE_DRAFT_COLOR,
                strokeWidth = 6f
            )
            Marker(
                state = MarkerState(position = center),
                title = current.label.ifBlank { "אזור חדש" }
            )
        }
    }
}

@Composable
private fun MapHintChip(isEditing: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = if (isEditing) {
            "הקישו על המפה כדי להזיז את הנקודה"
        } else {
            "הקישו על המפה כדי לסמן כתובת"
        },
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .padding(12.dp)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                MaterialTheme.shapes.small
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/**
 * The map on its own, for picking a spot precisely. The radius stays adjustable here — sizing
 * a circle is exactly the task you'd go full screen for, so sending the user back to the small
 * map to move the slider would defeat the point.
 */
@Composable
private fun FullScreenZoneMap(
    cameraPositionState: com.google.maps.android.compose.CameraPositionState,
    zones: List<AutomationZone>,
    draft: AutomationZonesViewModel.ZoneDraft?,
    hasLocationPermission: Boolean,
    onMapClick: (LatLng) -> Unit,
    onRadiusChange: (Int) -> Unit,
    onSave: () -> Unit,
    onCancelDraft: () -> Unit,
    onExitFullScreen: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        ZonesMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            zones = zones,
            draft = draft,
            hasLocationPermission = hasLocationPermission,
            onMapClick = onMapClick
        )

        IconButton(
            onClick = onExitFullScreen,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    androidx.compose.foundation.shape.CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.FullscreenExit,
                contentDescription = "צא ממסך מלא",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        if (draft == null) {
            MapHintChip(
                isEditing = false,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        } else {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = draft.label.ifBlank { "אזור חדש" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Text(
                            text = "${draft.radiusMeters} מטר",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = draft.radiusMeters.toFloat(),
                        onValueChange = { value ->
                            onRadiusChange((value / RADIUS_STEP_METERS).roundToInt() * RADIUS_STEP_METERS)
                        },
                        valueRange = AutomationZone.MIN_RADIUS_METERS.toFloat()..AutomationZone.MAX_RADIUS_METERS.toFloat(),
                        steps = (AutomationZone.MAX_RADIUS_METERS - AutomationZone.MIN_RADIUS_METERS) / RADIUS_STEP_METERS - 1
                    )

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                onSave()
                                onExitFullScreen()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = if (draft.isNew) "שמור אזור" else "עדכן אזור",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        TextButton(onClick = onCancelDraft, modifier = Modifier.weight(1f)) {
                            Text("ביטול")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneEditorCard(
    draft: AutomationZonesViewModel.ZoneDraft,
    onLabelChange: (String) -> Unit,
    onRadiusChange: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title and address share a row — the address is the subtitle of what's being edited,
            // and stacking them cost a line the editor can't spare next to the map.
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon = Icons.Default.Add,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 36.dp,
                    iconSize = 20.dp,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (draft.isNew) "אזור חדש" else "עריכת אזור",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (draft.isResolvingAddress) {
                            "מאתר כתובת..."
                        } else {
                            draft.address.ifBlank { "כתובת לא זוהתה" }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 2
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = draft.label,
                onValueChange = onLabelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("שם האזור") },
                placeholder = { Text("בית / עבודה / הורים") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "רדיוס",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${draft.radiusMeters} מטר",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Slider(
                value = draft.radiusMeters.toFloat(),
                onValueChange = { value ->
                    // Snap to 25 m so the number stays something a person would actually pick.
                    onRadiusChange((value / RADIUS_STEP_METERS).roundToInt() * RADIUS_STEP_METERS)
                },
                valueRange = AutomationZone.MIN_RADIUS_METERS.toFloat()..AutomationZone.MAX_RADIUS_METERS.toFloat(),
                steps = (AutomationZone.MAX_RADIUS_METERS - AutomationZone.MIN_RADIUS_METERS) / RADIUS_STEP_METERS - 1
            )

            Text(
                text = "רדיוס גדול סלחני יותר לסטיות GPS, אך עלול לתפוס חניות בסביבה.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (draft.isNew) "שמור אזור" else "עדכן אזור", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("ביטול")
                }
            }
        }
    }
}

@Composable
private fun ZonesOnlySwitchCard(
    zonesOnly: Boolean,
    zoneCount: Int,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon = Icons.Default.MyLocation,
                    tint = MaterialTheme.colorScheme.tertiary,
                    size = 36.dp,
                    iconSize = 20.dp,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "שלח רק מתוך האזורים",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            !zonesOnly -> "כרגע ההודעה נשלחת בכל חנייה, בכל מקום."
                            zoneCount == 0 -> "אין אזורים פעילים — עד שיוגדר אזור, ההודעה תישלח בכל חנייה."
                            zoneCount == 1 -> "ההודעה תישלח רק כשהרכב חונה בתוך האזור הפעיל."
                            else -> "ההודעה תישלח רק כשהרכב חונה בתוך אחד מ-$zoneCount האזורים הפעילים."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Switch(checked = zonesOnly, onCheckedChange = onToggle)
            }
        }
    }
}

@Composable
private fun ZoneRow(
    zone: AutomationZone,
    isEditing: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isEditing) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = Icons.Default.LocationOn,
                tint = if (zone.isEnabled) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                size = 40.dp,
                iconSize = 20.dp,
                modifier = Modifier.padding(end = 12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = zone.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = zone.address.ifBlank { "${zone.latitude}, ${zone.longitude}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = "רדיוס ${zone.radiusMeters} מטר",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Switch(checked = zone.isEnabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "מחק אזור",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * The square that just contains a circle of this radius, so the camera can frame it exactly
 * whatever the map's current height is.
 */
private fun boundsAround(latitude: Double, longitude: Double, radiusMeters: Int): LatLngBounds {
    val latDelta = radiusMeters / METERS_PER_DEGREE_LAT
    // Lines of longitude converge toward the poles, so a metre is worth more degrees up north.
    val lngDelta = radiusMeters / (METERS_PER_DEGREE_LAT * cos(Math.toRadians(latitude))).coerceAtLeast(1.0)
    return LatLngBounds(
        LatLng(latitude - latDelta, longitude - lngDelta),
        LatLng(latitude + latDelta, longitude + lngDelta)
    )
}

/** Fallback framing for the moments before the map reports its size. */
private fun zoomForRadius(radiusMeters: Int): Float = when {
    radiusMeters <= 100 -> 16f
    radiusMeters <= 200 -> 15.5f
    radiusMeters <= 300 -> 15f
    else -> 14.5f
}

private const val METERS_PER_DEGREE_LAT = 111_320.0
private val CAMERA_PADDING = 32.dp

private const val RADIUS_STEP_METERS = 25

private val MAP_HEIGHT_BROWSING = 280.dp
private val MAP_HEIGHT_EDITING = 180.dp

// Tel Aviv — only ever shown for the split second before the real camera target resolves.
private val DEFAULT_CENTER = LatLng(32.0853, 34.7818)
private val ZONE_ACTIVE_COLOR = Color(0xFF2E7D32)
private val ZONE_INACTIVE_COLOR = Color(0xFF9E9E9E)
private val ZONE_DRAFT_COLOR = Color(0xFF1565C0)
