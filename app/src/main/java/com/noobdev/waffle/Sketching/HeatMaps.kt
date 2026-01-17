package com.noobdev.waffle.Sketching

import android.content.Context
import android.graphics.Color
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noobdev.waffle.api.LocationAwareMap
import com.noobdev.waffle.utils.loadJsonFromAsset
import kotlinx.coroutines.launch
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
    val builtUpFile: String? = null
)

// (Keep your sectors list exactly as it was - I'm abbreviating for brevity,
// but in your code, keep the full list!)
val sectors = listOf(
    Sector(
        name = "Rasoo Nagar (AOI 01)", lat = 32.36, lon = 73.93,
        limitFile = "EMSR838_AOI01_DEL_PRODUCT_areaOfInterestA_v1.json",
        footprintFile = "EMSR838_AOI01_DEL_PRODUCT_imageFootprintA_v1.json",
        depthFile = "EMSR838_AOI01_DEL_PRODUCT_floodDepthA_v1.json",
        waterFile = "EMSR838_AOI01_DEL_PRODUCT_observedEventA_v1.json"
    ),
    Sector(
        name = "Sharaqpur (AOI 02)", lat = 31.40, lon = 74.15,
        limitFile = "EMSR838_AOI02_DEL_PRODUCT_areaOfInterestA_v1.json",
        footprintFile = "EMSR838_AOI02_DEL_PRODUCT_imageFootprintA_v1.json",
        depthFile = "EMSR838_AOI02_DEL_PRODUCT_floodDepthA_v1.json",
        waterFile = "EMSR838_AOI02_DEL_PRODUCT_observedEventA_v1.json"
    ),
    Sector(
        name = "Pir Khalis (AOI 03)", lat = 30.40, lon = 73.80,
        limitFile = "EMSR838_AOI03_DEL_PRODUCT_areaOfInterestA_v1.json",
        footprintFile = "EMSR838_AOI03_DEL_PRODUCT_imageFootprintA_v1.json",
        depthFile = "EMSR838_AOI03_DEL_PRODUCT_floodDepthA_v1.json",
        waterFile = "EMSR838_AOI03_DEL_PRODUCT_observedEventA_v1.json"
    ),
    Sector(
        name = "Najabat (AOI 04)", lat = 31.00, lon = 74.55,
        limitFile = "EMSR838_AOI04_DEL_PRODUCT_areaOfInterestA_v1.json",
        footprintFile = "EMSR838_AOI04_DEL_PRODUCT_imageFootprintA_v1.json",
        depthFile = "EMSR838_AOI04_DEL_PRODUCT_floodDepthA_v1.json",
        waterFile = "EMSR838_AOI04_DEL_PRODUCT_observedEventA_v1.json"
    ),
    // ... Add the rest of your sectors here
)

@Composable
fun HeatmapDemoScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentSectorIndex by remember { mutableStateOf(0) }

    // UI State for the "Mission Control"
    var isPanelExpanded by remember { mutableStateOf(false) }
    var geminiAnalysisText by remember { mutableStateOf("Waiting for drone link...") }

    Box(modifier = Modifier.fillMaxSize()) {

        // 1. THE MAP (Background)
        HeatmapSpecificParent { map ->
            mapInstance = map
            loadSectorData(context, map, sectors[0])
            geminiAnalysisText = "Analyzing sector: ${sectors[0].name}..."
            // Here you would trigger: getFloodIntel(sectors[0].reportText)
        }

        // 2. TOP MISSION BAR (HUD)
        TopIntelBar(modifier = Modifier.align(Alignment.TopCenter))

        // 3. BOTTOM MISSION PANEL (Replaces the Button)
        BottomMissionPanel(
            modifier = Modifier.align(Alignment.BottomCenter),
            sectorName = sectors[currentSectorIndex].name,
            geminiText = geminiAnalysisText,
            isExpanded = isPanelExpanded,
            onToggleExpand = { isPanelExpanded = !isPanelExpanded },
            onNextSector = {
                mapInstance?.let { map ->
                    currentSectorIndex = (currentSectorIndex + 1) % sectors.size
                    val nextSector = sectors[currentSectorIndex]

                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(nextSector.lat, nextSector.lon), 11.5),
                        2000
                    )
                    loadSectorData(context, map, nextSector)

                    // Reset UI for new sector
                    geminiAnalysisText = "Initializing scan for ${nextSector.name}..."
                    // Launch Gemini API call here in the future
                }
            }
        )
    }
}

