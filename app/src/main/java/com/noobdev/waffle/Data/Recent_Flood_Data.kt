package com.noobdev.waffle.Data

data class Recent_Flood_Data(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val severity: Int,
    val description: String
)
