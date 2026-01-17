package com.noobdev.waffle.Screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.noobdev.waffle.api.mapTilerStyles
import com.noobdev.waffle.ui.theme.*
import com.noobdev.waffle.utils.PreferencesManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings(navController: NavController, preferencesManager: PreferencesManager) {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        // Collect preferences
        val appTheme by preferencesManager.appTheme.collectAsState(initial = AppTheme.DEFAULT)
        val mapStyleIndex by preferencesManager.mapStyleIndex.collectAsState(initial = 0)
        val notificationsEnabled by
                preferencesManager.notificationsEnabled.collectAsState(initial = true)
        val floodAlerts by preferencesManager.floodAlertsEnabled.collectAsState(initial = true)
        val earthquakeAlerts by
                preferencesManager.earthquakeAlertsEnabled.collectAsState(initial = true)
        val wildfireAlerts by
                preferencesManager.wildfireAlertsEnabled.collectAsState(initial = true)

        var showThemePicker by remember { mutableStateOf(false) }
        var showMapStylePicker by remember { mutableStateOf(false) }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        // === HEADER ===
                        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                        Text(
                                                text = "Settings",
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                        )
                                }
                        }

                        Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                                // === APPEARANCE SECTION ===
                                SettingsSection(title = "Appearance") {
                                        SettingsCard {
                                                SettingsItem(
                                                        icon = Icons.Default.Palette,
                                                        title = "Theme",
                                                        subtitle =
                                                                appTheme.name.lowercase()
                                                                        .replaceFirstChar {
                                                                                it.uppercase()
                                                                        },
                                                        onClick = { showThemePicker = true }
                                                )

                                                HorizontalDivider(
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .outlineVariant
                                                )

                                                SettingsItem(
                                                        icon = Icons.Default.Map,
                                                        title = "Map Style",
                                                        subtitle =
                                                                mapTilerStyles.keys.toList()
                                                                        .getOrElse(mapStyleIndex) {
                                                                                "Basic"
                                                                        },
                                                        onClick = { showMapStylePicker = true }
                                                )
                                        }
                                }

                                // === NOTIFICATIONS SECTION ===
                                SettingsSection(title = "Notifications") {
                                        SettingsCard {
                                                SettingsSwitchItem(
                                                        icon = Icons.Default.Notifications,
                                                        title = "Push Notifications",
                                                        subtitle = "Receive alerts about disasters",
                                                        checked = notificationsEnabled,
                                                        onCheckedChange = {
                                                                scope.launch {
                                                                        preferencesManager
                                                                                .setNotificationsEnabled(
                                                                                        it
                                                                                )
                                                                }
                                                        }
                                                )

                                                HorizontalDivider(
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .outlineVariant
                                                )

                                                SettingsSwitchItem(
                                                        icon = Icons.Default.WaterDrop,
                                                        title = "Flood Alerts",
                                                        subtitle =
                                                                "Get notified about flood warnings",
                                                        checked = floodAlerts,
                                                        onCheckedChange = {
                                                                scope.launch {
                                                                        preferencesManager
                                                                                .setFloodAlertsEnabled(
                                                                                        it
                                                                                )
                                                                }
                                                        }
                                                )

                                                HorizontalDivider(
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .outlineVariant
                                                )

                                                SettingsSwitchItem(
                                                        icon = Icons.Default.Public,
                                                        title = "Earthquake Alerts",
                                                        subtitle =
                                                                "Get notified about seismic activity",
                                                        checked = earthquakeAlerts,
                                                        onCheckedChange = {
                                                                scope.launch {
                                                                        preferencesManager
                                                                                .setEarthquakeAlertsEnabled(
                                                                                        it
                                                                                )
                                                                }
                                                        }
                                                )

                                                HorizontalDivider(
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .outlineVariant
                                                )

                                                SettingsSwitchItem(
                                                        icon = Icons.Default.Whatshot,
                                                        title = "Wildfire Alerts",
                                                        subtitle = "Get notified about fire risks",
                                                        checked = wildfireAlerts,
                                                        onCheckedChange = {
                                                                scope.launch {
                                                                        preferencesManager
                                                                                .setWildfireAlertsEnabled(
                                                                                        it
                                                                                )
                                                                }
                                                        }
                                                )
                                        }
                                }

                                // === EMERGENCY CONTACTS SECTION ===
                                SettingsSection(title = "Emergency Contacts") {
                                        SettingsCard {
                                                EmergencyContactItem(
                                                        name = "Emergency Services",
                                                        number = "911",
                                                        icon = Icons.Default.LocalHospital,
                                                        iconTint = DangerRed
                                                )

                                                HorizontalDivider(
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .outlineVariant
                                                )

                                                EmergencyContactItem(
                                                        name = "NDMA Helpline",
                                                        number = "1166",
                                                        icon = Icons.Default.HealthAndSafety,
                                                        iconTint = InfoBlue
                                                )

                                                HorizontalDivider(
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .outlineVariant
                                                )

                                                EmergencyContactItem(
                                                        name = "Rescue 1122",
                                                        number = "1122",
                                                        icon = Icons.Default.LocalFireDepartment,
                                                        iconTint = WarningOrange
                                                )
                                        }
                                }

                                // === ABOUT SECTION ===
                                SettingsSection(title = "About") {
                                        SettingsCard {
                                                SettingsItem(
                                                        icon = Icons.Default.Info,
                                                        title = "App Version",
                                                        subtitle = "1.0.0 (Build 1)",
                                                        onClick = {}
                                                )

                                                HorizontalDivider(
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .outlineVariant
                                                )

                                                SettingsItem(
                                                        icon = Icons.Default.Description,
                                                        title = "Terms of Service",
                                                        onClick = {
                                                                Toast.makeText(
                                                                                context,
                                                                                "Opening Terms of Service",
                                                                                Toast.LENGTH_SHORT
                                                                        )
                                                                        .show()
                                                        }
                                                )

                                                HorizontalDivider(
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .outlineVariant
                                                )

                                                SettingsItem(
                                                        icon = Icons.Default.PrivacyTip,
                                                        title = "Privacy Policy",
                                                        onClick = {
                                                                Toast.makeText(
                                                                                context,
                                                                                "Opening Privacy Policy",
                                                                                Toast.LENGTH_SHORT
                                                                        )
                                                                        .show()
                                                        }
                                                )

                                                HorizontalDivider(
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .outlineVariant
                                                )

                                                SettingsItem(
                                                        icon = Icons.Default.Code,
                                                        title = "Open Source Licenses",
                                                        onClick = {}
                                                )
                                        }
                                }

                                // === DANGER ZONE ===
                                SettingsSection(title = "Data") {
                                        SettingsCard {
                                                SettingsItem(
                                                        icon = Icons.Default.DeleteForever,
                                                        title = "Clear Cache",
                                                        titleColor =
                                                                MaterialTheme.colorScheme.error,
                                                        onClick = {
                                                                Toast.makeText(
                                                                                context,
                                                                                "Cache cleared",
                                                                                Toast.LENGTH_SHORT
                                                                        )
                                                                        .show()
                                                        }
                                                )
                                        }
                                }

                                Spacer(modifier = Modifier.height(80.dp)) // Bottom padding
                        }
                }
        }

        // === THEME PICKER BOTTOM SHEET ===
        if (showThemePicker) {
                ModalBottomSheet(
                        onDismissRequest = { showThemePicker = false },
                        containerColor = MaterialTheme.colorScheme.surface
                ) {
                        ThemePicker(
                                currentTheme = appTheme,
                                onThemeSelected = { theme ->
                                        scope.launch {
                                                preferencesManager.setAppTheme(theme)
                                                showThemePicker = false
                                        }
                                }
                        )
                }
        }

        // === MAP STYLE PICKER BOTTOM SHEET ===
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
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
        Column {
                Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )
                content()
        }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
        Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors =
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) { Column(modifier = Modifier.padding(4.dp), content = content) }
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
                modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = titleColor
                        )
                        if (subtitle != null) {
                                Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }
                }

                Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
        }
}

