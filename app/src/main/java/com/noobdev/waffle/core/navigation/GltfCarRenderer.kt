package com.noobdev.waffle.core.navigation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

/**
 * Renders the CC0 Kenney car GLB as a textured software 3D sprite.
 *
 * This is a real 3D model: the GLB's meshes, node transforms, normals, UVs and texture are parsed
 * once, then rasterised with a z-buffer at the requested bearing. The result is cached by
 * model/bearing/size, so the navigation overlay only redraws a bitmap each frame.
 *
 * Asset: Kenney Car Kit (CC0), https://kenney.nl/assets/car-kit.
 */
object GltfCarRenderer {

    private const val TAG = "GltfCarRenderer"
    private const val ASSET_DIR = "cars"

    private data class V(
            val x: Float,
            val y: Float,
            val z: Float,
            val nx: Float,
            val ny: Float,
            val nz: Float,
            val u: Float,
            val v: Float
    )

    private data class Triangle(
            val a: V,
            val b: V,
            val c: V,
            val isWheel: Boolean,
            val textureIndex: Int = 0
    )

    private data class Model(
            val triangles: List<Triangle>,
            val textures: List<Bitmap>,
            val targetY: Float,
            val targetZ: Float,
            val isNarrow: Boolean
    )

    private val modelCache = HashMap<String, Model?>()
    private val bitmapCache =
            object : LruCache<String, Bitmap>(8 * 1024) {
                override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
            }

    private fun fileFor(modelKey: String): String =
            when (modelKey.uppercase()) {
                "SUV" -> "suv-luxury.glb"
                "PICKUP" -> "truck-flat.glb"
                "MOTORCYCLE" -> "motorcycle.glb"
                "BICYCLE" -> "bicycle.glb"
                "WALKER" -> "walker.glb"
                else -> "sedan-sports.glb"
            }

