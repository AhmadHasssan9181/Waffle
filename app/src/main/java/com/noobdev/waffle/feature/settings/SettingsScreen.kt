package com.noobdev.waffle.feature.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.noobdev.waffle.BuildConfig
import com.noobdev.waffle.Screen
import com.noobdev.waffle.core.prefs.PreferencesManager
import com.noobdev.waffle.core.prefs.SpeedUnit
import com.noobdev.waffle.data.model.StatusLevel
import com.noobdev.waffle.design.components.WaffleSpace
import com.noobdev.waffle.design.components.disasterColor
import com.noobdev.waffle.design.components.statusColor
import com.noobdev.waffle.design.theme.AppTheme
import com.noobdev.waffle.design.theme.DangerRed
import com.noobdev.waffle.design.theme.SafeGreen
import com.noobdev.waffle.feature.map.autoStyleIndex
import com.noobdev.waffle.feature.map.mapTilerStyles
import com.noobdev.waffle.feature.map.VehicleIcon
import com.noobdev.waffle.feature.map.VehicleIconPicker
import com.noobdev.waffle.feature.shops.data.ShopDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, preferencesManager: PreferencesManager) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val appTheme by preferencesManager.appTheme.collectAsState(initial = AppTheme.SYSTEM)
    val speedUnit by preferencesManager.speedUnit.collectAsState(initial = SpeedUnit.KMH)
    val vehicleIconKey by preferencesManager.vehicleIcon.collectAsState(initial = "SEDAN")
    val vehicleIcon = remember(vehicleIconKey) { VehicleIcon.fromKey(vehicleIconKey) }
    val mapStyleIndex by preferencesManager.mapStyleIndex.collectAsState(initial = -1)
    var floatingSizeStep by remember {
        mutableStateOf(
                context.getSharedPreferences("waffle_floating", Context.MODE_PRIVATE)
                        .getInt("size_step", 0)
        )
    }
    val floatingSizeLabel =
            when (floatingSizeStep) {
                1 -> "Compact"
                2 -> "Large"
                else -> "Balanced"
            }
    val lifecycleOwner = LocalLifecycleOwner.current
    var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer =
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        overlayAllowed = Settings.canDrawOverlays(context)
                    }
                }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val effectiveMapStyleIndex =
            if (mapStyleIndex >= 0) mapStyleIndex
            else autoStyleIndex(MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val notificationsEnabled by preferencesManager.notificationsEnabled.collectAsState(initial = true)
    val floodAlerts by preferencesManager.floodAlertsEnabled.collectAsState(initial = true)
    val earthquakeAlerts by
            preferencesManager.earthquakeAlertsEnabled.collectAsState(initial = true)
    val wildfireAlerts by preferencesManager.wildfireAlertsEnabled.collectAsState(initial = true)

    var showMapStylePicker by remember { mutableStateOf(false) }
    var showVehiclePicker by remember { mutableStateOf(false) }
    var showLicences by remember { mutableStateOf(false) }

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = WaffleSpace.lg)
    ) {
        Spacer(Modifier.height(WaffleSpace.md))

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(Modifier.width(WaffleSpace.xs))
            Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(Modifier.height(WaffleSpace.lg))

        // === APPEARANCE ===
        SettingsSection(title = "Appearance") {
            SettingsCard {
                Column(Modifier.padding(WaffleSpace.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsIcon(Icons.Rounded.Palette)
                        Spacer(Modifier.width(WaffleSpace.md))
                        Column {
                            Text(
                                    "Theme",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                    "Light, dark, or sensor auto",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(WaffleSpace.md))
                    ThemeSegmented(
                            current = appTheme,
                            onSelect = { theme ->
                                scope.launch { preferencesManager.setAppTheme(theme) }
                            }
                    )
                }

                SettingsDivider()

                SettingsItem(
                        icon = Icons.Rounded.Speed,
                        title = "Speed units",
                        subtitle = if (speedUnit == SpeedUnit.KMH) "km/h" else "mph",
                        onClick = {
                                scope.launch {
                                        preferencesManager.setSpeedUnit(
                                                if (speedUnit == SpeedUnit.KMH) SpeedUnit.MPH
                                                else SpeedUnit.KMH
                                        )
                                }
                        }
                )

                SettingsDivider()

                SettingsItem(
                        icon = Icons.Rounded.DirectionsCar,
                        title = "Vehicle icon",
                        subtitle = vehicleIcon.label,
                        onClick = { showVehiclePicker = true }
                )

                SettingsDivider()

                SettingsItem(
                        icon = Icons.Rounded.OpenInFull,
                        title = "Floating map size",
                        subtitle = "$floatingSizeLabel · tap to change",
                        onClick = {
                            floatingSizeStep = (floatingSizeStep + 1) % 3
                            context.getSharedPreferences(
                                            "waffle_floating",
                                            Context.MODE_PRIVATE
                                    )
                                    .edit()
                                    .putInt("size_step", floatingSizeStep)
                                    .apply()
                        }
                )

                SettingsDivider()

                SettingsItem(
                        icon = Icons.Rounded.OpenInFull,
                        title = "Floating navigation window",
                        subtitle =
                                if (overlayAllowed) "Allowed · tap to manage"
                                else "Allow display over other apps",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                        Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                        )
                                )
                            }
                        }
                )

                SettingsDivider()

                SettingsItem(
                        icon = Icons.Rounded.Map,
                        title = "Map style",
                        subtitle =
                                (if (mapStyleIndex < 0) "Auto · " else "") +
                                        (mapTilerStyles.keys.elementAtOrNull(
                                                effectiveMapStyleIndex
                                        ) ?: "Streets"),
                        onClick = { showMapStylePicker = true }
                )
            }
        }

        Spacer(Modifier.height(WaffleSpace.xl))

        // === OFFLINE ===
        SettingsSection(title = "Offline") {
            SettingsCard {
                SettingsItem(
                        icon = Icons.Rounded.CloudDownload,
                        title = "Offline maps",
                        subtitle = "Streets, satellite and terrain with no signal",
                        onClick = { navController.navigate(Screen.OfflineMaps.route) }
                )
            }
        }

        Spacer(Modifier.height(WaffleSpace.xl))

        // === NOTIFICATIONS ===
        SettingsSection(title = "Notifications") {
            SettingsCard {
                SettingsSwitchItem(
                        icon = Icons.Rounded.Notifications,
                        title = "Emergency alerts",
                        subtitle = "Critical warnings near you",
                        checked = notificationsEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { preferencesManager.setNotificationsEnabled(enabled) }
                        }
                )
                SettingsDivider()
                SettingsSwitchItem(
                        icon = Icons.Rounded.WaterDrop,
                        title = "Floods",
                        subtitle = "River and flash-flood warnings",
                        checked = floodAlerts,
                        tint = disasterColor("flood"),
                        onCheckedChange = { enabled ->
                            scope.launch { preferencesManager.setFloodAlertsEnabled(enabled) }
                        }
                )
                SettingsDivider()
                SettingsSwitchItem(
                        icon = Icons.Rounded.Public,
                        title = "Earthquakes",
                        subtitle = "Seismic activity in the region",
                        checked = earthquakeAlerts,
                        tint = disasterColor("earthquake"),
                        onCheckedChange = { enabled ->
                            scope.launch { preferencesManager.setEarthquakeAlertsEnabled(enabled) }
                        }
                )
                SettingsDivider()
                SettingsSwitchItem(
                        icon = Icons.Rounded.Whatshot,
                        title = "Wildfires",
                        subtitle = "Fire risk and active burns",
                        checked = wildfireAlerts,
                        tint = dangerColor(),
                        onCheckedChange = { enabled ->
                            scope.launch { preferencesManager.setWildfireAlertsEnabled(enabled) }
                        }
                )
            }
        }

        Spacer(Modifier.height(WaffleSpace.xl))

        // === EMERGENCY CONTACTS ===
        SettingsSection(title = "Emergency numbers") {
            SettingsCard {
                EmergencyContactItem("Rescue 1122", "1122", Icons.Rounded.LocalFireDepartment, dangerColor())
                SettingsDivider()
                EmergencyContactItem("NDMA", "1166", Icons.Rounded.HealthAndSafety, disasterColor("flood"))
                SettingsDivider()
                EmergencyContactItem("Police", "15", Icons.Rounded.LocalHospital, statusColor(StatusLevel.INFO))
            }
        }

        Spacer(Modifier.height(WaffleSpace.xl))

        // === ABOUT ===
        SettingsSection(title = "About") {
            SettingsCard {
                SettingsItem(
                        icon = Icons.Rounded.Info,
                        title = "Waffle",
                        subtitle = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        onClick = {}
                )
                SettingsDivider()
                SettingsItem(
                        icon = Icons.Rounded.Description,
                        title = "Data sources & licences",
                        subtitle = "OpenStreetMap · USGS · GDACS · 3D models",
                        onClick = { showLicences = true }
                )
                SettingsDivider()
                SettingsItem(
                        icon = Icons.Rounded.DeleteForever,
                        title = "Clear cached places",
                        titleColor = dangerColor(),
                        subtitle = "Keeps your downloaded maps",
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    runCatching { ShopDatabase.get(context).clearAllTables() }
                                }
                                Toast.makeText(context, "Cached places cleared", Toast.LENGTH_SHORT).show()
                            }
                        }
                )
            }
        }

        Spacer(Modifier.height(40.dp))
        Text(
                text = "Built so no one gets lost.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth().padding(bottom = WaffleSpace.xxl)
        )
    }

    if (showMapStylePicker) {
        ModalBottomSheet(
                onDismissRequest = { showMapStylePicker = false },
                containerColor = MaterialTheme.colorScheme.surface
        ) {
            MapStylePicker(
                    currentIndex = mapStyleIndex,
                    onStyleSelected = { index ->
                        scope.launch {
                            preferencesManager.setMapStyleIndex(index)
                            showMapStylePicker = false
                        }
                    }
            )
        }
    }

    if (showVehiclePicker) {
        ModalBottomSheet(
                onDismissRequest = { showVehiclePicker = false },
                containerColor = MaterialTheme.colorScheme.surface
        ) {
            VehicleIconPicker(
                    current = vehicleIcon,
                    onSelect = { icon ->
                        scope.launch {
                            preferencesManager.setVehicleIcon(icon.storageKey)
                            showVehiclePicker = false
                        }
                    },
                    onClose = { showVehiclePicker = false }
            )
        }
    }

    if (showLicences) {
        AlertDialog(
                onDismissRequest = { showLicences = false },
                title = { Text("Data sources & licences") },
                text = {
                    Text(
                            text =
                                    """
                                    Maps, routing and hazard data
                                    • OpenStreetMap contributors — ODbL
                                    • MapLibre, GraphHopper, OpenRouteService
                                    • USGS, GDACS, Wikimedia

                                    3D vehicle and pedestrian models
                                    • Kenney Car Kit — CC0 1.0
                                    • Quaternius motorcycle — CC0 1.0
                                    • Hoodie pedestrian — original Waffle asset
                                    • "Bicycle" by Poly by Google, via Poly Pizza — CC BY 3.0
                                      https://creativecommons.org/licenses/by/3.0/
                                    """.trimIndent(),
                            modifier =
                                    Modifier.heightIn(max = 420.dp)
                                            .verticalScroll(rememberScrollState())
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showLicences = false }) { Text("Close") }
                }
        )
    }
}