@Composable
fun SettingsSwitchItem(
        icon: ImageVector,
        title: String,
        subtitle: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
) {
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .clickable { onCheckedChange(!checked) }
                                .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
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
                                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                                        checkedTrackColor =
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                )
        }
}

@Composable
fun EmergencyContactItem(name: String, number: String, icon: ImageVector, iconTint: Color) {
        Row(
                modifier = Modifier.fillMaxWidth().clickable { /* Dial number */}.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                Box(
                        modifier =
                                Modifier.size(40.dp)
                                        .background(iconTint.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                ) {
                        Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                        )
                }

                Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                        )
                        Text(
                                text = number,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                        )
                }

                Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Call",
                        tint = MaterialTheme.colorScheme.primary
                )
        }
}

@Composable
fun ThemePicker(currentTheme: AppTheme, onThemeSelected: (AppTheme) -> Unit) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                        text = "Choose Theme",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                val themes =
                        listOf(
                                AppTheme.DEFAULT to
                                        Triple("Default", WafflePrimary, DarkBackground),
                                AppTheme.OCEAN to Triple("Ocean", OceanPrimary, OceanBackground),
                                AppTheme.FOREST to
                                        Triple("Forest", ForestPrimary, ForestBackground),
                                AppTheme.SUNSET to
                                        Triple("Sunset", SunsetPrimary, SunsetBackground),
                                AppTheme.MIDNIGHT to
                                        Triple("Midnight", MidnightPrimary, MidnightBackground),
                                AppTheme.EMERGENCY to
                                        Triple("Emergency", EmergencyPrimary, EmergencyBackground)
                        )

                themes.forEach { (theme, details) ->
                        val (name, primary, background) = details
                        val isSelected = currentTheme == theme

                        Card(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .clickable { onThemeSelected(theme) }
                                                .then(
                                                        if (isSelected)
                                                                Modifier.border(
                                                                        2.dp,
                                                                        primary,
                                                                        RoundedCornerShape(12.dp)
                                                                )
                                                        else Modifier
                                                ),
                                shape = RoundedCornerShape(12.dp),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor =
                                                        MaterialTheme.colorScheme.surfaceVariant
                                        )
                        ) {
                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                        // Color preview
                                        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                                                Box(
                                                        modifier =
                                                                Modifier.size(32.dp)
                                                                        .clip(CircleShape)
                                                                        .background(background)
                                                                        .border(
                                                                                2.dp,
                                                                                Color.White.copy(
                                                                                        alpha = 0.3f
                                                                                ),
                                                                                CircleShape
                                                                        )
                                                )
                                                Box(
                                                        modifier =
                                                                Modifier.size(32.dp)
                                                                        .clip(CircleShape)
                                                                        .background(primary)
                                                                        .border(
                                                                                2.dp,
                                                                                Color.White.copy(
                                                                                        alpha = 0.3f
                                                                                ),
                                                                                CircleShape
                                                                        )
                                                )
                                        }

                                        Text(
                                                text = name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight =
                                                        if (isSelected) FontWeight.Bold
                                                        else FontWeight.Normal,
                                                modifier = Modifier.weight(1f)
                                        )

                                        if (isSelected) {
                                                Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "Selected",
                                                        tint = primary
                                                )
                                        }
                                }
                        }
                }

                Spacer(modifier = Modifier.height(32.dp))
        }
}

