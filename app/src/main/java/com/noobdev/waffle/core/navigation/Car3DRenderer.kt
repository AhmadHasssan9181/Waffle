package com.noobdev.waffle.core.navigation

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A small software 3D renderer for the navigation vehicle.
 *
 * MapLibre's Android SDK does not expose a usable custom-layer host, and the old puck was a flat
 * perspective sprite. This draws a real low-poly car mesh with a rear/above camera, dynamic yaw,
 * per-face lighting, specular highlights and a ground shadow. It is used by the main map overlay
 * and the floating navigation window, so both show the same 3D vehicle.
 */
object Car3DRenderer {

    private data class V3(val x: Float, val y: Float, val z: Float)

    private data class Face(
            val points: List<V3>,
            val color: Int,
            val emissive: Boolean = false
    )

    private data class CameraPoint(val x: Float, val y: Float, val depth: Float)

    private data class Station(
            val z: Float,
            val halfWidth: Float,
            val bottom: Float,
            val top: Float
    )

    private data class CabinStation(
            val z: Float,
            val halfWidth: Float,
            val bottom: Float,
            val top: Float
    )

    private val meshCache = HashMap<String, List<Face>>()

    fun draw(
            canvas: Canvas,
            centerX: Float,
            centerY: Float,
            sizePx: Float,
            bearingDegrees: Float,
            modelKey: String
    ) {
        if (sizePx <= 0f) return
        drawShadow(canvas, centerX, centerY, sizePx)

        val faces = synchronized(meshCache) {
            meshCache.getOrPut(modelKey.uppercase()) { buildCar(modelKey.uppercase()) }
        }
        val yaw = Math.toRadians(bearingDegrees.toDouble())
        val cosYaw = cos(yaw).toFloat()
        val sinYaw = sin(yaw).toFloat()

        val eye = V3(3.2f, 3.0f, -6.0f)
        val target = V3(0f, 0.62f, 0f)
        val up = V3(0f, 1f, 0f)
        val forward = normalize(sub(target, eye))
        val right = normalize(cross(forward, up))
        val trueUp = cross(right, forward)
        val fov = Math.toRadians(30.0)
        val focal = (1.0 / tan(fov / 2.0)).toFloat()
        val scale = sizePx * 0.70f
        val light = normalize(V3(-0.45f, 0.85f, -0.30f))

        data class Projected(
                val path: Path,
                val color: Int,
                val depth: Float
        )

        val projected = ArrayList<Projected>(faces.size)
        for (face in faces) {
            val screenPoints = ArrayList<CameraPoint>(face.points.size)
            var depthSum = 0f
            var visible = true
            var centerModel = V3(0f, 0f, 0f)
            for (point in face.points) {
                // Rotate the model clockwise around Y so the front follows the bearing.
                val rx = point.x * cosYaw + point.z * sinYaw
                val rz = -point.x * sinYaw + point.z * cosYaw
                val world = V3(rx, point.y, rz)
                val relative = sub(world, eye)
                val depth = dot(relative, forward)
                if (depth <= 0.05f) {
                    visible = false
                    break
                }
                val xCam = dot(relative, right)
                val yCam = dot(relative, trueUp)
                val sx = centerX + (xCam * focal / depth) * scale
                val sy = centerY - (yCam * focal / depth) * scale
                screenPoints.add(CameraPoint(sx, sy, depth))
                depthSum += depth
                centerModel = add(centerModel, point)
            }
            if (!visible || screenPoints.size < 3) continue

            val path = Path()
            path.moveTo(screenPoints[0].x, screenPoints[0].y)
            for (index in 1 until screenPoints.size) {
                path.lineTo(screenPoints[index].x, screenPoints[index].y)
            }
            path.close()

            val modelNormal = faceNormal(face.points)
            val normal =
                    V3(
                            modelNormal.x * cosYaw + modelNormal.z * sinYaw,
                            modelNormal.y,
                            -modelNormal.x * sinYaw + modelNormal.z * cosYaw
                    )
            val center = scale(centerModel, 1f / face.points.size)
            val worldCenter =
                    V3(
                            center.x * cosYaw + center.z * sinYaw,
                            center.y,
                            -center.x * sinYaw + center.z * cosYaw
                    )
            val view = normalize(sub(eye, worldCenter))
            val halfVector = normalize(add(light, view))
            val specular =
                    if (face.emissive) 0f
                    else
                            Math.pow(
                                            max(dot(normal, halfVector), 0f).toDouble(),
                                            18.0
                                    )
                                    .toFloat() * 0.40f
            val shadeFactor =
                    if (face.emissive) 1f
                    else 0.58f + 0.42f * abs(dot(normal, light))
            val color = addSpecular(applyShade(face.color, shadeFactor), specular)
            projected.add(Projected(path, color, depthSum / screenPoints.size))
        }

        projected.sortByDescending { it.depth }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(0.7f, sizePx * 0.006f)
            color = Color.argb(38, 0, 0, 0)
        }
        for (face in projected) {
            fill.color = face.color
            canvas.drawPath(face.path, fill)
            canvas.drawPath(face.path, outline)
        }
    }

    private fun drawShadow(canvas: Canvas, centerX: Float, centerY: Float, sizePx: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        for (layer in 0..5) {
            val expansion = sizePx * (0.050f * layer)
            paint.color = Color.argb(16 - layer * 2, 0, 0, 0)
            canvas.drawOval(
                    centerX - sizePx * 0.34f - expansion,
                    centerY + sizePx * 0.25f - expansion * 0.4f,
                    centerX + sizePx * 0.34f + expansion,
                    centerY + sizePx * 0.43f + expansion * 0.4f,
                    paint
            )
        }
        paint.color = Color.argb(58, 0, 0, 0)
        canvas.drawOval(
                centerX - sizePx * 0.24f,
                centerY + sizePx * 0.30f,
                centerX + sizePx * 0.24f,
                centerY + sizePx * 0.37f,
                paint
        )
    }

    private fun buildCar(model: String): List<Face> {
        val faces = ArrayList<Face>(260)
        val suv = model == "SUV"
        val pickup = model == "PICKUP"
        val body =
                when {
                    suv -> Color.rgb(232, 174, 38)
                    pickup -> Color.rgb(42, 126, 176)
                    else -> Color.rgb(205, 65, 44)
                }
        val bodyDark = applyShade(body, 0.60f)
        val bodyLight = applyShade(body, 1.22f)
        val glass = Color.rgb(24, 46, 60)
        val glassLight = Color.rgb(88, 137, 164)
        val wheel = Color.rgb(22, 25, 29)
        val rim = Color.rgb(178, 190, 198)
        val chrome = Color.rgb(214, 226, 231)

        val bodyBottom = 0.18f
        val bodyTop = if (suv) 0.84f else if (pickup) 0.78f else 0.72f
        val roofY = if (suv) 1.52f else if (pickup) 1.38f else 1.28f
        val rearZ = -1.42f
        val frontZ = 1.45f
        val bodyHalf = if (suv) 0.66f else 0.64f
        val cabinRear = if (pickup) -0.20f else -0.80f
        val cabinFront = 0.68f
        val cabinHalf = if (suv) 0.48f else 0.45f

        val bodyStations =
                listOf(
                        Station(rearZ, bodyHalf * 0.88f, bodyBottom, bodyTop - 0.12f),
                        Station(-1.28f, bodyHalf, bodyBottom, bodyTop - 0.03f),
                        Station(-0.80f, bodyHalf, bodyBottom, bodyTop),
                        Station(0.00f, bodyHalf, bodyBottom, bodyTop),
                        Station(0.80f, bodyHalf * 0.98f, bodyBottom, bodyTop - 0.02f),
                        Station(1.24f, bodyHalf * 0.88f, bodyBottom, bodyTop - 0.08f),
                        Station(frontZ, bodyHalf * 0.78f, bodyBottom, bodyTop - 0.14f)
                )
        addBody(faces, bodyStations, body, bodyDark, bodyLight)
        addCabin(
                faces = faces,
                rearZ = cabinRear,
                frontZ = cabinFront,
                halfWidth = cabinHalf,
                bottom = bodyTop,
                top = roofY,
                glass = glass,
                glassLight = glassLight,
                roof = bodyLight,
                chrome = chrome,
                rearWindow = !pickup
        )

        // Wheels sit just outside the body so they are visible in the 3/4 view.
        val wheelX = bodyHalf + 0.055f
        val wheelRadius = if (suv) 0.35f else 0.33f
        addWheel(faces, -wheelX, 0.34f, -0.96f, wheelRadius, 0.24f, wheel, rim)
        addWheel(faces, wheelX, 0.34f, -0.96f, wheelRadius, 0.24f, wheel, rim)
        addWheel(faces, -wheelX, 0.34f, 0.96f, wheelRadius, 0.22f, wheel, rim)
        addWheel(faces, wheelX, 0.34f, 0.96f, wheelRadius, 0.22f, wheel, rim)

        // Mirrors.
        addBox(faces, bodyHalf - 0.02f, bodyHalf + 0.16f, 0.76f, 0.90f, 0.44f, 0.62f, body)
        addBox(faces, -bodyHalf - 0.16f, -bodyHalf + 0.02f, 0.76f, 0.90f, 0.44f, 0.62f, body)
        faces +=
                Face(
                        listOf(
                                V3(bodyHalf + 0.15f, 0.78f, 0.63f),
                                V3(bodyHalf + 0.15f, 0.88f, 0.63f),
                                V3(bodyHalf + 0.15f, 0.88f, 0.44f),
                                V3(bodyHalf + 0.15f, 0.78f, 0.44f)
                        ),
                        glassLight
                )
        faces +=
                Face(
                        listOf(
                                V3(-bodyHalf - 0.15f, 0.78f, 0.63f),
                                V3(-bodyHalf - 0.15f, 0.78f, 0.44f),
                                V3(-bodyHalf - 0.15f, 0.88f, 0.44f),
                                V3(-bodyHalf - 0.15f, 0.88f, 0.63f)
                        ),
                        glassLight
                )

        // Door crease on both flanks.
        faces +=
                Face(
                        listOf(
                                V3(bodyHalf + 0.005f, 0.34f, -0.10f),
                                V3(bodyHalf + 0.005f, 0.64f, -0.10f),
                                V3(bodyHalf + 0.005f, 0.64f, 0.58f),
                                V3(bodyHalf + 0.005f, 0.34f, 0.58f)
                        ),
                        bodyDark
                )
        faces +=
                Face(
                        listOf(
                                V3(-bodyHalf - 0.005f, 0.34f, -0.10f),
                                V3(-bodyHalf - 0.005f, 0.34f, 0.58f),
                                V3(-bodyHalf - 0.005f, 0.64f, 0.58f),
                                V3(-bodyHalf - 0.005f, 0.64f, -0.10f)
                        ),
                        bodyDark
                )

        // Rear light bar, plate and exhaust.
        val rearLightZ = rearZ - 0.012f
        faces +=
                Face(
                        listOf(
                                V3(-0.48f, 0.50f, rearLightZ),
                                V3(-0.08f, 0.50f, rearLightZ),
                                V3(-0.08f, 0.62f, rearLightZ),
                                V3(-0.48f, 0.62f, rearLightZ)
                        ),
                        Color.rgb(226, 38, 38),
                        emissive = true
                )
        faces +=
                Face(
                        listOf(
                                V3(0.08f, 0.50f, rearLightZ),
                                V3(0.48f, 0.50f, rearLightZ),
                                V3(0.48f, 0.62f, rearLightZ),
                                V3(0.08f, 0.62f, rearLightZ)
                        ),
                        Color.rgb(226, 38, 38),
                        emissive = true
                )
        faces +=
                Face(
                        listOf(
                                V3(-0.18f, 0.29f, rearLightZ),
                                V3(0.18f, 0.29f, rearLightZ),
                                V3(0.18f, 0.42f, rearLightZ),
                                V3(-0.18f, 0.42f, rearLightZ)
                        ),
                        chrome
                )
        addBox(faces, -0.30f, -0.20f, 0.18f, 0.24f, rearZ - 0.05f, rearZ, wheel)
        addBox(faces, 0.20f, 0.30f, 0.18f, 0.24f, rearZ - 0.05f, rearZ, wheel)

        // Headlights and grille.
        val frontLightZ = frontZ + 0.012f
        faces +=
                Face(
                        listOf(
                                V3(-0.46f, 0.46f, frontLightZ),
                                V3(-0.18f, 0.46f, frontLightZ),
                                V3(-0.18f, 0.62f, frontLightZ),
                                V3(-0.46f, 0.62f, frontLightZ)
                        ),
                        Color.rgb(255, 244, 190),
                        emissive = true
                )
        faces +=
                Face(
                        listOf(
                                V3(0.18f, 0.46f, frontLightZ),
                                V3(0.46f, 0.46f, frontLightZ),
                                V3(0.46f, 0.62f, frontLightZ),
                                V3(0.18f, 0.62f, frontLightZ)
                        ),
                        Color.rgb(255, 244, 190),
                        emissive = true
                )
        faces +=
                Face(
                        listOf(
                                V3(-0.17f, 0.27f, frontLightZ),
                                V3(0.17f, 0.27f, frontLightZ),
                                V3(0.17f, 0.44f, frontLightZ),
                                V3(-0.17f, 0.44f, frontLightZ)
                        ),
                        Color.rgb(18, 23, 27)
                )

        // Roof rails / spoiler.
        if (suv) {
            addBox(faces, -0.43f, -0.36f, roofY + 0.02f, roofY + 0.07f, -0.55f, 0.45f, bodyLight)
            addBox(faces, 0.36f, 0.43f, roofY + 0.02f, roofY + 0.07f, -0.55f, 0.45f, bodyLight)
        } else if (!pickup) {
            addBox(faces, -0.34f, 0.34f, roofY + 0.01f, roofY + 0.05f, -0.88f, -0.72f, bodyLight)
        }

        // Sunroof glass.
        faces +=
                Face(
                        listOf(
                                V3(-0.22f, roofY + 0.006f, -0.42f),
                                V3(0.22f, roofY + 0.006f, -0.42f),
                                V3(0.22f, roofY + 0.006f, 0.22f),
                                V3(-0.22f, roofY + 0.006f, 0.22f)
                        ),
                        glass
                )

        if (pickup) {
            val bedTop = bodyTop + 0.02f
            faces +=
                    Face(
                            listOf(
                                    V3(-0.46f, bedTop, rearZ + 0.06f),
                                    V3(0.46f, bedTop, rearZ + 0.06f),
                                    V3(0.46f, bedTop, cabinRear - 0.08f),
                                    V3(-0.46f, bedTop, cabinRear - 0.08f)
                            ),
                            Color.rgb(18, 25, 31)
                    )
            addBox(faces, -0.54f, -0.46f, bedTop, bedTop + 0.28f, rearZ + 0.04f, cabinRear - 0.06f, bodyDark)
            addBox(faces, 0.46f, 0.54f, bedTop, bedTop + 0.28f, rearZ + 0.04f, cabinRear - 0.06f, bodyDark)
            addBox(faces, -0.54f, 0.54f, bedTop, bedTop + 0.28f, rearZ + 0.04f, rearZ + 0.12f, bodyDark)
        }

        return faces
    }

    private fun addBody(
            faces: MutableList<Face>,
            stations: List<Station>,
            body: Int,
            bodyDark: Int,
            bodyLight: Int
    ) {
        val rings = stations.map { station ->
            val half = station.halfWidth
            val corner = 0.07f
            listOf(
                    V3(-half + corner, station.bottom, station.z),
                    V3(half - corner, station.bottom, station.z),
                    V3(half, station.bottom + 0.10f, station.z),
                    V3(half, station.top - 0.10f, station.z),
                    V3(half - corner, station.top, station.z),
                    V3(-half + corner, station.top, station.z),
                    V3(-half, station.top - 0.10f, station.z),
                    V3(-half, station.bottom + 0.10f, station.z)
            )
        }
        for (index in 0 until rings.size - 1) {
            val a = rings[index]
            val b = rings[index + 1]
            for (edge in 0 until 8) {
                val next = (edge + 1) % 8
                val color =
                        when (edge) {
                            0, 6, 7 -> bodyDark
                            3, 4 -> bodyLight
                            else -> body
                        }
                faces += Face(listOf(a[edge], a[next], b[next], b[edge]), color)
            }
        }
        faces += Face(rings.first().reversed(), bodyDark)
        faces += Face(rings.last(), body)
    }

    private fun addCabin(
            faces: MutableList<Face>,
            rearZ: Float,
            frontZ: Float,
            halfWidth: Float,
            bottom: Float,
            top: Float,
            glass: Int,
            glassLight: Int,
            roof: Int,
            chrome: Int,
            rearWindow: Boolean
    ) {
        val stations =
                listOf(
                        CabinStation(rearZ, halfWidth, bottom, top),
                        CabinStation(rearZ + 0.28f, halfWidth, bottom, top),
                        CabinStation(frontZ - 0.28f, halfWidth * 0.96f, bottom, top),
                        CabinStation(frontZ, halfWidth * 0.88f, bottom, top)
                )
        val rings = stations.map { station ->
            val half = station.halfWidth
            listOf(
                    V3(-half, station.bottom, station.z),
                    V3(half, station.bottom, station.z),
                    V3(half * 0.90f, station.top, station.z),
                    V3(-half * 0.90f, station.top, station.z)
            )
        }
        for (index in 0 until rings.size - 1) {
            val a = rings[index]
            val b = rings[index + 1]
            // Side glass.
            faces += Face(listOf(a[0], a[3], b[3], b[0]), glass)
            faces += Face(listOf(a[1], b[1], b[2], a[2]), glass)
            // Roof.
            faces += Face(listOf(a[3], a[2], b[2], b[3]), roof)
            // Window chrome at the belt line.
            faces +=
                    Face(
                            listOf(
                                    V3(-halfWidth - 0.005f, bottom + 0.01f, a[0].z),
                                    V3(-halfWidth - 0.005f, bottom + 0.035f, a[0].z),
                                    V3(-halfWidth - 0.005f, bottom + 0.035f, b[0].z),
                                    V3(-halfWidth - 0.005f, bottom + 0.01f, b[0].z)
                            ),
                            chrome
                    )
            faces +=
                    Face(
                            listOf(
                                    V3(halfWidth + 0.005f, bottom + 0.01f, a[0].z),
                                    V3(halfWidth + 0.005f, bottom + 0.01f, b[0].z),
                                    V3(halfWidth + 0.005f, bottom + 0.035f, b[0].z),
                                    V3(halfWidth + 0.005f, bottom + 0.035f, a[0].z)
                            ),
                            chrome
                    )
            // A soft reflection on the upper side glass.
            faces +=
                    Face(
                            listOf(
                                    V3(-halfWidth - 0.006f, top - 0.10f, a[0].z + 0.12f),
                                    V3(-halfWidth - 0.006f, top - 0.06f, a[0].z + 0.12f),
                                    V3(-halfWidth - 0.006f, top - 0.06f, b[0].z - 0.18f),
                                    V3(-halfWidth - 0.006f, top - 0.10f, b[0].z - 0.18f)
                            ),
                            glassLight
                    )
            faces +=
                    Face(
                            listOf(
                                    V3(halfWidth + 0.006f, top - 0.10f, a[0].z + 0.12f),
                                    V3(halfWidth + 0.006f, top - 0.10f, b[0].z - 0.18f),
                                    V3(halfWidth + 0.006f, top - 0.06f, b[0].z - 0.18f),
                                    V3(halfWidth + 0.006f, top - 0.06f, a[0].z + 0.12f)
                            ),
                            glassLight
                    )
        }
        if (rearWindow) {
            faces += Face(rings.first(), glass)
        } else {
            faces += Face(rings.first(), roof)
        }
        faces += Face(rings.last().reversed(), glass)
    }

    private fun addWheel(
            faces: MutableList<Face>,
            x: Float,
            y: Float,
            z: Float,
            radius: Float,
            width: Float,
            tire: Int,
            rim: Int
    ) {
        val sides = 12
        val x0 = x - width / 2f
        val x1 = x + width / 2f
        val ring0 = ArrayList<V3>(sides)
        val ring1 = ArrayList<V3>(sides)
        for (index in 0 until sides) {
            val angle = Math.toRadians(index * 360.0 / sides)
            val dy = (cos(angle) * radius).toFloat()
            val dz = (sin(angle) * radius).toFloat()
            ring0.add(V3(x0, y + dy, z + dz))
            ring1.add(V3(x1, y + dy, z + dz))
        }
        for (index in 0 until sides) {
            val next = (index + 1) % sides
            faces += Face(listOf(ring0[index], ring1[index], ring1[next], ring0[next]), tire)
        }
        faces += Face(ring0, tire)
        faces += Face(ring1, tire)

        val outward = if (x < 0f) -0.006f else 0.006f
        val outerX = if (x < 0f) x0 - 0.006f else x1 + 0.006f
        val detailX = outerX + outward
        val rimRing = ArrayList<V3>(sides)
        val hub = ArrayList<V3>(sides)
        for (index in 0 until sides) {
            val angle = Math.toRadians(index * 360.0 / sides)
            rimRing.add(
                    V3(
                            outerX,
                            y + (cos(angle) * radius * 0.62f).toFloat(),
                            z + (sin(angle) * radius * 0.62f).toFloat()
                    )
            )
            hub.add(
                    V3(
                            detailX,
                            y + (cos(angle) * radius * 0.16f).toFloat(),
                            z + (sin(angle) * radius * 0.16f).toFloat()
                    )
            )
        }
        faces += Face(rimRing, tire)
        // Five simple spokes on the outer face.
        for (spoke in 0 until 5) {
            val angle = Math.toRadians(spoke * 72.0 - 90.0)
            val inner = radius * 0.18f
            val outer = radius * 0.58f
            val widthAngle = Math.toRadians(5.0)
            val points =
                    listOf(
                            V3(
                                    detailX,
                                    y + (cos(angle - widthAngle) * inner).toFloat(),
                                    z + (sin(angle - widthAngle) * inner).toFloat()
                            ),
                            V3(
                                    detailX,
                                    y + (cos(angle - widthAngle) * outer).toFloat(),
                                    z + (sin(angle - widthAngle) * outer).toFloat()
                            ),
                            V3(
                                    detailX,
                                    y + (cos(angle + widthAngle) * outer).toFloat(),
                                    z + (sin(angle + widthAngle) * outer).toFloat()
                            ),
                            V3(
                                    detailX,
                                    y + (cos(angle + widthAngle) * inner).toFloat(),
                                    z + (sin(angle + widthAngle) * inner).toFloat()
                            )
                    )
            faces += Face(points, rim)
        }
        faces += Face(hub, rim)
    }

    private fun addBox(
            faces: MutableList<Face>,
            x0: Float,
            x1: Float,
            y0: Float,
            y1: Float,
            z0: Float,
            z1: Float,
            color: Int
    ) {
        val a = V3(x0, y0, z0)
        val b = V3(x1, y0, z0)
        val c = V3(x1, y1, z0)
        val d = V3(x0, y1, z0)
        val e = V3(x0, y0, z1)
        val f = V3(x1, y0, z1)
        val g = V3(x1, y1, z1)
        val h = V3(x0, y1, z1)
        faces += Face(listOf(a, b, c, d), color)
        faces += Face(listOf(e, h, g, f), color)
        faces += Face(listOf(a, e, f, b), color)
        faces += Face(listOf(d, c, g, h), color)
        faces += Face(listOf(a, d, h, e), color)
        faces += Face(listOf(b, f, g, c), color)
    }

    private fun faceNormal(points: List<V3>): V3 {
        if (points.size < 3) return V3(0f, 1f, 0f)
        val a = points[0]
        val b = points[1]
        val c = points[2]
        return normalize(cross(sub(b, a), sub(c, a)))
    }

    private fun applyShade(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun addSpecular(color: Int, amount: Float): Int {
        if (amount <= 0f) return color
        val r = (Color.red(color) + 255 * amount).toInt().coerceIn(0, 255)
        val g = (Color.green(color) + 255 * amount).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) + 255 * amount).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun add(a: V3, b: V3) = V3(a.x + b.x, a.y + b.y, a.z + b.z)

    private fun scale(a: V3, factor: Float) = V3(a.x * factor, a.y * factor, a.z * factor)

    private fun sub(a: V3, b: V3) = V3(a.x - b.x, a.y - b.y, a.z - b.z)

    private fun dot(a: V3, b: V3) = a.x * b.x + a.y * b.y + a.z * b.z

    private fun cross(a: V3, b: V3) =
            V3(
                    a.y * b.z - a.z * b.y,
                    a.z * b.x - a.x * b.z,
                    a.x * b.y - a.y * b.x
            )

    private fun normalize(value: V3): V3 {
        val length = sqrt(value.x * value.x + value.y * value.y + value.z * value.z)
        if (length <= 0.00001f) return V3(0f, 1f, 0f)
        return V3(value.x / length, value.y / length, value.z / length)
    }
}
