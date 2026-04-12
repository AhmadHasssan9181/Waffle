package com.noobdev.waffle.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import kotlinx.serialization.ExperimentalSerializationApi

/*
* Metro Weather Service (Open-Metro)
* Usage:
* - Inside Composable: use `LaunchedEffect { fetchWeather(...) }`
* - Outside Composable: use `CoroutineScope(Dispatchers.IO).launch { fetchWeather(...) }`
*/


@Serializable
data class HourlyWeather(
    val time: List<String>,
    @SerialName("temperature_2m") val temperature: List<Float>,
    val precipitation: List<Float>
)

@Serializable
data class WeatherResponse(
    val latitude: Double,
    val longitude: Double,
    val hourly: HourlyWeather
)


interface OpenMetroApiService {
    @GET("v1/forecast")
    suspend fun getHourlyWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String = "temperature_2m,precipitation"
    ): WeatherResponse
}


object OpenMetroRetrofitInstance {

    private val json = Json { ignoreUnknownKeys = true }

    private val contentType = "application/json".toMediaType()

    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor()
        logging.setLevel(HttpLoggingInterceptor.Level.BODY)
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    @OptIn(ExperimentalSerializationApi::class)
    val api: OpenMetroApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(OpenMetroApiService::class.java)
    }
}


suspend fun fetchWeather(latitude: Double, longitude: Double): WeatherResponse? {
    return try {
        OpenMetroRetrofitInstance.api.getHourlyWeather(latitude, longitude)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// ----------------------
// Composable Usage Extension
// ----------------------

@Composable
fun fetchWeatherComposable(latitude: Double, longitude: Double, onResult: (WeatherResponse?) -> Unit) {
    LaunchedEffect(latitude, longitude) {
        val weather = fetchWeather(latitude, longitude)
        onResult(weather)
    }
}

// ----------------------
// Outside Composable Usage Example
// ----------------------

fun fetchWeatherOutsideComposable(latitude: Double, longitude: Double, onResult: (WeatherResponse?) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        val weather = fetchWeather(latitude, longitude)
        onResult(weather)
    }
}