package com.noobdev.waffle.Screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.noobdev.waffle.Screen
import com.noobdev.waffle.api.fetchAreaName
import com.noobdev.waffle.api.fetchWeather
import com.noobdev.waffle.ui.components.*
import com.noobdev.waffle.ui.theme.*
import com.noobdev.waffle.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import com.noobdev.waffle.database.WaffleClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home(navController: NavController, viewModel: HomeViewModel = viewModel()) {
        val uiState by viewModel.uiState.collectAsState()
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        // Database connection test state
        var dbConnectionStatus by remember { mutableStateOf("Testing connection...") }

        // State for dynamic data
        var temperature by remember { mutableStateOf("--") }
        var precipitation by remember { mutableStateOf("0") }
        var weatherCondition by remember { mutableStateOf("Loading...") }
        var locationName by remember { mutableStateOf("Pakistan") }
        var currentLat by remember { mutableStateOf(30.3753) }
        var currentLng by remember { mutableStateOf(69.3451) }

        // OpenCage API key - replace with actual
        val openCageKey = "YOUR_OPENCAGE_KEY"

        // Test FastAPI backend connection
        LaunchedEffect(Unit) {
            try {
                withContext(Dispatchers.IO) {
                    val data = WaffleClient.api.getMapClosures()
                    dbConnectionStatus = "✅ Connected! Map data length: ${data.length} chars"
                }
            } catch (e: Exception) {
                dbConnectionStatus = "❌ Connection Failed: ${e.localizedMessage}"
            }
        }

        // Get location and weather on first load
        LaunchedEffect(Unit) {
                val hasLocationPermission =
                        ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

                if (hasLocationPermission) {
                        try {
                                val fusedLocationClient =
                                        LocationServices.getFusedLocationProviderClient(context)
                                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                        location?.let {
                                                currentLat = it.latitude
                                                currentLng = it.longitude

                                                // Fetch location name
                                                scope.launch {
                                                        val area =
                                                                fetchAreaName(
                                                                        currentLat,
                                                                        currentLng,
                                                                        openCageKey
                                                                )
                                                        locationName = area ?: "Pakistan"
                                                }

                                                // Fetch weather
                                                scope.launch {
                                                        val weather =
                                                                fetchWeather(currentLat, currentLng)
                                                        weather?.let { w ->
                                                                val temp =
                                                                        w.hourly.temperature
                                                                                .firstOrNull()
                                                                                ?: 0f
                                                                val precip =
                                                                        w.hourly.precipitation
                                                                                .firstOrNull()
                                                                                ?: 0f
                                                                temperature = "${temp.toInt()}"
                                                                precipitation = "${precip.toInt()}"
                                                                weatherCondition =
                                                                        when {
                                                                                precip > 5 ->
                                                                                        "Heavy Rain"
                                                                                precip > 1 ->
                                                                                        "Light Rain"
                                                                                temp > 35 -> "Hot"
                                                                                temp < 10 -> "Cold"
                                                                                else -> "Mild"
                                                                        }
                                                        }
                                                }
                                        }
                                }
                        } catch (e: Exception) {
                                e.printStackTrace()
                        }
                }
        }

        Scaffold(
                topBar = {
                        TopAppBar(
                                title = {
                                        Column {
                                                Text(
                                                        "Good ${uiState.greeting}, Waffle",
                                                        style = MaterialTheme.typography.titleLarge,
                                                )
                                                Text(
                                                        "Current location: $locationName",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                        }
                                },
                                actions = {
                                        IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                                                Icon(
                                                        imageVector = Icons.Default.Settings,
                                                        contentDescription = "Settings",
                                                        tint = MaterialTheme.colorScheme.onPrimary
                                                )
                                        }
                                }
                        )
                }
        ) { paddingValues ->
                Column(
                        modifier =
                                Modifier
                                        .fillMaxSize()
                                        .padding(paddingValues)
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp)
                ) {
                        // === GREETING ===
                        GreetingSection(greeting = uiState.greeting, locationName = locationName)

                        Spacer(modifier = Modifier.height(20.dp))

                        // FAST API BACKEND TEST UI
                        Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                        containerColor = if (dbConnectionStatus.contains("✅"))
                                                Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                                )
                        ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                                text = "Backend Database Status",
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Black
                                        )
                                        Text(
                                                text = dbConnectionStatus,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.Black
                                        )
                                }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // === PAKISTAN ALERT BANNER ===
                        PakistanAlertBanner()

                        Spacer(modifier = Modifier.height(16.dp))

                        // === WEATHER CARD ===
                        WeatherCard(
                                temperature = "$temperature°C",
                                condition = weatherCondition,
                                precipitation = "${precipitation}mm",
                                location = locationName
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // === QUICK ACTIONS ===
                        SectionHeader(title = "Quick Actions")
                        Spacer(modifier = Modifier.height(12.dp))
                        QuickActionsRow(navController = navController)

                        Spacer(modifier = Modifier.height(24.dp))

                        // === EMERGENCY NUMBERS ===
                        SectionHeader(title = "Emergency Numbers")
                        Spacer(modifier = Modifier.height(12.dp))
                        EmergencyNumbersRow(context)

                        Spacer(modifier = Modifier.height(24.dp))

                        // === ACTIVE ALERTS (Pakistan Focused) ===
                        SectionHeader(
                                title = "Active Alerts - Pakistan",
                                action = {
                                        TextButton(
                                                onClick = {
                                                        navController.navigate(Screen.Alerts.route)
                                                }
                                        ) { Text("View All") }
                                }
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (uiState.isLoading) {
                                Box(
                                        modifier = Modifier.fillMaxWidth().height(100.dp),
                                        contentAlignment = Alignment.Center
                                ) { WaffleLoadingSpinner(size = 36.dp) }
                        } else if (uiState.activeAlerts.isEmpty()) {
                                EmptyAlertsCard()
                        } else {
                                uiState.activeAlerts.take(3).forEach { alert ->
                                        DisasterAlertCard(
                                                title = alert.title,
                                                location = alert.location,
                                                status = alert.status,
                                                disasterType = alert.type,
                                                timestamp = alert.timestamp,
                                                onClick = {
                                                        navController.navigate(Screen.Map.route)
                                                }
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // === STATS OVERVIEW ===
                        SectionHeader(title = "Overview")
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                                MetricCard(
                                        value = "${uiState.activeAlerts.size}",
                                        label = "Active Alerts",
                                        icon = Icons.Default.Warning,
                                        iconTint = DangerRed,
                                        modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                        value = "4",
                                        label = "Flood Sectors",
                                        icon = Icons.Default.WaterDrop,
                                        iconTint = FloodColor,
                                        modifier = Modifier.weight(1f)
                                )
                        }

                        Spacer(modifier = Modifier.height(80.dp))
                }
        }
}

@Composable
fun PakistanAlertBanner() {
        Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = WarningOrange.copy(alpha = 0.15f))
        ) {
                Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = null,
                                tint = WarningOrange,
                                modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        "Pakistan Disaster Monitor",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = WarningOrange
                                )
                                Text(
                                        "Monitoring floods, earthquakes & weather across Pakistan",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }
                }
        }
}

