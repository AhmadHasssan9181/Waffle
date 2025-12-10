package com.noobdev.waffle.Sketching

import android.content.Context
import android.graphics.Color
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.noobdev.waffle.api.LocationAwareMap
import com.noobdev.waffle.utils.loadJsonFromAsset
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression.color
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource

// --- 1. EXPANDED SECTOR DATA CLASS ---
data class Sector(
    val name: String,
    val lat: Double,
    val lon: Double,
    val limitFile: String? = null,
    val footprintFile: String? = null,
    val depthFile: String? = null,
    val waterFile: String? = null,
    val extentFile: String? = null,
    val roadsFile: String? = null,
    val builtUpFile: String? = null // <--- ADD THIS
)

val sectors = listOf(
    // AOI 01 - RASOO NAGAR (Fixed: Uses DEL_PRODUCT)
    Sector(
        name = "Rasoo Nagar (AOI 01)", lat = 32.36, lon = 73.93,
        limitFile = "EMSR838_AOI01_DEL_PRODUCT_areaOfInterestA_v1.json",
        footprintFile = "EMSR838_AOI01_DEL_PRODUCT_imageFootprintA_v1.json",
        depthFile = "EMSR838_AOI01_DEL_PRODUCT_floodDepthA_v1.json",
        waterFile = "EMSR838_AOI01_DEL_PRODUCT_observedEventA_v1.json"
    ),

    // AOI 02 - SHARAQPUR
    Sector(
        name = "Sharaqpur (AOI 02)", lat = 31.40, lon = 74.15,
        limitFile = "EMSR838_AOI02_DEL_PRODUCT_areaOfInterestA_v1.json",
        footprintFile = "EMSR838_AOI02_DEL_PRODUCT_imageFootprintA_v1.json",
        depthFile = "EMSR838_AOI02_DEL_PRODUCT_floodDepthA_v1.json",
        waterFile = "EMSR838_AOI02_DEL_PRODUCT_observedEventA_v1.json"
    ),

    // AOI 03 - PIR KHALIS
    Sector(
        name = "Pir Khalis (AOI 03)", lat = 30.40, lon = 73.80,
        limitFile = "EMSR838_AOI03_DEL_PRODUCT_areaOfInterestA_v1.json",
        footprintFile = "EMSR838_AOI03_DEL_PRODUCT_imageFootprintA_v1.json",
        depthFile = "EMSR838_AOI03_DEL_PRODUCT_floodDepthA_v1.json",
        waterFile = "EMSR838_AOI03_DEL_PRODUCT_observedEventA_v1.json"
    ),

    // AOI 04 - NAJABAT
    Sector(
        name = "Najabat (AOI 04)", lat = 31.00, lon = 74.55,
        limitFile = "EMSR838_AOI04_DEL_PRODUCT_areaOfInterestA_v1.json",
        footprintFile = "EMSR838_AOI04_DEL_PRODUCT_imageFootprintA_v1.json",
        depthFile = "EMSR838_AOI04_DEL_PRODUCT_floodDepthA_v1.json",
        waterFile = "EMSR838_AOI04_DEL_PRODUCT_observedEventA_v1.json"
    ),

    // AOI 05 - TRIMMU
    Sector(
        name = "Trimmu (AOI 05)", lat = 31.10, lon = 72.05,
        limitFile = "EMSR838_AOI05_DEL_PRODUCT_areaOfInterestA_v1.json",
        footprintFile = "EMSR838_AOI05_DEL_PRODUCT_imageFootprintA_v1.json",
        depthFile = "EMSR838_AOI05_DEL_PRODUCT_floodDepthA_v1.json",
        waterFile = "EMSR838_AOI05_DEL_PRODUCT_observedEventA_v1.json"
    ),

    // AOI 06 - MISSING FROM YOUR FOLDER (Skipped)

    // AOI 07 - SAHUKA (Has Max Extent v2!)
    Sector(
        name = "Sahuka (AOI 07)", lat = 30.05, lon = 73.00,
        limitFile = "EMSR838_AOI07_DEL_PRODUCT_areaOfInterestA_v1.json",
        footprintFile = "EMSR838_AOI07_DEL_PRODUCT_imageFootprintA_v1.json",
        depthFile = "EMSR838_AOI07_DEL_PRODUCT_floodDepthA_v1.json",
        waterFile = "EMSR838_AOI07_DEL_PRODUCT_observedEventA_v1.json",
        extentFile = "EMSR838_AOI07_DEL_PRODUCT_maximumFloodExtentA_v2.json"
    ),

    // AOI 08 - VANIKE
    Sector(
        name = "Vanike (AOI 08)", lat = 32.15, lon = 73.50,
        limitFile = "EMSR838_AOI08_DEL_PRODUCT_areaOfInterestA_v1.json",
        footprintFile = "EMSR838_AOI08_DEL_PRODUCT_imageFootprintA_v1.json",
        depthFile = "EMSR838_AOI08_DEL_PRODUCT_floodDepthA_v1.json",
        waterFile = "EMSR838_AOI08_DEL_PRODUCT_observedEventA_v1.json"
    ),

    // AOI 10 - DAGGAR
    Sector(
        name = "Daggar (AOI 10)", lat = 34.55, lon = 72.45,
        limitFile = "EMSR838_AOI10_GRA_PRODUCT_areaOfInterestA_v1.json",
        footprintFile = "EMSR838_AOI10_GRA_PRODUCT_imageFootprintA_v1.json",
        waterFile = "EMSR838_AOI10_GRA_PRODUCT_observedEventA_v1.json",
        roadsFile = "EMSR838_AOI10_GRA_PRODUCT_transportationL_v1.json",
        builtUpFile = "EMSR838_AOI10_GRA_PRODUCT_builtUpP_v1.json" // <--- NEW
    ),

    // AOI 11 - WAKEEL ABAD
    Sector(
        name = "Wakeel Abad (AOI 11)", lat = 34.51, lon = 72.58,
        limitFile = "EMSR838_AOI11_GRA_PRODUCT_areaOfInterestA_v1.json",
        footprintFile = "EMSR838_AOI11_GRA_PRODUCT_imageFootprintA_v1.json",
        waterFile = "EMSR838_AOI11_GRA_PRODUCT_observedEventA_v1.json",
        roadsFile = "EMSR838_AOI11_GRA_PRODUCT_transportationL_v1.json",
        builtUpFile = "EMSR838_AOI11_GRA_PRODUCT_builtUpP_v1.json" // <--- NEW
    )
)

