package com.noobdev.waffle.database

import retrofit2.http.GET

interface WaffleApi {
    // Returns the raw GeoJSON string of the map closures
    @GET("/api/v1/map/closures")
    suspend fun getMapClosures(): String

    // Returns the parsed list of active disaster and traffic alerts
    @GET("/api/v1/map/alerts")
    suspend fun getAlerts(): List<com.noobdev.waffle.viewmodel.DisasterAlert>
}