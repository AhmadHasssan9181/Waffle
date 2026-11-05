package com.noobdev.waffle.feature.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.noobdev.waffle.design.components.Eyebrow
import com.noobdev.waffle.design.components.WaffleSpace
import com.noobdev.waffle.feature.map.MapStyleResolver
import com.noobdev.waffle.feature.offline.InstalledOfflineRegion
import com.noobdev.waffle.feature.offline.OfflineDownloadState
import com.noobdev.waffle.feature.offline.OfflineDownloadStatus
import com.noobdev.waffle.feature.offline.OfflineDownloadTarget
import com.noobdev.waffle.feature.offline.OfflineLayer
import com.noobdev.waffle.feature.offline.OfflineMapManager
import com.noobdev.waffle.feature.offline.OfflineRegionCatalog
import com.noobdev.waffle.feature.offline.OfflineRegionPack
import com.noobdev.waffle.core.network.NetworkState
import com.noobdev.waffle.core.storage.StorageUtils
import kotlinx.coroutines.launch

/**
 * Google-Maps-style offline area manager.
 *
 * Every area can be downloaded in several layers. Street/terrain/dark are vector styles and stay
 * small; satellite is raster and an order of magnitude larger, so it has a lower zoom cap and an
 * explicit confirmation on mobile data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapsScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val downloads by OfflineMapManager.downloads.collectAsState()
    val installed by OfflineMapManager.installed.collectAsState()

    var pendingTarget by remember { mutableStateOf<OfflineDownloadTarget?>(null) }
    var isPreparing by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { OfflineMapManager.refresh(context) }

    val completedIds = downloads.filterValues { it.state == OfflineDownloadState.COMPLETE }.keys
    LaunchedEffect(completedIds) {
        if (completedIds.isNotEmpty()) OfflineMapManager.refresh(context)
    }

    fun performDownload(target: OfflineDownloadTarget) {
        scope.launch {
            isPreparing = target.id
            val result =
                    OfflineMapManager.download(
                            context = context,
                            target = target,
                            styleUrl = MapStyleResolver.styleUrlFor(target.layer)
                    )
            isPreparing = null
            result
                    .onSuccess { OfflineDownloadWorker.start(context) }
                    .onFailure { failure ->
                        snackbarHostState.showSnackbar(
                                failure.message ?: "Download could not start"
                        )
                    }
        }
    }

    fun startDownload(target: OfflineDownloadTarget) {
        scope.launch {
            if (!NetworkState.isOnline(context)) {
                snackbarHostState.showSnackbar("No internet connection. Connect once to download this area.")
                return@launch
            }
            if (!StorageUtils.hasRoomFor(context, target.estimatedMegabytes)) {
                val free = StorageUtils.formatBytes(StorageUtils.freeBytes(context))
                snackbarHostState.showSnackbar(
                        "Not enough space. ${target.displayName} needs about ${target.estimatedMegabytes} MB and only $free is free."
                )
                return@launch
            }
            if (!NetworkState.isUnmetered(context) && target.estimatedMegabytes >= 25) {
                pendingTarget = target
                return@launch
            }
            performDownload(target)
        }
    }

    Scaffold(
            topBar = { OfflineHeader(onBack = { navController.popBackStack() }) },
            snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { OfflineIntroCard(installed) }

            items(OfflineRegionCatalog.all, key = { it.id }) { pack ->
                OfflineAreaCard(
                        pack = pack,
                        downloads = downloads,
                        installed = installed,
                        preparingId = isPreparing,
                        onDownload = ::startDownload,
                        onPause = { OfflineMapManager.pause(it) },
                        onResume = { OfflineMapManager.resume(it) },
                        onDelete = { targetId ->
                            scope.launch {
                                OfflineMapManager.delete(context, targetId)
                                        .onFailure {
                                            snackbarHostState.showSnackbar(
                                                    it.message ?: "Could not delete pack"
                                            )
                                        }
                            }
                        }
                )
            }

            item {
                Text(
                        text =
                                "Tip: download the map style you navigate with. When the phone loses signal, Waffle automatically falls back to a downloaded layer instead of a blank map.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            item {
                Text(
                        text =
                                "Satellite packs are much larger than street packs, so they stop at a lower zoom. Download them on Wi-Fi before a trip or storm season.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }

    pendingTarget?.let { target ->
        AlertDialog(
                onDismissRequest = { pendingTarget = null },
                icon = { Icon(Icons.Default.Wifi, contentDescription = null) },
                title = { Text("Download on mobile data?") },
                text = {
                    Text(
                            "${target.displayName} is about ${target.estimatedMegabytes} MB. " +
                                    "Waiting for Wi-Fi is kinder to your data plan."
                    )
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                pendingTarget = null
                                performDownload(target)
                            }
                    ) { Text("Download anyway") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingTarget = null }) { Text("Wait for Wi-Fi") }
                }
        )
    }
}

@Composable
private fun OfflineHeader(onBack: () -> Unit) {
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .statusBarsPadding()
                            .padding(start = 4.dp, end = WaffleSpace.lg, top = WaffleSpace.sm),
            verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(Modifier.width(WaffleSpace.xs))
        Column {
            Eyebrow("No signal required")
            Text(
                    text = "Offline maps",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun OfflineIntroCard(installed: List<InstalledOfflineRegion>) {
    val totalBytes = installed.sumOf { it.downloadedBytes }
    val freeBytes = StorageUtils.freeBytes(androidx.compose.ui.platform.LocalContext.current)

    Card(
            colors =
                    CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
            shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.width(10.dp))
                Text(
                        "Maps that survive a blackout",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                    "Download an area before you travel. Streets packs are compact; satellite " +
                            "imagery is much larger and is capped at a lower zoom.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatChip(
                        icon = Icons.Default.Storage,
                        label = if (installed.isEmpty()) "No areas saved"
                        else "${installed.size} layer${if (installed.size == 1) "" else "s"} · ${StorageUtils.formatBytes(totalBytes)}"
                )
                StatChip(
                        icon = Icons.Default.CheckCircle,
                        label = "${StorageUtils.formatBytes(freeBytes)} free"
                )
            }
        }
    }
}

@Composable
private fun StatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
        Spacer(Modifier.width(4.dp))
        Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun OfflineAreaCard(
        pack: OfflineRegionPack,
        downloads: Map<String, OfflineDownloadStatus>,
        installed: List<InstalledOfflineRegion>,
        preparingId: String?,
        onDownload: (OfflineDownloadTarget) -> Unit,
        onPause: (String) -> Unit,
        onResume: (String) -> Unit,
        onDelete: (String) -> Unit
) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                        Icons.Default.SatelliteAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(pack.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                            pack.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                        "z${pack.minZoom.toInt()}–${pack.maxZoom.toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            OfflineLayer.entries.forEach { layer ->
                val target = pack.target(layer)
                val status = downloads[target.id]
                val installedRegion = installed.firstOrNull { it.id == target.id }
                OfflineLayerRow(
                        target = target,
                        status = status,
                        installedRegion = installedRegion,
                        isPreparing = preparingId == target.id,
                        onDownload = { onDownload(target) },
                        onPause = { onPause(target.id) },
                        onResume = { onResume(target.id) },
                        onDelete = { onDelete(target.id) }
                )
                if (layer != OfflineLayer.entries.last()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun OfflineLayerRow(
        target: OfflineDownloadTarget,
        status: OfflineDownloadStatus?,
        installedRegion: InstalledOfflineRegion?,
        isPreparing: Boolean,
        onDownload: () -> Unit,
        onPause: () -> Unit,
        onResume: () -> Unit,
        onDelete: () -> Unit
) {
    val isComplete = installedRegion?.complete == true || status?.state == OfflineDownloadState.COMPLETE
    val isDownloading =
            status?.state == OfflineDownloadState.DOWNLOADING ||
                    status?.state == OfflineDownloadState.QUEUED
    val isPaused = status?.state == OfflineDownloadState.PAUSED
    val hasFailed = status?.state == OfflineDownloadState.FAILED

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                            target.layer.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                    )
                    if (target.layer == OfflineLayer.SATELLITE) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                                "LARGE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier =
                                        Modifier.background(
                                                        MaterialTheme.colorScheme.tertiaryContainer,
                                                        RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                        "${target.layer.detail} · ~${target.estimatedMegabytes}\u00A0MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasFailed && !status?.error.isNullOrBlank()) {
                    Text(
                            status?.error.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                    )
                }
            }

            when {
                isPreparing -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                isComplete ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Downloaded",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                            )
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete ${target.displayName}")
                            }
                        }
                isDownloading ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onPause) {
                                Icon(Icons.Default.Pause, contentDescription = "Pause download")
                            }
                        }
                isPaused ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onResume) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Resume download")
                            }
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete ${target.displayName}")
                            }
                        }
                hasFailed ->
                        Button(onClick = onDownload, contentPadding = PaddingValues(horizontal = 12.dp)) {
                            Text("Retry")
                        }
                else ->
                        Button(onClick = onDownload, contentPadding = PaddingValues(horizontal = 12.dp)) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Download")
                        }
            }
        }

        if (isDownloading || isPaused) {
            Spacer(Modifier.height(8.dp))
            val progress = status?.progress ?: 0f
            if (progress > 0f) {
                LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(4.dp))
            val downloaded = status?.downloadedBytes ?: 0L
            Text(
                    text =
                            if (downloaded > 0) {
                                "${StorageUtils.formatBytes(downloaded)} downloaded · ${(progress * 100).toInt()}%"
                            } else {
                                "Preparing download…"
                            },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
