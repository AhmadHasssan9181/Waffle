package com.noobdev.waffle.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.noobdev.waffle.data.model.StatusLevel
import kotlin.math.roundToInt
import com.noobdev.waffle.design.theme.DangerRed
import com.noobdev.waffle.design.theme.EarthquakeColor
import com.noobdev.waffle.design.theme.FloodColor
import com.noobdev.waffle.design.theme.Honey
import com.noobdev.waffle.design.theme.InfoBlue
import com.noobdev.waffle.design.theme.SafeGreen
import com.noobdev.waffle.design.theme.StormColor
import com.noobdev.waffle.design.theme.WaffleTextStyles
import com.noobdev.waffle.design.theme.WarningOrange
import com.noobdev.waffle.design.theme.WildfireColor

/** One spacing scale for the whole app. Use these instead of ad-hoc dp values. */
object WaffleSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}

// ============================================
// STATUS
// ============================================

@Composable
fun statusColor(status: StatusLevel): Color =
        when (status) {
            StatusLevel.CRITICAL -> DangerRed
            StatusLevel.WARNING -> WarningOrange
            StatusLevel.INFO -> InfoBlue
            StatusLevel.SAFE -> SafeGreen
        }

/**
 * A quiet pill: tinted background, solid dot, coloured text. Reads as a label rather than a
 * blocking red slab, which keeps the screen calm when several alerts are visible.
 */
@Composable
fun StatusBadge(status: StatusLevel, modifier: Modifier = Modifier) {
    val color = statusColor(status)
    val label =
            when (status) {
                StatusLevel.CRITICAL -> "CRITICAL"
                StatusLevel.WARNING -> "WARNING"
                StatusLevel.INFO -> "INFO"
                StatusLevel.SAFE -> "SAFE"
            }

    Surface(
            modifier = modifier,
            shape = RoundedCornerShape(50),
            color = color.copy(alpha = 0.14f)
    ) {
        Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Text(
                    text = label,
                    style = WaffleTextStyles.statusBadge,
                    color = color,
                    maxLines = 1,
                    softWrap = false
            )
        }
    }
}

@Composable
fun disasterIcon(type: String?): ImageVector =
        when (type?.lowercase()) {
            "earthquake" -> Icons.Default.Public
            "flood" -> Icons.Default.WaterDrop
            "wildfire" -> Icons.Default.Whatshot
            "storm" -> Icons.Default.Thunderstorm
            else -> Icons.Default.Warning
        }

@Composable
fun disasterColor(type: String?): Color =
        when (type?.lowercase()) {
            "earthquake" -> EarthquakeColor
            "flood" -> FloodColor
            "wildfire" -> WildfireColor
            "storm" -> StormColor
            else -> InfoBlue
        }

@Composable
fun DisasterTypeBadge(type: String?, modifier: Modifier = Modifier) {
    val color = disasterColor(type)
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = color.copy(alpha = 0.12f)) {
        Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(disasterIcon(type), null, Modifier.size(13.dp), tint = color)
            Text(
                    text = (type ?: "alert").replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = color
            )
        }
    }
}

// ============================================
// SURFACES
// ============================================

/**
 * Standard card. A hairline outline instead of a drop shadow keeps the UI calm and works in both
 * themes; heavy elevation in a dark UI reads as mud.
 */
@Composable
fun WaffleCard(
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
        content: @Composable ColumnScope.() -> Unit
) {
    Surface(
            modifier =
                    modifier.fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .then(
                                    if (onClick != null) Modifier.clickable { onClick() }
                                    else Modifier
                            ),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            )
    ) {
        Column(modifier = Modifier.padding(WaffleSpace.lg), content = content)
    }
}

@Composable
fun SoftDivider(modifier: Modifier = Modifier) {
    Box(
            modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    )
}

// ============================================
// ALERT CARD
// ============================================

