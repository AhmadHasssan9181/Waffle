package com.noobdev.waffle.database

import retrofit2.http.GET

interface WaffleApi {
    // Returns the raw GeoJSON string of the map closures
    @GET("/api/v1/map/closures")
    suspend fun getMapClosures(): String
}

