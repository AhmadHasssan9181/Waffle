package com.noobdev.waffle.api
import android.util.Log
import retrofit2.http.Query
import retrofit2.http.GET
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
    * OpenCage Geocoding API integration
    * Documentation: https://opencagedata.com/api
    *
    * This file contains functions to interact with the OpenCage Geocoding API
    * to convert geographic coordinates into human-readable location names.
    *Responses:
    * 200 OK: The request was successful, and the response contains the geocoding data.
    * 402 Quota Exceeded: The request could not be completed because the API quota has been exceeded.
    * 403 Forbidden: The request was not authorized. This may occur if the API key is missing or invalid
    * 403 Ip Address Denied: The request was denied based on the IP address of the client.
    * 429 Too Many Requests: The request was rejected due to rate limiting.
* */

data class OpenCageResponseItem(
    val formatted: String,
    val components: OpenCageComponents?
)
data class OpenCageResult(
    val results: List<OpenCageResponseItem>?
)
data class  OpenCageComponents(
    val city: String?,
    val town: String?,
    val village: String?,
    val state: String?,
    val country: String?
)
interface OpenCageApiService {
    @GET("/geocode/v1/json")
    suspend fun getAreaName(
        @Query("q") coordinates: String,
        @Query("key") apiKey: String
    ): OpenCageResult
}
object RetrofitInstance {
    private const val BASE_URL = "https://api.opencagedata.com/"
    val api: OpenCageApiService by lazy {
        retrofit2.Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(OpenCageApiService::class.java)
    }
}
suspend fun fetchAreaName(lat: Double, lng: Double, apiKey: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val response = RetrofitInstance.api.getAreaName("$lat+$lng", apiKey)
            val area = response.results?.firstOrNull()?.components
            area?.city ?: area?.town ?: area?.village ?: "Unknown Area"
        } catch (e: Exception) {
            println(" Error fetching area: ${e.message}")
            Log.e("OpenCageAPI", "Error fetching area: ${e.message}")
            null
        }
    }
}
/*
* how to use :
* if in a viewmodel:
* viewModelScope.launch {
    val area = fetchAreaName(24.8607, 67.0011, "YOUR_API_KEY")
    println("Area: $area")
}
* if outside a viewmodel i.e inside a composable
* LaunchedEffect(Unit) {
    val area = fetchAreaName(24.8607, 67.0011, "YOUR_API_KEY")
    println("Area: $area")
}
*/