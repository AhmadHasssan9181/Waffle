package com.noobdev.waffle.core.navigation

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.noobdev.waffle.MainActivity
import com.noobdev.waffle.R
import com.noobdev.waffle.data.remote.formatDistance
import com.noobdev.waffle.feature.map.MapStyleResolver
import com.noobdev.waffle.feature.map.VehicleIcon
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.snapshotter.MapSnapshot
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter

/**
 * A large, draggable, edge-resizable floating navigation window shown over the launcher when the
 * user leaves the app. Android's system PiP window is capped to a very small rectangle on many
 * devices, so this overlay is the "real" mini navigation screen.
 *
 * The map is a MapSnapshotter bitmap with the route and vehicle drawn on top. That keeps the
 * window free of a GLSurfaceView, so dragging an edge or corner to resize it is cheap and cannot
 * stall the main thread. The base map refreshes every few seconds; the route and car redraw on
 * every navigation update.
 *
 * Requires `Settings.canDrawOverlays(context)`. The map is display-only; use the ⛶ button to
 * open the full app.
 */
class FloatingNavigationOverlay(
        private val context: Context,
        private val windowManager: WindowManager,
        private val sizeStep: Int = 0,
        private val snapshotFlow: StateFlow<NavigationSnapshot> = NavigationSession.state
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var root: FrameLayout? = null
    private var imageView: ImageView? = null
    private var params: WindowManager.LayoutParams? = null
    private var instructionView: TextView? = null
    private var detailView: TextView? = null
    private var arrowView: TextView? = null
    private var collectJob: Job? = null
    private var blinkJob: Job? = null
    private var blinkOn = true
    private var lastSpeed = 0f
    private var braking = false
    private var resizeToken = 0

    private var baseBitmap: Bitmap? = null
    private var compositeBitmap: Bitmap? = null
    private var projection: Projection? = null
    private var snapshotter: MapSnapshotter? = null
    private var carBitmap: Bitmap? = null
    private var carDrawableRes: Int = 0
    private var walkerFrames: List<Bitmap> = emptyList()
    private var walkerScaledFrames: List<Bitmap> = emptyList()
    private var walkerScaledSize = 0
    private var walkerFrameIndex = 0
    private var walkerJob: Job? = null
    private var snapshotGeneration = 0
    private var snapshotInFlight = false
    private var snapshotPending = false
    private var snapshotPendingForce = false
    private var lastSnapshotAt = 0L
    private var lastSnapshotLat = 0.0
    private var lastSnapshotLng = 0.0
    private var lastSnapshotBearing = 0.0
    private var current: NavigationSnapshot? = null

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

        val image =
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_XY
                    setBackgroundColor(Color.parseColor("#10151D"))
                }
        imageView = image
        frame.addView(
                image,
                FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        )

        // The map is display-only. Taps are swallowed here so they do not fall through to
        // the launcher behind the overlay; only the ? button opens the full app.
        val tapLayer =
                View(context).apply {
                    setBackgroundColor(Color.TRANSPARENT)
                    isClickable = true
                    setOnClickListener { }
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
                    setPadding(dp(10), dp(7), dp(10), dp(7))
                    background =
                            GradientDrawable().apply {
                                setColor(Color.parseColor("#F210151D"))
                            }
                }
        frame.addView(
                bar,
                FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                Gravity.BOTTOM
                        )
        )

        arrowView =
                TextView(context).apply {
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#F2A33C"))
                    textSize = 30f
                    text = "↑"
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
        textColumn.addView(
                TextView(context).apply {
                    text = "© MapTiler © OpenStreetMap"
                    textSize = 8f
                    setTextColor(Color.parseColor("#99A3B3"))
                    setSingleLine(true)
                }
        )

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

        // Visible resize grips, placed inside the window so Android's edge-back gesture
        // cannot steal the drag. Hold a side or the corner to resize, like system PiP.
        val rightGrip =
                TextView(context).apply {
                    text = "⇔"
                    textSize = 18f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    contentDescription = "Drag to resize width"
                    background =
                            GradientDrawable().apply {
                                setColor(Color.parseColor("#88000000"))
                                cornerRadius = dp(14).toFloat()
                            }
                }
        frame.addView(
                rightGrip,
                FrameLayout.LayoutParams(
                                dp(36),
                                dp(56),
                                Gravity.END or Gravity.CENTER_VERTICAL
                        )
                        .apply { rightMargin = dp(24) }
        )
        attachResizeTouch(rightGrip, resizeWidth = true, resizeHeight = false)

        val bottomGrip =
                TextView(context).apply {
                    text = "⇕"
                    textSize = 18f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    contentDescription = "Drag to resize height"
                    background =
                            GradientDrawable().apply {
                                setColor(Color.parseColor("#88000000"))
                                cornerRadius = dp(14).toFloat()
                            }
                }
        frame.addView(
                bottomGrip,
                FrameLayout.LayoutParams(
                                dp(56),
                                dp(36),
                                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                        )
                        .apply { bottomMargin = dp(84) }
        )
        attachResizeTouch(bottomGrip, resizeWidth = false, resizeHeight = true)

        val corner =
                TextView(context).apply {
                    text = "◢"
                    textSize = 18f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    contentDescription = "Drag to resize"
                    background =
                            GradientDrawable().apply {
                                setColor(Color.parseColor("#88000000"))
                                cornerRadius = dp(14).toFloat()
                            }
                }
        frame.addView(
                corner,
                FrameLayout.LayoutParams(dp(40), dp(40), Gravity.BOTTOM or Gravity.END).apply {
                    bottomMargin = dp(84)
                    rightMargin = dp(24)
                }
        )
        attachResizeTouch(corner, resizeWidth = true, resizeHeight = true)

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

        // Full-width drag strip.
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
                            "Tap ⛶ for full screen · drag ≡ to move · drag ⇔ / ⇕ / ◢ to resize",
                            Toast.LENGTH_LONG
                    )
                    .show()
            prefs.edit().putBoolean("hint_shown", true).apply()
        }

        collectJob =
                scope.launch {
                    snapshotFlow.collect { snapshot ->
                        if (!snapshot.active) {
                            hide()
                            return@collect
                        }
                        current = snapshot
                        runCatching {
                            updateText(snapshot)
                            updateWalkerAnimation(snapshot)
                            redrawComposite()
                            requestSnapshot(force = false)
                        }
                    }
                }

        runCatching { requestSnapshot(force = true) }
        return true
    }

    fun hide() {
        collectJob?.cancel()
        collectJob = null
        blinkJob?.cancel()
        blinkJob = null
        walkerJob?.cancel()
        walkerJob = null
        walkerFrameIndex = 0
        blinkOn = true
        snapshotGeneration++
        snapshotInFlight = false
        snapshotPending = false
        snapshotPendingForce = false
        runCatching { snapshotter?.cancel() }
        snapshotter = null
        projection = null
        baseBitmap = null
        compositeBitmap = null
        carBitmap = null
        carDrawableRes = 0
        val view = root ?: return
        root = null
        instructionView = null
        detailView = null
        arrowView = null
        imageView = null
        runCatching { windowManager.removeView(view) }
        params = null
    }

    private fun attachResizeTouch(
            view: View,
            resizeWidth: Boolean,
            resizeHeight: Boolean
    ) {
        view.setOnTouchListener(
                object : View.OnTouchListener {
                    private var startW = 0
                    private var startH = 0
                    private var touchX = 0f
                    private var touchY = 0f
                    private var pendingW = 0
                    private var pendingH = 0
                    private var active = false

                    override fun onTouch(v: View, event: MotionEvent): Boolean {
                        val p = params ?: return false
                        val frame = root ?: return false
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                startW = p.width
                                startH = p.height
                                pendingW = startW
                                pendingH = startH
                                touchX = event.rawX
                                touchY = event.rawY
                                active = true
                                resizeToken++
                                return true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if (!active) return false
                                val dm = context.resources.displayMetrics
                                if (resizeWidth) {
                                    pendingW =
                                            (startW + (event.rawX - touchX).toInt())
                                                    .coerceIn(
                                                            dp(180),
                                                            dm.widthPixels - dp(8)
                                                    )
                                }
                                if (resizeHeight) {
                                    pendingH =
                                            (startH + (event.rawY - touchY).toInt())
                                                    .coerceIn(
                                                            dp(260),
                                                            dm.heightPixels - dp(80)
                                                    )
                                }
                                p.width = pendingW
                                p.height = pendingH
                                runCatching {
                                    windowManager.updateViewLayout(frame, p)
                                    frame.requestLayout()
                                    frame.invalidate()
                                }
                                return true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                if (!active) return false
                                active = false
                                p.width = pendingW
                                p.height = pendingH
                                runCatching {
                                    windowManager.updateViewLayout(frame, p)
                                    frame.requestLayout()
                                    frame.invalidate()
                                }
                                runCatching { requestSnapshot(force = true) }
                                return true
                            }
                        }
                        return false
                    }
                }
        )
    }

    private fun requestSnapshot(force: Boolean) {
        val snapshot = current ?: return
        if (snapshot.route == null) return
        if (snapshotInFlight) {
            snapshotPending = true
            snapshotPendingForce = snapshotPendingForce || force
            return
        }
        val now = SystemClock.elapsedRealtime()
        val moved =
                GeoGeometry.haversineMeters(
                        GeoPoint(snapshot.lat, snapshot.lng),
                        GeoPoint(lastSnapshotLat, lastSnapshotLng)
                )
        val bearingDelta = abs(snapshot.bearing - lastSnapshotBearing)
        // Keep the floating map close to the live navigation camera in every mode.
        // The old car/bike throttle waited 3 s or 25 m, which felt frozen at city
        // speeds. One second / a few metres makes the route and puck track smoothly.
        val maxAge = 1_000L
        val maxMove =
                when (snapshot.profile) {
                        "foot-walking" -> 4.0
                        "cycling-regular" -> 8.0
                        else -> 12.0
                }
        if (!force &&
                        projection != null &&
                        now - lastSnapshotAt < maxAge &&
                        moved < maxMove &&
                        bearingDelta < 15.0
        ) {
            return
        }
        val width = params?.width ?: return
        val height = params?.height ?: return
        if (width <= 0 || height <= 0) return

        lastSnapshotAt = now
        lastSnapshotLat = snapshot.lat
        lastSnapshotLng = snapshot.lng
        lastSnapshotBearing = snapshot.bearing
        val generation = ++snapshotGeneration
        snapshotInFlight = true
        runCatching { snapshotter?.cancel() }
        val options =
                MapSnapshotter.Options(width, height)
                        .withStyleBuilder(
                                Style.Builder()
                                        .fromUri(
                                                MapStyleResolver.resolve(
                                                        context,
                                                        snapshot.styleIndex
                                                )
                                        )
                        )
                        .withCameraPosition(
                                CameraPosition.Builder()
                                        .target(LatLng(snapshot.lat, snapshot.lng))
                                        .zoom(15.2)
                                        .tilt(0.0)
                                        .bearing(snapshot.bearing)
                                        .build()
                        )
                        .withLogo(false)
                        .withPixelRatio(1f)
        runCatching {
                    snapshotter =
                            MapSnapshotter(context, options).also { snap ->
                                snap.start(
                                        MapSnapshotter.SnapshotReadyCallback { mapSnapshot ->
                                            Handler(Looper.getMainLooper())
                                                    .post {
                                                        snapshotInFlight = false
                                                        if (generation ==
                                                                        snapshotGeneration &&
                                                                        root != null
                                                        ) {
                                                            val base =
                                                                    runCatching {
                                                                                    mapSnapshot
                                                                                            .bitmap
                                                                                            ?.copy(
                                                                                                    Bitmap
                                                                                                            .Config
                                                                                                            .ARGB_8888,
                                                                                                    false
                                                                                            )
                                                                            }
                                                                            .getOrNull()
                                                            if (base != null) {
                                                                val proj =
                                                                        runCatching {
                                                                                        Projection.from(
                                                                                                mapSnapshot,
                                                                                                snapshot.lat,
                                                                                                snapshot.lng
                                                                                        )
                                                                                }
                                                                                .getOrNull()
                                                                if (proj != null) {
                                                                    projection = proj
                                                                    baseBitmap = base
                                                                    compositeBitmap = null
                                                                    redrawComposite()
                                                                }
                                                            }
                                                        }
                                                        if (snapshotPending) {
                                                            snapshotPending = false
                                                            val pendingForce =
                                                                    snapshotPendingForce
                                                            snapshotPendingForce = false
                                                            runCatching { requestSnapshot(pendingForce) }
                                                        }
                                                    }
                                        },
                                        MapSnapshotter.ErrorHandler { _ ->
                                            Handler(Looper.getMainLooper())
                                                    .post {
                                                        snapshotInFlight = false
                                                        if (snapshotPending) {
                                                            snapshotPending = false
                                                            val pendingForce =
                                                                    snapshotPendingForce
                                                            snapshotPendingForce = false
                                                            runCatching { requestSnapshot(pendingForce) }
                                                        }
                                                    }
                                        }
                                )
                            }
                }
                .onFailure { snapshotInFlight = false }
    }

    private fun updateBlink(turnSignal: TurnSignal) {
        if (turnSignal == TurnSignal.NONE) {
            blinkJob?.cancel()
            blinkJob = null
            blinkOn = true
            return
        }
        if (blinkJob?.isActive == true) return
        blinkJob =
                scope.launch {
                    while (true) {
                        blinkOn = !blinkOn
                        redrawComposite()
                        delay(450)
                    }
                }
    }

    private fun redrawComposite() {
        runCatching {
        val base = baseBitmap ?: return
        val projection = projection ?: return
        val nav = current ?: return
        val route = nav.route ?: return
        if (compositeBitmap == null ||
                        compositeBitmap?.width != base.width ||
                        compositeBitmap?.height != base.height
        ) {
            compositeBitmap = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        }
        val composite = compositeBitmap ?: return
        val canvas = Canvas(composite)
        canvas.drawBitmap(base, 0f, 0f, null)

        val path = Path()
        var started = false
        // Drop the part of the route already travelled so the floating line shrinks
        // just like the main map instead of showing the whole trip forever.
        val firstPoint = (nav.matchedIndex - 1).coerceAtLeast(0)
        route.coordinates.drop(firstPoint).forEach { point ->
            val pixel = projection.project(point.first, point.second)
            if (!started) {
                path.moveTo(pixel.x, pixel.y)
                started = true
            } else {
                path.lineTo(pixel.x, pixel.y)
            }
        }
        if (started) {
            if (nav.profile == "foot-walking") {
                val dots =
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#2F80ED")
                            style = Paint.Style.STROKE
                            strokeWidth = 9f
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            pathEffect =
                                    DashPathEffect(floatArrayOf(0.1f, 18f), 0f)
                        }
                canvas.drawPath(path, dots)
            } else {
                val casing =
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#F2FFFFFF")
                            style = Paint.Style.STROKE
                            strokeWidth = 13f
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                        }
                canvas.drawPath(path, casing)
                val line =
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#2F80ED")
                            style = Paint.Style.STROKE
                            strokeWidth = 8f
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                        }
                canvas.drawPath(path, line)
            }
        }

        val speed = nav.speedMps
        braking = speed < 1.5f || (lastSpeed > 0f && speed < lastSpeed - 0.6f)
        lastSpeed = speed
        val turnSignal =
                if (nav.distanceToNextTurn in 1.0..100.0) {
                        turnSignalFor(nav.route?.steps?.getOrNull(nav.instructionIndex)?.maneuver)
                } else {
                        TurnSignal.NONE
                }
        updateBlink(turnSignal)
        val lights =
                VehicleLights(
                        headlights = isNightTime(),
                        braking = braking,
                        turnSignal = turnSignal,
                        blinkOn = blinkOn
                )

        val pixel = projection.project(nav.lat, nav.lng)
        if (pixel != null) {
            val isMotorcycle = nav.profile == "driving-motorcycle"
            if ((nav.profile == "driving-car" || isMotorcycle) &&
                            (isMotorcycle || nav.vehicleIconKey != "ARROW")
            ) {
                val carSize = (base.width * 0.22f).coerceIn(64f, 140f)
                val modelKey = if (isMotorcycle) "MOTORCYCLE" else nav.vehicleIconKey
                val drawn =
                        GltfCarRenderer.draw(
                                context = context,
                                canvas = canvas,
                                centerX = pixel.x,
                                centerY = pixel.y,
                                sizePx = carSize,
                                bearingDegrees = 0f,
                                modelKey = modelKey,
                                lights = lights
                        )
                if (!drawn) {
                    Car3DRenderer.draw(
                            canvas = canvas,
                            centerX = pixel.x,
                            centerY = pixel.y,
                            sizePx = carSize,
                            bearingDegrees = 0f,
                            modelKey = modelKey
                    )
                }
            } else {
                val car = carBitmap(nav, base.width) ?: return
                canvas.drawBitmap(car, pixel.x - car.width / 2f, pixel.y - car.height / 2f, null)
            }
        }
        imageView?.setImageBitmap(composite)
        }
    }

    private fun carBitmap(snapshot: NavigationSnapshot, baseWidth: Int): Bitmap? {
        if (snapshot.profile == "foot-walking") {
            val target = (baseWidth * 0.28f).toInt().coerceIn(72, 160)
            walkerBitmap(target)?.let { return it }
        }
        val res =
                when {
                        snapshot.profile == "cycling-regular" -> R.drawable.ic_puck_bike_3d
                        snapshot.profile == "foot-walking" -> R.drawable.ic_puck_walk_3d
                        else -> VehicleIcon.fromKey(snapshot.vehicleIconKey).drawable
                }
        if (res != carDrawableRes || carBitmap == null) {
            val raw =
                    runCatching { BitmapFactory.decodeResource(context.resources, res) }
                            .getOrNull()
                            ?: return null
            val target = (baseWidth * 0.22f).toInt().coerceIn(64, 140)
            carBitmap =
                    runCatching {
                                    Bitmap.createScaledBitmap(raw, target, target, true)
                            }
                            .getOrNull()
            carDrawableRes = res
        }
        return carBitmap
    }

    private fun ensureWalkerFrames() {
        if (walkerFrames.isNotEmpty()) return
        walkerFrames =
                buildList {
                    for (index in 0 until 12) {
                        val path =
                                "walker/frame_${index.toString().padStart(2, '0')}.png"
                        runCatching {
                                    context.assets.open(path).use {
                                        BitmapFactory.decodeStream(it)
                                    }
                                }
                                .getOrNull()
                                ?.let { add(it) }
                    }
                }
    }

    private fun walkerBitmap(target: Int): Bitmap? {
        ensureWalkerFrames()
        if (walkerFrames.isEmpty()) return null
        if (walkerScaledSize != target || walkerScaledFrames.size != walkerFrames.size) {
            walkerScaledSize = target
            walkerScaledFrames =
                    walkerFrames.map { frame ->
                        Bitmap.createScaledBitmap(frame, target, target, true)
                    }
        }
        val index = walkerFrameIndex.coerceIn(0, walkerScaledFrames.lastIndex)
        return walkerScaledFrames[index]
    }

    private fun updateWalkerAnimation(snapshot: NavigationSnapshot) {
        val moving = snapshot.profile == "foot-walking" && snapshot.speedMps > 0.3f
        if (!moving) {
            walkerJob?.cancel()
            walkerJob = null
            if (walkerFrameIndex != 0) {
                walkerFrameIndex = 0
                redrawComposite()
            }
            return
        }
        if (walkerJob?.isActive == true) return
        walkerJob =
                scope.launch {
                    while (isActive) {
                        val speed = (current?.speedMps ?: 1.4f).toDouble()
                        val stepMs =
                                (85.0 * (1.4 / speed.coerceAtLeast(0.5)))
                                        .toLong()
                                        .coerceIn(55L, 140L)
                        delay(stepMs)
                        ensureWalkerFrames()
                        if (walkerFrames.isEmpty()) continue
                        walkerFrameIndex = (walkerFrameIndex + 1) % walkerFrames.size
                        redrawComposite()
                    }
                }
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
        val moved =
                runCatching {
                                val am =
                                        context.getSystemService(Context.ACTIVITY_SERVICE)
                                                as ActivityManager
                                val task = am.appTasks.firstOrNull()
                                task != null && task.moveToFront().let { true }
                        }
                        .getOrDefault(false)
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

/**
 * Local affine projection derived from a MapSnapshot while that snapshot is still alive. We
 * deliberately do not keep the MapSnapshot or call `pixelForLatLng` after the snapshotter is
 * cancelled: that native object can be freed underneath us, which is the kind of crash a
 * try/catch cannot intercept.
 */
private data class Projection(
        val centerLat: Double,
        val centerLng: Double,
        val centerX: Float,
        val centerY: Float,
        val eastX: Float,
        val eastY: Float,
        val northX: Float,
        val northY: Float
) {
    fun project(lat: Double, lng: Double): PointF {
        val dLat = (lat - centerLat).toFloat()
        val dLng = (lng - centerLng).toFloat()
        return PointF(
                centerX + dLng * eastX + dLat * northX,
                centerY + dLng * eastY + dLat * northY
        )
    }

    companion object {
        private const val DELTA = 0.001

        fun from(snapshot: MapSnapshot, centerLat: Double, centerLng: Double): Projection? {
            val center =
                    snapshot.pixelForLatLng(LatLng(centerLat, centerLng)) ?: return null
            val east =
                    snapshot.pixelForLatLng(LatLng(centerLat, centerLng + DELTA))
                            ?: return null
            val north =
                    snapshot.pixelForLatLng(LatLng(centerLat + DELTA, centerLng))
                            ?: return null
            val delta = DELTA.toFloat()
            return Projection(
                    centerLat = centerLat,
                    centerLng = centerLng,
                    centerX = center.x,
                    centerY = center.y,
                    eastX = (east.x - center.x) / delta,
                    eastY = (east.y - center.y) / delta,
                    northX = (north.x - center.x) / delta,
                    northY = (north.y - center.y) / delta
            )
        }
    }
}
