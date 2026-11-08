package com.noobdev.waffle.feature.map

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noobdev.waffle.data.remote.GeoSearchResult
import com.noobdev.waffle.data.remote.ManualPlacesStore
import com.noobdev.waffle.design.components.WaffleSpace
import java.util.Locale

/**
 * Google-Maps-style place sheet shown after tapping a search result. The result is not routed
 * automatically: the user first sees the place and chooses Directions, Save, Share or Copy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailSheet(
        place: GeoSearchResult,
        onDismiss: () -> Unit,
        onDirections: () -> Unit
) {
    val context = LocalContext.current
    var saved by remember(place.name, place.lat, place.lng) { mutableStateOf(false) }
    val readableType =
            place.type.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.ROOT) }
    val shareText =
            buildString {
                append(place.name)
                if (place.address.isNotBlank()) append("\n").append(place.address)
                append("\n")
                append(
                        "https://www.openstreetmap.org/?mlat=${place.lat}&mlon=${place.lng}" +
                                "#map=17/${place.lat}/${place.lng}"
                )
            }

    ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(Modifier.padding(bottom = WaffleSpace.xxl)) {
            Column(Modifier.padding(horizontal = WaffleSpace.xl)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
                    ) {
                        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                    Icons.Rounded.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(WaffleSpace.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                                text = place.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                        )
                        if (readableType.isNotBlank()) {
                            Text(
                                    text = readableType,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (place.address.isNotBlank()) {
                    Spacer(Modifier.height(WaffleSpace.md))
                    Text(
                            text = place.address,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(WaffleSpace.lg))

                // Four Google-Maps-style actions. They are always available so a search result
                // with no phone/website still has a complete action row.
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PlaceAction(
                            label = "Directions",
                            icon = Icons.Rounded.Directions,
                            primary = true,
                            onClick = onDirections
                    )
                    PlaceAction(
                            label = if (saved) "Saved" else "Save",
                            icon =
                                    if (saved) Icons.Rounded.Bookmark
                                    else Icons.Rounded.BookmarkBorder,
                            onClick = {
                                val ok =
                                        ManualPlacesStore.add(
                                                context,
                                                ManualPlacesStore.ManualPlace(
                                                        name = place.name,
                                                        address =
                                                                place.address.ifBlank {
                                                                        "Saved place"
                                                                },
                                                        lat = place.lat,
                                                        lng = place.lng,
                                                        type = place.type
                                                )
                                        )
                                saved = ok
                                Toast.makeText(
                                                context,
                                                if (ok) "Saved to your places"
                                                else "Could not save this place",
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                            }
                    )
                    PlaceAction(
                            label = "Share",
                            icon = Icons.Rounded.Share,
                            onClick = {
                                val send =
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                        }
                                context.startActivity(
                                        Intent.createChooser(send, "Share place")
                                )
                            }
                    )
                    PlaceAction(
                            label = "Copy",
                            icon = Icons.Rounded.ContentCopy,
                            onClick = {
                                val clipboard =
                                        context.getSystemService(Context.CLIPBOARD_SERVICE)
                                                as ClipboardManager
                                clipboard.setPrimaryClip(
                                        ClipData.newPlainText("Place", shareText)
                                )
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }
                    )
                }

                Spacer(Modifier.height(WaffleSpace.md))
                Text(
                        text = "Data © OpenStreetMap contributors · © Overture Maps Foundation",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun PlaceAction(
        label: String,
        icon: ImageVector,
        onClick: () -> Unit,
        primary: Boolean = false
) {
    val background =
            if (primary) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant
    val foreground =
            if (primary) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
            modifier = Modifier.width(78.dp).clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
                shape = CircleShape,
                color = background,
                modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = foreground, modifier = Modifier.size(23.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (primary) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
        )
    }
}