@Composable
private fun dangerColor(): Color = DangerRed

// ============================================
// BUILDING BLOCKS
// ============================================

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = WaffleSpace.sm, start = WaffleSpace.xs)
        )
        content()
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) { Column(content = content) }
}

@Composable
private fun SettingsDivider() {
    Box(
            Modifier.fillMaxWidth()
                    .padding(start = 56.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    )
}

@Composable
private fun SettingsIcon(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(
            modifier =
                    Modifier.size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(tint.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size(19.dp), tint = tint)
    }
}

@Composable
fun SettingsItem(
        icon: ImageVector,
        title: String,
        subtitle: String? = null,
        titleColor: Color = MaterialTheme.colorScheme.onSurface,
        onClick: () -> Unit
) {
    Row(
            modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(WaffleSpace.lg),
            verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(WaffleSpace.md))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) {
                Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
                Icons.Rounded.ChevronRight,
                null,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun SettingsSwitchItem(
        icon: ImageVector,
        title: String,
        subtitle: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        tint: Color = MaterialTheme.colorScheme.primary
) {
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .clickable { onCheckedChange(!checked) }
                            .padding(WaffleSpace.lg),
            verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon, tint)
        Spacer(Modifier.width(WaffleSpace.md))
        Column(Modifier.weight(1f)) {
            Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors =
                        SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
        )
    }
}

