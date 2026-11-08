package com.noobdev.waffle.feature.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.noobdev.waffle.feature.flood.loadSectorData
import com.noobdev.waffle.feature.flood.sectors
import com.noobdev.waffle.data.remote.*
import com.noobdev.waffle.core.navigation.CameraTransitionManager
import com.noobdev.waffle.core.navigation.GeoGeometry
import com.noobdev.waffle.core.navigation.GeoPoint
import com.noobdev.waffle.core.navigation.HazardRegistry
import com.noobdev.waffle.core.navigation.HazardZone
import com.noobdev.waffle.core.navigation.LaneGuidanceRepository
import com.noobdev.waffle.core.navigation.LaneHint
import com.noobdev.waffle.core.navigation.ManeuverType
import com.noobdev.waffle.core.navigation.NavigationSession
import com.noobdev.waffle.core.navigation.RouteSimulator
import com.noobdev.waffle.core.navigation.TtsAudioFocus
import com.noobdev.waffle.core.navigation.SimulationSpeed
import com.noobdev.waffle.core.prefs.PreferencesManager
import com.noobdev.waffle.core.prefs.SpeedUnit
import com.noobdev.waffle.core.routing.OfflineRoutingManager
import com.noobdev.waffle.core.routing.RouteRepository
import com.noobdev.waffle.core.routing.RouteSource
import com.noobdev.waffle.design.theme.*
import kotlin.math.max
import kotlin.math.roundToInt
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationModeUI(
        routeResult: RouteResult,
        remainingDistance: Double,
        remainingDuration: Double,
        currentInstruction: String,
        currentIndex: Int,
        totalInstructions: Int,
        speedMps: Float = 0f,
        distanceToNextTurn: Double = 0.0,
        speedLimitKmh: Int? = null,
        speedUnit: SpeedUnit = SpeedUnit.KMH,
        onToggleSpeedUnit: () -> Unit = {},
        trafficLabel: String? = null,
        trafficColor: Color = SafeGreen,
        isInPip: Boolean = false,
        navigationNotice: String? = null,
        isRerouting: Boolean = false,
        wrongWay: Boolean = false,
        onNext: () -> Unit,
        onExit: () -> Unit
) {
        val approaching = distanceToNextTurn in 1.0..180.0
        val context = LocalContext.current
        val preferencesManager = remember { PreferencesManager(context.applicationContext) }
        val voiceEnabled by preferencesManager.voiceEnabled.collectAsState(initial = true)
        val scope = rememberCoroutineScope()
        var showSteps by remember { mutableStateOf(false) }
        var laneHint by remember { mutableStateOf<LaneHint?>(null) }

        LaunchedEffect(routeResult, currentIndex) {
                laneHint = null
                val step = routeResult.steps.getOrNull(currentIndex) ?: return@LaunchedEffect
                if (step.maneuver == ManeuverType.ARRIVE ||
                                step.maneuver == ManeuverType.WAYPOINT
                ) {
                        return@LaunchedEffect
                }
                val index = step.wayPointIndex.coerceIn(0, routeResult.coordinates.lastIndex)
                val point = routeResult.coordinates.getOrNull(index) ?: return@LaunchedEffect
                val previous =
                        routeResult.coordinates.getOrNull((index - 1).coerceAtLeast(0)) ?: point
                val bearing =
                        CameraTransitionManager.calculateBearing(
                                LatLng(previous.first, previous.second),
                                LatLng(point.first, point.second)
                        )
                laneHint =
                        withContext(Dispatchers.IO) {
                                LaneGuidanceRepository.ensureLoaded(context)
                                LaneGuidanceRepository.hintAt(
                                        point.first,
                                        point.second,
                                        bearing,
                                        step.maneuver
                                )
                        }
        }
        var ttsReady by remember { mutableStateOf(false) }
        var lastSpokenInstruction by remember { mutableStateOf("") }
        var lastSpokenNotice by remember { mutableStateOf<String?>(null) }
        var lastSpokenArrival by remember { mutableStateOf(false) }
        val tts =
                remember {
                        TextToSpeech(context) { status ->
                                ttsReady = status == TextToSpeech.SUCCESS
                        }
                }
        val audioFocus = remember { TtsAudioFocus(context.applicationContext) }
        DisposableEffect(tts) {
                onDispose {
                        audioFocus.abandon()
                        tts.stop()
                        tts.shutdown()
                }
        }
        LaunchedEffect(ttsReady) {
                if (ttsReady) {
                        val result = tts.setLanguage(Locale.US)
                        if (result == TextToSpeech.LANG_MISSING_DATA ||
                                        result == TextToSpeech.LANG_NOT_SUPPORTED
                        ) {
                                tts.setLanguage(Locale.getDefault())
                        }
                }
        }
        LaunchedEffect(voiceEnabled) {
                if (!voiceEnabled) {
                        audioFocus.abandon()
                        tts.stop()
                }
        }
        LaunchedEffect(currentInstruction, voiceEnabled, ttsReady) {
                if (!voiceEnabled || !ttsReady) return@LaunchedEffect
                // Real navigation is spoken by NavigationService, even with the screen off.
                if (NavigationSession.isActive()) return@LaunchedEffect
                val text = currentInstruction.trim()
                if (text.isEmpty() || text == lastSpokenInstruction) return@LaunchedEffect
                lastSpokenInstruction = text
                val spoken =
                        if (text.contains("arrive", ignoreCase = true))
                                "Continue to your destination"
                        else text
                audioFocus.begin()
                tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "instruction")
        }
        LaunchedEffect(remainingDistance, voiceEnabled, ttsReady) {
                if (!voiceEnabled || !ttsReady) return@LaunchedEffect
                if (NavigationSession.isActive()) return@LaunchedEffect
                if (remainingDistance <= 25.0) {
                        if (!lastSpokenArrival) {
                                lastSpokenArrival = true
                                audioFocus.begin()
                                tts.speak(
                                        "You have arrived at your destination",
                                        TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        "arrival"
                                )
                        }
                } else if (remainingDistance > 100.0) {
                        lastSpokenArrival = false
                }
        }
        LaunchedEffect(navigationNotice, voiceEnabled, ttsReady) {
                if (!voiceEnabled || !ttsReady) return@LaunchedEffect
                if (NavigationSession.isActive()) return@LaunchedEffect
                val notice = navigationNotice ?: return@LaunchedEffect
                if (notice == lastSpokenNotice) return@LaunchedEffect
                val phrase =
                        when {
                                notice.startsWith("Wrong way") ->
                                        "Wrong way. Turn around."
                                notice.startsWith("Off route") ->
                                        "You are off route. Finding a new way."
                                notice.startsWith("Hazard ahead") ->
                                        "Hazard ahead. Finding a safe detour."
                                notice.startsWith("Stop reached") ->
                                        "Stop reached. Continuing."
                                notice.startsWith("Rerouted") -> "Rerouted."
                                else -> null
                        }
                if (phrase != null) {
                        lastSpokenNotice = notice
                        audioFocus.begin()
                        tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "notice")
                }
        }
        Box(modifier = Modifier.fillMaxSize()) {
                val stepsSheetState =
                        rememberModalBottomSheetState(skipPartiallyExpanded = true)

                val isOverSpeedLimit =
                        speedLimitKmh != null && speedMps * 3.6f > speedLimitKmh + 2f
                if (!isInPip) {
                        NavigationSpeedBadge(
                                speedMps = speedMps,
                                unit = speedUnit,
                                isOverLimit = isOverSpeedLimit,
                                onClick = onToggleSpeedUnit,
                                modifier =
                                        Modifier.align(Alignment.BottomStart)
                                                .padding(start = 16.dp, bottom = 150.dp)
                        )
                        if (speedLimitKmh != null) {
                                SpeedLimitBadge(
                                        limitKmh = speedLimitKmh,
                                        unit = speedUnit,
                                        modifier =
                                                Modifier.align(Alignment.BottomStart)
                                                        .padding(start = 82.dp, bottom = 156.dp)
                                )
                        }
                }

                // Top instruction bar (the full card is hidden in the tiny PiP window).
                if (!isInPip) {
                val nextStep = routeResult.steps.getOrNull(currentIndex + 1)
                Card(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .statusBarsPadding()
                                        .padding(start = 12.dp, end = 12.dp, top = 6.dp),
                        shape = RoundedCornerShape(22.dp),
                        border =
                                BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                )
                ) {
                        Column(
                                modifier =
                                        Modifier.padding(
                                                horizontal = 14.dp,
                                                vertical = 12.dp
                                        )
                        ) {
                                AnimatedVisibility(
                                        visible = !isInPip && wrongWay,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                ) {
                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .background(
                                                                        DangerRed.copy(alpha = 0.12f)
                                                                )
                                                                .padding(
                                                                        horizontal = 12.dp,
                                                                        vertical = 10.dp
                                                                ),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.Warning,
                                                        contentDescription = null,
                                                        tint = DangerRed,
                                                        modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                        "Wrong way — turn around",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = DangerRed,
                                                        fontWeight = FontWeight.Bold
                                                )
                                        }
                                }
                                if (!isInPip && wrongWay) Spacer(Modifier.height(12.dp))

                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Box(
                                                Modifier.size(if (approaching) 54.dp else 48.dp)
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .background(
                                                                if (approaching)
                                                                        MaterialTheme.colorScheme.primary
                                                                else
                                                                        MaterialTheme.colorScheme.primary
                                                                                .copy(alpha = 0.14f)
                                                        ),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Icon(
                                                        imageVector =
                                                                getDirectionIcon(
                                                                        currentInstruction,
                                                                        routeResult.steps
                                                                                .getOrNull(
                                                                                        currentIndex
                                                                                )
                                                                                ?.maneuver
                                                                ),
                                                        contentDescription = null,
                                                        tint =
                                                                if (approaching)
                                                                        MaterialTheme.colorScheme.onPrimary
                                                                else
                                                                        MaterialTheme.colorScheme.primary,
                                                        modifier =
                                                                Modifier.size(
                                                                        if (approaching) 32.dp
                                                                        else 28.dp
                                                                )
                                                )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                                if (distanceToNextTurn > 0.0) {
                                                        Text(
                                                                formatDistance(distanceToNextTurn),
                                                                style = MaterialTheme.typography.titleLarge,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                fontWeight = FontWeight.ExtraBold
                                                        )
                                                }
                                                Text(
                                                        currentInstruction.ifEmpty {
                                                                "Continue straight"
                                                        },
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                )
                                                laneHint?.let { hint ->
                                                        Spacer(Modifier.height(4.dp))
                                                        LaneHintRow(hint)
                                                }
                                        }
                                        if (!isInPip) {
                                                IconButton(
                                                        onClick = { showSteps = true },
                                                        modifier = Modifier.size(40.dp)
                                                ) {
                                                        Icon(
                                                                Icons.Default.List,
                                                                contentDescription = "Steps",
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                        )
                                                }
                                                IconButton(
                                                        onClick = {
                                                        scope.launch {
                                                                preferencesManager.setVoiceEnabled(
                                                                        !voiceEnabled
                                                                )
                                                        }
                                                },
                                                        modifier = Modifier.size(40.dp)
                                                ) {
                                                        Icon(
                                                                if (voiceEnabled)
                                                                        Icons.Default.VolumeUp
                                                                else Icons.Default.VolumeOff,
                                                                contentDescription =
                                                                        if (voiceEnabled) "Mute voice"
                                                                        else "Unmute voice",
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                        )
                                                }
                                        }
                                }

                                if (nextStep != null && distanceToNextTurn in 1.0..350.0) {
                                        Spacer(Modifier.height(8.dp))
                                        Row(
                                                modifier =
                                                        Modifier.clip(RoundedCornerShape(10.dp))
                                                                .background(
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant
                                                                                .copy(alpha = 0.6f)
                                                                )
                                                                .padding(
                                                                        horizontal = 10.dp,
                                                                        vertical = 5.dp
                                                                ),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Text(
                                                        "Then",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Icon(
                                                        imageVector =
                                                                getDirectionIcon(
                                                                        nextStep.instruction,
                                                                        nextStep.maneuver
                                                                ),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(Modifier.width(5.dp))
                                                Text(
                                                        nextStep.instruction,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                )
                                        }
                                }

                                // Off-route / reroute feedback. Without this, a new route
                                // appears silently and it is impossible to tell whether the
                                // app noticed the deviation.
                                AnimatedVisibility(
                                        visible =
                                                !isInPip &&
                                                        (isRerouting ||
                                                                !navigationNotice.isNullOrBlank()),
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                ) {
                                        Row(
                                                modifier =
                                                        Modifier.padding(top = 12.dp)
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .background(
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                                                .copy(alpha = 0.12f)
                                                                )
                                                                .padding(
                                                                        horizontal = 12.dp,
                                                                        vertical = 9.dp
                                                                ),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                if (isRerouting) {
                                                        CircularProgressIndicator(
                                                                modifier = Modifier.size(14.dp),
                                                                strokeWidth = 2.dp,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                        )
                                                } else {
                                                        Icon(
                                                                imageVector =
                                                                        Icons.Default.Refresh,
                                                                contentDescription = null,
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .primary,
                                                                modifier = Modifier.size(16.dp)
                                                        )
                                                }
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                        text =
                                                                navigationNotice
                                                                        ?: "Rerouting…",
                                                        style =
                                                                MaterialTheme.typography.bodySmall,
                                                        color =
                                                                MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                )
                                        }
                                }
                                if (!isInPip && trafficLabel != null) {
                                        Row(
                                                modifier =
                                                        Modifier.padding(top = 10.dp)
                                                                .clip(RoundedCornerShape(50))
                                                                .background(
                                                                        trafficColor.copy(
                                                                                alpha = 0.12f
                                                                        )
                                                                )
                                                                .padding(
                                                                        horizontal = 10.dp,
                                                                        vertical = 6.dp
                                                                ),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Icon(
                                                        Icons.Default.Traffic,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = trafficColor
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                        trafficLabel,
                                                        style =
                                                                MaterialTheme.typography
                                                                        .labelSmall,
                                                        color = trafficColor,
                                                        fontWeight = FontWeight.SemiBold
                                                )
                                        }
                                }
                        }
                }
                }

                if (isInPip) {
                        val totalDist = routeResult.distanceMeters.coerceAtLeast(1.0)
                        val pipProgress =
                                (1.0 - (remainingDistance.coerceIn(0.0, totalDist) / totalDist))
                                        .toFloat()
                                        .coerceIn(0.02f, 1f)
                        PipNavigationBar(
                                instruction = currentInstruction,
                                distanceToNextTurn = distanceToNextTurn,
                                remainingDistance = remainingDistance,
                                remainingDuration = remainingDuration,
                                tripProgress = pipProgress,
                                maneuver = routeResult.steps.getOrNull(currentIndex)?.maneuver,
                                nextStep = routeResult.steps.getOrNull(currentIndex + 1),
                                modifier = Modifier.align(Alignment.BottomCenter)
                        )
                }

                // Bottom bar
                if (!isInPip) {
                val totalDistance = routeResult.distanceMeters.coerceAtLeast(1.0)
                val tripProgress =
                        (1.0 - (remainingDistance.coerceIn(0.0, totalDistance) / totalDistance))
                                .toFloat()
                                .coerceIn(0.02f, 1f)
                val etaText =
                        remember(remainingDuration.roundToInt() / 30) {
                                val etaMillis =
                                        System.currentTimeMillis() +
                                                (remainingDuration.coerceAtLeast(0.0) * 1000.0)
                                                        .toLong()
                                java.text.SimpleDateFormat("h:mm a", Locale.getDefault())
                                        .format(java.util.Date(etaMillis))
                        }
                Card(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .navigationBarsPadding(),
                        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                        border =
                                BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                )
                ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                                LinearProgressIndicator(
                                        progress = { tripProgress },
                                        modifier = Modifier.fillMaxWidth().height(3.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor =
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                )
                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                                formatDuration(
                                                                        remainingDuration
                                                                                .coerceAtLeast(0.0)
                                                                ),
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .titleLarge,
                                                                fontWeight = FontWeight.Bold,
                                                                color = SafeGreen
                                                        )
                                                        Spacer(Modifier.width(8.dp))
                                                        Surface(
                                                                shape = RoundedCornerShape(50),
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant
                                                        ) {
                                                                Text(
                                                                        "ETA $etaText",
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        horizontal =
                                                                                                8.dp,
                                                                                        vertical =
                                                                                                3.dp
                                                                                ),
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelSmall,
                                                                        fontWeight =
                                                                                FontWeight.SemiBold,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurfaceVariant
                                                                )
                                                        }
                                                }
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                        if (remainingDistance <= 0.0) "Arrived"
                                                        else
                                                                "${formatDistance(remainingDistance)} remaining",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                // Next step button
                                                FilledTonalButton(
                                                        onClick = onNext,
                                                        enabled =
                                                                currentIndex <
                                                                        totalInstructions - 1
                                                ) { Text("Next") }

                                                // Exit navigation
                                                FilledTonalButton(
                                                        onClick = onExit,
                                                        colors =
                                                                ButtonDefaults
                                                                        .filledTonalButtonColors(
                                                                                containerColor =
                                                                                        DangerRed
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.12f
                                                                                                ),
                                                                                contentColor =
                                                                                        DangerRed
                                                                        )
                                                ) { Text("Exit") }
                                        }
                                }
                        }
                }
                }

                if (showSteps) {
                        ModalBottomSheet(
                                onDismissRequest = { showSteps = false },
                                sheetState = stepsSheetState,
                                containerColor = MaterialTheme.colorScheme.surface
                        ) {
                                Column(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .padding(horizontal = 16.dp)
                                                        .padding(bottom = 24.dp)
                                ) {
                                        Text(
                                                "Steps",
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.height(8.dp))

                                        val steps = routeResult.steps.drop(currentIndex)
                                        if (steps.isEmpty()) {
                                                Text(
                                                        "No step details for this route.",
                                                        style =
                                                                MaterialTheme.typography
                                                                        .bodyMedium,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                        } else {
                                                LazyColumn(
                                                        modifier =
                                                                Modifier.heightIn(max = 420.dp)
                                                ) {
                                                        itemsIndexed(steps) { index, step ->
                                                                Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth()
                                                                                        .padding(
                                                                                                vertical =
                                                                                                        10.dp
                                                                                        ),
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically
                                                                ) {
                                                                        Icon(
                                                                                imageVector =
                                                                                        getDirectionIcon(
                                                                                                step.instruction,
                                                                                                step.maneuver
                                                                                        ),
                                                                                contentDescription =
                                                                                        null,
                                                                                tint =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .primary,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                24.dp
                                                                                        )
                                                                        )
                                                                        Spacer(
                                                                                Modifier.width(
                                                                                        12.dp
                                                                                )
                                                                        )
                                                                        Column(
                                                                                modifier =
                                                                                        Modifier
                                                                                                .weight(
                                                                                                        1f
                                                                                                )
                                                                        ) {
                                                                                Text(
                                                                                        step.instruction,
                                                                                        style =
                                                                                                MaterialTheme
                                                                                                        .typography
                                                                                                        .bodyMedium,
                                                                                        color =
                                                                                                MaterialTheme
                                                                                                        .colorScheme
                                                                                                        .onSurface
                                                                                )
                                                                                if (step.distance >
                                                                                                0.0
                                                                                ) {
                                                                                        Text(
                                                                                                formatDistance(
                                                                                                        step.distance
                                                                                                ),
                                                                                                style =
                                                                                                        MaterialTheme
                                                                                                                .typography
                                                                                                                .labelSmall,
                                                                                                color =
                                                                                                        MaterialTheme
                                                                                                                .colorScheme
                                                                                                                .onSurfaceVariant
                                                                                        )
                                                                                }
                                                                        }
                                                                }
                                                                if (index <
                                                                                steps.lastIndex
                                                                ) {
                                                                        HorizontalDivider(
                                                                                color =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .outline
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.25f
                                                                                                )
                                                                        )
                                                                }
                                                        }
                                                }
                                        }
                                }
                        }
                }
                if (isInPip) {
                        val displaySpeed =
                                if (speedUnit == SpeedUnit.KMH) speedMps.coerceAtLeast(0f) * 3.6f
                                else speedMps.coerceAtLeast(0f) * 2.23694f
                        val unitLabel = if (speedUnit == SpeedUnit.KMH) "km/h" else "mph"
                        Surface(
                                modifier =
                                        Modifier.align(Alignment.TopStart)
                                                .padding(start = 6.dp, top = 6.dp),
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                border =
                                        BorderStroke(
                                                if (isOverSpeedLimit) 1.5.dp else 1.dp,
                                                if (isOverSpeedLimit) DangerRed
                                                else
                                                        MaterialTheme.colorScheme.outline.copy(
                                                                alpha = 0.35f
                                                        )
                                        ),
                                shadowElevation = 3.dp
                        ) {
                                Text(
                                        text = "${displaySpeed.roundToInt()} $unitLabel",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color =
                                                if (isOverSpeedLimit) DangerRed
                                                else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        modifier =
                                                Modifier.padding(
                                                        horizontal = 8.dp,
                                                        vertical = 3.dp
                                                )
                                )
                        }
                }
        }
}