// --- NEW UI COMPONENT: TOP HUD ---
@Composable
fun TopIntelBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(top = 48.dp) // Status bar spacing
            .clip(RoundedCornerShape(50))
            .background(ComposeColor.Black.copy(alpha = 0.6f))
            .border(1.dp, ComposeColor.White.copy(alpha = 0.2f), RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mock Status
        Icon(Icons.Default.Warning, contentDescription = null, tint = ComposeColor.Red, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("FLEET STATUS: ACTIVE", color = ComposeColor.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.width(16.dp))

        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(ComposeColor.Green))
        Spacer(Modifier.width(6.dp))
        Text("LIVE FEED", color = ComposeColor.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// --- NEW UI COMPONENT: BOTTOM PANEL (The "Gemini Chat" Area) ---
@Composable
fun BottomMissionPanel(
    modifier: Modifier = Modifier,
    sectorName: String,
    geminiText: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onNextSector: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ComposeColor(0xFF1E293B).copy(alpha = 0.95f),
                        ComposeColor(0xFF0F172A).copy(alpha = 0.98f)
                    )
                )
            )
            .border(1.dp, ComposeColor.Cyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            // Click the whole panel to expand/collapse
            .clickable { onToggleExpand() }
            .padding(16.dp)
    ) {
        // --- HEADER (Always Visible) ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text("SECTOR INTEL", color = ComposeColor.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(sectorName, color = ComposeColor.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            // The "Next" Button is now a small pill inside the panel
            Button(
                onClick = onNextSector,
                colors = ButtonDefaults.buttonColors(containerColor = ComposeColor.Cyan.copy(alpha = 0.2f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("NEXT AOI", color = ComposeColor.Cyan, fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ArrowForward, null, tint = ComposeColor.Cyan, modifier = Modifier.size(12.dp))
            }
        }

        // --- EXPANDABLE GEMINI CONTENT ---
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + slideInVertically()
        ) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Divider(color = ComposeColor.White.copy(alpha = 0.1f))
                Spacer(Modifier.height(16.dp))

                Text("GEMINI TACTICAL ANALYSIS:", color = ComposeColor.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                // This Text block is where your API result will go
                Text(
                    text = geminiText,
                    color = ComposeColor.LightGray,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }

        // Expansion Hint
        if (!isExpanded) {
            Icon(
                Icons.Default.KeyboardArrowUp,
                contentDescription = "Expand",
                tint = ComposeColor.Gray,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
            )
        }
    }
}

// --- KEEPING YOUR MAP & LOAD LOGIC EXACTLY AS IS ---

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
    // (Paste your exact loadSectorData function here)
    // I am not modifying this part as you requested,
    // but you must ensure it matches your existing map logic.
    map.getStyle { style ->
        Log.d("WAFFLE_DEBUG", "LOADING SECTOR: ${sector.name}")

        fun loadLayer(fileName: String?, layerId: String) {
            if (fileName == null) return
            val json = loadJsonFromAsset(context, fileName) ?: return

            val sourceId = "source-$layerId"
            var source = style.getSourceAs<GeoJsonSource>(sourceId)
            if (source == null) {
                source = GeoJsonSource(sourceId, json)
                style.addSource(source)
            } else {
                source.setGeoJson(json)
            }

            if (style.getLayer(layerId) == null) {
                when (layerId) {
                    "layer-depth" -> {
                        val fillLayer = FillLayer(layerId, sourceId)
                        fillLayer.setProperties(
                            fillColor(match(get("class"),
                                literal("0-0.5m"), color(Color.parseColor("#4FC3F7")),
                                literal("0.5-1m"), color(Color.parseColor("#2196F3")),
                                literal("1-2m"), color(Color.parseColor("#1A237E")),
                                literal(">2m"), color(Color.parseColor("#E91E63")),
                                color(Color.parseColor("#4FC3F7"))
                            )),
                            fillOpacity(0.7f),
                            fillOutlineColor(Color.TRANSPARENT)
                        )
                        style.addLayer(fillLayer)
                    }
                    "layer-water" -> {
                        val fillLayer = FillLayer(layerId, sourceId)
                        fillLayer.setProperties(
                            fillColor(Color.parseColor("#B3E5FC")),
                            fillOpacity(0.3f),
                            fillOutlineColor(Color.WHITE)
                        )
                        style.addLayer(fillLayer)
                    }
                    "layer-extent" -> {
                        val fillLayer = FillLayer(layerId, sourceId)
                        fillLayer.setProperties(fillColor(Color.parseColor("#E0F7FA")), fillOpacity(0.2f))
                        style.addLayer(fillLayer)
                    }
                    "layer-roads" -> {
                        val lineLayer = LineLayer(layerId, sourceId)
                        lineLayer.setProperties(
                            lineColor(Color.parseColor("#FFC107")), lineWidth(2f),
                            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)
                        )
                        style.addLayer(lineLayer)
                    }
                    "layer-builtup" -> {
                        val circleLayer = CircleLayer(layerId, sourceId)
                        circleLayer.setProperties(
                            circleColor(Color.WHITE), circleRadius(4f),
                            circleStrokeColor(Color.BLACK), circleStrokeWidth(1f)
                        )
                        style.addLayer(circleLayer)
                    }
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

        // Load Order
        loadLayer(sector.footprintFile, "layer-footprint")
        loadLayer(sector.limitFile, "layer-limit")
        loadLayer(sector.extentFile, "layer-extent")
        loadLayer(sector.waterFile, "layer-water")
        loadLayer(sector.depthFile, "layer-depth")
        loadLayer(sector.roadsFile, "layer-roads")
        loadLayer(sector.builtUpFile, "layer-builtup")
    }
}