@Composable
fun HeatmapDemoScreen() {
    val context = LocalContext.current
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentSectorIndex by remember { mutableStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        HeatmapSpecificParent { map ->
            mapInstance = map
            loadSectorData(context, map, sectors[0])
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(32.dp),
            horizontalAlignment = Alignment.End
        ) {
            FloatingActionButton(
                onClick = {
                    mapInstance?.let { map ->
                        currentSectorIndex = (currentSectorIndex + 1) % sectors.size
                        val nextSector = sectors[currentSectorIndex]

                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(nextSector.lat, nextSector.lon), 11.5),
                            2000
                        )
                        loadSectorData(context, map, nextSector)
                    }
                },
                containerColor = ComposeColor.Cyan
            ) {
                Row(Modifier.padding(16.dp)) {
                    Text("NEXT: ${sectors[(currentSectorIndex + 1) % sectors.size].name}")
                    Icon(Icons.Default.ArrowForward, "Next")
                }
            }
        }
    }
}

@Composable
fun HeatmapSpecificParent(mapConfiguration: (MapLibreMap) -> Unit) {
    Box(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(ComposeColor(0xFF121212))
            .clip(RoundedCornerShape(10.dp))
    ) {
        Column {
            LocationAwareMap(
                currentStyleIndex = 6,
                onMapReady = mapConfiguration
            )
        }
    }
}