@Composable
fun GreetingSection(greeting: String, locationName: String) {
        Column {
                Text(
                        text = greeting,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                        Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                                text = locationName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                }
        }
}

@Composable
fun WeatherCard(temperature: String, condition: String, precipitation: String, location: String) {
        Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .background(
                                                brush =
                                                        Brush.linearGradient(
                                                                colors =
                                                                        listOf(
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary,
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primaryContainer
                                                                        )
                                                        )
                                        )
                                        .padding(20.dp)
                ) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Column {
                                        Text(
                                                text = temperature,
                                                style = MaterialTheme.typography.displayMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                        )
                                        Text(
                                                text = condition,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color.White.copy(alpha = 0.9f)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.WaterDrop,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = Color.White.copy(alpha = 0.8f)
                                                )
                                                Text(
                                                        text = "Precipitation: $precipitation",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color.White.copy(alpha = 0.8f)
                                                )
                                        }
                                }

                                Box(
                                        modifier =
                                                Modifier.size(72.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.White.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Icon(
                                                imageVector =
                                                        when {
                                                                condition.contains("Rain", true) ->
                                                                        Icons.Default.WaterDrop
                                                                condition.contains("Hot", true) ->
                                                                        Icons.Default.WbSunny
                                                                condition.contains("Cold", true) ->
                                                                        Icons.Default.AcUnit
                                                                else -> Icons.Default.WbSunny
                                                        },
                                                contentDescription = condition,
                                                modifier = Modifier.size(40.dp),
                                                tint = Color.White
                                        )
                                }
                        }
                }
        }
}

@Composable
fun QuickActionsRow(navController: NavController) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                QuickActionButton(
                        icon = Icons.Default.Map,
                        label = "Flood Map",
                        onClick = { navController.navigate(Screen.Map.route) },
                        tint = FloodColor
                )
                QuickActionButton(
                        icon = Icons.Default.LocalHospital,
                        label = "Rescue",
                        onClick = { navController.navigate(Screen.Rescue.route) },
                        tint = DangerRed
                )
                QuickActionButton(
                        icon = Icons.Default.Notifications,
                        label = "Alerts",
                        onClick = { navController.navigate(Screen.Alerts.route) },
                        tint = WarningOrange
                )
                QuickActionButton(
                        icon = Icons.Default.Settings,
                        label = "Settings",
                        onClick = { navController.navigate(Screen.Settings.route) },
                        tint = MaterialTheme.colorScheme.primary
                )
        }
}

@Composable
fun EmergencyNumbersRow(context: android.content.Context) {
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                EmergencyNumberCard(
                        name = "Rescue 1122",
                        number = "1122",
                        color = DangerRed,
                        onClick = {
                                val intent =
                                        Intent(Intent.ACTION_DIAL).apply {
                                                data = Uri.parse("tel:1122")
                                        }
                                context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f)
                )
                EmergencyNumberCard(
                        name = "NDMA",
                        number = "1166",
                        color = InfoBlue,
                        onClick = {
                                val intent =
                                        Intent(Intent.ACTION_DIAL).apply {
                                                data = Uri.parse("tel:1166")
                                        }
                                context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f)
                )
                EmergencyNumberCard(
                        name = "Police",
                        number = "15",
                        color = SafeGreen,
                        onClick = {
                                val intent =
                                        Intent(Intent.ACTION_DIAL).apply {
                                                data = Uri.parse("tel:15")
                                        }
                                context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f)
                )
        }
}

@Composable
fun EmergencyNumberCard(
        name: String,
        number: String,
        color: Color,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
) {
        Card(
                modifier = modifier.clickable { onClick() },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
        ) {
                Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                        Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                                text = number,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = color
                        )
                        Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                }
        }
}

@Composable
fun EmptyAlertsCard() {
        Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SafeGreen.copy(alpha = 0.1f))
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                        Box(
                                modifier =
                                        Modifier.size(48.dp)
                                                .clip(CircleShape)
                                                .background(SafeGreen.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                        ) {
                                Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = SafeGreen,
                                        modifier = Modifier.size(28.dp)
                                )
                        }
                        Column {
                                Text(
                                        text = "All Clear",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = SafeGreen
                                )
                                Text(
                                        text = "No active disasters in Pakistan",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }
                }
        }
}