    /**
     * Draws the model at the requested screen position. Returns false when the asset cannot be
     * parsed, so the caller can fall back to the procedural car.
     */
    fun draw(
            context: Context,
            canvas: Canvas,
            centerX: Float,
            centerY: Float,
            sizePx: Float,
            bearingDegrees: Float,
            modelKey: String,
            lights: VehicleLights = VehicleLights(),
            topDown: Boolean = false
    ): Boolean {
        val bitmap =
                renderBitmap(context, modelKey, sizePx, bearingDegrees, lights, topDown)
                        ?: return false
        val rect =
                RectF(
                        centerX - sizePx / 2f,
                        centerY - sizePx / 2f,
                        centerX + sizePx / 2f,
                        centerY + sizePx / 2f
                )
        canvas.drawBitmap(bitmap, null, rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return true
    }

    fun renderBitmap(
            context: Context,
            modelKey: String,
            sizePx: Float,
            bearingDegrees: Float,
            lights: VehicleLights = VehicleLights(),
            topDown: Boolean = false
    ): Bitmap? {
        val model = modelFor(context, modelKey) ?: return null
        val roundedBearing = (bearingDegrees / 3f).roundToInt() * 3f
        val key =
                "${modelKey.uppercase()}|$roundedBearing|${sizePx.roundToInt()}|$topDown|" +
                        "${lights.headlights}|${lights.braking}|${lights.turnSignal}|${lights.blinkOn}"
        bitmapCache.get(key)?.let { return it }
        val meshBitmap = render(model, sizePx, roundedBearing, topDown) ?: return null
        val out = Bitmap.createBitmap(meshBitmap.width, meshBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val cx = out.width / 2f
        val cy = out.height / 2f
        val spriteScale = out.width.toFloat()
        drawShadow(canvas, cx, cy, spriteScale, roundedBearing, model.isNarrow)
        drawHeadlightBeams(canvas, cx, cy, spriteScale, roundedBearing, modelKey, lights)
        canvas.drawBitmap(meshBitmap, 0f, 0f, null)
        drawVehicleLights(
                canvas,
                cx,
                cy,
                spriteScale,
                roundedBearing,
                modelKey,
                lights
        )
        bitmapCache.put(key, out)
        return out
    }

    private fun drawHeadlightBeams(
            canvas: Canvas,
            centerX: Float,
            centerY: Float,
            sizePx: Float,
            bearingDegrees: Float,
            modelKey: String,
            lights: VehicleLights
    ) {
        if (!lights.headlights ||
                        modelKey.equals("BICYCLE", ignoreCase = true) ||
                        modelKey.equals("WALKER", ignoreCase = true)) {
            return
        }
        val motorcycle = modelKey.equals("MOTORCYCLE", ignoreCase = true)
        canvas.save()
        canvas.rotate(bearingDegrees, centerX, centerY)
        fun beam(dx: Float, dy: Float, rx: Float, ry: Float) {
            val x = centerX + dx * sizePx
            val y = centerY + dy * sizePx
            val r = max(rx, ry) * sizePx
            val paint =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader =
                                android.graphics.RadialGradient(
                                        x,
                                        y,
                                        r,
                                        intArrayOf(
                                                Color.argb(145, 255, 248, 210),
                                                Color.argb(65, 255, 244, 195),
                                                Color.TRANSPARENT
                                        ),
                                        floatArrayOf(0f, 0.55f, 1f),
                                        android.graphics.Shader.TileMode.CLAMP
                                )
                    }
            canvas.save()
            canvas.translate(x, y)
            canvas.scale(rx / max(rx, ry), ry / max(rx, ry))
            canvas.drawCircle(0f, 0f, r, paint)
            canvas.restore()
        }
        if (motorcycle) {
            beam(0f, -0.32f, 0.08f, 0.14f)
        } else {
            for (side in listOf(-1f, 1f)) {
                beam(side * 0.12f, -0.31f, 0.075f, 0.13f)
            }
        }
        canvas.restore()
    }

    private fun drawVehicleLights(
            canvas: Canvas,
            centerX: Float,
            centerY: Float,
            sizePx: Float,
            bearingDegrees: Float,
            modelKey: String,
            lights: VehicleLights
    ) {
        if (modelKey.equals("BICYCLE", ignoreCase = true) ||
                        modelKey.equals("WALKER", ignoreCase = true)) {
            return
        }
        val motorcycle = modelKey.equals("MOTORCYCLE", ignoreCase = true)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        fun bar(dx: Float, dy: Float, halfWidth: Float, halfHeight: Float, color: Int, radius: Float) {
            fill.color = color
            val x = centerX + dx * sizePx
            val y = centerY + dy * sizePx
            val r = radius * sizePx
            canvas.drawRoundRect(
                    x - halfWidth * sizePx,
                    y - halfHeight * sizePx,
                    x + halfWidth * sizePx,
                    y + halfHeight * sizePx,
                    r,
                    r,
                    fill
            )
        }

        fun glow(dx: Float, dy: Float, radius: Float, color: Int) {
            val x = centerX + dx * sizePx
            val y = centerY + dy * sizePx
            val paint =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader =
                                android.graphics.RadialGradient(
                                        x,
                                        y,
                                        radius * sizePx,
                                        intArrayOf(color, Color.TRANSPARENT),
                                        floatArrayOf(0f, 1f),
                                        android.graphics.Shader.TileMode.CLAMP
                                )
                    }
            canvas.drawCircle(x, y, radius * sizePx, paint)
        }

        canvas.save()
        canvas.rotate(bearingDegrees, centerX, centerY)

        if (motorcycle) {
            if (lights.braking) {
                glow(0f, 0.16f, 0.14f, Color.argb(120, 255, 25, 20))
            }
            bar(
                    0f,
                    0.16f,
                    0.045f,
                    0.022f,
                    if (lights.braking) Color.rgb(255, 55, 45) else Color.argb(195, 230, 28, 28),
                    0.012f
            )
            glow(
                    0f,
                    0.16f,
                    if (lights.braking) 0.11f else 0.045f,
                    if (lights.braking) Color.argb(210, 255, 30, 25) else Color.argb(90, 255, 30, 25)
            )
        } else {
            if (lights.braking) {
                bar(0f, 0.05f, 0.075f, 0.014f, Color.rgb(255, 45, 35), 0.008f)
                glow(0f, 0.05f, 0.085f, Color.argb(150, 255, 25, 20))
            }
            for (side in listOf(-1f, 1f)) {
                val lampX = side * 0.135f
                val lampY = 0.195f
                if (lights.braking) {
                    glow(lampX, lampY, 0.11f, Color.argb(115, 255, 25, 20))
                }
                bar(
                        lampX,
                        lampY,
                        0.052f,
                        0.022f,
                        if (lights.braking) Color.rgb(255, 45, 35) else Color.argb(195, 230, 28, 28),
                        0.012f
                )
                glow(
                        lampX,
                        lampY,
                        if (lights.braking) 0.095f else 0.04f,
                        if (lights.braking) Color.argb(210, 255, 30, 25) else Color.argb(85, 255, 30, 25)
                )
            }
        }

        if (lights.turnSignal != TurnSignal.NONE) {
            val side = if (lights.turnSignal == TurnSignal.LEFT) -1f else 1f
            val amber =
                    if (lights.blinkOn) Color.rgb(255, 175, 35)
                    else Color.argb(120, 255, 150, 20)
            if (motorcycle) {
                bar(side * 0.11f, -0.14f, 0.032f, 0.024f, amber, 0.012f)
                bar(side * 0.09f, 0.15f, 0.032f, 0.024f, amber, 0.012f)
                if (lights.blinkOn) {
                    glow(side * 0.11f, -0.14f, 0.065f, Color.argb(160, 255, 175, 35))
                    glow(side * 0.09f, 0.15f, 0.065f, Color.argb(160, 255, 175, 35))
                }
            } else {
                bar(side * 0.135f, -0.22f, 0.038f, 0.024f, amber, 0.012f)
                bar(side * 0.175f, 0.195f, 0.034f, 0.022f, amber, 0.012f)
                if (lights.blinkOn) {
                    glow(side * 0.135f, -0.22f, 0.065f, Color.argb(160, 255, 175, 35))
                    glow(side * 0.175f, 0.195f, 0.065f, Color.argb(160, 255, 175, 35))
                }
            }
        }

        canvas.restore()
    }

