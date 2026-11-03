package com.noobdev.waffle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.noobdev.waffle.core.prefs.PreferencesManager
import com.noobdev.waffle.core.navigation.NavigationSession
import com.noobdev.waffle.core.navigation.PipState
import com.noobdev.waffle.design.theme.AppTheme
import com.noobdev.waffle.feature.alerts.AlertsScreen
import com.noobdev.waffle.feature.home.HomeScreen
import com.noobdev.waffle.feature.map.MapScreen
import com.noobdev.waffle.feature.offline.OfflineMapsScreen
import com.noobdev.waffle.feature.rescue.RescueScreen
import com.noobdev.waffle.feature.settings.SettingsScreen
import com.noobdev.waffle.feature.shops.ShopsScreen

// ============================================
// SCREEN ROUTES
// ============================================

sealed class Screen(
        val route: String,
        val title: String,
        val selectedIcon: ImageVector,
        val unselectedIcon: ImageVector
) {
    object Home :
            Screen(
                    route = "home",
                    title = "Home",
                    selectedIcon = Icons.Rounded.Home,
                    unselectedIcon = Icons.Outlined.Home
            )

    object Map :
            Screen(
                    route = "map",
                    title = "Map",
                    selectedIcon = Icons.Rounded.Map,
                    unselectedIcon = Icons.Outlined.Map
            )

    object Nearby :
            Screen(
                    route = "nearby",
                    title = "Nearby",
                    selectedIcon = Icons.Rounded.Storefront,
                    unselectedIcon = Icons.Outlined.Storefront
            )

    object Alerts :
            Screen(
                    route = "alerts",
                    title = "Alerts",
                    selectedIcon = Icons.Rounded.Notifications,
                    unselectedIcon = Icons.Outlined.Notifications
            )

    object Rescue :
            Screen(
                    route = "rescue",
                    title = "Rescue",
                    selectedIcon = Icons.Rounded.HealthAndSafety,
                    unselectedIcon = Icons.Outlined.HealthAndSafety
            )

    object Settings :
            Screen(
                    route = "settings",
                    title = "Settings",
                    selectedIcon = Icons.Rounded.Settings,
                    unselectedIcon = Icons.Rounded.Settings
            )

    object OfflineMaps :
            Screen(
                    route = "offline_maps",
                    title = "Offline maps",
                    selectedIcon = Icons.Rounded.CloudDownload,
                    unselectedIcon = Icons.Rounded.CloudDownload
            )
}

/** The five destinations that own a tab. Settings and Offline maps are pushed on top. */
val bottomNavItems = listOf(Screen.Home, Screen.Map, Screen.Nearby, Screen.Alerts, Screen.Rescue)

// ============================================
// WAFFLE APP ROOT COMPOSABLE
// ============================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaffleApp(preferencesManager: PreferencesManager) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val mapStyleIndex by preferencesManager.mapStyleIndex.collectAsState(initial = -1)
    val appTheme by preferencesManager.appTheme.collectAsState(initial = AppTheme.SYSTEM)
    val inPip by PipState.active.collectAsState()
    val openMap by NavigationSession.openMapRequested.collectAsState()

    LaunchedEffect(openMap) {
        if (openMap) {
            navController.navigate(Screen.Map.route) { launchSingleTop = true }
            NavigationSession.consumeOpenMapRequest()
        }
    }

    // Reopening the app during a live trip goes straight back to navigation, like Maps.
    LaunchedEffect(Unit) {
        if (NavigationSession.isActive()) {
            navController.navigate(Screen.Map.route) { launchSingleTop = true }
        }
    }

    Scaffold(
            bottomBar = {
                if (!inPip && bottomNavItems.any { it.route == currentRoute }) {
                    WaffleBottomNavBar(navController = navController, currentRoute = currentRoute)
                }
            },
            containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Navigation(
                navController = navController,
                modifier = Modifier.padding(innerPadding),
                mapStyleIndex = mapStyleIndex,
                preferencesManager = preferencesManager
        )
    }
}

// ============================================
// BOTTOM NAVIGATION BAR
// ============================================

/**
 * Icon + label bar with a soft honey pill behind the active icon. A hairline separates it from
 * content, which reads cleaner than a shadow on both cream and ink backgrounds.
 */
@Composable
fun WaffleBottomNavBar(navController: NavController, currentRoute: String?) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            Box(
                    Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            )
            Row(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 6.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                bottomNavItems.forEach { screen ->
                    val selected = currentRoute == screen.route
                    val tint =
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant

                    Column(
                            modifier =
                                    Modifier.weight(1f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable {
                                                if (currentRoute != screen.route) {
                                                    navController.navigate(screen.route) {
                                                        popUpTo(Screen.Home.route) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            }
                                            .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                                modifier =
                                        Modifier.height(28.dp)
                                                .width(48.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                        if (selected)
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(alpha = 0.14f)
                                                        else androidx.compose.ui.graphics.Color
                                                                .Transparent
                                                ),
                                contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                    imageVector =
                                            if (selected) screen.selectedIcon
                                            else screen.unselectedIcon,
                                    contentDescription = screen.title,
                                    tint = tint,
                                    modifier = Modifier.size(21.dp)
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                                text = screen.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = tint
                        )
                    }
                }
            }
        }
    }
}

// ============================================
// NAVIGATION HOST
// ============================================

@Composable
fun Navigation(
        navController: NavHostController,
        modifier: Modifier = Modifier,
        mapStyleIndex: Int,
        preferencesManager: PreferencesManager
) {
    NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = modifier
    ) {
        composable(Screen.Home.route) { HomeScreen(navController = navController) }
        composable(Screen.Map.route) {
            MapScreen(navController = navController, currentStyleIndex = mapStyleIndex)
        }
        composable(Screen.Nearby.route) { ShopsScreen(navController = navController) }
        composable(Screen.Alerts.route) { AlertsScreen(navController = navController) }
        composable(Screen.Rescue.route) { RescueScreen(navController = navController) }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController, preferencesManager = preferencesManager)
        }
        composable(Screen.OfflineMaps.route) { OfflineMapsScreen(navController = navController) }
    }
}
