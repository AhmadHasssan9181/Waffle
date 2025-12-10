package com.noobdev.waffle

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.noobdev.waffle.Sketching.HeatmapDemoScreen
import com.noobdev.waffle.map.ParentMapScreen
import com.noobdev.waffle.ui.theme.WaffleTheme
import kotlinx.coroutines.GlobalScope.coroutineContext
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import kotlin.coroutines.coroutineContext

class MainActivity : ComponentActivity() {

    private val locationPermission = Manifest.permission.ACCESS_FINE_LOCATION
    @RequiresApi(Build.VERSION_CODES.Q)
    private val backgroundLocationPermission = Manifest.permission.ACCESS_BACKGROUND_LOCATION

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(
            this,
            "U7fg3KGqTysSBCZJpaNH",
            WellKnownTileServer.MapLibre
        )
        enableEdgeToEdge()
        setContent {
            WaffleTheme {
                //LocationPermissionScreen()
                HeatmapDemoScreen()
                //ParentMapScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun LocationPermissionScreen() {
        var hasFineLocation by remember { mutableStateOf(false) }
        var hasBackgroundLocation by remember { mutableStateOf(false) }

        val locationPermissions = mutableListOf(locationPermission).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                add(backgroundLocationPermission)
        }.toTypedArray()

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            hasFineLocation = permissions[locationPermission] == true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                hasBackgroundLocation = permissions[backgroundLocationPermission] == true
            }
        }

        // Check current permission status
        LaunchedEffect(Unit) {
            hasFineLocation = ContextCompat.checkSelfPermission(
                this@MainActivity, locationPermission
            ) == PackageManager.PERMISSION_GRANTED

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                hasBackgroundLocation = ContextCompat.checkSelfPermission(
                    this@MainActivity, backgroundLocationPermission
                ) == PackageManager.PERMISSION_GRANTED
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Location Access") })
            },
            modifier = Modifier.fillMaxSize()
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (hasFineLocation && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || hasBackgroundLocation)) {
                    Text("Location permission granted", color = MaterialTheme.colorScheme.primary)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("We need your location to detect hazards nearby.")
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { launcher.launch(locationPermissions) }) {
                            Text("Grant Location Permission")
                        }
                    }
                }
            }
        }
    }
}
