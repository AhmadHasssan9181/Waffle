package com.noobdev.waffle.core.navigation

import android.content.Context
import android.provider.Settings
import android.view.WindowManager
import kotlinx.coroutines.flow.StateFlow

/**
 * Lets Test Mode show the same large floating navigation map as a real trip. The
 * navigation service owns the overlay for live trips; simulations run without that
 * service, so the activity hosts the overlay directly against [SimulationState.snapshot].
 */
object FloatingMapHost {
    private var overlay: FloatingNavigationOverlay? = null

    fun show(
            context: Context,
            snapshotFlow: StateFlow<NavigationSnapshot>
    ): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        hide()
        val windowManager =
                context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        val sizeStep =
                context.getSharedPreferences("waffle_floating", Context.MODE_PRIVATE)
                        .getInt("size_step", 0)
        val created =
                runCatching {
                            FloatingNavigationOverlay(
                                    context.applicationContext,
                                    windowManager,
                                    sizeStep,
                                    snapshotFlow
                            )
                    }
                    .getOrNull()
                    ?: return false
        if (!created.show()) return false
        overlay = created
        return true
    }

    fun hide() {
        runCatching { overlay?.hide() }
        overlay = null
    }

    fun isShowing(): Boolean = overlay?.isShowing() == true
}