@Composable
fun EmergencyContactItem(name: String, number: String, icon: ImageVector, iconTint: Color) {
    val context = LocalContext.current
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                                    )
                                }
                            }
                            .padding(WaffleSpace.lg),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
                modifier =
                        Modifier.size(36.dp)
                                .clip(CircleShape)
                                .background(iconTint.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = iconTint)
        }
        Spacer(Modifier.width(WaffleSpace.md))
        Column(Modifier.weight(1f)) {
            Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                    text = number,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.Rounded.Phone, "Call", Modifier.size(18.dp), tint = iconTint)
    }
}

// ============================================
// THEME
// ============================================

@Composable
private fun ThemeSegmented(current: AppTheme, onSelect: (AppTheme) -> Unit) {
    val options =
            listOf(
                    Triple(AppTheme.SYSTEM, "System", Icons.Rounded.SettingsBrightness),
                    Triple(AppTheme.LIGHT, "Light", Icons.Rounded.LightMode),
                    Triple(AppTheme.DARK, "Dark", Icons.Rounded.DarkMode),
                    Triple(AppTheme.AUTO, "Auto", Icons.Rounded.BrightnessMedium)
            )

    Row(horizontalArrangement = Arrangement.spacedBy(WaffleSpace.sm)) {
        options.forEach { (theme, label, icon) ->
            val selected = current == theme
            Surface(
                    modifier =
                            Modifier.weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onSelect(theme) },
                    shape = RoundedCornerShape(14.dp),
                    color =
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                    border =
                            BorderStroke(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            )
            ) {
                Column(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                            icon,
                            null,
                            Modifier.size(18.dp),
                            tint =
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun MapStylePicker(currentIndex: Int, onStyleSelected: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(WaffleSpace.xl)) {
        Text(
                text = "Map style",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(WaffleSpace.md))

        val options =
                listOf("Auto (match theme)" to -1) +
                        mapTilerStyles.keys.mapIndexed { index, name -> name to index }
        options.forEach { (styleName, index) ->
            val selected = index == currentIndex
            Surface(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onStyleSelected(index) },
                    shape = RoundedCornerShape(14.dp),
                    color =
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent
            ) {
                Row(
                        modifier = Modifier.padding(horizontal = WaffleSpace.lg, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            text = styleName,
                            style = MaterialTheme.typography.bodyLarge,
                            color =
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                    )
                    if (selected) {
                        Box(
                                Modifier.size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(WaffleSpace.xxl))
    }
}
