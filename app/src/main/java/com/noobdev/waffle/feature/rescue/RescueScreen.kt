package com.noobdev.waffle.feature.rescue

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.noobdev.waffle.Screen
import com.noobdev.waffle.design.components.EmergencyButton
import com.noobdev.waffle.design.components.Eyebrow
import com.noobdev.waffle.design.components.WaffleSecondaryButton
import com.noobdev.waffle.design.components.WaffleSpace
import com.noobdev.waffle.design.theme.DangerRed
import com.noobdev.waffle.design.theme.SafeGreen
import com.noobdev.waffle.design.theme.WaffleTextStyles
import com.noobdev.waffle.feature.flood.sectors
import com.noobdev.waffle.feature.map.MapFocus
import com.noobdev.waffle.feature.map.MapTarget
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * SOS. Deliberately honest: Waffle has no dispatch backend, so this screen does not pretend to
 * send a rescue request. It dials the real services and hands a prefilled message with exact
 * coordinates to the phone's SMS app — both of which work with a weak signal.
 */
@Composable
fun RescueScreen(navController: NavController) {
    val context = LocalContext.current
    var lat by remember { mutableStateOf<Double?>(null) }
    var lon by remember { mutableStateOf<Double?>(null) }
    var note by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val fix = lastLocation(context)
        if (fix != null) {
            lat = fix.first
            lon = fix.second
        }
    }

    val currentLat = lat
    val currentLon = lon

    val coordinates =
            if (currentLat != null && currentLon != null)
                    String.format("%.4f, %.4f", currentLat, currentLon)
            else "Location unavailable"

    val message =
            buildString {
                append("SOS — I need help. ")
                if (currentLat != null && currentLon != null) {
                    append("My location: ")
                    append(String.format("%.4f, %.4f", currentLat, currentLon))
                    append(" · https://maps.google.com/?q=")
                    append(currentLat)
                    append(",")
                    append(currentLon)
                    append(". ")
                } else {
                    append("My location is not available. ")
                }
                if (note.isNotBlank()) append(note.trim())
            }

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = WaffleSpace.lg)
    ) {
        Spacer(Modifier.height(WaffleSpace.lg))
        Eyebrow("Emergency", color = DangerRed)
        Spacer(Modifier.height(4.dp))
        Text(
                text = "Rescue",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(2.dp))
        Text(
                text = "Call, or send your exact position to someone who can reach you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(WaffleSpace.xl))

        // === CALL 1122 ===
        Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.3f))
        ) {
            Column(
                    modifier =
                            Modifier.background(
                                            Brush.linearGradient(
                                                    listOf(
                                                            DangerRed.copy(alpha = 0.16f),
                                                            Color.Transparent
                                                    )
                                            )
                                    )
                                    .padding(WaffleSpace.xl)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                            Modifier.size(48.dp)
                                    .clip(CircleShape)
                                    .background(DangerRed.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                    ) {
                        Icon(
                                Icons.Rounded.HealthAndSafety,
                                null,
                                Modifier.size(24.dp),
                                tint = DangerRed
                        )
                    }
                    Spacer(Modifier.width(WaffleSpace.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                                "Rescue 1122",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                                "Punjab & Islamabad emergency service",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(WaffleSpace.lg))
                EmergencyButton(
                        text = "Call 1122",
                        onClick = { dial(context, "1122") },
                        modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(WaffleSpace.xl))

        // === FLOOD MAPS ===
        Text(
                text = "Flood maps",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(2.dp))
        Text(
                text = "Copernicus EMSR838 · historical flood depth and extent",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(WaffleSpace.sm))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(WaffleSpace.sm)) {
            items(sectors) { sector ->
                Surface(
                        onClick = {
                            MapFocus.request(
                                    MapTarget(
                                            lat = sector.lat,
                                            lon = sector.lon,
                                            label = "Flood · ${sector.name}",
                                            zoom = 9.5,
                                            floodSector = sector
                                    )
                            )
                            navController.navigate(Screen.Map.route) {
                                launchSingleTop = true
                            }
                        },
                        modifier = Modifier.width(190.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border =
                                BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                )
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Icon(
                                Icons.Rounded.Water,
                                null,
                                Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                                sector.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                                "Historical flood layer",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                                "Open map",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(WaffleSpace.xl))

        // === COORDINATES ===
        Text(
                text = "Your position",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(WaffleSpace.sm))
        Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        ) {
            Column(Modifier.padding(WaffleSpace.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                            Icons.Rounded.LocationOn,
                            null,
                            Modifier.size(17.dp),
                            tint =
                                    if (currentLat != null) SafeGreen
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                            text = coordinates,
                            style = WaffleTextStyles.coordinates,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(
                            onClick = {
                                val clipboard =
                                        context.getSystemService(Context.CLIPBOARD_SERVICE) as
                                                ClipboardManager
                                clipboard.setPrimaryClip(
                                        ClipData.newPlainText("Coordinates", coordinates)
                                )
                                Toast.makeText(context, "Coordinates copied", Toast.LENGTH_SHORT)
                                        .show()
                            }
                    ) {
                        Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = "Copy coordinates",
                                modifier = Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(WaffleSpace.sm))
                Text(
                        text =
                                if (currentLat != null) "GPS fix from this phone. Shared only when you send it."
                                else "No GPS fix yet. Move outdoors or enable location.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(WaffleSpace.md))

        // === WHAT HAPPENED ===
        Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        ) {
            Column(Modifier.padding(WaffleSpace.lg)) {
                Text(
                        text = "What happened? (optional)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(WaffleSpace.sm))
                BasicTextField(
                        value = note,
                        onValueChange = { note = it },
                        textStyle =
                                MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (note.isBlank()) {
                                Text(
                                        text = "e.g. 3 people trapped, one injured, water rising",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            inner()
                        }
                )
            }
        }

        Spacer(Modifier.height(WaffleSpace.md))

        WaffleSecondaryButton(
                text = "Send by SMS",
                onClick = { sendSms(context, message) },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Message
        )

        Spacer(Modifier.height(WaffleSpace.xl))

        // === ALL EMERGENCY NUMBERS ===
        Text(
                text = "Emergency numbers",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(WaffleSpace.sm))
        Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        ) {
            Column {
                NumberRow("Rescue", "1122", DangerRed)
                NumberRow("NDMA", "1166", MaterialTheme.colorScheme.primary)
                NumberRow("Edhi Ambulance", "115", SafeGreen)
                NumberRow("Police", "15", MaterialTheme.colorScheme.secondary)
            }
        }

        Spacer(Modifier.height(WaffleSpace.xl))

        // === WHILE YOU WAIT ===
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                    Icons.Rounded.Shield,
                    null,
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                    text = "While you wait",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(Modifier.height(WaffleSpace.sm))
        Column(verticalArrangement = Arrangement.spacedBy(WaffleSpace.sm)) {
            Tip("Move to higher ground if water is rising — never walk through moving water.")
            Tip("Stay where rescuers can see you. Turn on your phone's flashlight at night.")
            Tip("Keep your phone alive: lower brightness, close other apps.")
            Tip("Conserve battery and keep this screen open for your coordinates.")
        }

        Spacer(Modifier.height(110.dp))
    }
}

@Composable
private fun NumberRow(label: String, number: String, tint: Color) {
    val context = LocalContext.current
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Transparent)
                            .padding(WaffleSpace.lg),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
                Modifier.size(34.dp).clip(CircleShape).background(tint.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Phone, null, Modifier.size(16.dp), tint = tint)
        }
        Spacer(Modifier.width(WaffleSpace.md))
        Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
        )
        Text(
                text = number,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(WaffleSpace.md))
        Surface(
                modifier =
                        Modifier.clip(RoundedCornerShape(10.dp))
                                .background(tint.copy(alpha = 0.13f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color.Transparent
        ) {
            Text(
                    text = "Call",
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    modifier = Modifier.clickable { dial(context, number) }
            )
        }
    }
}

@Composable
private fun Tip(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
                Modifier.size(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(top = 6.dp)
        )
        Spacer(Modifier.width(WaffleSpace.sm))
        Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ============================================
// INTENTS
// ============================================

private fun dial(context: Context, number: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    }
            .onFailure {
                Toast.makeText(context, "No dialer app found", Toast.LENGTH_SHORT).show()
            }
}

private fun sendSms(context: Context, message: String) {
    val intent =
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
                    .putExtra("sms_body", message)
    runCatching { context.startActivity(intent) }
            .onFailure {
                val fallback = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                }
                runCatching { context.startActivity(Intent.createChooser(fallback, "Send SOS")) }
                        .onFailure {
                            val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as
                                            ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("SOS", message))
                            Toast.makeText(context, "Copied SOS message", Toast.LENGTH_SHORT).show()
                        }
            }
}

private suspend fun lastLocation(context: Context): Pair<Double, Double>? {
    val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
    if (!granted) return null

    return suspendCancellableCoroutine { continuation ->
        try {
            LocationServices.getFusedLocationProviderClient(context)
                    .lastLocation
                    .addOnSuccessListener { location ->
                        continuation.resume(location?.let { it.latitude to it.longitude })
                    }
                    .addOnFailureListener { continuation.resume(null) }
        } catch (security: SecurityException) {
            continuation.resume(null)
        }
    }
}

// Extension function for formatting doubles
fun Double.format(digits: Int) = "%.${digits}f".format(this)
