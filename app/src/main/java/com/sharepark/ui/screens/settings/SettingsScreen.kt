package com.sharepark.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sharepark.R
import com.sharepark.ui.components.IconBadge
import com.sharepark.ui.components.simpleVerticalScrollbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToTrustedContacts: () -> Unit = {},
    onNavigateToWhatsAppAutomation: () -> Unit = {},
    onNavigateToAutomationZones: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activeVehicle by viewModel.activeVehicle.collectAsState()
    val scrollState = rememberScrollState()
    
    var batteryIgnored by remember {
        mutableStateOf(viewModel.isIgnoringBatteryOptimizations(context))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "הגדרות האפליקציה",
                        fontWeight = FontWeight.Bold
                    )
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
            // Background running configurations
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(
                            icon = Icons.Default.BatteryAlert,
                            tint = MaterialTheme.colorScheme.secondary,
                            size = 36.dp,
                            iconSize = 20.dp,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Text(
                            text = "מניעת סגירת האפליקציה ברקע",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "מערכות הפעלה של אנדרואיד עשויות לסגור את שירות מעקב הבלוטות' ברקע כדי לחסוך בסוללה. מומלץ לבטל את אופטימיזציית הסוללה עבור SharePark.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            viewModel.requestIgnoreBatteryOptimizations(context)
                            // update status when user returns
                            batteryIgnored = viewModel.isIgnoringBatteryOptimizations(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (batteryIgnored) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                                           else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = if (batteryIgnored) "התעלמות מאופטימיזציה מאופשרת" else "אפשר התעלמות מאופטימיזציית סוללה",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Auto-start permission — often needed on OPPO/ColorOS so the app can reliably
            // detect Bluetooth disconnects and post notifications while running in the background
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(
                            icon = Icons.Default.BatteryAlert,
                            tint = MaterialTheme.colorScheme.tertiary,
                            size = 36.dp,
                            iconSize = 20.dp,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Text(
                            text = "הפעלה אוטומטית ברקע",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "מכשירי OPPO/ColorOS עשויים לחסום את SharePark מלהתעורר ברקע ולזהות חנייה כשהאפליקציה סגורה. אשרו הפעלה אוטומטית (Auto-start) עבור האפליקציה במסך שייפתח.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.openAutoStartSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text(
                            text = "פתח הגדרות הפעלה אוטומטית",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Testing / Simulation Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(
                            icon = Icons.Default.PlayArrow,
                            tint = MaterialTheme.colorScheme.secondary,
                            size = 36.dp,
                            iconSize = 20.dp,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Text(
                            text = "סימולציית זיהוי חנייה",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "אין צורך לצאת לנסיעה כדי לבדוק את האפליקציה! לחץ על הכפתור כדי לדמות אירוע ניתוק בלוטות' ולשמור את מיקומך הנוכחי.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { viewModel.simulateParkingDisconnect() },
                        enabled = activeVehicle != null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text(
                            text = if (activeVehicle != null) "סמל ניתוק עבור ${activeVehicle?.name}" else "אין רכב פעיל לסימולציה",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Trusted contacts (family sharing) entry point
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToTrustedContacts() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(
                        icon = Icons.Default.People,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "אנשי קשר מורשים",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "נהל את בני המשפחה שאיתם תוכל לשתף את מיקום החנייה ב-WhatsApp",
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

            Spacer(modifier = Modifier.height(20.dp))

            // WhatsApp automation entry point
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToWhatsAppAutomation() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(
                        icon = Icons.Default.Send,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "אוטומציית WhatsApp",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "שליחת מיקום החנייה אוטומטית לאיש קשר או קבוצה — בלי אף לחיצה",
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

            Spacer(modifier = Modifier.height(20.dp))

            // Automation zones entry point
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToAutomationZones() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
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
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "אזורי אוטומציה",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "סמנו כתובות ורדיוס סביבן — ההודעה האוטומטית תישלח רק כשחונים בתוכן",
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

            Spacer(modifier = Modifier.height(20.dp))

            // Helpful hints Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(
                            icon = Icons.Default.Info,
                            tint = MaterialTheme.colorScheme.primary,
                            size = 36.dp,
                            iconSize = 20.dp,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Text(
                            text = "מדריך קצר",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val guideText = """
                        1. ודא שהטלפון מוצמד (Paired) לבלוטות' של הרכב בהגדרות המערכת.
                        2. הוסף את הרכב בכרטיסייה 'רכבים'.
                        3. ודא שהרשאת המיקום מוגדרת כ-'אפשר תמיד' (Allow all the time) על מנת שהשירות יפעל גם כשהמסך כבוי.
                        4. השירות ירוץ ברקע בצורה חסכונית במיוחד וישמור את המיקום ברגע שתכבה את המנוע והרכב יתנתק מהטלפון.
                    """.trimIndent()
                    
                    Text(
                        text = guideText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
