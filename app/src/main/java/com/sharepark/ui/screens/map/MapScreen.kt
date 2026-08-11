package com.sharepark.ui.screens.map

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.sharepark.R
import com.sharepark.domain.model.ParkingRecord
import com.sharepark.domain.model.TrustedContact
import com.sharepark.domain.usecase.WhatsAppLinkBuilder
import com.sharepark.platform.location.LocationHelper
import com.sharepark.platform.permissions.PermissionManager
import com.sharepark.ui.components.IconBadge
import com.sharepark.ui.components.pressScale
import com.sharepark.ui.components.rememberBreathingScale
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            if (!isFullScreen) {
                TopAppBar(
                    title = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 16.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = stringResource(R.string.app_name),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                uiSettings = MapUiSettings(myLocationButtonEnabled = false),
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
                IconButton(
                    onClick = onToggleFullScreen,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "צא ממסך מלא",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Floating action buttons — full screen / re-center on my location / on the car
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                // Tapping the map also expands it, but that's invisible until you try it.
                if (!isFullScreen) {
                    FloatingActionButton(
                        onClick = onToggleFullScreen,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "מסך מלא")
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                FloatingActionButton(
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
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "המיקום שלי")
                }

                Spacer(modifier = Modifier.height(12.dp))

                FloatingActionButton(
                    onClick = {
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(position, 16f)
                    },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = "מיקום הרכב")
                }
            }

            // Bottom detail card
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(
                                icon = Icons.Default.DirectionsCar,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Column {
                                Text(
                                    text = activeVehicleName ?: "הרכב שלי",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                val timeString = DateUtils.getRelativeTimeSpanString(
                                    parking.parkedAt,
                                    System.currentTimeMillis(),
                                    DateUtils.MINUTE_IN_MILLIS
                                ).toString()

                                Text(
                                    text = "חנה: $timeString",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = parking.address ?: "מעבד מיקום...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            val shareInteractionSource = remember { MutableInteractionSource() }
                            Button(
                                onClick = onShare,
                                interactionSource = shareInteractionSource,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .pressScale(shareInteractionSource),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "שתף")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("שתף", fontWeight = FontWeight.Bold, maxLines = 1)
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            val navigateInteractionSource = remember { MutableInteractionSource() }
                            Button(
                                onClick = { onNavigate(parking.latitude, parking.longitude) },
                                interactionSource = navigateInteractionSource,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .pressScale(navigateInteractionSource),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Icon(Icons.Default.Directions, contentDescription = "ניווט")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ניווט", fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        } else {
            // Empty State
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val breathingScale = rememberBreathingScale()
                    IconBadge(
                        icon = Icons.Default.Map,
                        tint = MaterialTheme.colorScheme.primary,
                        size = 72.dp,
                        iconSize = 34.dp,
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .scale(breathingScale)
                    )
                    Text(
                        text = if (activeVehicleName == null) "אין רכב פעיל רשום. פנה לכרטיסיית 'רכבים' והוסף רכב."
                               else "אין מידע חנייה פעיל עבור $activeVehicleName. המיקום יישמר אוטומטית בעת ניתוק הבלוטות'.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
            }
        }
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
    val context = LocalContext.current
    var requesting by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "נדרשות הרשאות מערכת",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "על מנת לזהות חנייה אוטומטית ברקע ולשמור את מיקום הרכב, האפליקציה זקוקה להרשאות מיקום (כולל ברקע), בלוטות' והתראות.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            if (requesting) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        requesting = true
                        // A callback to prompt permissions in MainActivity can be linked here.
                        // For simplicity, we trigger permission requests inside AppNavigation.
                        onPermissionsGranted()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("המשך להגדרת הרשאות", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
