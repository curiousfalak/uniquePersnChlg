package com.example.uniquepersnchlg.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uniquepersnchlg.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Branded animated splash: logo badge pops in with an overshoot bounce, gently wobbles while
 * four hand-drawn sparkle marks pulse in around it, tagline fades/slides up after. Auto-advances
 * once startAnim's delay elapses. Every visual here is custom-drawn (see BrandIcons.kt) - no
 * system emoji, no Material Icons glyphs.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var startAnim by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        startAnim = true
        delay(1500)
        onFinished()
    }

    // Logo pop-in: scale from 0 with a bouncy overshoot spring.
    val logoScale by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "logoScale"
    )
    // Gentle continuous wobble once popped in.
    val infinite = rememberInfiniteTransition(label = "splashInfinite")
    val wobble by infinite.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wobble"
    )
    // Sparkles get a slow continuous spin for extra liveliness once they've popped in.
    val sparkleSpin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "sparkleSpin"
    )
    val taglineAlpha by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(600, delayMillis = 500),
        label = "taglineAlpha"
    )
    val taglineOffset by animateFloatAsState(
        targetValue = if (startAnim) 0f else 24f,
        animationSpec = tween(600, delayMillis = 500, easing = FastOutSlowInEasing),
        label = "taglineOffset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(SplashGradientStart, SplashGradientMid, SplashGradientEnd)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Floating hand-drawn sparkle marks, staggered pulse-in, slow continuous spin.
        SparkleFlourish(color = SunnyYellow, offsetX = (-90).dp, offsetY = (-120).dp, delayMs = 200, spin = sparkleSpin)
        SparkleFlourish(color = MintPop, offsetX = 100.dp, offsetY = (-90).dp, delayMs = 350, spin = sparkleSpin)
        SparkleFlourish(color = SkyBlast, offsetX = 110.dp, offsetY = 110.dp, delayMs = 500, spin = sparkleSpin)
        SparkleFlourish(color = SunnyYellow, offsetX = (-100.dp), offsetY = 100.dp, delayMs = 650, spin = sparkleSpin)

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .scale(logoScale)
                    .rotate(if (startAnim) wobble else 0f)
                    .background(
                        brush = Brush.linearGradient(listOf(HotPink, ElectricPurple)),
                        shape = RoundedCornerShape(28.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                FaceMarkIcon(size = 52.dp, tint = Color.White)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "FaceCollage",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .scale(logoScale)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                "loading the vibes...",
                color = HotPinkLight,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .alpha(taglineAlpha)
                    .offset(y = taglineOffset.dp)
            )
        }
    }
}

@Composable
private fun SparkleFlourish(
    color: Color,
    offsetX: androidx.compose.ui.unit.Dp,
    offsetY: androidx.compose.ui.unit.Dp,
    delayMs: Int,
    spin: Float
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        visible = true
    }
    val popScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium),
        label = "sparklePop"
    )
    Box(
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .scale(popScale)
            .rotate(spin)
    ) {
        SparkleStarIcon(size = 16.dp, tint = color)
    }
}
