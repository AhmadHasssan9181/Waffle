package com.noobdev.waffle.core.navigation

/**
 * Latest TomTom flow pace for the current road, shared with the foreground navigation
 * service so the ETA can use real traffic instead of only the router's free-flow estimate.
 */
object TrafficState {
    @Volatile var paceMps: Double? = null
}
