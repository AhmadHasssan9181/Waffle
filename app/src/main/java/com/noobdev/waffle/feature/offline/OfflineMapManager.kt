package com.noobdev.waffle.feature.offline

import android.content.Context
import android.util.Log
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

enum class OfflineDownloadState {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETE,
    FAILED
}

data class OfflineDownloadStatus(
        val packId: String,
        val name: String,
        val state: OfflineDownloadState,
        val progress: Float = 0f,
        val downloadedBytes: Long = 0L,
        val requiredResources: Long = 0L,
        val completedResources: Long = 0L,
        val error: String? = null
)

data class InstalledOfflineRegion(
        val id: String,
        val name: String,
        val south: Double,
        val west: Double,
        val north: Double,
        val east: Double,
        val minZoom: Double,
        val maxZoom: Double,
        val downloadedBytes: Long,
        val complete: Boolean,
        val styleUrl: String?
) {
    fun contains(latitude: Double, longitude: Double): Boolean =
            latitude in south..north && longitude in west..east
}

/**
 * Thin, coroutine-friendly wrapper around MapLibre's [OfflineManager].
 *
 * MapLibre already knows how to download a style + every resource it needs. This class adds the
 * things the app cares about: stable pack ids, observable progress, pause/resume, deletion and a
 * persisted list of what is actually on disk.
 */
object OfflineMapManager {

    private const val TAG = "OfflineMapManager"

    private val _downloads = MutableStateFlow<Map<String, OfflineDownloadStatus>>(emptyMap())
    val downloads: StateFlow<Map<String, OfflineDownloadStatus>> = _downloads.asStateFlow()

    private val _installed = MutableStateFlow<List<InstalledOfflineRegion>>(emptyList())
    val installed: StateFlow<List<InstalledOfflineRegion>> = _installed.asStateFlow()

    private val openRegions = mutableMapOf<String, OfflineRegion>()

    fun installedRegionFor(latitude: Double, longitude: Double): InstalledOfflineRegion? =
            _installed.value.firstOrNull { it.complete && it.contains(latitude, longitude) }

    fun statusFor(packId: String): OfflineDownloadStatus? = _downloads.value[packId]

    /** Loads the on-disk region list. Safe to call on every screen start. */
    suspend fun refresh(context: Context) {
        val manager = OfflineManager.getInstance(context.applicationContext)
        val regions = listRegions(manager)
        val installed = mutableListOf<InstalledOfflineRegion>()

        for (region in regions) {
            val metadata = parseMetadata(region)
            val id = metadata.optString("id").takeIf { it.isNotBlank() } ?: "region-${region.id}"
            val name = metadata.optString("name").takeIf { it.isNotBlank() } ?: "Offline region"
            val status = statusOf(region)
            val definition = region.definition as? OfflineTilePyramidRegionDefinition

            openRegions[id] = region
            installed.add(
                    InstalledOfflineRegion(
                            id = id,
                            name = name,
                            south = definition?.bounds?.latitudeSouth ?: 0.0,
                            west = definition?.bounds?.longitudeWest ?: 0.0,
                            north = definition?.bounds?.latitudeNorth ?: 0.0,
                            east = definition?.bounds?.longitudeEast ?: 0.0,
                            minZoom = definition?.minZoom ?: 0.0,
                            maxZoom = definition?.maxZoom ?: 0.0,
                            downloadedBytes = status?.completedResourceSize ?: 0L,
                            complete = status?.isComplete == true,
                            styleUrl = definition?.styleURL
                    )
            )

            if (status != null && !status.isComplete) {
                attachObserver(id, name, region, status)
            }
        }

        _installed.value = installed.sortedBy { it.name }
        Log.d(TAG, "Found ${installed.size} offline region(s)")
    }

