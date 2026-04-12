package com.noobdev.waffle.database

import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.converter.gson.GsonConverterFactory

object WaffleClient {
    // For local machine from Android Emulator, use 10.0.2.2.
    // Replace with IP Address (e.g. "http://192.168.1.X:8000") for physical device.
    private const val BASE_URL = "http://192.168.18.141:8000"

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            // Order matters: Scalars catches Strings/Primitives, Gson catches Objects/Lists
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    val api: WaffleApi by lazy {
        retrofit.create(WaffleApi::class.java)
    }
}
