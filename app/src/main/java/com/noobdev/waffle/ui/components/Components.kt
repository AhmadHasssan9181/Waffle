package com.noobdev.waffle.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.noobdev.waffle.ui.theme.*

// ============================================
// STATUS BADGES
// ============================================

enum class StatusLevel {
    CRITICAL,
    WARNING,
    MONITORING,
    SAFE
}

@Composable
fun StatusBadge(status: StatusLevel, modifier: Modifier = Modifier) {
    val (backgroundColor, textColor, label) =
            when (status) {
                StatusLevel.CRITICAL -> Triple(DangerRed, Color.White, "CRITICAL")
                StatusLevel.WARNING -> Triple(WarningOrange, Color.Black, "WARNING")
                StatusLevel.MONITORING -> Triple(InfoBlue, Color.White, "MONITORING")
                StatusLevel.SAFE -> Triple(SafeGreen, Color.White, "SAFE")
            }

    Surface(modifier = modifier, shape = RoundedCornerShape(4.dp), color = backgroundColor) {
        Text(
                text = label,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = WaffleTextStyles.statusBadge,
                color = textColor
        )
    }
}

@Composable
fun DisasterTypeBadge(type: String, modifier: Modifier = Modifier) {
    val (color, icon) =
            when (type.lowercase()) {
                "earthquake" -> EarthquakeColor to Icons.Default.Public
                "flood" -> FloodColor to Icons.Default.WaterDrop
                "wildfire" -> WildfireColor to Icons.Default.Whatshot
                "storm" -> StormColor to Icons.Default.Thunderstorm
                else -> InfoBlue to Icons.Default.Warning
            }

    Surface(
            modifier = modifier,
            shape = RoundedCornerShape(8.dp),
            color = color.copy(alpha = 0.15f)
    ) {
        Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = color
            )
            Text(
                    text = type.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================
// DISASTER CARDS
// ============================================

@Composable
fun WaffleCard(
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
        content: @Composable ColumnScope.() -> Unit
) {
    Card(
            modifier =
                    modifier.fillMaxWidth()
                            .then(
                                    if (onClick != null) Modifier.clickable { onClick() }
                                    else Modifier
                            ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) { Column(modifier = Modifier.padding(16.dp), content = content) }
}

@Composable
fun DisasterAlertCard(
        title: String,
        location: String,
        status: StatusLevel,
        disasterType: String,
        timestamp: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
) {
    val borderColor =
            when (status) {
                StatusLevel.CRITICAL -> DangerRed
                StatusLevel.WARNING -> WarningOrange
                StatusLevel.MONITORING -> InfoBlue
                StatusLevel.SAFE -> SafeGreen
            }

    Card(
            modifier =
                    modifier.fillMaxWidth()
                            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
                            .clickable { onClick() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                DisasterTypeBadge(type = disasterType)
                StatusBadge(status = status)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                        text = location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                    text = timestamp,
                    style = WaffleTextStyles.timestamp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============================================
// METRIC CARDS
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
        Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                    modifier =
                            Modifier.size(48.dp)
                                    .background(
                                            color = iconTint.copy(alpha = 0.15f),
                                            shape = CircleShape
                                    ),
                    contentAlignment = Alignment.Center
            ) {
                Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                )
            }

            Column {
                Text(
                        text = value,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
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
            shape = RoundedCornerShape(12.dp),
            border = ButtonDefaults.outlinedButtonBorder
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun EmergencyButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
            onClick = onClick,
            modifier = modifier.height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
    ) {
        Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
        )
    }
}

// ============================================
// QUICK ACTION BUTTONS
// ============================================

@Composable
fun QuickActionButton(
        icon: ImageVector,
        label: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        tint: Color = MaterialTheme.colorScheme.primary
) {
    Column(
            modifier =
                    modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
                modifier =
                        Modifier.size(56.dp)
                                .background(color = tint.copy(alpha = 0.15f), shape = CircleShape),
                contentAlignment = Alignment.Center
        ) {
            Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ============================================
// LOADING STATES
// ============================================

@Composable
fun WaffleLoadingSpinner(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    CircularProgressIndicator(
            modifier = modifier.size(size),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp
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
                                    .background(
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.7f)
                                    ),
                    contentAlignment = Alignment.Center
            ) { WaffleLoadingSpinner() }
        }
    }
}

// ============================================
// SECTION HEADERS
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
        )
        action?.invoke()
    }
}

// ============================================
// INFO ROW (for details)
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
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}
