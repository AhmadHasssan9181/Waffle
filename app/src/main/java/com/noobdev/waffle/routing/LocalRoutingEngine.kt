package com.noobdev.waffle.routing

import com.graphhopper.GraphHopper
import com.graphhopper.GHRequest
import com.graphhopper.GHResponse
import com.graphhopper.config.Profile

class LocalRoutingEngine(graphFolderAbsolutePath: String) {

    private val hopper: GraphHopper

    init {
        hopper = GraphHopper().apply {
            // Point GraphHopper to the internal storage folder
            setGraphHopperLocation(graphFolderAbsolutePath)

            // Set up the exact car profile we baked on Fedora
            setProfiles(Profile("car").setVehicle("car").setWeighting("fastest"))

            // Load the offline map into the phone's RAM!
            importOrLoad()
        }
    }

    // Call this to get a route instantly
    fun calculateRoute(startLat: Double, startLng: Double, endLat: Double, endLng: Double): GHResponse {
        val request = GHRequest(startLat, startLng, endLat, endLng).apply {
            profile = "car"
        }
        return hopper.route(request)
    }
}