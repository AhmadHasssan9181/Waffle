package com.noobdev.waffle.data.remote


import com.noobdev.waffle.feature.flood.data.Content
import com.noobdev.waffle.feature.flood.data.GeminiRequest
import com.noobdev.waffle.feature.flood.data.Part
import com.noobdev.waffle.BuildConfig
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import com.noobdev.waffle.feature.flood.data.GeminiResponse
import java.util.concurrent.atomic.AtomicLong

/*
* im experimenting with the geminis api im pretty sure that this would not work
* as the context limit is pretty short
* but we can be a bit smart with it if we upload the summary report and just spam it
* and i can maybe give it some internet access well lets see*/
interface GeminiService {

    // The base URL in your Retrofit Builder should be: "https://generativelanguage.googleapis.com/"

    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    suspend fun analyzeFloodData(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}

private val lastGeminiRequestAt = AtomicLong(0L)

suspend fun chatWithGemini(promptText: String): String? {
    val now = System.currentTimeMillis()
    if (now - lastGeminiRequestAt.getAndSet(now) < 1_500L) {
        return "Please wait a moment before asking again."
    }
    val myApiKey = BuildConfig.GEMINI_API_KEY
    val request = GeminiRequest(
        contents = listOf(
            Content(parts = listOf(Part(text = promptText)))
        )
    )
    try {
        val response = GeminiClient.api.analyzeFloodData(
            apiKey = myApiKey,
            request = request
        )
        if (response.isSuccessful) {
            return response.body()?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        } else {
            if (BuildConfig.DEBUG) {
                android.util.Log.e("WAFFLE_AI", "Gemini HTTP ${response.code()}")
            }
        }
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) android.util.Log.e("WAFFLE_AI", "Gemini request failed: ${e.message}")
    }
    return "Failed to get AI response."
}
