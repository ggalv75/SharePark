package com.sharepark.ui.screens.vehicles

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sharepark.ui.components.CircleIconButton
import com.sharepark.ui.components.ScreenHeader
import com.sharepark.ui.components.pressScale
import com.sharepark.ui.components.simpleVerticalScrollbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVehicleScreen(
    onNavigateBack: () -> Unit,
    viewModel: VehiclesViewModel = hiltViewModel()
) {
    var nickname by remember { mutableStateOf("") }
    var selectedDevice by remember { mutableStateOf<VehiclesViewModel.BondedDevice?>(null) }
    
    val bondedDevices by viewModel.bondedDevices.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadBondedDevices()
    }

    Scaffold(
        topBar = {
            ScreenHeader(
                title = "הוסף רכב חדש",
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
                .padding(20.dp)
        ) {
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("כינוי לרכב (לדוגמה: יאריס שלי)") },
                leadingIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "בחר את התקן הבלוטות' של הרכב:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "האפליקציה תעקוב אחרי ניתוק מהתקן זה כדי לזהות חנייה אוטומטית.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (bondedDevices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "לא נמצאו התקני בלוטות' מוצמדים (Paired).\nאנא ודא שהרכב מוצמד בהגדרות הטלפון.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .simpleVerticalScrollbar(listState, MaterialTheme.colorScheme.onBackground)
                ) {
                    items(bondedDevices) { device ->
                        val isSelected = selectedDevice?.address == device.address
                        DeviceSelectionCard(
                            device = device,
                            isSelected = isSelected,
                            onSelect = { selectedDevice = device }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val saveInteractionSource = remember { MutableInteractionSource() }
            Button(
                onClick = {
                    selectedDevice?.let { device ->
                        viewModel.addVehicle(
                            name = nickname.ifEmpty { device.name },
                            btAddress = device.address,
                            btName = device.name
                        )
                        onNavigateBack()
                    }
                },
                enabled = selectedDevice != null,
                interactionSource = saveInteractionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .pressScale(saveInteractionSource),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "שמור רכב",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun DeviceSelectionCard(
    device: VehiclesViewModel.BondedDevice,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.secondary else Color.Transparent,
                shape = MaterialTheme.shapes.small
            )
            .clickable { onSelect() },
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surface 
                             else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