fun loadSectorData(context: Context, map: MapLibreMap, sector: Sector) {
    map.getStyle { style ->
        Log.d("WAFFLE_DEBUG", "--------------------------------------------------")
        Log.d("WAFFLE_DEBUG", "LOADING SECTOR: ${sector.name}")

        // A smarter loader that applies specific styles based on what the layer IS
        fun loadLayer(fileName: String?, layerId: String) {
            if (fileName == null) {
                Log.d("WAFFLE_DEBUG", " [SKIP] $layerId: Filename is null in config")
                return
            }

            val json = loadJsonFromAsset(context, fileName)
            if (json == null) {
                Log.e("WAFFLE_DEBUG", " [FAIL] $layerId: Could not read file '$fileName' from assets!")
                return
            } else {
                Log.d("WAFFLE_DEBUG", " [OK] $layerId: Loaded ${json.length} bytes")
            }

            val sourceId = "source-$layerId"

            // 1. Source Logic
            var source = style.getSourceAs<GeoJsonSource>(sourceId)
            if (source == null) {
                source = GeoJsonSource(sourceId, json)
                style.addSource(source)
            } else {
                source.setGeoJson(json)
            }

            // 2. Styling Logic (The "Pretty" Part)
            if (style.getLayer(layerId) == null) {
                when (layerId) {
                    // --- THE MAIN EVENT: DATA-DRIVEN FLOOD DEPTH ---
                    "layer-depth" -> {
                        val fillLayer = FillLayer(layerId, sourceId)
                        fillLayer.setProperties(
                            // This is the magic. We "match" the "class" property in the GeoJSON
                            // to a specific color.
                            fillColor(
                                match(
                                    get("class"), // The property name in your GeoJSON file
                                    literal("0-0.5m"), color(Color.parseColor("#4FC3F7")), // Light Blue (Shallow)
                                    literal("0.5-1m"), color(Color.parseColor("#2196F3")), // Medium Blue
                                    literal("1-2m"), color(Color.parseColor("#1A237E")), // Deep Indigo
                                    literal(">2m"), color(Color.parseColor("#E91E63")),   // Pink/Red (Very Deep - like the reference image)
                                    color(Color.parseColor("#4FC3F7")) // Default color if no match
                                )
                            ),
                            fillOpacity(0.7f), // Translucent so you can see the map underneath
                            fillOutlineColor(Color.TRANSPARENT) // Smooth blending, no outlines
                        )
                        style.addLayer(fillLayer)
                    }

                    // --- SUPPORTING LAYERS ---
                    "layer-water" -> {
                        // The overall water footprint. Make it subtle.
                        val fillLayer = FillLayer(layerId, sourceId)
                        fillLayer.setProperties(
                            fillColor(Color.parseColor("#B3E5FC")), // Very light blue
                            fillOpacity(0.3f),
                            // A thin, crisp white outline helps define the edge of the water
                            fillOutlineColor(Color.WHITE)
                        )
                        style.addLayer(fillLayer)
                    }
                    "layer-extent" -> {
                        // Historical maximum. Keep it very subtle in the background.
                        val fillLayer = FillLayer(layerId, sourceId)
                        fillLayer.setProperties(
                            fillColor(Color.parseColor("#E0F7FA")), // Almost transparent cyan
                            fillOpacity(0.2f)
                        )
                        style.addLayer(fillLayer)
                    }

                    // --- INFRASTRUCTURE (Popped on top) ---
                    "layer-roads" -> {
                        val lineLayer = LineLayer(layerId, sourceId)
                        lineLayer.setProperties(
                            lineColor(Color.parseColor("#FFC107")), // Amber gold
                            lineWidth(2f),
                            lineCap(Property.LINE_CAP_ROUND),
                            lineJoin(Property.LINE_JOIN_ROUND)
                        )
                        style.addLayer(lineLayer)
                    }
                    "layer-builtup" -> {
                        val circleLayer = CircleLayer(layerId, sourceId)
                        circleLayer.setProperties(
                            circleColor(Color.WHITE),
                            circleRadius(4f),
                            circleStrokeColor(Color.BLACK),
                            circleStrokeWidth(1f)
                        )
                        style.addLayer(circleLayer)
                    }

                    // --- TECHNICAL BOUNDARIES ---
                    "layer-footprint" -> {
                        val lineLayer = LineLayer(layerId, sourceId)
                        lineLayer.setProperties(
                            lineColor(Color.GRAY), lineWidth(1f), lineOpacity(0.5f),
                            lineDasharray(arrayOf(2f, 4f))
                        )
                        style.addLayer(lineLayer)
                    }
                    "layer-limit" -> {
                        val lineLayer = LineLayer(layerId, sourceId)
                        lineLayer.setProperties(
                            lineColor(Color.CYAN), lineWidth(1.5f), lineOpacity(0.8f),
                            lineDasharray(arrayOf(4f, 4f))
                        )
                        style.addLayer(lineLayer)
                    }
                }
            }
        }

        // --- LOAD ORDER (Bottom to Top) ---
        // Load the broad, subtle layers first, then the detailed ones on top.
        loadLayer(sector.footprintFile, "layer-footprint")
        loadLayer(sector.limitFile, "layer-limit")
        loadLayer(sector.extentFile, "layer-extent") // Max extent at bottom
        loadLayer(sector.waterFile, "layer-water")   // Overall water on top of that
        loadLayer(sector.depthFile, "layer-depth")   // The beautiful depth gradient is the main focus
        loadLayer(sector.roadsFile, "layer-roads")   // Roads on top of water
        loadLayer(sector.builtUpFile, "layer-builtup") // Points on very top
    }
}