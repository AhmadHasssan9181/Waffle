package com.noobdev.waffle

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.noobdev.waffle.Screens.*
import com.noobdev.waffle.ui.theme.AppTheme
import com.noobdev.waffle.utils.PreferencesManager

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
                    selectedIcon = Icons.Filled.Home,
                    unselectedIcon = Icons.Outlined.Home
            )
    object Map :
            Screen(
                    route = "map",
                    title = "Map",
                    selectedIcon = Icons.Filled.Map,
                    unselectedIcon = Icons.Outlined.Map
            )
    object Alerts :
            Screen(
                    route = "alerts",
                    title = "Alerts",
                    selectedIcon = Icons.Filled.Notifications,
                    unselectedIcon = Icons.Outlined.Notifications
            )
    object Rescue :
            Screen(
                    route = "rescue",
                    title = "Rescue",
                    selectedIcon = Icons.Filled.LocalHospital,
                    unselectedIcon = Icons.Outlined.LocalHospital
            )
    object Settings :
            Screen(
                    route = "settings",
                    title = "Settings",
                    selectedIcon = Icons.Filled.Settings,
                    unselectedIcon = Icons.Outlined.Settings
            )
}

// List of bottom nav items
val bottomNavItems = listOf(Screen.Home, Screen.Map, Screen.Alerts, Screen.Rescue, Screen.Settings)

// ============================================
// WAFFLE APP ROOT COMPOSABLE
// ============================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaffleApp(preferencesManager: PreferencesManager) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Collect preferences
    val mapStyleIndex by preferencesManager.mapStyleIndex.collectAsState(initial = 0)
    val appTheme by preferencesManager.appTheme.collectAsState(initial = AppTheme.DEFAULT)

    Scaffold(
            bottomBar = {
                WaffleBottomNavBar(navController = navController, currentRoute = currentRoute)
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

@Composable
fun WaffleBottomNavBar(navController: NavController, currentRoute: String?) {
    NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        bottomNavItems.forEach { screen ->
            val isSelected = currentRoute == screen.route

            NavigationBarItem(
                    selected = isSelected,
                    onClick = {
                        if (currentRoute != screen.route) {
                            navController.navigate(screen.route) {
                                // Pop up to start destination to avoid building up a large stack
                                popUpTo(Screen.Home.route) { saveState = true }
                                // Avoid multiple copies of the same destination
                                launchSingleTop = true
                                // Restore state when reselecting a previously selected item
                                restoreState = true
                            }
                        }
                    },
                    icon = {
                        Icon(
                                imageVector =
                                        if (isSelected) screen.selectedIcon
                                        else screen.unselectedIcon,
                                contentDescription = screen.title
                        )
                    },
                    label = {
                        Text(text = screen.title, style = MaterialTheme.typography.labelSmall)
                    },
                    colors =
                            NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor =
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor =
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor =
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
            )
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
        composable(Screen.Home.route) { Home(navController = navController) }
        composable(Screen.Map.route) {
            MapScreen(navController = navController, currentStyleIndex = mapStyleIndex)
        }
        composable(Screen.Alerts.route) { AlertsScreen(navController = navController) }
        composable(Screen.Rescue.route) { Rescue(navController = navController) }
        composable(Screen.Settings.route) {
            Settings(navController = navController, preferencesManager = preferencesManager)
        }
    }
}
