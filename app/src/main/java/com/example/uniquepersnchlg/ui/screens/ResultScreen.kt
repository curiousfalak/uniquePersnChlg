package com.example.facecollage.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.facecollage.util.SaveShareUtils


import com.example.uniquepersnchlg.data.model.VideoResult

@Composable
fun ResultScreen(result: VideoResult, onProcessAnother: () -> Unit) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text("Your collage", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(12.dp))
            Image(
                bitmap = result.collageBitmap.asImageBitmap(),
                contentDescription = "Generated collage",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    val uri = SaveShareUtils.saveToGallery(context, result.collageBitmap, "facecollage_${System.currentTimeMillis()}")
                    Toast.makeText(
                        context,
                        if (uri != null) "Saved to gallery" else "Save failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }) { Text("Save") }

                OutlinedButton(onClick = {
                    val uri = SaveShareUtils.cacheForSharing(context, result.collageBitmap, "facecollage_${System.currentTimeMillis()}")
                    context.startActivity(SaveShareUtils.shareIntent(context, uri))
                }) { Text("Share") }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "${result.identities.size} people detected",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(result.identities) { identity ->
            ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Person ${identity.id + 1}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${identity.appearanceCount} appearance${if (identity.appearanceCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onProcessAnother) { Text("Process another video") }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
