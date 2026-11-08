package com.noobdev.waffle.core.navigation

import android.animation.ValueAnimator
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
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.noobdev.waffle.MainActivity
import com.noobdev.waffle.R
import com.noobdev.waffle.data.remote.formatDistance
import com.noobdev.waffle.feature.map.MapPins
import com.noobdev.waffle.feature.map.MapStyleResolver
import com.noobdev.waffle.feature.map.VehicleIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
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
 * Requires `Settings.canDrawOverlays(context)`. Drag anywhere on the map to reposition, double-tap
 * or tap ⛶ to open the full app, and drag the right/bottom edges or corner to resize.
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
    private var distanceView: TextView? = null
    private var instructionView: TextView? = null
    private var detailView: TextView? = null
    private var etaPillView: TextView? = null
    private var nextTurnView: TextView? = null
    private var arrowView: TextView? = null
    private var arrowBadge: GradientDrawable? = null
    private var speedView: TextView? = null
    private var speedPillBg: GradientDrawable? = null
    private var speedLimitView: TextView? = null
    private var muteBtnView: TextView? = null
    private var collectJob: Job? = null
    private var blinkJob: Job? = null
    private var interpJob: Job? = null
    private var edgeSnapAnimator: ValueAnimator? = null
    private var renderLat = 0.0
    private var renderLng = 0.0
    private var renderBearing = 0.0
    private var hasRenderPos = false
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
    private var destinationPinBitmap: Bitmap? = null
    private var stopPinBitmap: Bitmap? = null
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
    private var lastSnapshotStyleIndex = -1
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
        val prefs = context.getSharedPreferences("waffle_floating", Context.MODE_PRIVATE)
        val defaultWidth =
                (dm.widthPixels * widthFactor)
                        .toInt()
                        .coerceAtMost(dm.widthPixels - dp(20))
        val defaultHeight =
                (dm.heightPixels * heightFactor)
                        .toInt()
                        .coerceAtMost(dm.heightPixels - dp(100))
        val width =
                if (sizeStep == 0 && prefs.contains("saved_w"))
                        prefs.getInt("saved_w", defaultWidth)
                                .coerceIn(dp(190), dm.widthPixels - dp(8))
                else defaultWidth
        val height =
                if (sizeStep == 0 && prefs.contains("saved_h"))
                        prefs.getInt("saved_h", defaultHeight)
                                .coerceIn(dp(250), dm.heightPixels - dp(80))
                else defaultHeight
        val initialX =
                if (sizeStep == 0 && prefs.contains("saved_x"))
                        prefs.getInt("saved_x", dm.widthPixels - width - dp(12))
                                .coerceIn(0, (dm.widthPixels - width).coerceAtLeast(0))
                else dm.widthPixels - width - dp(12)
        val initialY =
                if (sizeStep == 0 && prefs.contains("saved_y"))
                        prefs.getInt("saved_y", (dm.heightPixels - height) / 2)
                                .coerceIn(
                                        dp(24),
                                        (dm.heightPixels - height - dp(24)).coerceAtLeast(dp(24))
                                )
                else (dm.heightPixels - height) / 2
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
                            x = initialX
                            y = initialY
                        }
        params = layoutParams

        val frame =
                FrameLayout(context).apply {
                    background =
                            GradientDrawable().apply {
                                setColor(Color.parseColor("#0E1621"))
                                cornerRadius = dp(24).toFloat()
                                setStroke(dp(1), Color.parseColor("#38FFFFFF"))
                            }
                    outlineProvider =
                            object : ViewOutlineProvider() {
                                override fun getOutline(view: View, outline: Outline) {
                                    outline.setRoundRect(
                                            0,
                                            0,
                                            view.width,
                                            view.height,
                                            dp(24).toFloat()
                                    )
                                }
                            }
                    clipToOutline = true
                }
        root = frame

        val image =
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_XY
                    setBackgroundColor(Color.parseColor("#0E1621"))
                }
        imageView = image
        frame.addView(
                image,
                FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        )

        // Dragging anywhere on the map moves the floating window; double-tapping opens the full app.
        val tapLayer =
                View(context).apply {
                    setBackgroundColor(Color.TRANSPARENT)
                }
        frame.addView(
                tapLayer,
                FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        )
        attachDragAndDoubleTap(tapLayer, frame)

        // Subtle top grabber pill so users know the card is draggable.
        val grabber =
                View(context).apply {
                    background =
                            GradientDrawable().apply {
                                setColor(Color.parseColor("#80FFFFFF"))
                                cornerRadius = dp(3).toFloat()
                            }
                }
        frame.addView(
                grabber,
                FrameLayout.LayoutParams(dp(36), dp(4), Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                        .apply { topMargin = dp(8) }
        )

        // Top-left live speed + speed-limit row.
        val speedRow =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
        val speedBg =
                GradientDrawable().apply {
                    setColor(Color.parseColor("#E6101722"))
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(1), Color.parseColor("#33FFFFFF"))
                }
        speedPillBg = speedBg
        val speedBadge =
                TextView(context).apply {
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    setPadding(dp(10), dp(5), dp(10), dp(5))
                    background = speedBg
                    text = "0 km/h"
                }
        speedView = speedBadge
        speedRow.addView(speedBadge)

        val limitBadge =
                TextView(context).apply {
                    setTextColor(Color.parseColor("#111827"))
                    textSize = 11f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    visibility = View.GONE
                    background =
                            GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(Color.WHITE)
                                setStroke(dp(3), Color.parseColor("#E05252"))
                            }
                }
        speedLimitView = limitBadge
        speedRow.addView(
                limitBadge,
                LinearLayout.LayoutParams(dp(30), dp(30)).apply { leftMargin = dp(6) }
        )
        frame.addView(
                speedRow,
                FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                Gravity.TOP or Gravity.START
                        )
                        .apply {
                            topMargin = dp(10)
                            leftMargin = dp(10)
                        }
        )

        // Top-right compact action pill: Voice Mute (🔊/🔇) + Expand (⛶) + Close (✕).
        val topActions =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background =
                            GradientDrawable().apply {
                                setColor(Color.parseColor("#E6101722"))
                                cornerRadius = dp(18).toFloat()
                                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
                            }
                }
        val muteBtn =
                TextView(context).apply {
                    text = "🔊"
                    textSize = 13f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    contentDescription = "Toggle voice guidance"
                    setOnClickListener {
                        NavigationSession.toggleMuted()
                        runCatching {
                            context.startService(
                                    Intent(context, NavigationService::class.java)
                                            .setAction(NavigationService.ACTION_TOGGLE_VOICE)
                            )
                        }
                    }
                }
        muteBtnView = muteBtn
        topActions.addView(muteBtn, LinearLayout.LayoutParams(dp(34), dp(32)))

        val divider0 =
                View(context).apply {
                    setBackgroundColor(Color.parseColor("#26FFFFFF"))
                }
        topActions.addView(divider0, LinearLayout.LayoutParams(dp(1), dp(16)))

        val expandBtn =
                TextView(context).apply {
                    text = "⛶"
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    contentDescription = "Open full-screen navigation"
                    setOnClickListener { openApp() }
                }
        topActions.addView(expandBtn, LinearLayout.LayoutParams(dp(34), dp(32)))

        val divider =
                View(context).apply {
                    setBackgroundColor(Color.parseColor("#26FFFFFF"))
                }
        topActions.addView(divider, LinearLayout.LayoutParams(dp(1), dp(16)))

        val closeBtn =
                TextView(context).apply {
                    text = "✕"
                    textSize = 14f
                    setTextColor(Color.parseColor("#CBD5E1"))
                    gravity = Gravity.CENTER
                    contentDescription = "Close floating map"
                    setOnClickListener { hide() }
                }
        topActions.addView(closeBtn, LinearLayout.LayoutParams(dp(34), dp(32)))
        frame.addView(
                topActions,
                FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                Gravity.TOP or Gravity.END
                        )
                        .apply {
                            topMargin = dp(10)
                            rightMargin = dp(10)
                        }
        )

        // Bottom HUD Card: matches the redesigned NavigationModeUI.
        val barContainer =
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(9))
                    background =
                            GradientDrawable().apply {
                                setColor(Color.parseColor("#F5101722"))
                                cornerRadii =
                                        floatArrayOf(
                                                dp(20).toFloat(),
                                                dp(20).toFloat(),
                                                dp(20).toFloat(),
                                                dp(20).toFloat(),
                                                0f,
                                                0f,
                                                0f,
                                                0f
                                        )
                                setStroke(dp(1), Color.parseColor("#29FFFFFF"))
                            }
                }
        frame.addView(
                barContainer,
                FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM
                )
        )
        attachDragAndDoubleTap(barContainer, frame)

        val mainRow =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
        barContainer.addView(
                mainRow,
                LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        )

        val badgeDrawable =
                GradientDrawable().apply {
                    setColor(Color.parseColor("#29F2A33C"))
                    cornerRadius = dp(14).toFloat()
                    setStroke(dp(1), Color.parseColor("#66F2A33C"))
                }
        arrowBadge = badgeDrawable
        arrowView =
                TextView(context).apply {
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#F2A33C"))
                    textSize = 24f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    background = badgeDrawable
                    text = "↑"
                }
        mainRow.addView(
                arrowView,
                LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(10) }
        )

        val textColumn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        mainRow.addView(
                textColumn,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        val topDistanceRow =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
        distanceView =
                TextView(context).apply {
                    setTextColor(Color.parseColor("#F2A33C"))
                    textSize = 15f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    setSingleLine(true)
                    text = ""
                }
        topDistanceRow.addView(distanceView)

        nextTurnView =
                TextView(context).apply {
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 11f
                    setSingleLine(true)
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    visibility = View.GONE
                    setPadding(dp(8), 0, 0, 0)
                }
        topDistanceRow.addView(
                nextTurnView,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        textColumn.addView(topDistanceRow)

        instructionView =
                TextView(context).apply {
                    setTextColor(Color.WHITE)
                    textSize = 15f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    setSingleLine(true)
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    text = "Continue"
                }
        textColumn.addView(instructionView)

        val metaRow =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(3), 0, 0)
                }
        detailView =
                TextView(context).apply {
                    setTextColor(Color.parseColor("#4ADE80"))
                    textSize = 12f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    setSingleLine(true)
                    text = ""
                }
        metaRow.addView(detailView)

        etaPillView =
                TextView(context).apply {
                    setTextColor(Color.parseColor("#CBD5E1"))
                    textSize = 10.5f
                    setSingleLine(true)
                    setPadding(dp(7), dp(2), dp(7), dp(2))
                    visibility = View.GONE
                    background =
                            GradientDrawable().apply {
                                setColor(Color.parseColor("#24FFFFFF"))
                                cornerRadius = dp(10).toFloat()
                            }
                }
        metaRow.addView(
                etaPillView,
                LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        .apply { leftMargin = dp(6) }
        )

        val attrView =
                TextView(context).apply {
                    text = "© OpenMapTiles · © OSM"
                    textSize = 8f
                    setTextColor(Color.parseColor("#64748B"))
                    gravity = Gravity.END
                    setSingleLine(true)
                }
        metaRow.addView(
                attrView,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        textColumn.addView(metaRow)

        // Unobtrusive edge & corner resize touch zones (replaces the old opaque ⇔/⇕/◢ boxes).
        val rightEdgeZone = View(context).apply { setBackgroundColor(Color.TRANSPARENT) }
        frame.addView(
                rightEdgeZone,
                FrameLayout.LayoutParams(
                                dp(20),
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                Gravity.END
                        )
                        .apply {
                            topMargin = dp(48)
                            bottomMargin = dp(90)
                        }
        )
        attachResizeTouch(rightEdgeZone, resizeWidth = true, resizeHeight = false)

        val leftEdgeZone = View(context).apply { setBackgroundColor(Color.TRANSPARENT) }
        frame.addView(
                leftEdgeZone,
                FrameLayout.LayoutParams(
                                dp(18),
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                Gravity.START
                        )
                        .apply {
                            topMargin = dp(48)
                            bottomMargin = dp(90)
                        }
        )
        attachResizeTouch(
                leftEdgeZone,
                resizeWidth = true,
                resizeHeight = false,
                invertWidth = true
        )

        val cornerGrip =
                TextView(context).apply {
                    text = "⌟"
                    textSize = 16f
                    setTextColor(Color.parseColor("#99FFFFFF"))
                    gravity = Gravity.BOTTOM or Gravity.END
                    setPadding(0, 0, dp(6), dp(2))
                    contentDescription = "Drag corner to resize"
                }
        frame.addView(
                cornerGrip,
                FrameLayout.LayoutParams(dp(32), dp(32), Gravity.BOTTOM or Gravity.END)
        )
        attachResizeTouch(cornerGrip, resizeWidth = true, resizeHeight = true)

        runCatching { windowManager.addView(frame, layoutParams) }
                .onFailure {
                    hide()
                    return false
                }

        if (!prefs.getBoolean("hint_shown_v2", false)) {
            Toast.makeText(
                            context,
                            "Drag map to move · double-tap or ⛶ for full screen · drag edges to resize",
                            Toast.LENGTH_LONG
                    )
                    .show()
            prefs.edit().putBoolean("hint_shown_v2", true).apply()
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
                            animateToFix(snapshot)
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
        interpJob?.cancel()
        interpJob = null
        edgeSnapAnimator?.cancel()
        edgeSnapAnimator = null
        hasRenderPos = false
        walkerFrameIndex = 0
        blinkOn = true
        snapshotGeneration++
        snapshotInFlight = false
        snapshotPending = false
        snapshotPendingForce = false
        lastSnapshotStyleIndex = -1
        runCatching { snapshotter?.cancel() }
        snapshotter = null
        projection = null
        baseBitmap = null
        compositeBitmap = null
        carBitmap = null
        carDrawableRes = 0
        val view = root ?: return
        root = null
        distanceView = null
        instructionView = null
        detailView = null
        etaPillView = null
        nextTurnView = null
        arrowView = null
        arrowBadge = null
        speedView = null
        speedPillBg = null
        speedLimitView = null
        muteBtnView = null
        imageView = null
        runCatching { windowManager.removeView(view) }
        params = null
    }

    private fun saveBounds(p: WindowManager.LayoutParams) {
        context.getSharedPreferences("waffle_floating", Context.MODE_PRIVATE)
                .edit()
                .putInt("saved_x", p.x)
                .putInt("saved_y", p.y)
                .putInt("saved_w", p.width)
                .putInt("saved_h", p.height)
                .apply()
    }

    private fun snapToNearestEdge(frame: FrameLayout, p: WindowManager.LayoutParams) {
        val dm = context.resources.displayMetrics
        val margin = dp(12)
        val leftX = margin
        val rightX = (dm.widthPixels - p.width - margin).coerceAtLeast(margin)
        val centerX = p.x + p.width / 2
        val targetX = if (centerX < dm.widthPixels / 2) leftX else rightX
        if (abs(p.x - targetX) <= dp(4)) {
            p.x = targetX
            runCatching { windowManager.updateViewLayout(frame, p) }
            saveBounds(p)
            return
        }
        edgeSnapAnimator?.cancel()
        val startX = p.x
        edgeSnapAnimator =
                ValueAnimator.ofInt(startX, targetX).apply {
                    duration = 210L
                    interpolator = DecelerateInterpolator()
                    addUpdateListener { anim ->
                        p.x = anim.animatedValue as Int
                        runCatching { windowManager.updateViewLayout(frame, p) }
                    }
                    start()
                }
        p.x = targetX
        saveBounds(p)
    }

    private fun animateToFix(snapshot: NavigationSnapshot) {
        val targetLat = snapshot.lat
        val targetLng = snapshot.lng
        val targetBearing = snapshot.bearing
        if (!hasRenderPos ||
                        hypot(targetLat - renderLat, targetLng - renderLng) > 0.004 ||
                        hypot(targetLat - renderLat, targetLng - renderLng) < 1e-7
        ) {
            interpJob?.cancel()
            renderLat = targetLat
            renderLng = targetLng
            renderBearing = targetBearing
            hasRenderPos = true
            redrawComposite()
            return
        }
        val fromLat = renderLat
        val fromLng = renderLng
        val fromBearing = renderBearing
        val bearingDelta = ((targetBearing - fromBearing + 540.0) % 360.0) - 180.0
        interpJob?.cancel()
        interpJob =
                scope.launch {
                    val steps = 10
                    val stepDelayMs = 24L
                    for (i in 1..steps) {
                        val t = i.toDouble() / steps.toDouble()
                        renderLat = fromLat + (targetLat - fromLat) * t
                        renderLng = fromLng + (targetLng - fromLng) * t
                        renderBearing = ((fromBearing + bearingDelta * t) % 360.0 + 360.0) % 360.0
                        redrawComposite()
                        delay(stepDelayMs)
                    }
                }
    }

    private fun attachDragAndDoubleTap(target: View, frame: FrameLayout) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()
        var pinching = false
        val scaleDetector =
                ScaleGestureDetector(
                        context,
                        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                                pinching = true
                                edgeSnapAnimator?.cancel()
                                return true
                            }

                            override fun onScale(detector: ScaleGestureDetector): Boolean {
                                val p = params ?: return false
                                val factor = detector.scaleFactor.coerceIn(0.85f, 1.18f)
                                val dm = context.resources.displayMetrics
                                val minW = dp(220)
                                val minH = dp(260)
                                val maxW = (dm.widthPixels - dp(16)).coerceAtLeast(minW)
                                val maxH = (dm.heightPixels - dp(80)).coerceAtLeast(minH)
                                p.width = (p.width * factor).roundToInt().coerceIn(minW, maxW)
                                p.height = (p.height * factor).roundToInt().coerceIn(minH, maxH)
                                p.x = p.x.coerceIn(0, (dm.widthPixels - p.width).coerceAtLeast(0))
                                p.y =
                                        p.y.coerceIn(
                                                dp(24),
                                                (dm.heightPixels - p.height - dp(24))
                                                        .coerceAtLeast(dp(24))
                                        )
                                runCatching { windowManager.updateViewLayout(frame, p) }
                                return true
                            }

                            override fun onScaleEnd(detector: ScaleGestureDetector) {
                                val p = params ?: return
                                saveBounds(p)
                                requestSnapshot(force = true)
                            }
                        }
                )
        target.setOnTouchListener(
                object : View.OnTouchListener {
                    private var startX = 0
                    private var startY = 0
                    private var downRawX = 0f
                    private var downRawY = 0f
                    private var dragging = false
                    private var lastTapUpAt = 0L

                    override fun onTouch(v: View, event: MotionEvent): Boolean {
                        val p = params ?: return false
                        scaleDetector.onTouchEvent(event)
                        if (event.pointerCount > 1) {
                            dragging = false
                            return true
                        }
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                edgeSnapAnimator?.cancel()
                                pinching = false
                                startX = p.x
                                startY = p.y
                                downRawX = event.rawX
                                downRawY = event.rawY
                                dragging = false
                                return true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if (pinching) return true
                                val dx = event.rawX - downRawX
                                val dy = event.rawY - downRawY
                                if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                                    dragging = true
                                }
                                if (dragging) {
                                    val dm = context.resources.displayMetrics
                                    p.x =
                                            (startX + dx.toInt()).coerceIn(
                                                    0,
                                                    (dm.widthPixels - p.width).coerceAtLeast(0)
                                            )
                                    p.y =
                                            (startY + dy.toInt()).coerceIn(
                                                    dp(24),
                                                    (dm.heightPixels - p.height - dp(24))
                                                            .coerceAtLeast(dp(24))
                                            )
                                    runCatching { windowManager.updateViewLayout(frame, p) }
                                }
                                return true
                            }
                            MotionEvent.ACTION_UP -> {
                                if (pinching) {
                                    pinching = false
                                    dragging = false
                                    return true
                                }
                                if (dragging) {
                                    snapToNearestEdge(frame, p)
                                } else {
                                    val now = SystemClock.uptimeMillis()
                                    if (now - lastTapUpAt in 40L..doubleTapTimeout) {
                                        lastTapUpAt = 0L
                                        openApp()
                                    } else {
                                        lastTapUpAt = now
                                    }
                                }
                                dragging = false
                                return true
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                pinching = false
                                dragging = false
                                return true
                            }
                        }
                        return false
                    }
                }
        )
    }

    private fun attachResizeTouch(
            view: View,
            resizeWidth: Boolean,
            resizeHeight: Boolean,
            invertWidth: Boolean = false
    ) {
        view.setOnTouchListener(
                object : View.OnTouchListener {
                    private var startW = 0
                    private var startH = 0
                    private var startX = 0
                    private var touchX = 0f
                    private var touchY = 0f
                    private var pendingW = 0
                    private var pendingH = 0
                    private var pendingX = 0
                    private var active = false

                    override fun onTouch(v: View, event: MotionEvent): Boolean {
                        val p = params ?: return false
                        val frame = root ?: return false
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                startW = p.width
                                startH = p.height
                                startX = p.x
                                pendingW = startW
                                pendingH = startH
                                pendingX = startX
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
                                    val dx = (event.rawX - touchX).toInt()
                                    if (invertWidth) {
                                        val newW =
                                                (startW - dx).coerceIn(
                                                        dp(190),
                                                        dm.widthPixels - dp(8)
                                                )
                                        pendingX =
                                                (startX + (startW - newW)).coerceIn(
                                                        0,
                                                        (dm.widthPixels - newW).coerceAtLeast(0)
                                                )
                                        pendingW = newW
                                    } else {
                                        pendingW =
                                                (startW + dx).coerceIn(
                                                        dp(190),
                                                        dm.widthPixels - dp(8)
                                                )
                                    }
                                }
                                if (resizeHeight) {
                                    pendingH =
                                            (startH + (event.rawY - touchY).toInt()).coerceIn(
                                                    dp(250),
                                                    dm.heightPixels - dp(80)
                                            )
                                }
                                p.width = pendingW
                                p.height = pendingH
                                p.x = pendingX
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
                                p.x = pendingX
                                runCatching {
                                    windowManager.updateViewLayout(frame, p)
                                    frame.requestLayout()
                                    frame.invalidate()
                                }
                                saveBounds(p)
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
        val bearingDelta =
                abs((((snapshot.bearing - lastSnapshotBearing) + 540.0) % 360.0) - 180.0)
        val styleChanged = snapshot.styleIndex != lastSnapshotStyleIndex
        val maxAge = 950L
        val maxMove =
                when (snapshot.profile) {
                    "foot-walking" -> 3.5
                    "cycling-regular" -> 7.0
                    else -> 10.0
                }
        if (!force &&
                        !styleChanged &&
                        projection != null &&
                        now - lastSnapshotAt < maxAge &&
                        moved < maxMove &&
                        bearingDelta < 12.0
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
        lastSnapshotStyleIndex = snapshot.styleIndex
        val generation = ++snapshotGeneration
        snapshotInFlight = true
        runCatching { snapshotter?.cancel() }

        // Shift the snapshot camera target ahead of the vehicle along its travel heading so the
        // 3D vehicle sits in the lower third above the HUD card and more upcoming road is visible.
        val lookAheadMeters =
                when (snapshot.profile) {
                    "foot-walking" -> 35.0
                    "cycling-regular" -> 55.0
                    else -> 85.0
                }
        val lookAhead =
                GeoGeometry.destinationMeters(
                        GeoPoint(snapshot.lat, snapshot.lng),
                        snapshot.bearing,
                        lookAheadMeters
                )
        val zoom =
                when (snapshot.profile) {
                    "foot-walking" -> 16.4
                    "cycling-regular" -> 16.0
                    else -> 15.6
                }
        val snapshotBearing = snapshot.bearing
        val styleJson = MapStyleResolver.snapshotStyleJson(context, snapshot.styleIndex)

        val options =
                MapSnapshotter.Options(width, height)
                        .withStyleBuilder(Style.Builder().fromJson(styleJson))
                        .withCameraPosition(
                                CameraPosition.Builder()
                                        .target(LatLng(lookAhead.latitude, lookAhead.longitude))
                                        .zoom(zoom)
                                        .tilt(0.0)
                                        .bearing(snapshotBearing)
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
                                                                                                lookAhead.latitude,
                                                                                                lookAhead.longitude,
                                                                                                snapshotBearing
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

        val liveLat = if (hasRenderPos) renderLat else nav.lat
        val liveLng = if (hasRenderPos) renderLng else nav.lng
        val liveBearing = if (hasRenderPos) renderBearing else nav.bearing

        // Snap the live vehicle position to the route so the line starts cleanly right under the
        // 3D vehicle and never leaves a stale tail behind the car.
        val snapped =
                CameraTransitionManager.snapToRoute(
                        liveLat,
                        liveLng,
                        route.coordinates,
                        maxSnapDistance = 80.0,
                        fromIndex = nav.matchedIndex.takeIf { it >= 0 } ?: 0
                )
        val matchedIdx =
                when {
                    snapped.matchedIndex >= 0 -> snapped.matchedIndex
                    nav.matchedIndex >= 0 -> nav.matchedIndex
                    else -> 0
                }
        val startLat = if (snapped.matchedIndex >= 0) snapped.snappedLat else liveLat
        val startLng = if (snapped.matchedIndex >= 0) snapped.snappedLng else liveLng

        val path = Path()
        val startPixel = projection.project(startLat, startLng)
        path.moveTo(startPixel.x, startPixel.y)
        var hasSegments = false
        route.coordinates.drop((matchedIdx + 1).coerceAtLeast(1)).forEach { point ->
            val pt = projection.project(point.first, point.second)
            path.lineTo(pt.x, pt.y)
            hasSegments = true
        }
        if (hasSegments) {
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

        // Draw a directional turn arrow chevron on the route at the upcoming maneuver vertex.
        drawTurnChevronOnRoute(canvas, projection, route.coordinates, nav, base.width, base.height)

        // Draw intermediate stops and destination pin so waypoints are visible on the mini map.
        if (nav.stops.isNotEmpty()) {
            val stopPin =
                    stopPinBitmap
                            ?: Bitmap.createScaledBitmap(
                                            MapPins.bitmap(Color.parseColor("#38BDF8")),
                                            44,
                                            44,
                                            true
                                    )
                                    .also { stopPinBitmap = it }
            nav.stops.forEach { stop ->
                val p = projection.project(stop.lat, stop.lng)
                if (p.x in -60f..(base.width + 60f) && p.y in -60f..(base.height + 60f)) {
                    canvas.drawBitmap(
                            stopPin,
                            p.x - stopPin.width / 2f,
                            p.y - stopPin.height.toFloat(),
                            null
                    )
                }
            }
        }
        nav.destination?.let { dest ->
            val destPin =
                    destinationPinBitmap
                            ?: Bitmap.createScaledBitmap(
                                            MapPins.bitmap(Color.parseColor("#F2A33C")),
                                            54,
                                            54,
                                            true
                                    )
                                    .also { destinationPinBitmap = it }
            val p = projection.project(dest.lat, dest.lng)
            if (p.x in -60f..(base.width + 60f) && p.y in -60f..(base.height + 60f)) {
                canvas.drawBitmap(
                        destPin,
                        p.x - destPin.width / 2f,
                        p.y - destPin.height.toFloat(),
                        null
                )
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

        val pixel = projection.project(liveLat, liveLng)
        val isMotorcycle = nav.profile == "driving-motorcycle"
        val isBike = nav.profile == "cycling-regular"
        // Measure vehicle rotation against the bearing of the bitmap currently on screen
        // (`projection.bearing`), not an in-flight snapshot request.
        val relativeBearing =
                (((liveBearing - projection.bearing).toFloat() + 540f) % 360f) - 180f
        val minDim = min(base.width, base.height)
        if ((nav.profile == "driving-car" || isMotorcycle || isBike) &&
                        (isMotorcycle || isBike || nav.vehicleIconKey != "ARROW")
        ) {
            val carSize = (minDim * 0.24f).coerceIn(64f, 116f)
            val modelKey =
                    when {
                        isMotorcycle -> "MOTORCYCLE"
                        isBike -> "BICYCLE"
                        else -> nav.vehicleIconKey
                    }
            val renderLights = if (isBike) VehicleLights() else lights
            val drawn =
                    GltfCarRenderer.draw(
                            context = context,
                            canvas = canvas,
                            centerX = pixel.x,
                            centerY = pixel.y,
                            sizePx = carSize,
                            bearingDegrees = relativeBearing,
                            modelKey = modelKey,
                            lights = renderLights,
                            topDown = true
                    )
            if (!drawn) {
                Car3DRenderer.draw(
                        canvas = canvas,
                        centerX = pixel.x,
                        centerY = pixel.y,
                        sizePx = carSize,
                        bearingDegrees = relativeBearing,
                        modelKey = modelKey
                )
            }
        } else {
            val car = carBitmap(nav, minDim) ?: return
            canvas.save()
            canvas.rotate(relativeBearing, pixel.x, pixel.y)
            canvas.drawBitmap(car, pixel.x - car.width / 2f, pixel.y - car.height / 2f, null)
            canvas.restore()
        }
        imageView?.setImageBitmap(composite)
        }
    }

    /**
     * Draws a high-contrast directional arrow on the road polyline at the upcoming turn vertex so
     * the driver can spot the exact intersection on the mini map.
     */
    private fun drawTurnChevronOnRoute(
            canvas: Canvas,
            projection: Projection,
            coords: List<Pair<Double, Double>>,
            nav: NavigationSnapshot,
            width: Int,
            height: Int
    ) {
        val step = nav.route?.steps?.getOrNull(nav.instructionIndex) ?: return
        val wp = step.wayPointIndex
        if (wp <= 0 || wp >= coords.lastIndex) return
        val before = coords[(wp - 1).coerceAtLeast(0)]
        val turn = coords[wp]
        val after = coords[(wp + 1).coerceAtMost(coords.lastIndex)]
        val pBefore = projection.project(before.first, before.second)
        val pTurn = projection.project(turn.first, turn.second)
        val pAfter = projection.project(after.first, after.second)
        if (pTurn.x !in -40f..(width + 40f) || pTurn.y !in -40f..(height + 40f)) return

        val inDx = pTurn.x - pBefore.x
        val inDy = pTurn.y - pBefore.y
        val outDx = pAfter.x - pTurn.x
        val outDy = pAfter.y - pTurn.y
        val inLen = hypot(inDx.toDouble(), inDy.toDouble()).toFloat().coerceAtLeast(1f)
        val outLen = hypot(outDx.toDouble(), outDy.toDouble()).toFloat().coerceAtLeast(1f)
        val stemLen = 18f
        val sx = pTurn.x - (inDx / inLen) * stemLen
        val sy = pTurn.y - (inDy / inLen) * stemLen
        val ex = pTurn.x + (outDx / outLen) * stemLen
        val ey = pTurn.y + (outDy / outLen) * stemLen

        val arrowPath =
                Path().apply {
                    moveTo(sx, sy)
                    lineTo(pTurn.x, pTurn.y)
                    lineTo(ex, ey)
                }
        val outlinePaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#0E1621")
                    style = Paint.Style.STROKE
                    strokeWidth = 9f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
        val fillPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#F2A33C")
                    style = Paint.Style.STROKE
                    strokeWidth = 5f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
        canvas.drawPath(arrowPath, outlinePaint)
        canvas.drawPath(arrowPath, fillPaint)

        // Arrowhead wings at (ex, ey) pointing along out vector
        val angle = atan2(outDy.toDouble(), outDx.toDouble())
        val wing = 10.0
        val w1x = (ex - wing * cos(angle - 0.65)).toFloat()
        val w1y = (ey - wing * sin(angle - 0.65)).toFloat()
        val w2x = (ex - wing * cos(angle + 0.65)).toFloat()
        val w2y = (ey - wing * sin(angle + 0.65)).toFloat()
        val headPath =
                Path().apply {
                    moveTo(w1x, w1y)
                    lineTo(ex, ey)
                    lineTo(w2x, w2y)
                }
        canvas.drawPath(headPath, outlinePaint)
        canvas.drawPath(headPath, fillPaint)
    }

    private fun carBitmap(snapshot: NavigationSnapshot, minDim: Int): Bitmap? {
        if (snapshot.profile == "foot-walking") {
            val target = (minDim * 0.26f).toInt().coerceIn(68, 128)
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
            val target = (minDim * 0.24f).toInt().coerceIn(64, 116)
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
        muteBtnView?.text = if (snapshot.muted) "🔇" else "🔊"
        val speedKmh = (snapshot.speedMps.coerceAtLeast(0f) * 3.6f).roundToInt()
        val limitKmh = snapshot.speedLimitKmh
        val overLimit = limitKmh != null && speedKmh > limitKmh + 2
        speedView?.text = "$speedKmh km/h"
        speedView?.setTextColor(if (overLimit) Color.parseColor("#FF6B6B") else Color.WHITE)
        speedPillBg?.setStroke(
                dp(if (overLimit) 2 else 1),
                Color.parseColor(if (overLimit) "#E05252" else "#33FFFFFF")
        )
        if (limitKmh != null && limitKmh > 0) {
            speedLimitView?.text = "$limitKmh"
            speedLimitView?.visibility = View.VISIBLE
        } else {
            speedLimitView?.visibility = View.GONE
        }

        val approaching = snapshot.distanceToNextTurn in 1.0..180.0
        val maneuver = snapshot.route?.steps?.getOrNull(snapshot.instructionIndex)?.maneuver
        arrowView?.text = arrowFor(snapshot.instruction, maneuver)
        if (approaching) {
            arrowBadge?.setColor(Color.parseColor("#F2A33C"))
            arrowBadge?.setStroke(dp(1), Color.parseColor("#F2A33C"))
            arrowView?.setTextColor(Color.parseColor("#0E1621"))
        } else {
            arrowBadge?.setColor(Color.parseColor("#29F2A33C"))
            arrowBadge?.setStroke(dp(1), Color.parseColor("#66F2A33C"))
            arrowView?.setTextColor(Color.parseColor("#F2A33C"))
        }

        if (snapshot.distanceToNextTurn > 0.0) {
            distanceView?.text = formatDistance(snapshot.distanceToNextTurn)
            distanceView?.visibility = View.VISIBLE
        } else {
            distanceView?.text = ""
            distanceView?.visibility = View.GONE
        }

        val nextStep = snapshot.route?.steps?.getOrNull(snapshot.instructionIndex + 1)
        if (nextStep != null && snapshot.distanceToNextTurn in 1.0..250.0) {
            val nextArrow = arrowFor(nextStep.instruction, nextStep.maneuver)
            nextTurnView?.text = "Then $nextArrow ${nextStep.instruction}"
            nextTurnView?.visibility = View.VISIBLE
        } else {
            nextTurnView?.visibility = View.GONE
        }

        instructionView?.text = snapshot.instruction.ifBlank { "Continue straight" }

        val notice = snapshot.notice
        if (!notice.isNullOrBlank()) {
            detailView?.text = notice
            detailView?.setTextColor(Color.parseColor("#F2A33C"))
            etaPillView?.visibility = View.GONE
            return
        }

        val minutes = (snapshot.remainingDuration / 60.0).roundToInt().coerceAtLeast(1)
        val durationStr = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "$minutes min"
        val summary =
                buildString {
                    if (snapshot.remainingDuration > 0.0) {
                        append(durationStr)
                    }
                    if (snapshot.remainingDistance > 0.0) {
                        if (isNotEmpty()) append(" · ")
                        append(formatDistance(snapshot.remainingDistance))
                    }
                }
        detailView?.text = summary.ifEmpty { "Navigating" }
        detailView?.setTextColor(Color.parseColor("#4ADE80"))

        if (snapshot.remainingDuration > 0.0) {
            val etaMillis =
                    System.currentTimeMillis() + (snapshot.remainingDuration * 1000.0).toLong()
            val etaClock =
                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(etaMillis))
            etaPillView?.text = "ETA $etaClock"
            etaPillView?.visibility = View.VISIBLE
        } else {
            etaPillView?.visibility = View.GONE
        }
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
        val bearing: Double,
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

        fun from(
                snapshot: MapSnapshot,
                centerLat: Double,
                centerLng: Double,
                bearing: Double
        ): Projection? {
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
                    bearing = bearing,
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
