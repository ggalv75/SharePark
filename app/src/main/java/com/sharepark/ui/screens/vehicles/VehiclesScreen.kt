package com.sharepark.ui.screens.vehicles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sharepark.domain.model.Vehicle
import com.sharepark.ui.components.AppCard
import com.sharepark.ui.components.IconBadge
import com.sharepark.ui.components.PrimaryPillButton
import com.sharepark.ui.components.ScreenHeader
import com.sharepark.ui.components.rememberBreathingScale
import com.sharepark.ui.components.simpleVerticalScrollbar

@Composable
fun VehiclesScreen(
    onNavigateToAddVehicle: () -> Unit,
    viewModel: VehiclesViewModel = hiltViewModel()
) {
    val vehicles by viewModel.vehicles.collectAsState()
    val activeVehicle by viewModel.activeVehicle.collectAsState()
    val sharingState by viewModel.sharingState.collectAsState()
    val context = LocalContext.current
    var vehicleBeingRenamed by remember { mutableStateOf<Vehicle?>(null) }
    var sharedVehicleOptions by remember { mutableStateOf<Vehicle?>(null) }

    SharingDialogs(
        state = sharingState,
        onDismiss = viewModel::dismissSharing,
        onSignIn = { viewModel.signIn(context) },
        onJoin = viewModel::joinWithCode,
        onCompleteJoin = viewModel::completeJoin
    )

    sharedVehicleOptions?.let { vehicle ->
        SharedVehicleOptionsDialog(
            vehicle = vehicle,
            onInvite = {
                sharedVehicleOptions = null
                viewModel.shareVehicle(vehicle)
            },
            onStopSharing = {
                sharedVehicleOptions = null
                viewModel.stopSharing(vehicle)
            },
            onDismiss = { sharedVehicleOptions = null }
        )
    }

    vehicleBeingRenamed?.let { vehicle ->
        RenameVehicleDialog(
            currentName = vehicle.name,
            onConfirm = { newName ->
                viewModel.renameVehicle(vehicle.id, newName)
                vehicleBeingRenamed = null
            },
            onDismiss = { vehicleBeingRenamed = null }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenHeader(
                title = "הרכבים שלי",
                actions = {
                    if (viewModel.isSharingAvailable) {
                        IconButton(onClick = viewModel::startJoin) {
                            Icon(Icons.Default.GroupAdd, contentDescription = "הצטרפות לרכב משותף")
                        }
                    }
                    IconButton(onClick = onNavigateToAddVehicle) {
                        Icon(Icons.Default.Add, contentDescription = "הוסף רכב")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (vehicles.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    val breathingScale = rememberBreathingScale()
                    IconBadge(
                        icon = Icons.Default.Bluetooth,
                        tint = MaterialTheme.colorScheme.primary,
                        size = 64.dp,
                        iconSize = 30.dp,
                        modifier = Modifier
                            .padding(bottom = 20.dp)
                            .scale(breathingScale)
                    )
                    Text(
                        text = "עוד אין רכבים",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "הוסף את הרכב שלך ולמד אותו לזהות את הבלוטות' של המכונית.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    PrimaryPillButton(
                        text = "הוסף רכב",
                        icon = Icons.Default.Add,
                        onClick = onNavigateToAddVehicle,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
                    items(vehicles) { vehicle ->
                        val isActive = activeVehicle?.id == vehicle.id
                        VehicleCard(
                            vehicle = vehicle,
                            isActive = isActive,
                            onSelect = { viewModel.setActiveVehicle(vehicle.id) },
                            onEdit = { vehicleBeingRenamed = vehicle },
                            onDelete = { viewModel.deleteVehicle(vehicle) },
                            onShare = if (viewModel.isSharingAvailable) {
                                {
                                    if (vehicle.isShared) sharedVehicleOptions = vehicle
                                    else viewModel.shareVehicle(vehicle)
                                }
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VehicleCard(
    vehicle: Vehicle,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: (() -> Unit)? = null
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    AppCard(
        onClick = onSelect,
        contentPadding = PaddingValues(start = 18.dp, end = 8.dp, top = 16.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = Icons.Default.DirectionsCar,
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(end = 14.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = vehicle.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (vehicle.isShared) Icons.Default.People else Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = muted,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when {
                            vehicle.isViewOnly -> "משותף · צפייה בלבד"
                            vehicle.isShared -> "${vehicle.btName} · משותף"
                            else -> vehicle.btName
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted
                    )
                }
            }

            if (isActive) {
                Text(
                    text = "פעיל",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Quiet action row under the title — icons only, aligned to the end
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            if (onShare != null) {
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = if (vehicle.isShared) Icons.Default.People else Icons.Default.Share,
                        contentDescription = if (vehicle.isShared) "אפשרויות שיתוף" else "שתף רכב",
                        tint = if (vehicle.isShared) MaterialTheme.colorScheme.primary else muted
                    )
                }
            }

            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "ערוך כינוי",
                    tint = muted
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "מחק",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun RenameVehicleDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("עריכת כינוי לרכב") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) {
                Text("שמור")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ביטול")
            }
        }
    )
}
