package com.noobdev.waffle.core.navigation

import android.content.Context
import com.noobdev.waffle.data.remote.GeoSearchResult
import com.noobdev.waffle.data.remote.RouteResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One live navigation trip. Owned by the foreground service so it survives screen changes, app
 * backgrounding, and (via [NavigationStore]) process death. The map UI only reads this state and
 * sends commands; it no longer owns the GPS callback.
 */
data class NavigationSnapshot(
        val active: Boolean = false,
        val route: RouteResult? = null,
        val destination: GeoSearchResult? = null,
        val profile: String = "driving-car",
        val stops: List<GeoSearchResult> = emptyList(),
        val lat: Double = 0.0,
        val lng: Double = 0.0,
        val bearing: Double = 0.0,
        val speedMps: Float = 0f,
        val instructionIndex: Int = 0,
        val remainingDistance: Double = 0.0,
        val remainingDuration: Double = 0.0,
        val distanceToNextTurn: Double = 0.0,
        val notice: String? = null,
        val isRerouting: Boolean = false,
        val wrongWay: Boolean = false,
        val muted: Boolean = false,
        val speedLimitKmh: Int? = null,
        val matchedIndex: Int = -1,
        val arrived: Boolean = false
) {
    val instruction: String
        get() = route?.steps?.getOrNull(instructionIndex)?.instruction
                ?: route?.instructions?.getOrNull(instructionIndex)
                ?: "Continue"
}

object NavigationSession {

    private val _state = MutableStateFlow(NavigationSnapshot())
    val state: StateFlow<NavigationSnapshot> = _state.asStateFlow()

    /** Set by the notification's content intent; WaffleApp consumes it to open the Map tab. */
    private val _openMapRequested = MutableStateFlow(false)
    val openMapRequested: StateFlow<Boolean> = _openMapRequested.asStateFlow()

    fun snapshot(): NavigationSnapshot = _state.value

    fun isActive(): Boolean = _state.value.active

    fun start(
            context: Context,
            route: RouteResult,
            destination: GeoSearchResult,
            profile: String,
            stops: List<GeoSearchResult>
    ) {
        _state.value =
                NavigationSnapshot(
                        active = true,
                        route = route,
                        destination = destination,
                        profile = profile,
                        stops = stops,
                        remainingDistance = route.distanceMeters,
                        remainingDuration = route.durationSeconds,
                        instructionIndex = 0
                )
        NavigationStore.save(context, _state.value)
        NavigationService.start(context)
    }

    fun update(transform: (NavigationSnapshot) -> NavigationSnapshot) {
        _state.update(transform)
    }

    fun setMuted(muted: Boolean) {
        _state.update { it.copy(muted = muted) }
    }

    fun toggleMuted() {
        _state.update { it.copy(muted = !it.muted) }
    }

    /** Stop from the UI: clear state, persist, and tell the service to stop. */
    fun stop(context: Context) {
        _state.value = NavigationSnapshot()
        NavigationStore.clear(context)
        NavigationService.stop(context)
    }

    /** Restore a saved trip after process death. The service is already running or is started. */
    fun restore(context: Context, snapshot: NavigationSnapshot) {
        _state.value = snapshot.copy(active = true)
        NavigationService.start(context)
    }

    fun restoreInternal(snapshot: NavigationSnapshot) {
        _state.value = snapshot.copy(active = true)
    }

    fun requestOpenMap() {
        _openMapRequested.value = true
    }

    fun consumeOpenMapRequest() {
        _openMapRequested.value = false
    }
}
