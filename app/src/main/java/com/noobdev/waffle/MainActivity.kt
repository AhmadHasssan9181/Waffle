package com.noobdev.waffle

import android.Manifest
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.noobdev.waffle.data.remote.OfflineGeocoder
import com.noobdev.waffle.core.navigation.FloatingMapHost
import com.noobdev.waffle.core.navigation.GeoPoint
import com.noobdev.waffle.core.navigation.NavigationService
import com.noobdev.waffle.core.navigation.NavigationSession
import com.noobdev.waffle.core.navigation.NavigationStore
import com.noobdev.waffle.core.navigation.PipState
import com.noobdev.waffle.core.navigation.SimulationState
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

    private var pendingPip = false
    private var leavingForFloating = false

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_navigation", false)) {
            NavigationSession.requestOpenMap()
        }
        if (intent.getBooleanExtra("enter_pip", false)) {
            pendingPip = true
            enterPipIfRequested()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val live = NavigationSession.isActive()
        val simulating = SimulationState.active.value
        if (!live && !simulating) return
        if (live) {
            NavigationSession.requestOpenMap()
            // Live navigation gets the large custom floating map when overlay permission exists.
            // Without it, do not leave the user with no map at all: use system PiP.
            if (Settings.canDrawOverlays(this)) {
                leavingForFloating = true
                NavigationService.showFloating(this)
            } else {
                enterSystemPip()
            }
            return
        }
        if (FloatingMapHost.show(this, SimulationState.snapshot)) {
            leavingForFloating = true
            return
        }
        enterSystemPip()
    }

    private fun buildPipParams(): PictureInPictureParams {
        val navSnap = NavigationSession.state.value
        val voiceEnabled = !navSnap.muted
        val mutePending =
                PendingIntent.getService(
                        this,
                        9101,
                        Intent(this, NavigationService::class.java).apply {
                            action = NavigationService.ACTION_TOGGLE_VOICE
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
        val stopPending =
                PendingIntent.getService(
                        this,
                        9102,
                        Intent(this, NavigationService::class.java).apply {
                            action = NavigationService.ACTION_STOP
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
        val muteAction =
                RemoteAction(
                        Icon.createWithResource(
                                this,
                                if (voiceEnabled) android.R.drawable.ic_lock_silent_mode_off
                                else android.R.drawable.ic_lock_silent_mode
                        ),
                        if (voiceEnabled) "Mute voice" else "Unmute voice",
                        if (voiceEnabled) "Mute voice guidance" else "Unmute voice guidance",
                        mutePending
                )
        val stopAction =
                RemoteAction(
                        Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                        "End route",
                        "Stop turn-by-turn navigation",
                        stopPending
                )
        val builder =
                PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(3, 4))
                        .setActions(listOf(muteAction, stopAction))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val activeNav = navSnap.active || SimulationState.active.value
            val autoEnterPip = activeNav && !Settings.canDrawOverlays(this)
            builder.setSeamlessResizeEnabled(true).setAutoEnterEnabled(autoEnterPip)
        }
        return builder.build()
    }

    private fun enterSystemPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching { enterPictureInPictureMode(buildPipParams()) }
    }

    private fun enterPipIfRequested() {
        if (!pendingPip) return
        if (!NavigationSession.isActive() && !SimulationState.active.value) {
            pendingPip = false
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            pendingPip = false
            return
        }
        val entered =
                runCatching { enterPictureInPictureMode(buildPipParams()) }
                        .getOrDefault(false)
        if (entered) pendingPip = false
    }

    override fun onResume() {
        super.onResume()
        // Do not immediately hide a floating map that was just requested by pressing Home.
        if (!leavingForFloating) {
            if (NavigationSession.isActive()) NavigationService.hideFloating(this)
            FloatingMapHost.hide()
        }
        enterPipIfRequested()
    }

    override fun onStop() {
        super.onStop()
        // The activity has really left; the next resume is the user opening the app again.
        leavingForFloating = false
    }

    override fun onPictureInPictureModeChanged(
            isInPictureInPictureMode: Boolean,
            newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipState.setActive(isInPictureInPictureMode)
    }

    override fun onDestroy() {
        FloatingMapHost.hide()
        PipState.setActive(false)
        super.onDestroy()
    }

    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.getBooleanExtra("enter_pip", false) == true) {
            pendingPip = true
        }

        // Initialize MapLibre (idempotent; the Application also does it for the service).
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            lifecycleScope.launch {
                NavigationSession.state.collect {
                    runCatching { setPictureInPictureParams(buildPipParams()) }
                }
            }
            lifecycleScope.launch {
                SimulationState.active.collect {
                    runCatching { setPictureInPictureParams(buildPipParams()) }
                }
            }
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
        var permissionChecked by remember { mutableStateOf(false) }

        val locationPermissions = arrayOf(locationPermission)

        val launcher =
                rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    hasFineLocation = permissions[locationPermission] == true
                    permissionChecked = true
                }

        // Check current permission status
        LaunchedEffect(Unit) {
            hasFineLocation =
                    ContextCompat.checkSelfPermission(this@MainActivity, locationPermission) ==
                            PackageManager.PERMISSION_GRANTED
            permissionChecked = true
        }

        // Navigation uses a foreground location service, which only needs the while-in-use
        // permission. Requesting "all the time" here was unnecessary and triggered Play policy
        // warnings.
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
