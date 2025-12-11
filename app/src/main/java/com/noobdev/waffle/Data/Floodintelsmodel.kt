package com.noobdev.waffle.Data


import com.google.gson.annotations.SerializedName

// --- REQUEST MODELS

data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig = GenerationConfig()
)

data class Content(
    val parts: List<Part>,
    val role: String = "user"
)

data class Part(
    val text: String
)

data class GenerationConfig(
    val responseMimeType: String = "application/json"
)

// --- RESPONSE MODELS (What Gemini sends back) ---

data class GeminiResponse(
    val candidates: List<Candidate>?
)

data class Candidate(
    val content: Content?,
    val finishReason: String?
)

// --- YOUR UI MODEL (The clean data for your App) ---

data class FloodUiData(
    @SerializedName("risk_level") val riskLevel: String,
    @SerializedName("risk_score") val riskScore: Int,
    @SerializedName("area_impacted_km") val areaKm: Double,
    @SerializedName("structures_count") val structures: Int,
    @SerializedName("brief_summary") val summary: List<String>,
    @SerializedName("tactical_recommendation") val action: String
)