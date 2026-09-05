package com.example.uniquepersnchlg.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.uniquepersnchlg.ui.theme.ElectricPurple
import com.example.uniquepersnchlg.ui.theme.FaceMarkIcon
import com.example.uniquepersnchlg.ui.theme.HotPink
import com.example.uniquepersnchlg.ui.theme.InkBlack
import kotlinx.coroutines.delay

@Composable
fun PickerScreen(onVideoChosen: (Uri, String) -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onVideoChosen(uri, uri.lastPathSegment ?: "video")
        }
    }

    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60)
        contentVisible = true
    }

    // Idle logo wobble - subtle, keeps the screen feeling alive rather than static.
    val infinite = rememberInfiniteTransition(label = "pickerIdle")
    val logoWobble by infinite.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "logoWobble"
    )
    val logoBob by infinite.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "logoBob"
    )

    // Press-scale on the CTA button - a real finger-feel micro-interaction, not just a color change.
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "buttonScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = contentVisible,
            enter = androidx.compose.animation.fadeIn(tween(400)) +
                androidx.compose.animation.slideInVertically(tween(450, easing = FastOutSlowInEasing)) { it / 4 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .offset(y = logoBob.dp)
                        .rotate(logoWobble)
                        .background(
                            brush = Brush.linearGradient(listOf(HotPink, ElectricPurple)),
                            shape = RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    FaceMarkIcon(size = 40.dp, tint = Color.White)
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text("FaceCollage", style = MaterialTheme.typography.headlineLarge, color = InkBlack)

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    "never lose track of a face again",
                    style = MaterialTheme.typography.titleMedium,
                    color = HotPink,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Drop in a video. We'll find every face, figure out who's who, and turn it into " +
                        "a collage worth keeping - all on your phone, nothing leaves your device.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(36.dp))

                Button(
                    onClick = { launcher.launch("video/*") },
                    shape = RoundedCornerShape(50),
                    interactionSource = interactionSource,
                    colors = ButtonDefaults.buttonColors(containerColor = HotPink, contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(56.dp)
                        .scale(buttonScale)
                ) {
                    Text("Pick a video", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
