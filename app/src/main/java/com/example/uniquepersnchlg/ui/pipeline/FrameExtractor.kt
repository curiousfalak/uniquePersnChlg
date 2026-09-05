package com.example.facecollage.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExtractedFrame(
    val index: Int,
    val timestampMs: Long,
    val bitmap: Bitmap
)

class FrameExtractor(private val context: Context) {


    suspend fun extract(
        videoUri: Uri,
        samplesPerSecond: Int = 6,
        onProgress: (Int, Int) -> Unit
    ): List<ExtractedFrame> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<ExtractedFrame>()
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            if (durationMs <= 0L) return@withContext emptyList()

            val stepMs = (1000 / samplesPerSecond).toLong().coerceAtLeast(16L)
            val timestamps = (0 until durationMs step stepMs).toList()

            for ((i, tMs) in timestamps.withIndex()) {
                // OPTION_CLOSEST is slower than OPTION_CLOSEST_SYNC but avoids landing only on
                // keyframes, which for a 30s single-shot clip would badly undersample motion.
                val bmp = retriever.getFrameAtTime(tMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                if (bmp != null) {
                    frames.add(ExtractedFrame(i, tMs, bmp))
                }
                onProgress(i + 1, timestamps.size)
            }
        } finally {
            retriever.release()
        }
        frames
    }
}