    private fun drawShadow(
            canvas: Canvas,
            centerX: Float,
            centerY: Float,
            sizePx: Float,
            bearingDegrees: Float,
            isNarrow: Boolean
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val halfW = sizePx * (if (isNarrow) 0.12f else 0.21f)
        val halfH = sizePx * 0.26f
        val yOff = sizePx * 0.02f
        canvas.save()
        canvas.rotate(bearingDegrees, centerX, centerY)
        for (layer in 0..5) {
            val exp = sizePx * (0.024f * layer)
            paint.color = Color.argb(20 - layer * 3, 0, 0, 0)
            canvas.drawOval(
                    centerX - halfW - exp,
                    centerY - halfH + yOff - exp,
                    centerX + halfW + exp,
                    centerY + halfH + yOff + exp,
                    paint
            )
        }
        paint.color = Color.argb(55, 0, 0, 0)
        canvas.drawOval(
                centerX - halfW * 0.82f,
                centerY - halfH * 0.82f + yOff,
                centerX + halfW * 0.82f,
                centerY + halfH * 0.82f + yOff,
                paint
        )
        canvas.restore()
    }

    private fun modelFor(context: Context, modelKey: String): Model? {
        val key = modelKey.uppercase()
        synchronized(modelCache) {
            if (modelCache.containsKey(key)) return modelCache[key]
        }
        val model =
                runCatching { loadModel(context.applicationContext, key) }
                        .onFailure { android.util.Log.e(TAG, "Could not load $key", it) }
                        .getOrNull()
        synchronized(modelCache) { modelCache[key] = model }
        return model
    }

