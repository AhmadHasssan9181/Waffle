package com.noobdev.waffle.core.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Test Mode runs inside MapScreen without the foreground navigation service. PiP and the
 * floating map still need to know that a simulated trip is live, so keep that flag here.
 */
object SimulationState {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _snapshot = MutableStateFlow(NavigationSnapshot())
    val snapshot: StateFlow<NavigationSnapshot> = _snapshot.asStateFlow()

    fun setActive(active: Boolean) {
        _active.value = active
        if (!active) _snapshot.value = NavigationSnapshot()
    }

    fun publish(snapshot: NavigationSnapshot) {
        _snapshot.value = snapshot
    }

    fun clearSnapshot() {
        _snapshot.value = NavigationSnapshot()
    }
}
