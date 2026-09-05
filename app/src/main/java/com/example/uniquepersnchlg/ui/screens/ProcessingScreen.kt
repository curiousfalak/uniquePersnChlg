package com.example.facecollage.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.uniquepersnchlg.data.model.ProcessingState


@Composable
fun ProcessingScreen(
    state: ProcessingState,
    onDone: () -> Unit,
    onError: () -> Unit
) {
    LaunchedEffect(state) {
        when (state) {
            is ProcessingState.Done -> onDone()
            is ProcessingState.Error -> {} // stay on screen and show the message + a way back
            else -> {}
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val (label, progress) = state.toLabelAndProgress()

        Text("Processing video", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(24.dp))

        if (progress != null) {
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth(0.8f))
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.8f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)

        if (state is ProcessingState.Error) {
            Spacer(modifier = Modifier.height(24.dp))
            Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onError) { Text("Choose another video") }
        }
    }
}

private fun ProcessingState.toLabelAndProgress(): Pair<String, Float?> = when (this) {
    is ProcessingState.Idle -> "Starting..." to null
    is ProcessingState.ExtractingFrames -> "Reading frames ($done/$total)" to
        if (total > 0) done.toFloat() / total else null
    is ProcessingState.DetectingFaces -> "Detecting faces ($done/$total)" to
        if (total > 0) done.toFloat() / total else null
    is ProcessingState.Tracking -> "Tracking appearances across frames..." to null
    is ProcessingState.Clustering -> "Matching people across appearances..." to null
    is ProcessingState.BuildingCollage -> "Building your collage..." to null
    is ProcessingState.Done -> "Done!" to 1f
    is ProcessingState.Error -> "Something went wrong" to null
}