    private fun loadTextures(
            context: Context,
            json: JSONObject,
            bufferViews: JSONArray,
            bin: ByteArray
    ): List<Bitmap?> {
        val images = json.optJSONArray("images") ?: return emptyList()
        val out = ArrayList<Bitmap?>(images.length())
        for (index in 0 until images.length()) {
            val image = images.optJSONObject(index)
            if (image == null) {
                out.add(null)
                continue
            }
            val bitmap =
                    runCatching {
                                if (image.has("bufferView")) {
                                    val view =
                                            bufferViews.getJSONObject(
                                                    image.getInt("bufferView")
                                            )
                                    val offset = view.optInt("byteOffset", 0)
                                    val length = view.getInt("byteLength")
                                    if (offset < 0 || length <= 0 || offset + length > bin.size)
                                            null
                                    else BitmapFactory.decodeByteArray(bin, offset, length)
                                } else {
                                    val uri = image.optString("uri").trim()
                                    if (uri.isEmpty()) null
                                    else {
                                        val name = uri.substringAfterLast('/')
                                        context.assets.open("$ASSET_DIR/$name").use {
                                            BitmapFactory.decodeStream(it)
                                        }
                                    }
                                }
                            }
                            .getOrNull()
            out.add(bitmap?.let { downscaleTexture(it) })
        }
        return out
    }

    private fun downscaleTexture(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= 1024) return bitmap
        val scale = 512f / longest
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun loadModel(context: Context, modelKey: String): Model? {
        val bytes = context.assets.open("$ASSET_DIR/${fileFor(modelKey)}").readBytes()
        if (bytes.size < 20) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.getInt(0) != 0x46546C67) return null
        val jsonLength = buffer.getInt(12)
        val jsonBytes = ByteArray(jsonLength)
        buffer.position(20)
        buffer.get(jsonBytes)
        val json = JSONObject(String(jsonBytes, Charsets.UTF_8))
        buffer.position(20 + jsonLength)
        val binLength = if (buffer.remaining() >= 8) buffer.getInt() else 0
        val binType = if (buffer.remaining() >= 4) buffer.getInt() else 0
        if (binLength <= 0 || binType != 0x004E4942) return null
        val bin = ByteArray(binLength)
        buffer.get(bin)

