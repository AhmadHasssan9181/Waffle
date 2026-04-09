package com.noobdev.waffle.routing

import com.graphhopper.GraphHopper
import com.graphhopper.GHRequest
import com.graphhopper.GHResponse
import com.graphhopper.config.CHProfile
import com.graphhopper.config.Profile

class LocalRoutingEngine(graphFolderAbsolutePath: String) {

    private val hopper: GraphHopper

    init {
        hopper = GraphHopper().apply {
            setGraphHopperLocation(graphFolderAbsolutePath)
            // v1.0 safe profile
            setProfiles(Profile("car").setVehicle("car").setWeighting("fastest"))
            getCHPreparationHandler().setCHProfiles(CHProfile("car"))
            importOrLoad()
        }
    }

    fun calculateRoute(startLat: Double, startLng: Double, endLat: Double, endLng: Double): GHResponse {
        val request = GHRequest(startLat, startLng, endLat, endLng).apply {
            profile = "car"
        }
        return hopper.route(request)
    }
}