package com.noobdev.waffle.core.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the activity is currently in picture-in-picture. The map UI uses it to hide chrome. */
object PipState {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun setActive(active: Boolean) {
        _active.value = active
    }
}
