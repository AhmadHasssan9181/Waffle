package com.noobdev.waffle

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.noobdev.waffle.api.OfflineGeocoder
import com.noobdev.waffle.routing.OfflineRoutingManager
import com.noobdev.waffle.ui.theme.AppTheme
import com.noobdev.waffle.ui.theme.WaffleTheme
import com.noobdev.waffle.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private val locationPermission = Manifest.permission.ACCESS_FINE_LOCATION
    @RequiresApi(Build.VERSION_CODES.Q)
    private val backgroundLocationPermission = Manifest.permission.ACCESS_BACKGROUND_LOCATION

    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize MapLibre
        MapLibre.getInstance(this, "U7fg3KGqTysSBCZJpaNH", WellKnownTileServer.MapLibre)

        // Initialize Preferences Manager
        preferencesManager = PreferencesManager(applicationContext)

        lifecycleScope.launch(Dispatchers.IO) {
            OfflineGeocoder.load(applicationContext)

            runCatching { OfflineRoutingManager.initialize(applicationContext) }
                    .onSuccess { routingEngine ->
                        Log.d(TAG, "Offline routing engine verification complete")

                        runCatching {
                            routingEngine.calculateRoute(
                                    startLat = 33.6844,
                                    startLng = 73.0479,
                                    endLat = 33.7294,
                                    endLng = 73.0931
                            )
                        }
                                .onSuccess { response ->
                                    if (response.hasErrors()) {
                                        Log.e(
                                                TAG,
                                                "Offline route test failed with errors: ${response.errors}"
                                        )
                                    } else {
                                        val bestPath = response.best
                                        Log.d(
                                                TAG,
                                                "Offline route test success distanceMeters=${bestPath.distance} timeMs=${bestPath.time} pointCount=${bestPath.points.size()}"
                                        )
                                    }
                                }
                                .onFailure { throwable ->
                                    Log.e(TAG, "Offline route test threw an exception", throwable)
                                }
                    }
                    .onFailure { throwable ->
                        Log.e(TAG, "Offline routing engine verification failed", throwable)
                    }
        }

        enableEdgeToEdge()

        setContent {
            // Collect theme preference
            val appTheme by preferencesManager.appTheme.collectAsState(initial = AppTheme.DEFAULT)

            WaffleTheme(appTheme = appTheme) {
                LocationPermissionWrapper { WaffleApp(preferencesManager = preferencesManager) }
            }
        }
    }

    @Composable
    fun LocationPermissionWrapper(content: @Composable () -> Unit) {
        var hasFineLocation by remember { mutableStateOf(false) }
        var hasBackgroundLocation by remember { mutableStateOf(false) }
        var permissionChecked by remember { mutableStateOf(false) }

        val locationPermissions =
                mutableListOf(locationPermission)
                        .apply {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                                    add(backgroundLocationPermission)
                        }
                        .toTypedArray()

        val launcher =
                rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    hasFineLocation = permissions[locationPermission] == true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        hasBackgroundLocation = permissions[backgroundLocationPermission] == true
                    }
                    permissionChecked = true
                }

        // Check current permission status
        LaunchedEffect(Unit) {
            hasFineLocation =
                    ContextCompat.checkSelfPermission(this@MainActivity, locationPermission) ==
                            PackageManager.PERMISSION_GRANTED

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                hasBackgroundLocation =
                        ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                backgroundLocationPermission
                        ) == PackageManager.PERMISSION_GRANTED
            }
            permissionChecked = true
        }

        val hasRequiredPermissions =
                hasFineLocation &&
                        (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || hasBackgroundLocation)

        if (!permissionChecked) {
            // Loading state
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (hasRequiredPermissions) {
            // Request Notification Permission on Android 13+ (Non-blocking)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                var notificationRequested by remember { mutableStateOf(false) }
                val notifLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    // Notification permission is optional, proceed either way
                }

                val hasNotif = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

                LaunchedEffect(Unit) {
                    if (!hasNotif && !notificationRequested) {
                        notificationRequested = true
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            // Permission granted, show main content
            content()
        } else {
            // Permission request screen
            LocationPermissionScreen(onRequestPermission = { launcher.launch(locationPermissions) })
        }
    }

    @Composable
    fun LocationPermissionScreen(onRequestPermission: () -> Unit) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
            ) {
                Text(text = "📍", style = MaterialTheme.typography.displayLarge)

                Spacer(Modifier.height(24.dp))

                Text(
                        text = "Location Access Required",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(Modifier.height(12.dp))

                Text(
                        text =
                                "Waffle needs your location to detect nearby hazards, show relevant disaster alerts, and help rescue services find you in emergencies.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(Modifier.height(32.dp))

                Button(
                        onClick = onRequestPermission,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Text(
                            text = "Grant Location Permission",
                            style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                        text = "Your location data is never shared without your consent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