@Composable
fun MapStylePicker(currentIndex: Int, onStyleSelected: (Int) -> Unit) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                        text = "Map Style",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                val styles = mapTilerStyles.keys.toList()

                styles.forEachIndexed { index, styleName ->
                        val isSelected = index == currentIndex

                        Card(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .clickable { onStyleSelected(index) }
                                                .then(
                                                        if (isSelected)
                                                                Modifier.border(
                                                                        2.dp,
                                                                        MaterialTheme.colorScheme
                                                                                .primary,
                                                                        RoundedCornerShape(12.dp)
                                                                )
                                                        else Modifier
                                                ),
                                shape = RoundedCornerShape(12.dp),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor =
                                                        if (isSelected)
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(alpha = 0.1f)
                                                        else
                                                                MaterialTheme.colorScheme
                                                                        .surfaceVariant
                                        )
                        ) {
                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Map,
                                                contentDescription = null,
                                                tint =
                                                        if (isSelected)
                                                                MaterialTheme.colorScheme.primary
                                                        else
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                        )
                                        Text(
                                                text = styleName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight =
                                                        if (isSelected) FontWeight.Bold
                                                        else FontWeight.Normal,
                                                modifier = Modifier.weight(1f)
                                        )
                                        if (isSelected) {
                                                Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "Selected",
                                                        tint = MaterialTheme.colorScheme.primary
                                                )
                                        }
                                }
                        }
                }

                Spacer(modifier = Modifier.height(32.dp))
        }
}
