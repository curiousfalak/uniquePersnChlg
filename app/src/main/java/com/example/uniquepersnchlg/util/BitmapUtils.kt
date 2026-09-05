package com.example.uniquepersnchlg.util

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
     *
     * @param neighborDistancePx if another face was detected in the same frame, its distance
     *   (box-center to box-center) in pixels. When provided, the horizontal margin is clamped
     *   so the crop can never reach past roughly the midpoint to that neighbor - otherwise a
     *   generous crop around one person's face bleeds into an adjacent person's face when two
     *   people share the frame, producing a tile that looks like two faces spliced together.
     */
    fun cropWithMargin(
        src: Bitmap,
        box: RectF,
        marginFraction: Float = 0.6f,
        neighborDistancePx: Float? = null
    ): Bitmap {
        var marginX = box.width() * marginFraction
        val marginY = box.height() * marginFraction
        if (neighborDistancePx != null) {
            // Leave a small buffer short of the true midpoint so we don't clip right up to
            // the neighbor's face edge either. IMPORTANT: no minimum floor here - an earlier
            // version floored this at 0.15x box width "just in case", but that floor ignored
            // how close the neighbor actually was. For any neighbor closer than ~1.3x the box
            // width (a completely normal distance for two people framed together, e.g. an
            // interview two-shot), that floor mathematically guaranteed the crop would cross
            // the midpoint and bleed into the neighbor's face - exactly the bug it was meant to
            // prevent. A tight-but-neighbor-safe crop is strictly better than a slightly more
            // generous crop that includes a second person's face.
            val safeHalfDistance = (neighborDistancePx / 2f) * 0.8f
            marginX = min(marginX, max(0f, safeHalfDistance - box.width() / 2f))
        }
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
