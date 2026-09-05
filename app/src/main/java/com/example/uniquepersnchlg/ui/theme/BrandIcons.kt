package com.example.uniquepersnchlg.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Hand-drawn brand marks, built entirely from Canvas primitives (no system emoji, no Material
 * Icons glyphs) so every visual in the app is genuinely custom rather than borrowed from an
 * OS/library icon set. Each icon fills its given [size] and takes a tint (or small palette).
 */

/** Simple friendly face: rounded head, two eye dots, a smile arc. Used as the app's logo mark. */
@Composable
fun FaceMarkIcon(size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val eyeR = w * 0.06f
        val eyeY = h * 0.42f

        drawCircle(color = tint, radius = eyeR, center = Offset(w * 0.36f, eyeY))
        drawCircle(color = tint, radius = eyeR, center = Offset(w * 0.64f, eyeY))

        val smile = Path().apply {
            moveTo(w * 0.32f, h * 0.58f)
            quadraticBezierTo(w * 0.5f, h * 0.74f, w * 0.68f, h * 0.58f)
        }
        drawPath(
            path = smile,
            color = tint,
            style = Stroke(width = w * 0.055f, cap = StrokeCap.Round)
        )
    }
}

/** Four-point sparkle/star, used for decorative flourishes and the "done" celebration moment. */
@Composable
fun SparkleStarIcon(size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val outer = min(w, h) / 2f
        val inner = outer * 0.32f

        val path = Path()
        for (i in 0 until 8) {
            val angle = (PI / 4) * i - PI / 2
            val r = if (i % 2 == 0) outer else inner
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy + (r * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path = path, color = tint)
    }
}

/** Simple right-pointing rounded triangle, a hand-built alternative to a "play" glyph. */
@Composable
fun PlayTriangleIcon(size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.28f, h * 0.18f)
            lineTo(w * 0.82f, h * 0.5f)
            lineTo(w * 0.28f, h * 0.82f)
            close()
        }
        drawPath(path = path, color = tint)
    }
}

/** Concentric arcs radiating from a center dot - a hand-built "scanning" motif. */
@Composable
fun ScanPulseIcon(size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        drawCircle(color = tint, radius = w * 0.07f, center = Offset(cx, cy))
        for (ring in 1..2) {
            val r = w * 0.18f * ring + w * 0.10f
            drawArc(
                color = tint.copy(alpha = 1f - ring * 0.28f),
                startAngle = -60f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = w * 0.05f, cap = StrokeCap.Round)
            )
        }
    }
}

/** Three overlapping filled circles in different accent colors - represents "multiple faces". */
@Composable
fun PeopleDotsIcon(size: Dp, colors: List<Color>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val r = w * 0.22f
        val centers = listOf(
            Offset(w * 0.32f, h * 0.42f),
            Offset(w * 0.68f, h * 0.42f),
            Offset(w * 0.50f, h * 0.68f)
        )
        centers.forEachIndexed { i, c ->
            drawCircle(color = colors[i % colors.size].copy(alpha = 0.9f), radius = r, center = c)
        }
    }
}

/** Two nodes joined by a rounded connecting bar - represents linking/matching. */
@Composable
fun LinkNodesIcon(size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val r = w * 0.14f
        val leftC = Offset(w * 0.28f, h * 0.5f)
        val rightC = Offset(w * 0.72f, h * 0.5f)
        drawLine(
            color = tint,
            start = leftC, end = rightC,
            strokeWidth = w * 0.09f,
            cap = StrokeCap.Round
        )
        drawCircle(color = tint, radius = r, center = leftC)
        drawCircle(color = tint, radius = r, center = rightC)
    }
}

/** Three circles converging toward a shared center - represents grouping/clustering. */
@Composable
fun GroupClusterIcon(size: Dp, colors: List<Color>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = w * 0.16f
        val orbit = w * 0.24f
        for (i in 0 until 3) {
            val angle = (2 * PI / 3) * i - PI / 2
            val x = cx + (orbit * cos(angle)).toFloat()
            val y = cy + (orbit * sin(angle)).toFloat()
            drawCircle(color = colors[i % colors.size].copy(alpha = 0.85f), radius = r, center = Offset(x, y))
        }
        drawCircle(color = InkBlack.copy(alpha = 0.15f), radius = r * 0.7f, center = Offset(cx, cy))
    }
}

/** A small cluster of colored blobs arranged like a paint palette - represents collage-building. */
@Composable
fun PaletteBlobsIcon(size: Dp, colors: List<Color>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val positions = listOf(
            Offset(w * 0.30f, h * 0.32f) to w * 0.16f,
            Offset(w * 0.66f, h * 0.28f) to w * 0.13f,
            Offset(w * 0.70f, h * 0.62f) to w * 0.15f,
            Offset(w * 0.34f, h * 0.66f) to w * 0.12f
        )
        positions.forEachIndexed { i, (offset, radius) ->
            drawCircle(color = colors[i % colors.size], radius = radius, center = offset)
        }
    }
}

/** Two short crossed strokes - a plain, hand-built "something went wrong" mark. */
@Composable
fun ErrorMarkIcon(size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val inset = w * 0.28f
        drawLine(
            color = tint,
            start = Offset(inset, inset), end = Offset(w - inset, h - inset),
            strokeWidth = w * 0.09f, cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w - inset, inset), end = Offset(inset, h - inset),
            strokeWidth = w * 0.09f, cap = StrokeCap.Round
        )
    }
}
