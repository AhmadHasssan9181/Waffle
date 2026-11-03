package com.noobdev.waffle.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.noobdev.waffle.feature.flood.Sector

@Composable
internal fun FloodLegend(
        sector: Sector,
        onClose: () -> Unit,
        modifier: Modifier = Modifier
) {
    Surface(
            modifier = modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 6.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                        Icons.Rounded.Water,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF4FC3F7)
                )
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            "Flood layer",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                            sector.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(
                            Icons.Rounded.Close,
                            "Close flood layer",
                            modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            if (sector.depthFile != null) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    LegendSwatch(Color(0xFF4FC3F7), "0–0.5 m")
                    LegendSwatch(Color(0xFF2196F3), "0.5–1 m")
                    LegendSwatch(Color(0xFF1A237E), "1–2 m")
                    LegendSwatch(Color(0xFFE91E63), ">2 m")
                }
            } else {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LegendSwatch(Color(0xFFB3E5FC), "Water")
                    LegendSwatch(Color(0xFFFFC107), "Roads")
                    LegendSwatch(Color.White, "Built-up")
                }
            }
            Spacer(Modifier.size(6.dp))
            Text(
                    "Copernicus EMSR838 · historical",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
                Modifier.size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape)
        )
        Spacer(Modifier.width(3.dp))
        Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
