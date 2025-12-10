package com.noobdev.waffle.Data
/*
* this will be used when we connect to the database for now let it chill*/
data class Recent_Flood_Data(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val severity: Int,
    val description: String
)
