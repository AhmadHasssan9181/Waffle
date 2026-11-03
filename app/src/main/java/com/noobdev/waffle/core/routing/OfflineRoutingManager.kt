package com.noobdev.waffle.core.routing

import android.content.Context
import android.util.Log
import com.noobdev.waffle.core.storage.AssetCopier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Loads the packaged GraphHopper graph once and exposes whether offline routing is actually usable.
 *
 * The previous implementation assumed `assets/graph-cache` existed. If it did not, GraphHopper
 * threw during startup and the app silently fell back to online routing forever. Now the missing
 * graph is a first-class state the UI can explain.
 */
object OfflineRoutingManager {

    private const val TAG = "OfflineRoutingManager"
    private const val GRAPH_ASSET_DIR = "graph-cache"

    // Bounds used by tools/graph-builder/README.md for the bundled metro graph.
    private const val MIN_LAT = 33.45
    private const val MAX_LAT = 33.95
    private const val MIN_LNG = 72.85
    private const val MAX_LNG = 73.35

    sealed interface State {
        data object Idle : State
        data object Loading : State
        data class Ready(val engine: LocalRoutingEngine) : State
        data class Unavailable(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile private var engine: LocalRoutingEngine? = null

    /** Returns the ready engine, or null when no graph is bundled / loading failed. */
    fun initialize(context: Context): LocalRoutingEngine? {
        engine?.let { return it }

        return synchronized(this) {
            engine?.let { return@synchronized it }

            val graphAssets =
                    runCatching { context.assets.list(GRAPH_ASSET_DIR)?.toList().orEmpty() }
                            .getOrDefault(emptyList())

            if (graphAssets.isEmpty()) {
                val reason = "No offline routing graph bundled (assets/$GRAPH_ASSET_DIR is empty)"
                Log.w(TAG, reason)
                _state.value = State.Unavailable(reason)
                return@synchronized null
            }

            _state.value = State.Loading
            try {
                val graphDir = AssetCopier.copyGraphCacheToStorage(context)
                Log.d(TAG, "Initializing GraphHopper from ${graphDir.absolutePath}")
                val initialized = LocalRoutingEngine(graphDir.absolutePath)
                engine = initialized
                _state.value = State.Ready(initialized)
                Log.d(TAG, "Offline routing ready (${graphAssets.size} asset files)")
                initialized
            } catch (throwable: Throwable) {
                val reason = throwable.message ?: throwable.javaClass.simpleName
                Log.e(TAG, "Offline routing failed to initialize: $reason", throwable)
                _state.value = State.Unavailable(reason)
                null
            }
        }
    }

    fun getEngineOrNull(): LocalRoutingEngine? = engine

    /** True when a coordinate is inside the bundled Islamabad–Rawalpindi graph. */
    fun isWithinCoverage(lat: Double, lng: Double): Boolean =
            lat in MIN_LAT..MAX_LAT && lng in MIN_LNG..MAX_LNG

    fun speedLimitKmhAt(lat: Double, lon: Double): Int? =
            engine?.speedLimitKmhAt(lat, lon)

    fun isReady(): Boolean = engine != null
}