/** Small Google-Maps-style speed bubble, bottom-left, above the navigation bar. */
@Composable
private fun NavigationSpeedBadge(
        speedMps: Float,
        unit: SpeedUnit,
        isOverLimit: Boolean = false,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
) {
        val displaySpeed =
                if (unit == SpeedUnit.KMH) speedMps.coerceAtLeast(0f) * 3.6f
                else speedMps.coerceAtLeast(0f) * 2.23694f
        Surface(
                modifier = modifier.size(60.dp).clickable { onClick() },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                border =
                        BorderStroke(
                                if (isOverLimit) 2.5.dp else 1.dp,
                                if (isOverLimit) DangerRed
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                        ),
                shadowElevation = 5.dp
        ) {
                Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                ) {
                        Text(
                                "${displaySpeed.roundToInt()}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color =
                                        if (isOverLimit) DangerRed
                                        else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                                if (unit == SpeedUnit.KMH) "km/h" else "mph",
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                        if (isOverLimit) DangerRed.copy(alpha = 0.85f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                }
        }
}

/** White disc with a red ring, like a European speed-limit sign. */
@Composable
private fun SpeedLimitBadge(
        limitKmh: Int,
        unit: SpeedUnit,
        modifier: Modifier = Modifier
) {
        val display =
                if (unit == SpeedUnit.KMH) limitKmh
                else (limitKmh * 0.621371f).roundToInt()
        Surface(
                modifier = modifier.size(46.dp),
                shape = CircleShape,
                color = Color.White,
                border = BorderStroke(4.dp, DangerRed),
                shadowElevation = 3.dp
        ) {
                Box(contentAlignment = Alignment.Center) {
                        Text(
                                "$display",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Ink
                        )
                }
        }
}

@Composable
private fun PipNavigationBar(
        instruction: String,
        distanceToNextTurn: Double,
        remainingDistance: Double,
        remainingDuration: Double,
        tripProgress: Float,
        maneuver: ManeuverType?,
        nextStep: NavigationStep? = null,
        modifier: Modifier = Modifier
) {
        val approaching = distanceToNextTurn in 1.0..180.0
        val etaText =
                remember(remainingDuration.roundToInt() / 30) {
                        val etaMillis =
                                System.currentTimeMillis() +
                                        (remainingDuration.coerceAtLeast(0.0) * 1000.0).toLong()
                        java.text.SimpleDateFormat("h:mm a", Locale.getDefault())
                                .format(java.util.Date(etaMillis))
                }
        Surface(
                modifier = modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                border =
                        BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        ),
                tonalElevation = 6.dp
        ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                                progress = { tripProgress },
                                modifier = Modifier.fillMaxWidth().height(2.5.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor =
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        )
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Box(
                                        Modifier.size(38.dp)
                                                .clip(RoundedCornerShape(11.dp))
                                                .background(
                                                        if (approaching)
                                                                MaterialTheme.colorScheme.primary
                                                        else
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(alpha = 0.16f)
                                                ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Icon(
                                                imageVector =
                                                        getDirectionIcon(instruction, maneuver),
                                                contentDescription = null,
                                                tint =
                                                        if (approaching)
                                                                MaterialTheme.colorScheme.onPrimary
                                                        else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
                                        )
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (distanceToNextTurn > 0.0) {
                                                        Text(
                                                                formatDistance(distanceToNextTurn),
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelLarge,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .primary,
                                                                maxLines = 1
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                }
                                                if (nextStep != null &&
                                                                distanceToNextTurn in 1.0..250.0
                                                ) {
                                                        Text(
                                                                "Then ${nextStep.instruction}",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                        )
                                                }
                                        }
                                        Text(
                                                instruction.ifEmpty { "Continue straight" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                        )
                                        val minutes =
                                                (remainingDuration / 60.0)
                                                        .roundToInt()
                                                        .coerceAtLeast(1)
                                        val durText =
                                                if (minutes >= 60)
                                                        "${minutes / 60}h ${minutes % 60}m"
                                                else "${minutes}m"
                                        val meta =
                                                buildString {
                                                        if (remainingDuration > 0.0) {
                                                                append(durText)
                                                        }
                                                        if (remainingDistance > 0.0) {
                                                                if (isNotEmpty()) append(" · ")
                                                                append(
                                                                        formatDistance(
                                                                                remainingDistance
                                                                        )
                                                                )
                                                        }
                                                        if (remainingDuration > 0.0) {
                                                                if (isNotEmpty()) append(" · ")
                                                                append("ETA $etaText")
                                                        }
                                                }
                                        if (meta.isNotEmpty()) {
                                                Text(
                                                        meta,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = SafeGreen,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                )
                                        }
                                }
                        }
                }
        }
}