    /**
     * Starts (or resumes) a download. Returns as soon as MapLibre accepts the region; progress is
     * exposed through [downloads]. A layer that is already on disk is reported complete instantly.
     */
    suspend fun download(
            context: Context,
            target: OfflineDownloadTarget,
            styleUrl: String
    ): Result<Unit> {
        val manager = OfflineManager.getInstance(context.applicationContext)

        val installed = _installed.value.firstOrNull { it.id == target.id }
        if (installed?.complete == true) {
            _downloads.update {
                it + (target.id to OfflineDownloadStatus(
                        packId = target.id,
                        name = target.displayName,
                        state = OfflineDownloadState.COMPLETE,
                        progress = 1f
                ))
            }
            return Result.success(Unit)
        }

        // A paused/partial region is already on disk. Resume it instead of reporting it as
        // complete just because it appears in the open-region map.
        val open = openRegions[target.id]
        if (open != null) {
            open.setDownloadState(OfflineRegion.STATE_ACTIVE)
            attachObserver(target.id, target.displayName, open, null)
            _downloads.update {
                it + (target.id to OfflineDownloadStatus(
                        packId = target.id,
                        name = target.displayName,
                        state = OfflineDownloadState.DOWNLOADING
                ))
            }
            return Result.success(Unit)
        }

        _downloads.update {
            it + (target.id to OfflineDownloadStatus(
                    packId = target.id,
                    name = target.displayName,
                    state = OfflineDownloadState.QUEUED
            ))
        }

        val pack = target.pack
        val definition = OfflineTilePyramidRegionDefinition(
                styleUrl,
                LatLngBounds.from(pack.south, pack.west, pack.north, pack.east),
                target.minZoom,
                target.maxZoom,
                context.resources.displayMetrics.density
        )
        val metadata = JSONObject()
                .put("id", target.id)
                .put("name", target.displayName)
                .put("packId", pack.id)
                .put("layer", target.layer.key)
                .toString()
                .toByteArray(Charsets.UTF_8)

        return suspendCancellableCoroutine { continuation ->
            manager.createOfflineRegion(definition, metadata, object :
                    OfflineManager.CreateOfflineRegionCallback {

                override fun onCreate(region: OfflineRegion) {
                    openRegions[target.id] = region
                    Log.d(TAG, "Created offline region ${target.id} (${region.id})")
                    attachObserver(target.id, target.displayName, region, null)
                    region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    if (continuation.isActive) continuation.resume(Result.success(Unit))
                }

                override fun onError(error: String) {
                    Log.e(TAG, "Failed to create offline region ${target.id}: $error")
                    _downloads.update {
                        it + (target.id to OfflineDownloadStatus(
                                packId = target.id,
                                name = target.displayName,
                                state = OfflineDownloadState.FAILED,
                                error = error
                        ))
                    }
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(IllegalStateException(error)))
                    }
                }
            })
        }
    }

    /**
     * Re-attach to incomplete regions after a process restart and set them active again. The
     * foreground download worker calls this before monitoring progress.
     */
    suspend fun resumeIncomplete(context: Context): Int {
        refresh(context)
        var resumed = 0
        for ((id, region) in openRegions.toMap()) {
            val status = statusOf(region) ?: continue
            if (!status.isComplete) {
                val name = parseMetadata(region).optString("name").ifBlank { id }
                region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                attachObserver(id, name, region, status)
                resumed++
            }
        }
        return resumed
    }

    fun pause(packId: String) {
        val region = openRegions[packId] ?: return
        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
        _downloads.update { statuses ->
            val current = statuses[packId] ?: return@update statuses
            statuses + (packId to current.copy(state = OfflineDownloadState.PAUSED))
        }
    }

    fun resume(packId: String) {
        val region = openRegions[packId] ?: return
        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
        _downloads.update { statuses ->
            val current = statuses[packId] ?: return@update statuses
            statuses + (packId to current.copy(state = OfflineDownloadState.DOWNLOADING))
        }
    }

    suspend fun delete(context: Context, packId: String): Result<Unit> {
        val manager = OfflineManager.getInstance(context.applicationContext)
        val region = openRegions[packId]
                ?: listRegions(manager).firstOrNull { parseMetadata(it).optString("id") == packId }
                ?: return Result.failure(IllegalStateException("Region $packId is not installed"))

        val result = suspendCancellableCoroutine { continuation ->
            region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                override fun onDelete() {
                    if (continuation.isActive) continuation.resume(Result.success(Unit))
                }

                override fun onError(error: String) {
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(IllegalStateException(error)))
                    }
                }
            })
        }

        if (result.isSuccess) {
            openRegions.remove(packId)
            _downloads.update { it - packId }
            refresh(context)
        }
        return result
    }

    private fun attachObserver(
            packId: String,
            name: String,
            region: OfflineRegion,
            initialStatus: OfflineRegionStatus?
    ) {
        if (initialStatus != null) {
            publishStatus(packId, name, initialStatus)
        }

        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                publishStatus(packId, name, status)
            }

            override fun onError(error: OfflineRegionError) {
                Log.e(TAG, "Offline download error for $packId: ${error.reason} ${error.message}")
                _downloads.update { statuses ->
                    val current = statuses[packId]
                    statuses + (packId to OfflineDownloadStatus(
                            packId = packId,
                            name = name,
                            state = OfflineDownloadState.FAILED,
                            progress = current?.progress ?: 0f,
                            downloadedBytes = current?.downloadedBytes ?: 0L,
                            error = "${error.reason}: ${error.message}"
                    ))
                }
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                Log.e(TAG, "Offline tile limit exceeded: $limit")
                _downloads.update { statuses ->
                    val current = statuses[packId] ?: return@update statuses
                    statuses + (packId to current.copy(
                            state = OfflineDownloadState.FAILED,
                            error = "Tile limit exceeded ($limit)"
                    ))
                }
            }
        })
    }

    private fun publishStatus(packId: String, name: String, status: OfflineRegionStatus) {
        val required = status.requiredResourceCount
        val completed = status.completedResourceCount
        val precise = status.isRequiredResourceCountPrecise
        val progress =
                if (precise && required > 0) {
                    (completed.toFloat() / required.toFloat()).coerceIn(0f, 1f)
                } else if (status.isComplete) 1f
                else 0f

        val state = when {
            status.isComplete -> OfflineDownloadState.COMPLETE
            status.downloadState == OfflineRegion.STATE_ACTIVE -> OfflineDownloadState.DOWNLOADING
            else -> OfflineDownloadState.PAUSED
        }

        _downloads.update {
            it + (packId to OfflineDownloadStatus(
                    packId = packId,
                    name = name,
                    state = state,
                    progress = progress,
                    downloadedBytes = status.completedResourceSize,
                    requiredResources = required,
                    completedResources = completed
            ))
        }

        if (status.isComplete) {
            Log.d(TAG, "Offline region $packId complete (${status.completedResourceSize} bytes)")
        }
    }

    private suspend fun listRegions(manager: OfflineManager): List<OfflineRegion> =
            suspendCancellableCoroutine { continuation ->
                manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                    override fun onList(regions: Array<OfflineRegion>?) {
                        if (continuation.isActive) continuation.resume(regions?.toList().orEmpty())
                    }

                    override fun onError(error: String) {
                        Log.e(TAG, "Failed to list offline regions: $error")
                        if (continuation.isActive) continuation.resume(emptyList())
                    }
                })
            }

    private suspend fun statusOf(region: OfflineRegion): OfflineRegionStatus? =
            suspendCancellableCoroutine { continuation ->
                region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                    override fun onStatus(status: OfflineRegionStatus?) {
                        if (continuation.isActive) continuation.resume(status)
                    }

                    override fun onError(error: String?) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                })
            }

    private fun parseMetadata(region: OfflineRegion): JSONObject =
            try {
                JSONObject(String(region.metadata, Charsets.UTF_8))
            } catch (exception: Exception) {
                JSONObject()
            }
}