        val bufferViews = json.getJSONArray("bufferViews")
        val imageBitmaps = loadTextures(context, json, bufferViews, bin)
        val textures = imageBitmaps.filterNotNull()
        if (textures.isEmpty()) return null
        val imageToTexture = HashMap<Int, Int>()
        var nextTexture = 0
        for (index in imageBitmaps.indices) {
            if (imageBitmaps[index] != null) imageToTexture[index] = nextTexture++
        }
        val accessors = json.getJSONArray("accessors")
        val meshes = json.getJSONArray("meshes")
        val nodes = json.getJSONArray("nodes")
        val materials = json.optJSONArray("materials")
        val textureDefs = json.optJSONArray("textures")
        val binBuffer = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN)
        val triangles = ArrayList<Triangle>(4096)

        fun textureIndexForMaterial(materialIndex: Int): Int {
            if (materials == null || textureDefs == null || materialIndex < 0) return 0
            val material = materials.optJSONObject(materialIndex) ?: return 0
            val pbr = material.optJSONObject("pbrMetallicRoughness") ?: return 0
            val baseColor = pbr.optJSONObject("baseColorTexture") ?: return 0
            val texture = textureDefs.optJSONObject(baseColor.optInt("index", -1)) ?: return 0
            val source = texture.optInt("source", -1)
            return imageToTexture[source] ?: 0
        }

        fun readFloats(accessorIndex: Int): FloatArray {
            val accessor = accessors.getJSONObject(accessorIndex)
            val view = bufferViews.getJSONObject(accessor.getInt("bufferView"))
            val count = accessor.getInt("count")
            val components =
                    when (accessor.getString("type")) {
                        "SCALAR" -> 1
                        "VEC2" -> 2
                        "VEC3" -> 3
                        "VEC4" -> 4
                        else -> 1
                    }
            val stride = view.optInt("byteStride", components * 4)
            val start =
                    view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
            val out = FloatArray(count * components)
            for (index in 0 until count) {
                val base = start + index * stride
                for (component in 0 until components) {
                    out[index * components + component] =
                            binBuffer.getFloat(base + component * 4)
                }
            }
            return out
        }

        fun readIndices(accessorIndex: Int): IntArray {
            val accessor = accessors.getJSONObject(accessorIndex)
            val view = bufferViews.getJSONObject(accessor.getInt("bufferView"))
            val count = accessor.getInt("count")
            val componentType = accessor.getInt("componentType")
            val componentSize =
                    when (componentType) {
                        5121 -> 1
                        5123 -> 2
                        5125 -> 4
                        else -> 4
                    }
            val stride = view.optInt("byteStride", componentSize)
            val start =
                    view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
            val out = IntArray(count)
            for (index in 0 until count) {
                val base = start + index * stride
                out[index] =
                        when (componentType) {
                            5121 -> binBuffer.get(base).toInt() and 0xFF
                            5123 -> binBuffer.getShort(base).toInt() and 0xFFFF
                            else -> binBuffer.getInt(base)
                        }
            }
            return out
        }

        data class Transform(
                val m0: Float,
                val m1: Float,
                val m2: Float,
                val m4: Float,
                val m5: Float,
                val m6: Float,
                val m8: Float,
                val m9: Float,
                val m10: Float,
                val tx: Float,
                val ty: Float,
                val tz: Float
        )

        fun nodeTransform(node: JSONObject): Transform {
            if (node.has("matrix")) {
                val m = node.getJSONArray("matrix")
                val values = FloatArray(16) { m.getDouble(it).toFloat() }
                return Transform(
                        values[0], values[1], values[2],
                        values[4], values[5], values[6],
                        values[8], values[9], values[10],
                        values[12], values[13], values[14]
                )
            }
            val translation =
                    node.optJSONArray("translation")?.let {
                        Triple(it.getDouble(0).toFloat(), it.getDouble(1).toFloat(), it.getDouble(2).toFloat())
                    } ?: Triple(0f, 0f, 0f)
            val scale =
                    node.optJSONArray("scale")?.let {
                        Triple(it.getDouble(0).toFloat(), it.getDouble(1).toFloat(), it.getDouble(2).toFloat())
                    } ?: Triple(1f, 1f, 1f)
            val rotation =
                    node.optJSONArray("rotation")?.let {
                        floatArrayOf(
                                it.getDouble(0).toFloat(),
                                it.getDouble(1).toFloat(),
                                it.getDouble(2).toFloat(),
                                it.getDouble(3).toFloat()
                        )
                    } ?: floatArrayOf(0f, 0f, 0f, 1f)
            // Quaternion to 3x3, then scale.
            val x = rotation[0]
            val y = rotation[1]
            val z = rotation[2]
            val w = rotation[3]
            val xx = x * x
            val yy = y * y
            val zz = z * z
            val xy = x * y
            val xz = x * z
            val yz = y * z
            val wx = w * x
            val wy = w * y
            val wz = w * z
            return Transform(
                    (1 - 2 * (yy + zz)) * scale.first,
                    (2 * (xy + wz)) * scale.first,
                    (2 * (xz - wy)) * scale.first,
                    (2 * (xy - wz)) * scale.second,
                    (1 - 2 * (xx + zz)) * scale.second,
                    (2 * (yz + wx)) * scale.second,
                    (2 * (xz + wy)) * scale.third,
                    (2 * (yz - wx)) * scale.third,
                    (1 - 2 * (xx + yy)) * scale.third,
                    translation.first,
                    translation.second,
                    translation.third
            )
        }

        fun transformPoint(t: Transform, x: Float, y: Float, z: Float): FloatArray =
                floatArrayOf(
                        t.m0 * x + t.m4 * y + t.m8 * z + t.tx,
                        t.m1 * x + t.m5 * y + t.m9 * z + t.ty,
                        t.m2 * x + t.m6 * y + t.m10 * z + t.tz
                )

        fun transformNormal(t: Transform, x: Float, y: Float, z: Float): FloatArray {
            val nx = t.m0 * x + t.m4 * y + t.m8 * z
            val ny = t.m1 * x + t.m5 * y + t.m9 * z
            val nz = t.m2 * x + t.m6 * y + t.m10 * z
            val length = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
            return floatArrayOf(nx / length, ny / length, nz / length)
        }

        for (nodeIndex in 0 until nodes.length()) {
            val node = nodes.getJSONObject(nodeIndex)
            if (!node.has("mesh")) continue
            val transform = nodeTransform(node)
            val mesh = meshes.getJSONObject(node.getInt("mesh"))
            val isWheel = mesh.optString("name").startsWith("wheel")
            val primitives = mesh.getJSONArray("primitives")
            for (primitiveIndex in 0 until primitives.length()) {
                val primitive = primitives.getJSONObject(primitiveIndex)
                val attributes = primitive.getJSONObject("attributes")
                val positions = readFloats(attributes.getInt("POSITION"))
                val normals =
                        if (attributes.has("NORMAL")) readFloats(attributes.getInt("NORMAL"))
                        else FloatArray(positions.size)
                val uvs =
                        if (attributes.has("TEXCOORD_0")) readFloats(attributes.getInt("TEXCOORD_0"))
                        else FloatArray(positions.size / 3 * 2)
                val indices =
                        if (primitive.has("indices")) readIndices(primitive.getInt("indices"))
                        else IntArray(positions.size / 3) { it }
                val textureIndex = textureIndexForMaterial(primitive.optInt("material", -1))
                val vertexCount = positions.size / 3
                val hasNormals = attributes.has("NORMAL")
                fun withFaceNormal(triangle: Triangle): Triangle {
                    if (hasNormals) return triangle
                    val ab = V3(triangle.b.x - triangle.a.x, triangle.b.y - triangle.a.y, triangle.b.z - triangle.a.z)
                    val ac = V3(triangle.c.x - triangle.a.x, triangle.c.y - triangle.a.y, triangle.c.z - triangle.a.z)
                    val normal = normalize(cross(ab, ac))
                    return Triangle(
                            triangle.a.copy(nx = normal.x, ny = normal.y, nz = normal.z),
                            triangle.b.copy(nx = normal.x, ny = normal.y, nz = normal.z),
                            triangle.c.copy(nx = normal.x, ny = normal.y, nz = normal.z),
                            triangle.isWheel,
                            triangle.textureIndex
                    )
                }
                fun vertex(index: Int): V {
                    val p = transformPoint(transform, positions[index * 3], positions[index * 3 + 1], positions[index * 3 + 2])
                    val n = transformNormal(transform, normals[index * 3], normals[index * 3 + 1], normals[index * 3 + 2])
                    return V(
                            p[0], p[1], p[2],
                            n[0], n[1], n[2],
                            if (uvs.size >= (index * 2 + 2)) uvs[index * 2] else 0f,
                            if (uvs.size >= (index * 2 + 2)) uvs[index * 2 + 1] else 0f
                    )
                }
                if (indices.isEmpty()) {
                    for (index in 0 until vertexCount step 3) {
                        triangles.add(
                                withFaceNormal(
                                        Triangle(
                                                vertex(index),
                                                vertex(index + 1),
                                                vertex(index + 2),
                                                isWheel,
                                                textureIndex
                                        )
                                )
                        )
                    }
                } else {
                    for (index in 0 until indices.size step 3) {
                        if (index + 2 >= indices.size) break
                        triangles.add(
                                withFaceNormal(
                                        Triangle(
                                                vertex(indices[index]),
                                                vertex(indices[index + 1]),
                                                vertex(indices[index + 2]),
                                                isWheel,
                                                textureIndex
                                        )
                                )
                        )
                    }
                }
            }
        }
        if (triangles.isEmpty()) return null

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE
        for (triangle in triangles) {
            for (point in listOf(triangle.a, triangle.b, triangle.c)) {
                minX = min(minX, point.x)
                maxX = max(maxX, point.x)
                minY = min(minY, point.y)
                maxY = max(maxY, point.y)
                minZ = min(minZ, point.z)
                maxZ = max(maxZ, point.z)
            }
        }
        val isBicycle = modelKey == "BICYCLE"
        val isWalker = modelKey == "WALKER"
        val isMotorcycle = modelKey == "MOTORCYCLE"
        val targetLength =
                when {
                    isWalker -> 2.05f
                    isBicycle || isMotorcycle -> 2.45f
                    else -> 2.55f
                }
        val sourceLength =
                if (isWalker) (maxY - minY).coerceAtLeast(0.001f)
                else (maxZ - minZ).coerceAtLeast(0.001f)
        val factor = targetLength / sourceLength
        val centerX = (minX + maxX) / 2f
        val centerZ = (minZ + maxZ) / 2f
        fun scaled(point: V): V =
                point.copy(
                        x = (point.x - centerX) * factor,
                        y = (point.y - minY) * factor,
                        z = (point.z - centerZ) * factor
                )
        val scaledTriangles =
                triangles.map { triangle ->
                    Triangle(
                            scaled(triangle.a),
                            scaled(triangle.b),
                            scaled(triangle.c),
                            triangle.isWheel,
                            triangle.textureIndex
                    )
                }

        return Model(
                triangles = scaledTriangles,
                textures = textures,
                targetY =
                        when {
                            isWalker -> 0.78f
                            isBicycle || isMotorcycle -> 0.56f
                            else -> 0.40f
                        },
                targetZ =
                        when {
                            isWalker -> -0.05f
                            isBicycle || isMotorcycle -> -0.14f
                            else -> -0.22f
                        },
                isNarrow = isBicycle || isMotorcycle || isWalker
        )
    }

    private fun render(
            model: Model,
            sizePx: Float,
            bearingDegrees: Float,
            topDown: Boolean = false
    ): Bitmap? {
        val outSize = (sizePx * 1.5f).roundToInt().coerceIn(128, 320)
        val ssaa = 2
        val width = outSize * ssaa
        val height = outSize * ssaa
        val pixels = IntArray(width * height)
        val depth = FloatArray(width * height) { Float.MAX_VALUE }
        val yaw = Math.toRadians(bearingDegrees.toDouble())
        val cosYaw = cos(yaw).toFloat()
        val sinYaw = sin(yaw).toFloat()
        val eye = if (topDown) V3(0f, 5.65f, -3.55f) else V3(0f, 4.5f, -5.0f)
        val target = V3(0f, model.targetY, model.targetZ)
        val forward = normalize(sub(target, eye))
        val right = normalize(cross(V3(0f, 1f, 0f), forward))
        val up = cross(forward, right)
        val focal = (1.0 / kotlin.math.tan(Math.toRadians(30.0) / 2.0)).toFloat()
        val scale = width * 0.50f
        val light = normalize(V3(-0.38f, 0.84f, -0.38f))
        val halfVec = normalize(V3(-0.22f, 0.78f, -0.58f))
        data class TexturePixels(val width: Int, val height: Int, val pixels: IntArray)
        val texturePixels =
                model.textures.map { texture ->
                    val texPixels = IntArray(texture.width * texture.height)
                    texture.getPixels(
                            texPixels,
                            0,
                            texture.width,
                            0,
                            0,
                            texture.width,
                            texture.height
                    )
                    TexturePixels(texture.width, texture.height, texPixels)
                }

        data class P(val x: Float, val y: Float, val z: Float, val nx: Float, val ny: Float, val nz: Float)

        fun project(v: V): P? {
            val rx = v.x * cosYaw + v.z * sinYaw
            val rz = -v.x * sinYaw + v.z * cosYaw
            val rnx = v.nx * cosYaw + v.nz * sinYaw
            val rnz = -v.nx * sinYaw + v.nz * cosYaw
            val relative = sub(V3(rx, v.y, rz), eye)
            val d = dot(relative, forward)
            if (d <= 0.05f) return null
            val sx = width / 2f + (dot(relative, right) * focal / d) * scale
            val sy = height / 2f - (dot(relative, up) * focal / d) * scale
            return P(sx, sy, d, rnx, v.ny, rnz)
        }

        for (triangle in model.triangles) {
            val a = project(triangle.a) ?: continue
            val b = project(triangle.b) ?: continue
            val c = project(triangle.c) ?: continue
            val minX = max(0, floor(min(a.x, min(b.x, c.x))).toInt())
            val maxX = min(width - 1, floor(max(a.x, max(b.x, c.x))).toInt())
            val minY = max(0, floor(min(a.y, min(b.y, c.y))).toInt())
            val maxY = min(height - 1, floor(max(a.y, max(b.y, c.y))).toInt())
            if (minX > maxX || minY > maxY) continue
            val denominator = (b.y - c.y) * (a.x - c.x) + (c.x - b.x) * (a.y - c.y)
            if (abs(denominator) < 0.00001f) continue
            val texture =
                    texturePixels.getOrElse(triangle.textureIndex) {
                        texturePixels.first()
                    }
            for (py in minY..maxY) {
                for (px in minX..maxX) {
                    val w0 = ((b.y - c.y) * (px - c.x) + (c.x - b.x) * (py - c.y)) / denominator
                    val w1 = ((c.y - a.y) * (px - c.x) + (a.x - c.x) * (py - c.y)) / denominator
                    val w2 = 1f - w0 - w1
                    if (w0 < -0.001f || w1 < -0.001f || w2 < -0.001f) continue
                    val z = w0 * a.z + w1 * b.z + w2 * c.z
                    val index = py * width + px
                    if (z >= depth[index]) continue
                    val u = w0 * triangle.a.u + w1 * triangle.b.u + w2 * triangle.c.u
                    val v = w0 * triangle.a.v + w1 * triangle.b.v + w2 * triangle.c.v
                    val tx =
                            ((u - floor(u)) * (texture.width - 1))
                                    .toInt()
                                    .coerceIn(0, texture.width - 1)
                    val ty =
                            ((v - floor(v)) * (texture.height - 1))
                                    .toInt()
                                    .coerceIn(0, texture.height - 1)
                    val finalTex = texture.pixels[ty * texture.width + tx]
                    if (Color.alpha(finalTex) < 8) continue
                    val nx = w0 * a.nx + w1 * b.nx + w2 * c.nx
                    val ny = w0 * a.ny + w1 * b.ny + w2 * c.ny
                    val nz = w0 * a.nz + w1 * b.nz + w2 * c.nz
                    val normal = normalize(V3(nx, ny, nz))
                    val ndotl = max(0f, dot(normal, light))
                    val shade = (0.54f + 0.16f * normal.y) + 0.36f * ndotl
                    val r = (Color.red(finalTex) * shade).toInt().coerceIn(0, 255)
                    val g = (Color.green(finalTex) * shade).toInt().coerceIn(0, 255)
                    val b = (Color.blue(finalTex) * shade).toInt().coerceIn(0, 255)
                    val specDot = max(0f, dot(normal, halfVec))
                    val s2 = specDot * specDot
                    val s4 = s2 * s2
                    val spec = s4 * s4 * s4 * s2 * 0.16f
                    pixels[index] =
                            Color.argb(
                                    Color.alpha(finalTex),
                                    (r + 255 * spec).toInt().coerceIn(0, 255),
                                    (g + 255 * spec).toInt().coerceIn(0, 255),
                                    (b + 255 * spec).toInt().coerceIn(0, 255)
                            )
                    depth[index] = z
                }
            }
        }

        val outPixels = IntArray(outSize * outSize)
        for (oy in 0 until outSize) {
            val y0 = oy * 2 * width
            val y1 = (oy * 2 + 1) * width
            val outRow = oy * outSize
            for (ox in 0 until outSize) {
                val x0 = ox * 2
                val p00 = pixels[y0 + x0]
                val p01 = pixels[y0 + x0 + 1]
                val p10 = pixels[y1 + x0]
                val p11 = pixels[y1 + x0 + 1]
                val a00 = Color.alpha(p00)
                val a01 = Color.alpha(p01)
                val a10 = Color.alpha(p10)
                val a11 = Color.alpha(p11)
                val aSum = a00 + a01 + a10 + a11
                if (aSum == 0) continue
                val rAvg = (Color.red(p00) * a00 + Color.red(p01) * a01 + Color.red(p10) * a10 + Color.red(p11) * a11) / aSum
                val gAvg = (Color.green(p00) * a00 + Color.green(p01) * a01 + Color.green(p10) * a10 + Color.green(p11) * a11) / aSum
                val bAvg = (Color.blue(p00) * a00 + Color.blue(p01) * a01 + Color.blue(p10) * a10 + Color.blue(p11) * a11) / aSum
                outPixels[outRow + ox] = Color.argb(aSum shr 2, rAvg, gAvg, bAvg)
            }
        }

        val bitmap = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(outPixels, 0, outSize, 0, 0, outSize, outSize)
        return bitmap
    }

    private data class V3(val x: Float, val y: Float, val z: Float)

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
