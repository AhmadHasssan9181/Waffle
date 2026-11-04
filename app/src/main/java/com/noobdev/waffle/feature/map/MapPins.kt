package com.noobdev.waffle.feature.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/** Small teardrop pins for the destination and the long-press dropped pin. */
internal object MapPins {
    private val cache = HashMap<Int, Bitmap>()

    @Synchronized
    fun bitmap(color: Int): Bitmap = cache.getOrPut(color) { create(color) }

    private fun create(color: Int): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val cx = size / 2f
        val cy = size * 0.34f
        val radius = size * 0.27f

        paint.color = Color.argb(55, 0, 0, 0)
        canvas.drawCircle(cx, cy + size * 0.035f, radius * 1.08f, paint)

        paint.color = color
        canvas.drawCircle(cx, cy, radius, paint)
        val tip =
                Path().apply {
                    moveTo(cx - radius * 0.78f, cy + radius * 0.58f)
                    lineTo(cx, size * 0.95f)
                    lineTo(cx + radius * 0.78f, cy + radius * 0.58f)
                    close()
                }
        canvas.drawPath(tip, paint)

        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, radius * 0.36f, paint)
        return bitmap
    }
}
