package com.example.uniquepersnchlg.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip

import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.facecollage.util.SaveShareUtils
import com.example.uniquepersnchlg.data.Identity
import com.example.uniquepersnchlg.data.VideoResult
import com.example.uniquepersnchlg.ui.theme.*

import kotlinx.coroutines.delay

@Composable
fun ResultScreen(result: VideoResult, onProcessAnother: () -> Unit) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80)
        revealed = true
    }

    // Collage image pops in with a bouncy scale - the "reveal" moment deserves more than a flat cut.
    val collageScale by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.85f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "collageScale"
    )
    val collageAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(400),
        label = "collageAlpha"
    )
    val headerSparkleInfinite = rememberInfiniteTransition(label = "headerSparkle")
    val headerSparkleSpin by headerSparkleInfinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "headerSparkleSpin"
    )

    // Scroll-linked header effect: as the list scrolls (while still on the first item, i.e. the
    // header block), the "your squad, unlocked" title shrinks slightly and fades - a lightweight
    // parallax feel instead of a static header that just gets pushed off-screen unchanged.
    val headerScrollProgress by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 300f).coerceIn(0f, 1f)
        }
    }
    val headerScale = 1f - headerScrollProgress * 0.15f
    val headerAlpha = 1f - headerScrollProgress * 0.5f

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            // Respect the status bar / notch so content never sits underneath it.
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            // Extra breathing room above the title on top of the safe-area inset above.
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.graphicsLayer {
                    scaleX = headerScale
                    scaleY = headerScale
                    alpha = headerAlpha
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                }
            ) {
                Text("your squad, unlocked", style = MaterialTheme.typography.headlineMedium, color = InkBlack)
                Spacer(modifier = Modifier.width(8.dp))
                SparkleStarIcon(size = 24.dp, tint = HotPink, modifier = Modifier.rotate(headerSparkleSpin))
            }
            Spacer(modifier = Modifier.height(16.dp))

            Image(
                bitmap = result.collageBitmap.asImageBitmap(),
                contentDescription = "Generated collage",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .scale(collageScale)
                    .alpha(collageAlpha)
            )
            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        val uri = SaveShareUtils.saveToGallery(context, result.collageBitmap, "facecollage_${System.currentTimeMillis()}")
                        Toast.makeText(context, if (uri != null) "Saved to gallery" else "Save failed", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = HotPink, contentColor = Color.White)
                ) { Text("Save", fontWeight = FontWeight.Bold) }

                OutlinedButton(
                    onClick = {
                        try {
                            val uri = SaveShareUtils.cacheForSharing(context, result.collageBitmap, "facecollage_${System.currentTimeMillis()}")
                            context.startActivity(SaveShareUtils.shareIntent(context, uri))
                        } catch (e: Exception) {
                            android.util.Log.e("ResultScreen", "Share failed", e)
                            Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HotPink),
                    border = androidx.compose.foundation.BorderStroke(2.dp, HotPink)
                ) { Text("Share", fontWeight = FontWeight.Bold) }
            }

            Spacer(modifier = Modifier.height(28.dp))
            Text(
                "${result.identities.size} ${if (result.identities.size == 1) "person" else "people"} found",
                style = MaterialTheme.typography.titleLarge,
                color = InkBlack
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        itemsIndexed(result.identities) { index, identity ->
            StaggeredPersonCard(identity = identity, index = index)
        }

        item {
            Spacer(modifier = Modifier.height(28.dp))
            OutlinedButton(
                onClick = onProcessAnother,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricPurple),
                border = androidx.compose.foundation.BorderStroke(2.dp, ElectricPurple)
            ) { Text("Try another video", fontWeight = FontWeight.Bold) }
            // Bottom safe-area padding too, so the last button isn't flush against a gesture bar.
            Spacer(modifier = Modifier.height(24.dp))
            Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}

/** Rounded pink card for a person, entering with a staggered fade+slide-up delayed by its index. */
@Composable
private fun StaggeredPersonCard(identity: Identity, index: Int) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(120L + index * 70L)
        visible = true
    }

    // Alternate the badge accent color per row so the list has some color rhythm instead of
    // repeating one pink over and over - still reads as "the same app", just livelier.
    val accent = when (index % 3) {
        0 -> HotPink
        1 -> ElectricPurple
        else -> SkyBlast
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(350)) + slideInVertically(tween(400, easing = FastOutSlowInEasing)) { it / 3 }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            shape = RoundedCornerShape(18.dp),
            color = HotPinkLight
        ) {
            Row(
                modifier = Modifier.padding(18.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Person ${identity.id + 1}", style = MaterialTheme.typography.titleMedium, color = InkBlack)
                Surface(shape = RoundedCornerShape(50), color = accent) {
                    Text(
                        "${identity.appearanceCount} ${if (identity.appearanceCount == 1) "time" else "times"}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
