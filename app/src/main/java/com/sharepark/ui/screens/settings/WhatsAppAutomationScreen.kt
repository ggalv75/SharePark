package com.sharepark.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sharepark.domain.model.Vehicle
import com.sharepark.platform.automation.AutomationStatusStore
import com.sharepark.platform.automation.WhatsAppAutoSendService
import com.sharepark.ui.components.IconBadge
import com.sharepark.ui.components.simpleVerticalScrollbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppAutomationScreen(
    onNavigateBack: () -> Unit,
    onNavigateToZones: () -> Unit = {},
    viewModel: WhatsAppAutomationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val config by viewModel.config.collectAsState()
    val vehicles by viewModel.vehicles.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val rules by viewModel.rules.collectAsState()
    val zones by viewModel.zones.collectAsState()
    val status by viewModel.status.collectAsState()
    val scrollState = rememberScrollState()

    // Re-check the accessibility grant whenever the user returns from system settings.
    var accessibilityEnabled by remember {
        mutableStateOf(WhatsAppAutoSendService.isEnabled(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityEnabled = WhatsAppAutoSendService.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Which vehicle we're currently choosing a target for.
    var editingVehicle by remember { mutableStateOf<Vehicle?>(null) }

    editingVehicle?.let { vehicle ->
        TargetPickerDialog(
            vehicle = vehicle,
            contacts = contacts,
            onPickContact = { contact ->
                viewModel.setContactTarget(vehicle.id, contact)
                editingVehicle = null
            },
            onClear = {
                viewModel.clearTarget(vehicle.id)
                editingVehicle = null
            },
            onDismiss = { editingVehicle = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("אוטומציית WhatsApp", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "חזור",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .simpleVerticalScrollbar(scrollState, MaterialTheme.colorScheme.onBackground)
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            // Master toggle
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(
                        icon = Icons.Default.Send,
                        tint = MaterialTheme.colorScheme.tertiary,
                        size = 36.dp,
                        iconSize = 20.dp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "שליחה אוטומטית בעת חנייה",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "לכל רכב אפשר להגדיר יעד משלו — ההודעה תישלח מעצמה כשהרכב חונה.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Switch(
                        checked = config.enabled,
                        onCheckedChange = { viewModel.setEnabled(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Accessibility service status / enable button
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(
                        icon = Icons.Default.Accessibility,
                        tint = if (accessibilityEnabled) MaterialTheme.colorScheme.tertiary
                               else MaterialTheme.colorScheme.error,
                        size = 36.dp,
                        iconSize = 20.dp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (accessibilityEnabled) "שירות הנגישות פעיל ✓"
                                   else "שירות הנגישות כבוי",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "האוטומציה מבוצעת דרך שירות נגישות שמאושר על ידכם ולוחץ על 'שלח' ב-WhatsApp במקומכם. הוא פועל אך ורק בתוך WhatsApp.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                if (!accessibilityEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("פתח הגדרות נגישות", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Per-vehicle targets
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(
                        icon = Icons.Default.DirectionsCar,
                        tint = MaterialTheme.colorScheme.secondary,
                        size = 36.dp,
                        iconSize = 20.dp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = "יעד לכל רכב",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "הקישו על רכב כדי לבחור למי תישלח ההודעה שלו",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (vehicles.isEmpty()) {
                    Text(
                        text = "לא הוגדרו רכבים עדיין.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                } else {
                    vehicles.forEach { vehicle ->
                        val rule = rules[vehicle.id]
                        VehicleTargetRow(
                            vehicleName = vehicle.name,
                            targetLabel = rule?.takeIf { it.isConfigured }?.targetLabel,
                            canTest = rule?.isConfigured == true && accessibilityEnabled,
                            onClick = { editingVehicle = vehicle },
                            onTest = { viewModel.sendTestMessage(vehicle.id) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Where automation is allowed to fire
            SectionCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToZones() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(
                        icon = Icons.Default.LocationOn,
                        tint = MaterialTheme.colorScheme.primary,
                        size = 36.dp,
                        iconSize = 20.dp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "אזורי אוטומציה",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val activeZones = zones.count { it.isEnabled }
                        Text(
                            text = when {
                                !config.zonesOnly -> "ההגבלה כבויה — ההודעה נשלחת בכל חנייה"
                                activeZones == 0 -> "לא הוגדרו אזורים — הקישו כדי לסמן כתובת ורדיוס"
                                activeZones == 1 -> "אזור פעיל אחד — ההודעה תישלח רק מתוכו"
                                else -> "$activeZones אזורים פעילים — ההודעה תישלח רק מתוכם"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // What happened on the last run — the only window into a flow that runs unattended
            status?.let { lastStatus ->
                LastAutomationStatusCard(lastStatus)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Disclaimer
            SectionCard(alpha = 0.5f) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(
                        icon = Icons.Default.Warning,
                        tint = MaterialTheme.colorScheme.error,
                        size = 36.dp,
                        iconSize = 20.dp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Text(
                        text = "חשוב לדעת",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• ההודעה נשלחת אוטומטית, ללא אישור נוסף — ברגע שזוהתה חנייה.\n" +
                            "• אם המסך כבוי והטלפון נעול, ההודעה תישלח מיד עם פתיחת הנעילה (מסך נעילה מאובטח לא ניתן לעקיפה).\n" +
                            "• האוטומציה מדמה לחיצות ב-WhatsApp ולכן עלולה להישבר אם WhatsApp ישנו את המסכים שלהם.\n" +
                            "• אוטומציה אינה נתמכת רשמית על ידי WhatsApp — השימוש באחריותכם.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun VehicleTargetRow(
    vehicleName: String,
    targetLabel: String?,
    canTest: Boolean,
    onClick: () -> Unit,
    onTest: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = vehicleName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = targetLabel?.let { "איש קשר: $it" } ?: "לא הוגדר יעד — הקישו לבחירה",
                style = MaterialTheme.typography.bodySmall,
                color = if (targetLabel == null) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.tertiary
                }
            )
        }
        if (canTest) {
            TextButton(onClick = onTest) {
                Text("בדיקה")
            }
        }
    }
}

@Composable
private fun LastAutomationStatusCard(status: AutomationStatusStore.Status) {
    val (icon, tint, title) = when (status.outcome) {
        AutomationStatusStore.Outcome.SENT ->
            Triple(Icons.Default.CheckCircle, MaterialTheme.colorScheme.tertiary, "ההודעה נשלחה")
        AutomationStatusStore.Outcome.DEFERRED ->
            Triple(Icons.Default.Schedule, MaterialTheme.colorScheme.secondary, "ההודעה ממתינה")
        AutomationStatusStore.Outcome.SKIPPED ->
            Triple(Icons.Default.Info, MaterialTheme.colorScheme.secondary, "ההודעה לא נשלחה")
        AutomationStatusStore.Outcome.FAILED ->
            Triple(Icons.Default.Warning, MaterialTheme.colorScheme.error, "השליחה נכשלה")
    }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = icon,
                tint = tint,
                size = 36.dp,
                iconSize = 20.dp,
                modifier = Modifier.padding(end = 12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ניסיון אחרון: $title",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = status.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (status.timestamp > 0L) {
                    Text(
                        text = DateUtils.getRelativeTimeSpanString(
                            status.timestamp,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS
                        ).toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetPickerDialog(
    vehicle: Vehicle,
    contacts: List<com.sharepark.domain.model.TrustedContact>,
    onPickContact: (com.sharepark.domain.model.TrustedContact) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("יעד עבור ${vehicle.name}") },
        text = {
            Column {
                Text(
                    text = "בחרו למי תישלח הודעת החנייה של הרכב הזה:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (contacts.isEmpty()) {
                    Text(
                        text = "אין אנשי קשר — הוסיפו במסך 'אנשי קשר מורשים'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                } else {
                    contacts.forEach { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPickContact(contact) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${contact.name} (${contact.phoneNumber})",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClear) {
                Text("הסר יעד")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("סגור")
            }
        }
    )
}

@Composable
private fun SectionCard(alpha: Float = 1f, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = alpha)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}
