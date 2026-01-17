package com.noobdev.waffle.Screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.noobdev.waffle.ui.components.*
import com.noobdev.waffle.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Rescue(navController: NavController) {
    var complaint by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var currentLat by remember { mutableStateOf<Double?>(null) }
    var currentLng by remember { mutableStateOf<Double?>(null) }
    var locationName by remember { mutableStateOf("Fetching location...") }
    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Image picker
    val imagePickerLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) {
                    uri: Uri? ->
                selectedImageUri = uri
            }

    // Get current location
    LaunchedEffect(Unit) {
        val hasPermission =
                ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        currentLat = it.latitude
                        currentLng = it.longitude
                        locationName = "${it.latitude.format(4)}, ${it.longitude.format(4)}"
                    }
                }
            } catch (e: SecurityException) {
                locationName = "Location unavailable"
            }
        } else {
            locationName = "Permission required"
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            // === HEADER ===
            Surface(color = DangerRed, shadowElevation = 4.dp) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp).statusBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                            imageVector = Icons.Default.LocalHospital,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                                text = "Emergency Rescue",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                        )
                        Text(
                                text = "Request immediate assistance",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Column(
                    modifier =
                            Modifier.fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp)
            ) {
                // === INFO BANNER ===
                Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = InfoBlue.copy(alpha = 0.1f)
                                )
                ) {
                    Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = InfoBlue,
                                modifier = Modifier.size(24.dp)
                        )
                        Text(
                                text =
                                        "This channel is for rescue services and emergency contacts during natural disasters.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // === LOCATION CARD ===
                Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                    text = "Your Location",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                                text = "Coordinates",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                                text = locationName,
                                style = WaffleTextStyles.coordinates,
                                color = MaterialTheme.colorScheme.onSurface
                        )

                        if (currentLat != null && currentLng != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = SafeGreen,
                                        modifier = Modifier.size(16.dp)
                                )
                                Text(
                                        text = "Location detected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SafeGreen
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // === EMERGENCY DESCRIPTION ===
                Text(
                        text = "Describe Your Emergency",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                        value = complaint,
                        onValueChange = { complaint = it },
                        placeholder = {
                            Text(
                                    "Describe your situation, number of people, injuries, specific needs..."
                            )
                        },
                        minLines = 5,
                        shape = RoundedCornerShape(12.dp),
                        colors =
                                OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                        modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // === IMAGE ATTACHMENT ===
                Text(
                        text = "Attach Photo (Optional)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (selectedImageUri != null) {
                    Box(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .height(180.dp)
                                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        AsyncImage(
                                model = selectedImageUri,
                                contentDescription = "Selected image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                        )

                        IconButton(
                                onClick = { selectedImageUri = null },
                                modifier =
                                        Modifier.align(Alignment.TopEnd)
                                                .padding(8.dp)
                                                .background(
                                                        color =
                                                                MaterialTheme.colorScheme.surface
                                                                        .copy(alpha = 0.9f),
                                                        shape = RoundedCornerShape(8.dp)
                                                )
                        ) {
                            Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove image",
                                    tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    Box(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .height(120.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(
                                                    2.dp,
                                                    MaterialTheme.colorScheme.outline.copy(
                                                            alpha = 0.5f
                                                    ),
                                                    RoundedCornerShape(12.dp)
                                            )
                                            .clickable { imagePickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                    text = "Tap to add photo",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // === SUBMIT BUTTON ===
                EmergencyButton(
                        text = if (isSubmitting) "Sending..." else "Send Rescue Request",
                        onClick = {
                            isSubmitting = true
                            // Simulate submission
                            // In production, this would send to your backend
                            showSuccess = true
                            isSubmitting = false
                        },
                        modifier = Modifier.fillMaxWidth()
                )

                // === SUCCESS FEEDBACK ===
                AnimatedVisibility(visible = showSuccess) {
                    Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor = SafeGreen.copy(alpha = 0.15f)
                                    )
                    ) {
                        Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SafeGreen,
                                    modifier = Modifier.size(32.dp)
                            )
                            Column {
                                Text(
                                        text = "Request Sent!",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = SafeGreen
                                )
                                Text(
                                        text = "Help is on the way. Stay where you are.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(100.dp)) // Bottom padding
            }
        }
    }
}

// Extension function for formatting doubles
fun Double.format(digits: Int) = "%.${digits}f".format(this)