@Composable
fun DisasterAlertCard(
        title: String?,
        location: String?,
        status: StatusLevel?,
        disasterType: String?,
        timestamp: String?,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        distanceKm: Double? = null
) {
    val level = status ?: StatusLevel.INFO
    val accent = statusColor(level)

    Surface(
            modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable { onClick() },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border =
                    androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    )
    ) {
        Column(Modifier.padding(WaffleSpace.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                        modifier =
                                Modifier.size(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(disasterColor(disasterType).copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center
                ) {
                    Icon(
                            disasterIcon(disasterType),
                            contentDescription = null,
                            tint = disasterColor(disasterType),
                            modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.width(WaffleSpace.md))
                Column(Modifier.weight(1f)) {
                    Text(
                            text = title ?: "Alert",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                    )
                    if (!location.isNullOrBlank() || distanceKm != null) {
                        Spacer(Modifier.height(2.dp))
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                    Icons.Default.LocationOn,
                                    null,
                                    Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                    text = location.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                            )
                            if (distanceKm != null) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                        text = formatDistanceKm(distanceKm),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                )
                            }
                        }
                    }
                }
                Icon(
                        Icons.Rounded.ChevronRight,
                        null,
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(Modifier.height(WaffleSpace.md))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                        Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(accent)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                        text =
                                when (level) {
                                    StatusLevel.CRITICAL -> "Critical"
                                    StatusLevel.WARNING -> "Warning"
                                    StatusLevel.INFO -> "Update"
                                    StatusLevel.SAFE -> "Resolved"
                                },
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                )
                Spacer(Modifier.width(WaffleSpace.sm))
                Text(
                        text = "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(WaffleSpace.sm))
                Text(
                        text = typeLabel(disasterType),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                if (!timestamp.isNullOrBlank()) {
                    Text(
                            text = timestamp,
                            style = WaffleTextStyles.timestamp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun typeLabel(type: String?): String =
        when (type?.lowercase()) {
            "earthquake" -> "Earthquake"
            "flood" -> "Flood"
            "wildfire" -> "Wildfire"
            "storm" -> "Storm"
            null, "" -> "General"
            else -> type.replaceFirstChar { it.uppercase() }
        }

// ============================================
// METRICS
// ============================================

@Composable
fun MetricCard(
        value: String,
        label: String,
        icon: ImageVector,
        iconTint: Color = MaterialTheme.colorScheme.primary,
        modifier: Modifier = Modifier
) {
    WaffleCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                    modifier =
                            Modifier.size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(iconTint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(21.dp), tint = iconTint)
            }
            Spacer(Modifier.width(WaffleSpace.md))
            Column {
                Text(
                        text = value,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ============================================
// BUTTONS
// ============================================

@Composable
fun WafflePrimaryButton(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        icon: ImageVector? = null
) {
    Button(
            onClick = onClick,
            modifier = modifier.height(52.dp),
            enabled = enabled,
            shape = RoundedCornerShape(16.dp),
            colors =
                    ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                    )
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(19.dp))
            Spacer(Modifier.width(WaffleSpace.sm))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun WaffleSecondaryButton(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        icon: ImageVector? = null
) {
    OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(52.dp),
            enabled = enabled,
            shape = RoundedCornerShape(16.dp),
            border =
                    androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline
                    ),
            colors =
                    ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                    )
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(19.dp))
            Spacer(Modifier.width(WaffleSpace.sm))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun EmergencyButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
            onClick = onClick,
            modifier = modifier.height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = Color.White)
    ) {
        Icon(Icons.Default.Warning, null, Modifier.size(20.dp))
        Spacer(Modifier.width(WaffleSpace.sm))
        Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun QuickActionButton(
        icon: ImageVector,
        label: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        tint: Color = MaterialTheme.colorScheme.primary
) {
    Column(
            modifier = modifier.clip(RoundedCornerShape(18.dp)).clickable { onClick() }.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
                modifier =
                        Modifier.size(54.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, Modifier.size(25.dp), tint = tint)
        }
        Spacer(Modifier.height(WaffleSpace.sm))
        Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Large tappable tile for the home grid: icon, title and a one-line reason to tap it.
 */
@Composable
fun ActionTile(
        icon: ImageVector,
        title: String,
        subtitle: String,
        tint: Color,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
) {
    Surface(
            modifier = modifier.clip(MaterialTheme.shapes.large).clickable { onClick() },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border =
                    androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    )
    ) {
        Column(Modifier.padding(WaffleSpace.lg)) {
            Box(
                    modifier =
                            Modifier.size(42.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(22.dp), tint = tint)
            }
            Spacer(Modifier.height(WaffleSpace.md))
            Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ============================================
// LOADING
// ============================================

@Composable
fun WaffleLoadingSpinner(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    CircularProgressIndicator(
            modifier = modifier.size(size),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp
    )
}

@Composable
fun LoadingOverlay(
        isLoading: Boolean,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
        if (isLoading) {
            Box(
                    modifier =
                            Modifier.matchParentSize()
                                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
            ) { WaffleLoadingSpinner() }
        }
    }
}

// ============================================
// HEADERS
// ============================================

@Composable
fun SectionHeader(
        title: String,
        modifier: Modifier = Modifier,
        action: (@Composable () -> Unit)? = null
) {
    Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
        )
        action?.invoke()
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
            text = text.uppercase(),
            modifier = modifier,
            style = WaffleTextStyles.eyebrow,
            color = color
    )
}

// ============================================
// EMPTY STATE
// ============================================

@Composable
fun EmptyState(
        icon: ImageVector,
        title: String,
        message: String,
        modifier: Modifier = Modifier,
        tint: Color = SafeGreen
) {
    WaffleCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                    modifier =
                            Modifier.size(46.dp)
                                    .clip(CircleShape)
                                    .background(tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(24.dp), tint = tint)
            }
            Spacer(Modifier.width(WaffleSpace.md))
            Column {
                Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AllClearCard(message: String = "No active disasters near you") {
    EmptyState(
            icon = Icons.Default.CheckCircle,
            title = "All clear",
            message = message,
            tint = SafeGreen
    )
}

// ============================================
// INFO ROW
// ============================================

@Composable
fun InfoRow(
        label: String,
        value: String,
        modifier: Modifier = Modifier,
        valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

/** "840 m" / "12.4 km" / "8412 km" — one formatter so every screen rounds the same way. */
fun formatDistanceKm(km: Double): String =
        when {
            km < 1.0 -> "${(km * 1000).roundToInt()} m"
            km < 9.95 -> String.format("%.1f km", km)
            else -> "${km.roundToInt()} km"
        }
