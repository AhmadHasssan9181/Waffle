package com.noobdev.waffle.data.remote


import com.noobdev.waffle.feature.flood.data.Content
import com.noobdev.waffle.feature.flood.data.GeminiRequest
import com.noobdev.waffle.feature.flood.data.Part
import com.noobdev.waffle.BuildConfig
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import com.google.gson.Gson
import com.noobdev.waffle.feature.flood.data.FloodUiData
import com.noobdev.waffle.feature.flood.data.GeminiResponse

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

suspend fun getFloodIntel(summaryReportText: String) {


    val myApiKey = BuildConfig.GEMINI_API_KEY
    // ----------------------------

    val promptText = """
        You are an AI Analyst for the WAFFLE Drone Fleet. 
        Analyze the following raw flood report data and extract tactical intel.
        
        RAW DATA:
        $summaryReportText
        
        OUTPUT REQUIREMENT:
        Return ONLY valid JSON matching this structure:
        {
          "risk_level": "String (LOW, MEDIUM, HIGH, CRITICAL)",
          "risk_score": Integer (0-100),
          "area_impacted_km": Double,
          "structures_count": Integer,
          "brief_summary": ["Point 1", "Point 2", "Point 3"],
          "tactical_recommendation": "String (Short command)"
        }
    """.trimIndent()

    // Create the Request Object
    val request = GeminiRequest(
        contents = listOf(
            Content(parts = listOf(Part(text = promptText)))
        )
    )

    try {
        // Make the call
        val response = GeminiClient.api.analyzeFloodData(
            apiKey = myApiKey,
            request = request
        )

        if (response.isSuccessful) {
            val jsonString = response.body()?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

            if (jsonString != null) {
                // Clean up markdown code blocks if Gemini adds them (e.g., ```json ... ```)
                val cleanJson = jsonString.replace("```json", "").replace("```", "").trim()

                val uiData = Gson().fromJson(cleanJson, FloodUiData::class.java)

                // Log it to see if it worked
                android.util.Log.d("WAFFLE_AI", "Risk Level: ${uiData.riskLevel}")
                android.util.Log.d("WAFFLE_AI", "Action: ${uiData.action}")

                // TODO: Update UI State here
            }
        } else {
            android.util.Log.e("WAFFLE_AI", "Error: ${response.code()} - ${response.errorBody()?.string()}")
        }
    } catch (e: Exception) {
        android.util.Log.e("WAFFLE_AI", "Exception: ${e.message}")
        e.printStackTrace()
    }
}

suspend fun chatWithGemini(promptText: String): String? {
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
            android.util.Log.e("WAFFLE_AI", "Error: ${response.code()} - ${response.errorBody()?.string()}")
        }
    } catch (e: Exception) {
        android.util.Log.e("WAFFLE_AI", "Exception: ${e.message}")
        e.printStackTrace()
    }
    return "Failed to get AI response."
}
