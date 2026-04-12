package com.noobdev.waffle.database
import com.google.gson.annotations.SerializedName
// If you ever want to parse the GeoJSON string into objects directly:
data class FeatureCollection(
    val type: String = "FeatureCollection",
    val features: List<Feature>
)
data class Feature(
    val type: String = "Feature",
    val id: String?,
    val geometry: Geometry,
    val properties: Map<String, Any>?
)
data class Geometry(
    val type: String, // "MultiPolygon" or "LineString"
    val coordinates: Any // Can be List<List<Double>> or List<List<List<...>>>
)

