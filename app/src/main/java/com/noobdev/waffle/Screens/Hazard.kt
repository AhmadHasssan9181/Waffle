package com.noobdev.waffle.Screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp


/* This file contains functions related to different types of hazards
   such as Earthquakes, Floods, and Wildfires. Each function can be expanded
   to include specific parameters and logic for assessing the hazard levels.
   Todo: Implement detailed logic for each hazard type.
   Todo: score for earthquake <4.0 - minor, 4.0-5.9 - light, 6.0-6.9 - strong, 7.0-7.9 - major, 8.0+ - veryhigh
   Todo: score for flood severity - low, moderate, significant, severe, catastrophic
   Todo: score for wildfire danger - low-Local, moderate, large-Threatning, Catastrophic
*/

// 🌍 EARTHQUAKE CARD
@Composable
fun EarthquakeCard() {
    Card(
        modifier = Modifier
            .padding(12.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Public, contentDescription = null, tint = Color(0xFFD84315))
                Spacer(Modifier.width(8.dp))
                Text("Earthquake Details", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }

            InfoRow("📍 Area", "Get area name from OpenCage")
            InfoRow("🌋 Magnitude", "Get magnitude from USGS")
            InfoRow("⚠️ Hazard Level", hazardLevel(7))
            InfoRow("🕒 Origin Time", "Get origin time from USGS")
            InfoRow("📏 Depth", "Get depth from USGS")
            InfoRow("🌐 Caused by", "Get tectonic location")
        }
    }
}

@Composable
fun FloodCard() {
    Card(
        modifier = Modifier
            .padding(12.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WaterDrop, contentDescription = null, tint = Color(0xFF0277BD))
                Spacer(Modifier.width(8.dp))
                Text("Flood Hazard", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }

            InfoRow("🌍 Region", "Get area from API")
            InfoRow("💧 Water Level", "Fetch water depth or height")
            InfoRow("⚠️ Severity", "Severe") // e.g., Low / Moderate / Significant / Severe / Catastrophic
            InfoRow("🕒 Duration", "Get duration of flooding")
            InfoRow("🏘️ Affected Area", "Get affected region size")
        }
    }
}

@Composable
fun WildfireCard() {
    Card(
        modifier = Modifier
            .padding(12.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Whatshot, contentDescription = null, tint = Color(0xFFD32F2F))
                Spacer(Modifier.width(8.dp))
                Text("Wildfire Risk", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }

            InfoRow("📍 Region", "Get region name from API")
            InfoRow("🔥 Fire Intensity", "Moderate") // Could link to NASA FIRMS or fire index API
            InfoRow("⚠️ Danger Level", "Very High") // Low / Moderate / High / Very High / Extreme
            InfoRow("💨 Spread Speed", "Fetch from fire modeling data")
            InfoRow("🌡️ Temperature", "Get from weather API")
        }
    }
}

// Helper Row
@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontWeight = FontWeight.Medium)
        Text(value, color = Color.Gray)
    }
}

fun hazardLevel(magnitude: Int): String {
    return when (magnitude) {
        in 0..3 -> "Low"
        in 4..5 -> "Moderate"
        in 6..6 -> "High"
        else -> "Very High"
    }
}

@Composable
@Preview
fun HazardPreview() {
    Column {
        EarthquakeCard()
        FloodCard()
        WildfireCard()
    }
}