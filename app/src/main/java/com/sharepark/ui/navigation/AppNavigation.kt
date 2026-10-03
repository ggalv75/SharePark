package com.sharepark.ui.navigation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.sharepark.platform.notification.InAppAlert
import com.sharepark.platform.permissions.PermissionManager
import com.sharepark.ui.components.InAppAlertBanner
import com.sharepark.ui.screens.history.HistoryScreen
import com.sharepark.ui.screens.map.MapScreen
import com.sharepark.ui.screens.settings.AutomationZonesScreen
import com.sharepark.ui.screens.settings.SettingsScreen
import com.sharepark.ui.screens.settings.TrustedContactsScreen
import com.sharepark.ui.screens.settings.WhatsAppAutomationScreen
import com.sharepark.ui.screens.vehicles.AddVehicleScreen
import com.sharepark.ui.screens.vehicles.ReservationsScreen
import com.sharepark.ui.screens.vehicles.VehiclesScreen

/** Detail screens that own the whole window — the bottom bar is hidden while they're up. */
private val FULL_SCREEN_ROUTES = setOf(
    "add_vehicle",
    "trusted_contacts",
    "wa_automation",
    "automation_zones",
    "reservations/{vehicleId}"
)

private const val ALERT_VISIBLE_MS = 6_000L

sealed class Screen(val route: String, val label: String, val icon: @Composable () -> Unit) {
    object Map : Screen("map", "מפה", { Icon(Icons.Default.Map, contentDescription = "מפה") })
    object Vehicles : Screen("vehicles", "רכבים", { Icon(Icons.Default.DirectionsCar, contentDescription = "רכבים") })
    object History : Screen("history", "היסטוריה", { Icon(Icons.Default.History, contentDescription = "היסטוריה") })
    object Settings : Screen("settings", "הגדרות", { Icon(Icons.Default.Settings, contentDescription = "הגדרות") })
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun AppNavigation(
    openReservationsFor: Long? = null,
    onReservationsOpened: () -> Unit = {},
    alerts: Flow<InAppAlert> = emptyFlow()
) {
    val navController = rememberNavController()

    // A banner over whatever screen is open, e.g. when another member books a shared car.
    var alert by remember { mutableStateOf<InAppAlert?>(null) }
    LaunchedEffect(alerts) { alerts.collect { alert = it } }
    LaunchedEffect(alert) {
        if (alert != null) {
            delay(ALERT_VISIBLE_MS)
            alert = null
        }
    }

    // Tapping a reservation notification lands on that car's reservations.
    LaunchedEffect(openReservationsFor) {
        val vehicleId = openReservationsFor ?: return@LaunchedEffect
        navController.navigate("reservations/$vehicleId")
        onReservationsOpened()
    }
    val context = LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Permissions state management
    var showBackgroundLocationDialog by remember { mutableStateOf(false) }
    var isMapFullScreen by remember { mutableStateOf(false) }
    
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Handle result if needed
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fineLocationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineLocationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // If location is granted, explain and request background location
            if (!PermissionManager.hasBackgroundLocationPermission(context)) {
                showBackgroundLocationDialog = true
            }
        }
    }

    LaunchedEffect(Unit) {
        // Trigger initial runtime permissions request (Fine location, BT, Notifications)
        val permissionsToRequest = PermissionManager.getRequiredPermissions().toTypedArray()
        if (!PermissionManager.hasPermissions(context, permissionsToRequest.toList())) {
            permissionsLauncher.launch(permissionsToRequest)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && 
                   !PermissionManager.hasBackgroundLocationPermission(context)) {
            showBackgroundLocationDialog = true
        }
    }

    if (showBackgroundLocationDialog) {
        AlertDialog(
            onDismissRequest = { showBackgroundLocationDialog = false },
            title = { Text("הרשאת מיקום ברקע") },
            text = { Text("כדי שהאפליקציה תוכל לזהות מתי החנית את הרכב גם כשהטלפון בכיס והאפליקציה סגורה, אנא בחר באפשרות 'אפשר תמיד' (Allow all the time) במסך ההרשאות הבא.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBackgroundLocationDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    }
                ) {
                    Text("המשך להגדרה")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundLocationDialog = false }) {
                    Text("ביטול")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute !in FULL_SCREEN_ROUTES && !isMapFullScreen) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    val items = listOf(Screen.Map, Screen.Vehicles, Screen.History, Screen.Settings)
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = screen.icon,
                            label = { Text(screen.label, style = MaterialTheme.typography.labelMedium) },
                            selected = currentRoute == screen.route,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Screen.Map.route,
                // Consume what the outer Scaffold already padded so each screen's own header/Scaffold
                // doesn't add the status/navigation bar insets a second time.
                modifier = Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
                enterTransition = {
                    fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.97f)
                },
                exitTransition = {
                    fadeOut(tween(150))
                },
                popEnterTransition = {
                    fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.97f)
                },
                popExitTransition = {
                    fadeOut(tween(150))
                }
            ) {
                composable(Screen.Map.route) {
                    MapScreen(
                        onFullScreenChange = { isMapFullScreen = it }
                    )
                }
                composable(Screen.Vehicles.route) {
                    VehiclesScreen(
                        onNavigateToAddVehicle = { navController.navigate("add_vehicle") },
                        onNavigateToReservations = { vehicleId -> navController.navigate("reservations/$vehicleId") }
                    )
                }
                composable(
                    route = "reservations/{vehicleId}",
                    arguments = listOf(navArgument("vehicleId") { type = NavType.LongType })
                ) {
                    ReservationsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable("add_vehicle") {
                    AddVehicleScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.History.route) {
                    HistoryScreen()
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onNavigateToTrustedContacts = { navController.navigate("trusted_contacts") },
                        onNavigateToWhatsAppAutomation = { navController.navigate("wa_automation") },
                        onNavigateToAutomationZones = { navController.navigate("automation_zones") }
                    )
                }
                composable("trusted_contacts") {
                    TrustedContactsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable("wa_automation") {
                    WhatsAppAutomationScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToZones = { navController.navigate("automation_zones") }
                    )
                }
                composable("automation_zones") {
                    AutomationZonesScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }

            InAppAlertBanner(
                alert = alert,
                onOpen = { vehicleId ->
                    alert = null
                    navController.navigate("reservations/$vehicleId")
                },
                onDismiss = { alert = null },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(innerPadding)
            )
        }
    }
}
