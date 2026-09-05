package com.example.uniquepersnchlg.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.uniquepersnchlg.data.ProcessingState
import com.example.uniquepersnchlg.ui.theme.*

/** Which hand-drawn icon (see BrandIcons.kt) represents each pipeline stage. */
private enum class StageIcon { SPARKLE, SCAN, PEOPLE, LINK, GROUP, PALETTE, ERROR }

private data class Display(val icon: StageIcon, val label: String, val progress: Float?)

private fun ProcessingState.toDisplay(): Display = when (this) {
    is ProcessingState.Idle -> Display(StageIcon.SPARKLE, "getting started...", null)
    is ProcessingState.ExtractingFrames -> Display(
        StageIcon.SCAN, "watching the video ($done/$total)",
        if (total > 0) done.toFloat() / total else null
    )
    is ProcessingState.DetectingFaces -> Display(
        StageIcon.PEOPLE, "spotting faces ($done/$total)",
        if (total > 0) done.toFloat() / total else null
    )
    is ProcessingState.Tracking -> Display(StageIcon.LINK, "following faces across the video...", null)
    is ProcessingState.Clustering -> Display(StageIcon.GROUP, "matching up who's who...", null)
    is ProcessingState.BuildingCollage -> Display(StageIcon.PALETTE, "putting your collage together...", null)
    is ProcessingState.Done -> Display(StageIcon.SPARKLE, "done!", 1f)
    is ProcessingState.Error -> Display(StageIcon.ERROR, "hit a snag", null)
}

@Composable
fun ProcessingScreen(
    state: ProcessingState,
    onDone: () -> Unit,
    onError: () -> Unit
) {
    LaunchedEffect(state) {
        when (state) {
            is ProcessingState.Done -> onDone()
            else -> {}
        }
    }

    // Idle pulse so the icon area never feels frozen between progress-percent updates.
    val infinite = rememberInfiniteTransition(label = "processingIdle")
    val iconScale by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "iconScale"
    )
    val iconSpin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "iconSpin"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val (stageIcon, label, progress) = state.toDisplay()

        AnimatedContent(
            targetState = stageIcon,
            transitionSpec = {
                (fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 2 }) togetherWith
                    (fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 2 })
            },
            label = "iconSwap"
        ) { icon ->
            Box(modifier = Modifier.size(64.dp).scale(iconScale), contentAlignment = Alignment.Center) {
                when (icon) {
                    StageIcon.SPARKLE -> SparkleStarIcon(size = 56.dp, tint = HotPink, modifier = Modifier.rotate(iconSpin))
                    StageIcon.SCAN -> ScanPulseIcon(size = 56.dp, tint = HotPink)
                    StageIcon.PEOPLE -> PeopleDotsIcon(size = 56.dp, colors = listOf(HotPink, ElectricPurple, SkyBlast))
                    StageIcon.LINK -> LinkNodesIcon(size = 56.dp, tint = ElectricPurple)
                    StageIcon.GROUP -> GroupClusterIcon(size = 56.dp, colors = listOf(HotPink, ElectricPurple, MintPop))
                    StageIcon.PALETTE -> PaletteBlobsIcon(size = 56.dp, colors = listOf(HotPink, SunnyYellow, MintPop, SkyBlast))
                    StageIcon.ERROR -> ErrorMarkIcon(size = 56.dp, tint = HotPinkDark)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("hang tight, working on it", style = MaterialTheme.typography.headlineMedium, color = InkBlack)

        Spacer(modifier = Modifier.height(28.dp))

        val animatedProgress by animateFloatAsState(
            targetValue = progress ?: 0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
            label = "progressBar"
        )
        LinearProgressIndicator(
            progress = { animatedProgress },
            color = HotPink,
            trackColor = HotPinkLight,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(10.dp)
                .clip(RoundedCornerShape(50))
        )

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(
            targetState = label,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(150)) },
            label = "labelSwap"
        ) { l ->
            Text(l, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (state is ProcessingState.Error) {
            Spacer(modifier = Modifier.height(24.dp))
            Text("Something went wrong: ${state.message}", color = HotPink, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onError,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple)
            ) {
                Text("Try another video")
            }
        }
    }
}
