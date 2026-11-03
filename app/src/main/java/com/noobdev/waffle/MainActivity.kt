package com.noobdev.waffle

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
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
import com.noobdev.waffle.data.remote.OfflineGeocoder
import com.noobdev.waffle.core.navigation.GeoPoint
import com.noobdev.waffle.core.navigation.NavigationService
import com.noobdev.waffle.core.navigation.NavigationSession
import com.noobdev.waffle.core.navigation.NavigationStore
import com.noobdev.waffle.core.navigation.PipState
import com.noobdev.waffle.feature.offline.OfflineMapManager
import com.noobdev.waffle.core.routing.OfflineRoutingManager
import com.noobdev.waffle.design.brand.WaffleLockup
import com.noobdev.waffle.design.components.WaffleLoadingSpinner
import com.noobdev.waffle.design.components.WafflePrimaryButton
import com.noobdev.waffle.design.theme.AppTheme
import com.noobdev.waffle.design.theme.WaffleTheme
import com.noobdev.waffle.core.prefs.PreferencesManager
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_navigation", false)) {
            NavigationSession.requestOpenMap()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && NavigationSession.isActive()) {
            // Make sure the PiP window shows navigation, not whichever tab was open.
            NavigationSession.requestOpenMap()
            runCatching {
                enterPictureInPictureMode(
                        PictureInPictureParams.Builder()
                                .setAspectRatio(Rational(16, 9))
                                .build()
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(
            isInPictureInPictureMode: Boolean,
            newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipState.setActive(isInPictureInPictureMode)
    }

    override fun onDestroy() {
        PipState.setActive(false)
        super.onDestroy()
    }

    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize MapLibre
        MapLibre.getInstance(this, BuildConfig.MAPTILER_API_KEY, WellKnownTileServer.MapLibre)

        // Initialize Preferences Manager
        preferencesManager = PreferencesManager(applicationContext)

        // Resume a saved trip after a process restart. If the trip is already live in this
        // process, just make sure its foreground service is still attached instead of resetting
        // the live progress to the last saved route.
        if (NavigationSession.isActive()) {
            NavigationService.start(this)
        } else {
            NavigationStore.load(this)?.let { NavigationSession.restore(this, it) }
        }
        if (intent?.getBooleanExtra("open_navigation", false) == true) {
            NavigationSession.requestOpenMap()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            OfflineGeocoder.load(applicationContext)

            // Load the installed offline-layer list early so an offline cold start can pick the
            // right cached map style instead of defaulting to a style the user never downloaded.
            OfflineMapManager.refresh(applicationContext)

            val routingEngine = OfflineRoutingManager.initialize(applicationContext)
            if (routingEngine != null) {
                runCatching {
                            routingEngine.calculateRoute(
                                    start = GeoPoint(33.6844, 73.0479),
                                    end = GeoPoint(33.7294, 73.0931)
                            )
                        }
                        .onSuccess { response ->
                            if (response.hasErrors()) {
                                Log.e(TAG, "Offline route test failed: ${response.errors}")
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
        }

        enableEdgeToEdge()

        setContent {
            // Collect theme preference
            val appTheme by preferencesManager.appTheme.collectAsState(initial = AppTheme.SYSTEM)

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

        // Background location is requested alongside the foreground permission, but the app is
        // fully usable without it (alerts still sync while the app is open). Blocking the whole
        // app on "Allow all the time" is hostile, so only the foreground grant is mandatory.
        val hasRequiredPermissions = hasFineLocation

        if (!permissionChecked) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                WaffleLoadingSpinner(size = 34.dp)
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
                    modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
            ) {
                WaffleLockup(markSize = 104.dp)

                Spacer(Modifier.height(28.dp))

                Text(
                        text = "Stay found.",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(Modifier.height(10.dp))

                Text(
                        text =
                                "Waffle warns you about floods, earthquakes and fires near you — and keeps working when the signal doesn't.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(Modifier.height(32.dp))

                WafflePrimaryButton(
                        text = "Allow location",
                        onClick = onRequestPermission,
                        modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))

                Text(
                        text = "Your coordinates stay on this phone unless you send an SOS.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
