package com.noobdev.waffle.core.navigation

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.noobdev.waffle.MainActivity
import com.noobdev.waffle.R
import com.noobdev.waffle.data.remote.formatDistance
import com.noobdev.waffle.feature.map.MapStyleResolver
import com.noobdev.waffle.feature.map.VehicleIcon
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconPitchAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * A large, draggable floating navigation window shown over the launcher when the user leaves the
 * app during a trip. Android's system PiP window is capped to a very small size on many devices,
 * so this overlay is the "real" mini navigation screen: map, route, car, instruction, distance and
 * ETA at a useful size.
 *
 * Requires `Settings.canDrawOverlays(context)`. The service owns the instance; tapping the map or
 * the instruction bar opens the app, the X hides the overlay while navigation keeps running.
 */
class FloatingNavigationOverlay(
        private val context: Context,
        private val windowManager: WindowManager,
        private val sizeStep: Int = 0
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var root: FrameLayout? = null
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var params: WindowManager.LayoutParams? = null
    private var instructionView: TextView? = null
    private var detailView: TextView? = null
    private var arrowView: TextView? = null
    private var routeSource: GeoJsonSource? = null
    private var puckSource: GeoJsonSource? = null
    private var styleReady = false
    private var current: NavigationSnapshot? = null
    private var collectJob: Job? = null

    fun isShowing(): Boolean = root != null

    fun show(): Boolean {
        if (root != null) return true
        val dm = context.resources.displayMetrics
        val (widthFactor, heightFactor) =
                when (sizeStep) {
                        1 -> 0.44f to 0.48f
                        2 -> 0.84f to 0.86f
                        else -> 0.56f to 0.60f
                }
        val width =
                (dm.widthPixels * widthFactor)
                        .toInt()
                        .coerceAtMost(dm.widthPixels - dp(20))
        val height =
                (dm.heightPixels * heightFactor)
                        .toInt()
                        .coerceAtMost(dm.heightPixels - dp(100))
        val layoutParams =
                WindowManager.LayoutParams(
                                width,
                                height,
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                else
                                        @Suppress("DEPRECATION")
                                        WindowManager.LayoutParams.TYPE_PHONE,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                                PixelFormat.TRANSLUCENT
                        )
                        .apply {
                            gravity = Gravity.TOP or Gravity.START
                            x = dm.widthPixels - width - dp(12)
                            y = (dm.heightPixels - height) / 2
                        }
        params = layoutParams

        val frame =
                FrameLayout(context).apply {
                    background =
                            GradientDrawable().apply {
                                setColor(Color.parseColor("#10151D"))
                                cornerRadius = dp(22).toFloat()
                                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
                            }
                    outlineProvider =
                            object : ViewOutlineProvider() {
                                override fun getOutline(view: View, outline: Outline) {
                                    outline.setRoundRect(
                                            0,
                                            0,
                                            view.width,
                                            view.height,
                                            dp(22).toFloat()
                                    )
                                }
                            }
                    clipToOutline = true
                }
        root = frame

        // Tapping anywhere on the map opens the full-screen navigation screen.
        val tapLayer =
                View(context).apply {
                    setBackgroundColor(Color.TRANSPARENT)
                    isClickable = true
                    setOnClickListener { openApp() }
                }
        frame.addView(
                tapLayer,
                FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        )

        // Instruction / ETA bar at the bottom.
        val bar =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(9), dp(12), dp(9))
                    background =
                            GradientDrawable().apply {
                                setColor(Color.parseColor("#F210151D"))
                            }
                    setOnClickListener { openApp() }
                }
        frame.addView(
                bar,
                FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                Gravity.BOTTOM
                        )
                        .apply { bottomMargin = 0 }
        )

        arrowView =
                TextView(context).apply {
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#F2A33C"))
                    textSize = 30f
                    text = "↑"
                    setPadding(dp(2), 0, dp(2), 0)
                }
        bar.addView(arrowView, LinearLayout.LayoutParams(dp(42), dp(52)))

        val textColumn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        bar.addView(
                textColumn,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        instructionView =
                TextView(context).apply {
                    setTextColor(Color.WHITE)
                    textSize = 18f
                    setSingleLine(true)
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    text = "Continue"
                }
        textColumn.addView(instructionView)
        detailView =
                TextView(context).apply {
                    setTextColor(Color.parseColor("#F2A33C"))
                    textSize = 13f
                    setSingleLine(true)
                    text = ""
                }
        textColumn.addView(detailView)

        val fullscreen =
                TextView(context).apply {
                    setTextColor(Color.WHITE)
                    textSize = 20f
                    text = "⛶"
                    gravity = Gravity.CENTER
                    contentDescription = "Open full-screen navigation"
                    setOnClickListener { openApp() }
                }
        bar.addView(fullscreen, LinearLayout.LayoutParams(dp(46), dp(44)))

        // Close X.
        val close =
                TextView(context).apply {
                    text = "✕"
                    textSize = 18f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    background =
                            GradientDrawable().apply {
                                setColor(Color.parseColor("#66000000"))
                                cornerRadius = dp(18).toFloat()
                            }
                    setOnClickListener { hide() }
                }
        frame.addView(
                close,
                FrameLayout.LayoutParams(dp(36), dp(36), Gravity.TOP or Gravity.END).apply {
                    topMargin = dp(8)
                    rightMargin = dp(8)
                }
        )

        // Drag handle.
        val drag =
                TextView(context).apply {
                    text = "≡"
                    textSize = 20f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    contentDescription = "Drag to move"
                }
        frame.addView(
                drag,
                FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                dp(40),
                                Gravity.TOP
                        )
                        .apply { topMargin = dp(6) }
        )
        drag.setOnTouchListener(
                object : View.OnTouchListener {
                    private var startX = 0
                    private var startY = 0
                    private var touchX = 0f
                    private var touchY = 0f

                    override fun onTouch(v: View, event: MotionEvent): Boolean {
                        val p = params ?: return false
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                startX = p.x
                                startY = p.y
                                touchX = event.rawX
                                touchY = event.rawY
                                return true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                p.x = startX + (event.rawX - touchX).toInt()
                                p.y = startY + (event.rawY - touchY).toInt()
                                runCatching { windowManager.updateViewLayout(frame, p) }
                                return true
                            }
                        }
                        return false
                    }
                }
        )

        // Keep the close button above the full-width drag strip.
        close.bringToFront()

        runCatching { windowManager.addView(frame, layoutParams) }
                .onFailure {
                    hide()
                    return false
                }
        val prefs = context.getSharedPreferences("waffle_floating", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("hint_shown", false)) {
            Toast.makeText(
                            context,
                            "Tap map for full screen · drag ≡ to move · ⤢ changes size",
                            Toast.LENGTH_LONG
                    )
                    .show()
            prefs.edit().putBoolean("hint_shown", true).apply()
        }

        collectJob =
                scope.launch {
                    NavigationSession.state.collect { snapshot ->
                        if (!snapshot.active) {
                            hide()
                            return@collect
                        }
                        current = snapshot
                        updateText(snapshot)
                        updateMap(snapshot)
                    }
                }

        attachMap()
        return true
    }

    private fun attachMap() {
        val frame = root ?: return
        val view =
                MapView(context).apply {
                    onCreate(null)
                    onStart()
                    onResume()
                }
        mapView = view
        frame.addView(
                view,
                0,
                FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        )
        view.getMapAsync { mapLibre ->
            map = mapLibre
            runCatching {
                mapLibre.uiSettings.isLogoEnabled = false
                mapLibre.uiSettings.isAttributionEnabled = false
                mapLibre.uiSettings.isCompassEnabled = false
                mapLibre.uiSettings.setAllGesturesEnabled(false)
            }
            val snapshot = current ?: NavigationSession.snapshot()
            val styleUrl = MapStyleResolver.resolve(context, snapshot.styleIndex)
            mapLibre.setStyle(Style.Builder().fromUri(styleUrl))
            mapLibre.getStyle { style ->
                styleReady = true
                setupLayers(style, snapshot)
                updateMap(snapshot)
            }
        }
    }

    private fun detachMap() {
        val view = mapView ?: return
        root?.removeView(view)
        runCatching {
            view.onPause()
            view.onStop()
            view.onDestroy()
        }
        mapView = null
        map = null
        styleReady = false
        routeSource = null
        puckSource = null
    }

    fun hide() {
        collectJob?.cancel()
        collectJob = null
        val view = root ?: return
        detachMap()
        root = null
        instructionView = null
        detailView = null
        arrowView = null
        runCatching { windowManager.removeView(view) }
        params = null
    }

    private fun updateText(snapshot: NavigationSnapshot) {
        instructionView?.text = snapshot.instruction
        val notice = snapshot.notice
        if (!notice.isNullOrBlank()) {
            detailView?.text = notice
            return
        }
        val minutes = (snapshot.remainingDuration / 60.0).roundToInt().coerceAtLeast(1)
        val eta = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
        val detail =
                buildString {
                        if (snapshot.distanceToNextTurn > 0.0) {
                                append(formatDistance(snapshot.distanceToNextTurn))
                        }
                        if (snapshot.remainingDuration > 0.0) {
                                if (isNotEmpty()) append(" · ")
                                append(eta)
                        }
                }
        detailView?.text = detail
        val maneuver = snapshot.route?.steps?.getOrNull(snapshot.instructionIndex)?.maneuver
        arrowView?.text = arrowFor(snapshot.instruction, maneuver)
    }

    private fun setupLayers(style: Style, snapshot: NavigationSnapshot) {
        val route = snapshot.route ?: return
        runCatching {
            style.removeLayer("float-route-layer")
            style.removeSource("float-route-source")
            style.removeLayer("float-puck-layer")
            style.removeSource("float-puck-source")
            style.removeImage("float-puck")

            val coords =
                    route.coordinates.joinToString(",") { "[${it.second},${it.first}]" }
            val routeGeoJson =
                    """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]}}"""
            val rs = GeoJsonSource("float-route-source", routeGeoJson)
            style.addSource(rs)
            routeSource = rs
            style.addLayer(
                    LineLayer("float-route-layer", "float-route-source")
                            .withProperties(
                                    lineColor(Color.parseColor("#F2A33C")),
                                    lineWidth(7f),
                                    lineOpacity(0.95f),
                                    lineCap(Property.LINE_CAP_ROUND),
                                    lineJoin(Property.LINE_JOIN_ROUND)
                            )
            )

            val puckRes =
                    when {
                        snapshot.profile == "cycling-regular" -> R.drawable.ic_puck_bike_3d
                        snapshot.profile == "foot-walking" -> R.drawable.ic_puck_walk_3d
                        else ->
                                VehicleIcon.fromKey(snapshot.vehicleIconKey).drawable
                    }
            val bitmap = android.graphics.BitmapFactory.decodeResource(context.resources, puckRes)
            style.addImage("float-puck", bitmap)
            val ps =
                    GeoJsonSource(
                            "float-puck-source",
                            puckGeoJson(snapshot.lat, snapshot.lng, snapshot.bearing)
                    )
            style.addSource(ps)
            puckSource = ps
            style.addLayer(
                    SymbolLayer("float-puck-layer", "float-puck-source")
                            .withProperties(
                                    iconImage("float-puck"),
                                    iconSize(0.55f),
                                    iconRotate(Expression.get("bearing")),
                                    iconRotationAlignment(
                                            Property.ICON_ROTATION_ALIGNMENT_MAP
                                    ),
                                    iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
                                    iconAllowOverlap(true),
                                    iconIgnorePlacement(true),
                                    iconAnchor(Property.ICON_ANCHOR_CENTER)
                            )
            )
        }
    }

    private fun updateMap(snapshot: NavigationSnapshot) {
        if (!styleReady) return
        val route = snapshot.route ?: return
        val coords = route.coordinates.joinToString(",") { "[${it.second},${it.first}]" }
        routeSource?.setGeoJson(
                """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]}}"""
        )
        puckSource?.setGeoJson(puckGeoJson(snapshot.lat, snapshot.lng, snapshot.bearing))
        map?.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                                .target(LatLng(snapshot.lat, snapshot.lng))
                                .zoom(15.2)
                                .tilt(0.0)
                                .bearing(snapshot.bearing)
                                .build()
                ),
                450
        )
    }

    private fun puckGeoJson(lat: Double, lng: Double, bearing: Double): String =
            JSONObject()
                    .put("type", "Feature")
                    .put(
                            "geometry",
                            JSONObject()
                                    .put("type", "Point")
                                    .put("coordinates", JSONArray().put(lng).put(lat))
                    )
                    .put("properties", JSONObject().put("bearing", bearing))
                    .toString()

    private fun arrowFor(instruction: String, maneuver: ManeuverType?): String {
        return when (maneuver) {
                        ManeuverType.U_TURN_LEFT,
                        ManeuverType.U_TURN_RIGHT -> "↺"
                        ManeuverType.ROUNDABOUT_LEFT,
                        ManeuverType.ROUNDABOUT_RIGHT -> "↻"
                        ManeuverType.TURN_LEFT,
                        ManeuverType.TURN_SHARP_LEFT,
                        ManeuverType.TURN_SLIGHT_LEFT,
                        ManeuverType.KEEP_LEFT,
                        ManeuverType.RAMP_LEFT -> "↰"
                        ManeuverType.TURN_RIGHT,
                        ManeuverType.TURN_SHARP_RIGHT,
                        ManeuverType.TURN_SLIGHT_RIGHT,
                        ManeuverType.KEEP_RIGHT,
                        ManeuverType.RAMP_RIGHT -> "↱"
                        ManeuverType.ARRIVE, ManeuverType.WAYPOINT -> "⚑"
                        else -> "↑"
                }
                .let { arrow ->
                        if (maneuver == null && instruction.contains("left", true)) "↰"
                        else if (maneuver == null && instruction.contains("right", true)) "↱"
                        else arrow
                }
    }

    private fun openApp() {
        // Bring the existing task forward. A background service cannot always start an
        // activity on newer Android versions, but moving the app's own task to the front is
        // allowed with REORDER_TASKS and gives the full-screen navigation UI immediately.
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val task = am.appTasks.firstOrNull()
        val moved = task != null && runCatching { task.moveToFront() }.isSuccess
        if (!moved) {
            runCatching {
                context.startActivity(
                        Intent(context, MainActivity::class.java).apply {
                            flags =
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                )
            }
        }
        NavigationSession.requestOpenMap()
        hide()
    }

    private fun dp(value: Int): Int =
            (value * context.resources.displayMetrics.density).roundToInt()
}