fun getDirectionIcon(
        instruction: String,
        maneuver: ManeuverType? = null
): ImageVector {
        val fromManeuver =
                when (maneuver) {
                        ManeuverType.U_TURN_LEFT -> Icons.Rounded.UTurnLeft
                        ManeuverType.U_TURN_RIGHT -> Icons.Rounded.UTurnRight
                        ManeuverType.ROUNDABOUT_LEFT -> Icons.Rounded.RoundaboutLeft
                        ManeuverType.ROUNDABOUT_RIGHT -> Icons.Rounded.RoundaboutRight
                        ManeuverType.MERGE -> Icons.Rounded.CallMerge
                        ManeuverType.RAMP_LEFT -> Icons.Rounded.RampLeft
                        ManeuverType.RAMP_RIGHT -> Icons.Rounded.RampRight
                        ManeuverType.TURN_SHARP_LEFT -> Icons.Rounded.TurnSharpLeft
                        ManeuverType.TURN_SHARP_RIGHT -> Icons.Rounded.TurnSharpRight
                        ManeuverType.TURN_SLIGHT_LEFT -> Icons.Rounded.TurnSlightLeft
                        ManeuverType.TURN_SLIGHT_RIGHT -> Icons.Rounded.TurnSlightRight
                        ManeuverType.KEEP_LEFT -> Icons.Rounded.ForkLeft
                        ManeuverType.KEEP_RIGHT -> Icons.Rounded.ForkRight
                        ManeuverType.TURN_LEFT -> Icons.Rounded.TurnLeft
                        ManeuverType.TURN_RIGHT -> Icons.Rounded.TurnRight
                        ManeuverType.ARRIVE, ManeuverType.WAYPOINT -> Icons.Rounded.Flag
                        ManeuverType.CONTINUE -> Icons.Rounded.Straight
                        ManeuverType.DEPART -> Icons.Rounded.Navigation
                        ManeuverType.UNKNOWN, null -> null
                }
        if (fromManeuver != null) return fromManeuver

        val lower = instruction.lowercase()
        return when {
                lower.contains("u-turn") || lower.contains("u turn") || lower.contains("turn around") ->
                        if (lower.contains("right")) Icons.Rounded.UTurnRight
                        else Icons.Rounded.UTurnLeft
                lower.contains("roundabout") || lower.contains("rotary") ->
                        if (lower.contains("left")) Icons.Rounded.RoundaboutLeft
                        else Icons.Rounded.RoundaboutRight
                lower.contains("merge") || lower.contains("motorway") || lower.contains("freeway") ->
                        Icons.Rounded.CallMerge
                lower.contains("ramp") || lower.contains("exit") ->
                        if (lower.contains("left")) Icons.Rounded.RampLeft
                        else Icons.Rounded.RampRight
                lower.contains("sharp left") -> Icons.Rounded.TurnSharpLeft
                lower.contains("sharp right") -> Icons.Rounded.TurnSharpRight
                lower.contains("slight left") || lower.contains("bear left") ->
                        Icons.Rounded.TurnSlightLeft
                lower.contains("slight right") || lower.contains("bear right") ->
                        Icons.Rounded.TurnSlightRight
                lower.contains("keep left") || lower.contains("fork left") ->
                        Icons.Rounded.ForkLeft
                lower.contains("keep right") || lower.contains("fork right") ->
                        Icons.Rounded.ForkRight
                lower.contains("left") -> Icons.Rounded.TurnLeft
                lower.contains("right") -> Icons.Rounded.TurnRight
                lower.contains("arrive") ||
                        lower.contains("destination") ||
                        lower.contains("via point") -> Icons.Rounded.Flag
                lower.contains("continue") ||
                        lower.contains("straight") ||
                        lower.contains("head") -> Icons.Rounded.Straight
                else -> Icons.Rounded.Navigation
        }
}

// ============================================
// MAP LAYERS SHEET
// ============================================

@Composable
private fun LaneHintRow(hint: LaneHint) {
        Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                Text(
                        if (hint.estimated) "Lanes · est." else "Lanes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(2.dp))
                hint.arrows.forEachIndexed { index, arrow ->
                        val active = index in hint.activeLanes
                        Surface(
                                shape = RoundedCornerShape(5.dp),
                                color =
                                        if (active)
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                        else
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.45f
                                                ),
                                border =
                                        if (active)
                                                BorderStroke(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.primary
                                                )
                                        else null
                        ) {
                                Text(
                                        text = arrow,
                                        modifier =
                                                Modifier.padding(
                                                        horizontal = 7.dp,
                                                        vertical = 1.dp
                                                ),
                                        style = MaterialTheme.typography.labelLarge,
                                        color =
                                                if (active)
                                                        MaterialTheme.colorScheme.primary
                                                else
                                                        MaterialTheme.colorScheme
                                                                .onSurfaceVariant,
                                        fontWeight =
                                                if (active) FontWeight.Bold
                                                else FontWeight.Normal
                                )
                        }
                }
        }
}
