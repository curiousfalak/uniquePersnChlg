package com.example.facecollage.pipeline

import android.graphics.*
import com.example.facecollage.util.BitmapUtils
import com.example.uniquepersnchlg.data.model.Identity

import kotlin.math.ceil
import kotlin.math.sqrt


class CollageComposer {

    companion object {
        const val CANVAS_W = 1080
        const val CANVAS_H = 1920
        private const val PADDING = 24f
        private const val CORNER_RADIUS = 28f
    }

    fun compose(identities: List<Identity>, videoLabel: String): Bitmap {
        val bmp = Bitmap.createBitmap(CANVAS_W, CANVAS_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Background: soft vertical gradient.
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, CANVAS_H.toFloat(),
                intArrayOf(Color.parseColor("#2b1055"), Color.parseColor("#7597de")),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CANVAS_W.toFloat(), CANVAS_H.toFloat(), bgPaint)

        // Header
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0E0E0")
            textSize = 32f
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("FaceCollage", PADDING, 90f, titlePaint)
        canvas.drawText(
            "$videoLabel  \u2022  ${identities.size} ${if (identities.size == 1) "person" else "people"}",
            PADDING, 134f, subtitlePaint
        )

        val gridTop = 170f
        val gridBottom = CANVAS_H - 60f
        val gridArea = RectF(PADDING, gridTop, CANVAS_W - PADDING, gridBottom)

        drawGrid(canvas, gridArea, identities)

        return bmp
    }

    private fun drawGrid(canvas: Canvas, area: RectF, identities: List<Identity>) {
        val n = identities.size
        if (n == 0) return

        val cols = ceil(sqrt(n.toDouble())).toInt().coerceAtLeast(1)
        val rows = ceil(n / cols.toDouble()).toInt().coerceAtLeast(1)

        val cellW = (area.width() - (cols - 1) * PADDING) / cols
        val cellH = (area.height() - (rows - 1) * PADDING) / rows

        identities.forEachIndexed { index, identity ->
            val col = index % cols
            val row = index / cols
            val left = area.left + col * (cellW + PADDING)
            val top = area.top + row * (cellH + PADDING)
            val cellRect = RectF(left, top, left + cellW, top + cellH)
            drawTile(canvas, cellRect, identity, index + 1)
        }
    }

    private fun drawTile(canvas: Canvas, rect: RectF, identity: Identity, personNumber: Int) {
        val shot = identity.representativeSample()
        val portrait = BitmapUtils.cropWithMargin(shot.frameBitmap, shot.boundingBox, marginFraction = 0.9f)

        // Clip to rounded rect
        canvas.save()
        val path = Path().apply { addRoundRect(rect, CORNER_RADIUS, CORNER_RADIUS, Path.Direction.CW) }

        // Soft drop shadow behind the tile
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 0, 0, 0)
            maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(path, shadowPaint)

        canvas.clipPath(path)

        // Cover-fit the portrait into the tile (center-crop) so faces aren't squashed.
        val src = fitCoverSrcRect(portrait.width, portrait.height, rect.width(), rect.height())
        canvas.drawBitmap(portrait, src, rect, null)

        // Bottom gradient + caption
        val gradPaint = Paint().apply {
            shader = LinearGradient(
                0f, rect.bottom - rect.height() * 0.35f, 0f, rect.bottom,
                intArrayOf(Color.TRANSPARENT, Color.argb(190, 0, 0, 0)),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(rect.left, rect.bottom - rect.height() * 0.35f, rect.right, rect.bottom, gradPaint)

        canvas.restore()

        // Thin white border for a "polaroid card" feel
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.argb(160, 255, 255, 255)
        }
        canvas.drawPath(path, borderPaint)

        // Caption text
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (rect.height() * 0.075f).coerceIn(24f, 44f)
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F0F0F0")
            textSize = (rect.height() * 0.055f).coerceIn(20f, 34f)
        }
        canvas.drawText("Person $personNumber", rect.left + 20f, rect.bottom - 46f, labelPaint)
        val times = if (identity.appearanceCount == 1) "1 appearance" else "${identity.appearanceCount} appearances"
        canvas.drawText(times, rect.left + 20f, rect.bottom - 16f, countPaint)
    }

    /** Returns the source rect to sample so [srcW]x[srcH] cover-fits into [dstW]x[dstH]. */
    private fun fitCoverSrcRect(srcW: Int, srcH: Int, dstW: Float, dstH: Float): Rect {
        val srcAspect = srcW.toFloat() / srcH
        val dstAspect = dstW / dstH
        return if (srcAspect > dstAspect) {
            // source is wider than target -> crop left/right
            val cropW = (srcH * dstAspect).toInt()
            val x0 = (srcW - cropW) / 2
            Rect(x0, 0, x0 + cropW, srcH)
        } else {
            // source is taller than target -> crop top/bottom
            val cropH = (srcW / dstAspect).toInt()
            val y0 = (srcH - cropH) / 2
            Rect(0, y0, srcW, y0 + cropH)
        }
    }
}
