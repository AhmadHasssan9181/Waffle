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

    fun setActive(active: Boolean) {
        _active.value = active
    }
}
