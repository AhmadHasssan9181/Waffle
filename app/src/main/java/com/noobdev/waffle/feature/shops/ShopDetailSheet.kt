package com.noobdev.waffle.feature.shops

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.noobdev.waffle.Screen
import com.noobdev.waffle.design.brand.WaffleMark
import com.noobdev.waffle.design.components.Eyebrow
import com.noobdev.waffle.design.components.WaffleSpace
import com.noobdev.waffle.design.theme.SafeGreen
import com.noobdev.waffle.feature.map.MapFocus
import com.noobdev.waffle.feature.map.MapTarget
import com.noobdev.waffle.feature.shops.data.Shop
import com.noobdev.waffle.feature.shops.data.ShopCategory
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopDetailSheet(
        shop: Shop,
        onDismiss: () -> Unit,
        onDirections: () -> Unit,
        primaryLabel: String = "Show on map"
) {
    val context = LocalContext.current

    ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(Modifier.padding(bottom = WaffleSpace.xxl)) {
            val hasPhoto = !shop.photoUrl.isNullOrBlank()
            Box(
                    Modifier.fillMaxWidth()
                            .padding(horizontal = WaffleSpace.lg)
                            .then(
                                    if (hasPhoto) Modifier.aspectRatio(16f / 10f)
                                    else Modifier.height(132.dp)
                            )
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                    Brush.linearGradient(
                                            listOf(
                                                    shop.category.tint.copy(alpha = 0.18f),
                                                    shop.category.tint.copy(alpha = 0.05f)
                                            )
                                    )
                            )
            ) {
                if (hasPhoto) {
                    AsyncImage(
                            model = shop.photoUrl,
                            contentDescription = shop.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                    )
                    Box(
                            Modifier.fillMaxSize()
                                    .background(
                                            Brush.verticalGradient(
                                                    0.55f to Color.Transparent,
                                                    1f to Color.Black.copy(alpha = 0.45f)
                                            )
                                    )
                    )
                    if (!shop.photoCredit.isNullOrBlank()) {
                        Text(
                                text = shop.photoCredit,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier =
                                        Modifier.align(Alignment.BottomStart)
                                                .padding(10.dp)
                        )
                    }
                } else {
                    Row(
                            Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                                Modifier.size(46.dp)
                                        .clip(CircleShape)
                                        .background(shop.category.tint.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                    shop.category.icon,
                                    null,
                                    Modifier.size(23.dp),
                                    tint = shop.category.tint
                            )
                        }
                        Spacer(Modifier.width(WaffleSpace.md))
                        Column {
                            Text(
                                    text = shop.category.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                    text = "No photo of this place nearby",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Column(Modifier.padding(horizontal = WaffleSpace.xl, vertical = WaffleSpace.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                            shape = RoundedCornerShape(50),
                            color = shop.category.tint.copy(alpha = 0.13f)
                    ) {
                        Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                    shop.category.icon,
                                    null,
                                    Modifier.size(12.dp),
                                    tint = shop.category.tint
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                    shop.category.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = shop.category.tint
                            )
                        }
                    }
                    Spacer(Modifier.width(WaffleSpace.sm))
                    Text(
                            shop.distanceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(WaffleSpace.md))
                Text(
                        text = shop.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                )
                if (!shop.brand.isNullOrBlank() || !shop.cuisine.isNullOrBlank()) {
                    Text(
                            text = listOfNotNull(shop.brand, shop.cuisine).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(WaffleSpace.lg))

                if (!shop.address.isNullOrBlank()) {
                    DetailLine(Icons.Rounded.LocationOn, shop.address)
                }
                if (!shop.openingHours.isNullOrBlank()) {
                    DetailLine(Icons.Rounded.Schedule, shop.openingHours)
                }
                if (!shop.phone.isNullOrBlank()) {
                    DetailLine(Icons.Rounded.Call, shop.phone)
                }
                if (!shop.website.isNullOrBlank()) {
                    DetailLine(Icons.Rounded.Language, shop.website)
                }

                Spacer(Modifier.height(WaffleSpace.lg))

                Button(
                        onClick = onDirections,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.Directions, null, Modifier.size(19.dp))
                    Spacer(Modifier.width(WaffleSpace.sm))
                    Text(primaryLabel)
                }

                if (!shop.phone.isNullOrBlank() || !shop.website.isNullOrBlank()) {
                    Spacer(Modifier.height(WaffleSpace.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(WaffleSpace.sm)) {
                        if (!shop.phone.isNullOrBlank()) {
                            OutlinedButton(
                                    onClick = {
                                        context.startActivity(
                                                Intent(
                                                        Intent.ACTION_DIAL,
                                                        Uri.parse("tel:${shop.phone}")
                                                )
                                        )
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Call, null, Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Call")
                            }
                        }
                        if (!shop.website.isNullOrBlank()) {
                            OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(shop.website))
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Language, null, Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Website")
                            }
                        }
                    }
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
private fun DetailLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
            modifier = Modifier.padding(vertical = 4.dp),
            verticalAlignment = Alignment.Top
    ) {
        Icon(
                icon,
                null,
                Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(10.dp))
        Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
        )
    }
}
