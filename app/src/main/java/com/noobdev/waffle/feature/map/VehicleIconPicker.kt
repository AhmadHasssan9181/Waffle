package com.noobdev.waffle.feature.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.noobdev.waffle.R

/**
 * The Google-Maps-style 3D vehicle chooser. Used from Settings and from the car button on the map.
 * The caller owns the ModalBottomSheet; this is just the content.
 */
@Composable
fun VehicleIconPicker(
        current: VehicleIcon,
        onSelect: (VehicleIcon) -> Unit,
        onClose: () -> Unit
) {
    Column(
            modifier =
                    Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 24.dp)
    ) {
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                        "Choose 3D vehicle",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                        "Shown on the map during turn-by-turn navigation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VehicleIcon.entries.forEach { icon ->
                val selected = icon == current
                Surface(
                        onClick = { onSelect(icon) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        color =
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        border =
                                BorderStroke(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                )
                ) {
                    Box(modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)) {
                        if (selected) {
                            Box(
                                    modifier =
                                            Modifier.align(Alignment.TopEnd)
                                                    .padding(end = 4.dp)
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                    modifier =
                                            Modifier.size(66.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                            Brush.radialGradient(
                                                                    listOf(
                                                                            MaterialTheme.colorScheme
                                                                                    .onSurface
                                                                                    .copy(alpha = 0.08f),
                                                                            Color.Transparent
                                                                    )
                                                            )
                                                    ),
                                    contentAlignment = Alignment.Center
                            ) {
                                Image(
                                        painter = painterResource(icon.drawable),
                                        contentDescription = icon.label,
                                        modifier = Modifier.size(62.dp)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                    icon.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight =
                                            if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color =
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border =
                        BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )
        ) {
            Row(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            "Cycling & walking 3D models",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                            "Automatically used when Bike or Walk mode is selected",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Image(
                            painter = painterResource(R.drawable.ic_puck_bike_3d),
                            contentDescription = "3D Cyclist",
                            modifier = Modifier.size(40.dp)
                    )
                    Image(
                            painter = painterResource(R.drawable.ic_puck_walk_3d),
                            contentDescription = "3D Walker",
                            modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}
