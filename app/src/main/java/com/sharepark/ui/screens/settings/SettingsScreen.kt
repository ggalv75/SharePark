package com.sharepark.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sharepark.ui.components.AppCard
import com.sharepark.ui.components.CardTitle
import com.sharepark.ui.components.NavigationRowCard
import com.sharepark.ui.components.PrimaryPillButton
import com.sharepark.ui.components.ScreenHeader
import com.sharepark.ui.components.SecondaryPillButton
import com.sharepark.ui.components.SectionHeader
import com.sharepark.ui.components.simpleVerticalScrollbar

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
    
    val currentUser by viewModel.currentUser.collectAsState()
    val accountError by viewModel.accountError.collectAsState()

    var batteryIgnored by remember {
        mutableStateOf(viewModel.isIgnoringBatteryOptimizations(context))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenHeader(title = "הגדרות") }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .simpleVerticalScrollbar(scrollState, MaterialTheme.colorScheme.onBackground)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (viewModel.isAccountAvailable) {
                AccountCard(
                    user = currentUser,
                    error = accountError,
                    onSignIn = { viewModel.signIn(context) },
                    onSignOut = viewModel::signOut
                )
            }

            SectionHeader(title = "שיתוף ואוטומציה", modifier = Modifier.padding(top = 8.dp))

            // Trusted contacts (family sharing) entry point
            NavigationRowCard(
                title = "אנשי קשר מורשים",
                description = "נהל את בני המשפחה שאיתם תוכל לשתף את מיקום החנייה ב-WhatsApp",
                icon = Icons.Default.People,
                onClick = onNavigateToTrustedContacts
            )

            // WhatsApp automation entry point
            NavigationRowCard(
                title = "אוטומציית WhatsApp",
                description = "שליחת מיקום החנייה אוטומטית לאיש קשר מורשה — בלי אף לחיצה",
                icon = Icons.Default.Send,
                onClick = onNavigateToWhatsAppAutomation
            )

            // Automation zones entry point
            NavigationRowCard(
                title = "אזורי אוטומציה",
                description = "סמנו כתובות ורדיוס סביבן — ההודעה האוטומטית תישלח רק כשחונים בתוכן",
                icon = Icons.Default.LocationOn,
                onClick = onNavigateToAutomationZones
            )

            SectionHeader(title = "פעולה ברקע", modifier = Modifier.padding(top = 8.dp))

            // Background running configurations
            AppCard {
                CardTitle(title = "מניעת סגירה ברקע", icon = Icons.Default.BatteryAlert)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "מערכות הפעלה של אנדרואיד עשויות לסגור את שירות מעקב הבלוטות' ברקע כדי לחסוך בסוללה. מומלץ לבטל את אופטימיזציית הסוללה עבור SharePark.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (batteryIgnored) {
                    SecondaryPillButton(
                        text = "התעלמות מאופטימיזציה מאופשרת",
                        icon = Icons.Default.CheckCircle,
                        onClick = {
                            viewModel.requestIgnoreBatteryOptimizations(context)
                            batteryIgnored = viewModel.isIgnoringBatteryOptimizations(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    PrimaryPillButton(
                        text = "אפשר התעלמות מאופטימיזציית סוללה",
                        onClick = {
                            viewModel.requestIgnoreBatteryOptimizations(context)
                            // update status when user returns
                            batteryIgnored = viewModel.isIgnoringBatteryOptimizations(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Auto-start permission — often needed on OPPO/ColorOS so the app can reliably
            // detect Bluetooth disconnects and post notifications while running in the background
            AppCard {
                CardTitle(title = "הפעלה אוטומטית ברקע", icon = Icons.Default.PowerSettingsNew)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "מכשירי OPPO/ColorOS עשויים לחסום את SharePark מלהתעורר ברקע ולזהות חנייה כשהאפליקציה סגורה. אשרו הפעלה אוטומטית (Auto-start) עבור האפליקציה במסך שייפתח.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                SecondaryPillButton(
                    text = "פתח הגדרות הפעלה אוטומטית",
                    onClick = { viewModel.openAutoStartSettings(context) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SectionHeader(title = "בדיקה ועזרה", modifier = Modifier.padding(top = 8.dp))

            // Testing / Simulation Card
            AppCard {
                CardTitle(title = "סימולציית זיהוי חנייה", icon = Icons.Default.PlayArrow)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "אין צורך לצאת לנסיעה כדי לבדוק את האפליקציה! לחץ על הכפתור כדי לדמות אירוע ניתוק בלוטות' ולשמור את מיקומך הנוכחי.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                SecondaryPillButton(
                    text = if (activeVehicle != null) "דמה ניתוק עבור ${activeVehicle?.name}" else "אין רכב פעיל לסימולציה",
                    onClick = { viewModel.simulateParkingDisconnect() },
                    enabled = activeVehicle != null,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Helpful hints Card
            AppCard {
                CardTitle(title = "מדריך קצר", icon = Icons.Default.Info)
                Spacer(modifier = Modifier.height(12.dp))

                val steps = listOf(
                    "ודא שהטלפון מוצמד (Paired) לבלוטות' של הרכב בהגדרות המערכת.",
                    "הוסף את הרכב בכרטיסייה 'רכבים'.",
                    "ודא שהרשאת המיקום מוגדרת כ-'אפשר תמיד' (Allow all the time) על מנת שהשירות יפעל גם כשהמסך כבוי.",
                    "השירות ירוץ ברקע בצורה חסכונית במיוחד וישמור את המיקום ברגע שתכבה את המנוע והרכב יתנתק מהטלפון."
                )
                steps.forEachIndexed { index, step ->
                    Row(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(24.dp)
                        )
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
