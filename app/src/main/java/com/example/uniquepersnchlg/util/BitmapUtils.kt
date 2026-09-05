package com.example.facecollage.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

object BitmapUtils {

    /**
     * Crops [box] out of [src] with a generous margin (default 60% padding each side) so the
     * result is a usable portrait crop rather than a tight, low-res face box. Clamped to bounds.
     */
    fun cropWithMargin(src: Bitmap, box: RectF, marginFraction: Float = 0.6f): Bitmap {
        val marginX = box.width() * marginFraction
        val marginY = box.height() * marginFraction
        val left = max(0f, box.left - marginX)
        val top = max(0f, box.top - marginY * 1.3f) // a bit extra above for hair/forehead
        val right = min(src.width.toFloat(), box.right + marginX)
        val bottom = min(src.height.toFloat(), box.bottom + marginY)
        val w = (right - left).toInt().coerceAtLeast(1)
        val h = (bottom - top).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(src, left.toInt(), top.toInt(), w, h)
    }

    /**
     * Rotates+crops a 112x112 face chip aligned by eye centers, for feeding the embedder.
     * Falls back to a plain square crop if eye landmarks are unavailable.
     */
    fun alignedFaceChip(
        src: Bitmap,
        box: RectF,
        leftEye: Pair<Float, Float>?,
        rightEye: Pair<Float, Float>?,
        outputSize: Int = 112
    ): Bitmap {
        val cropped = cropWithMargin(src, box, marginFraction = 0.25f)
        val rotated = if (leftEye != null && rightEye != null) {
            val dx = rightEye.first - leftEye.first
            val dy = rightEye.second - leftEye.second
            val angleDeg = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
            val m = Matrix().apply { postRotate(-angleDeg, cropped.width / 2f, cropped.height / 2f) }
            Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, m, true)
        } else cropped

        return Bitmap.createScaledBitmap(rotated, outputSize, outputSize, true)
    }

    /**
     * Normalized sharpness score in ~[0,1] from the variance of a fast Laplacian approximation
     * on a downsampled greyscale patch. Cheap enough to run per face per sampled frame.
     */
    fun sharpnessScore(src: Bitmap, box: RectF): Float {
        val patch = cropWithMargin(src, box, marginFraction = 0.05f)
        val small = Bitmap.createScaledBitmap(patch, 48, 48, true)
        val gray = IntArray(48 * 48)
        small.getPixels(gray, 0, 48, 0, 0, 48, 48)
        val lum = DoubleArray(48 * 48)
        for (i in gray.indices) {
            val p = gray[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            lum[i] = 0.299 * r + 0.587 * g + 0.114 * b
        }
        var mean = 0.0
        var count = 0
        val lap = DoubleArray(48 * 48)
        for (y in 1 until 47) {
            for (x in 1 until 47) {
                val idx = y * 48 + x
                val value = 4 * lum[idx] - lum[idx - 1] - lum[idx + 1] - lum[idx - 48] - lum[idx + 48]
                lap[idx] = value
                mean += value
                count++
            }
        }
        mean /= count
        var variance = 0.0
        for (v in lap) variance += (v - mean) * (v - mean)
        variance /= count
        // Empirically, variance above ~500 is "sharp" for this patch size; squash to [0,1].
        return (variance / 500.0).coerceIn(0.0, 1.0).toFloat()
    }

    fun touchesEdge(box: RectF, frameW: Int, frameH: Int, marginPx: Float = 2f): Boolean {
        return box.left <= marginPx || box.top <= marginPx ||
                box.right >= frameW - marginPx || box.bottom >= frameH - marginPx
    }

    fun drawOnto(canvas: Canvas, bmp: Bitmap, dst: RectF) {
        val src = android.graphics.Rect(0, 0, bmp.width, bmp.height)
        canvas.drawBitmap(bmp, src, dst, null)
    }
}
