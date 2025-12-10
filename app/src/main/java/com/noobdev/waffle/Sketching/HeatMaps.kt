package com.noobdev.waffle.Sketching

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
import androidx.compose.ui.unit.dp
import com.noobdev.waffle.Data.FloodAoisJson
import com.noobdev.waffle.Data.sectorCenters
import com.noobdev.waffle.api.LocationAwareMap
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.layers.Property

@Composable
fun HeatmapDemoScreen() {
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentSectorIndex by remember { mutableStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {

        // 1. THE MAP
        HeatmapSpecificParent { map ->
            mapInstance = map
            addRealFloodData(map)
        }

        // 2. THE "NEXT SECTOR" BUTTON
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(32.dp),
            horizontalAlignment = Alignment.End
        ) {
            FloatingActionButton(
                onClick = {
                    mapInstance?.let { map ->
                        // Calculate next index
                        currentSectorIndex = (currentSectorIndex + 1) % sectorCenters.size
                        val (lat, lon) = sectorCenters[currentSectorIndex]

                        // Fly the drone!
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 10.0),
                            2000 // 2 second cinematic flight
                        )
                    }
                },
                containerColor = ComposeColor.Cyan,
                contentColor = ComposeColor.Black
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("NEXT SECTOR")
                    Spacer(modifier = Modifier.width(8.dp))
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
            // Style 5 = Satellite Hybrid
            LocationAwareMap(
                currentStyleIndex = 6, // GPS OFF
                onMapReady = mapConfiguration
            )
        }
    }
}

fun addRealFloodData(map: MapLibreMap) {
    map.getStyle { style ->
        val sourceId = "copernicus-aois"
        val fillLayerId = "flood-fill"
        val borderLayerId = "flood-border"
        val textLayerId = "flood-labels"

        // 1. ADD DATA (From FloodData.kt)
        if (style.getSource(sourceId) == null) {
            style.addSource(GeoJsonSource(sourceId, FloodAoisJson))
        }

        // 2. BLUE FILL (The Water)
        if (style.getLayer(fillLayerId) == null) {
            val fillLayer = FillLayer(fillLayerId, sourceId)
            fillLayer.setProperties(
                fillColor(Color.parseColor("#0066FF")),
                fillOpacity(0.25f) // Subtle
            )
            style.addLayer(fillLayer)
        }

        // 3. CYAN BORDER (The Tactical Outline)
        if (style.getLayer(borderLayerId) == null) {
            val lineLayer = LineLayer(borderLayerId, sourceId)
            lineLayer.setProperties(
                lineColor(Color.CYAN),
                lineWidth(2f)
            )
            style.addLayer(lineLayer)
        }

        // 4. LABELS (City Names)
        if (style.getLayer(textLayerId) == null) {
            val symbolLayer = SymbolLayer(textLayerId, sourceId)
            symbolLayer.setProperties(
                textField(get("name")),
                textSize(16f),
                textColor(Color.WHITE),
                textHaloColor(Color.BLACK),
                textHaloWidth(2f),
                textAnchor(Property.TEXT_ANCHOR_CENTER)
            )
            style.addLayer(symbolLayer)
        }

        // Initial Position (First Sector)
        val (lat, lon) = sectorCenters[0]
        map.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
            .target(LatLng(lat, lon))
            .zoom(10.0)
            .build()
    }
}