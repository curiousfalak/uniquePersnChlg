package com.example.uniquepersnchlg.data

import android.graphics.Bitmap
import android.graphics.RectF


data class FaceSample(
    val frameIndex: Int,
    val timestampMs: Long,
    val frameBitmap: Bitmap,
    val boundingBox: RectF,          // in frameBitmap pixel coordinates
    val embedding: FloatArray,       // L2-normalized, from FaceEmbedder
    val headEulerAngleY: Float,      // yaw
    val headEulerAngleZ: Float,      // roll
    val leftEyeOpenProb: Float?,
    val rightEyeOpenProb: Float?,
    val smilingProb: Float?,
    val sharpness: Float,            // Laplacian-variance based, normalized-ish
    val touchesEdge: Boolean,
    val frameWidth: Int,
    val frameHeight: Int,
    // Distance in px from this face's box center to the nearest OTHER detected face's box
    // center in the same frame. Null if this was the only face detected in that frame.
    // Used to (a) penalize crowded frames when picking a representative shot, since a
    // generous collage crop around a face that's close to another person will bleed into
    // their face, and (b) as a secondary signal that a "full face visible" shot is more
    // likely (crowded/overlapping frames are more likely to have partial occlusion).
    val nearestNeighborDistancePx: Float? = null
) {

    fun qualityScore(): Float {
        val frontality = 1f - (kotlin.math.min(1f, (kotlin.math.abs(headEulerAngleY) + kotlin.math.abs(headEulerAngleZ)) / 90f))
        val eyesOpen = listOfNotNull(leftEyeOpenProb, rightEyeOpenProb).let {
            if (it.isEmpty()) 0.5f else it.average().toFloat()
        }
        val smile = smilingProb ?: 0.3f
        val sizeFraction = (boundingBox.width() * boundingBox.height()) / (frameWidth * frameHeight).toFloat()
        val sizeScore = kotlin.math.min(1f, sizeFraction * 12f) // reward reasonably large faces, saturate quickly

        var score = 0.30f * frontality +
                0.25f * sharpness.coerceIn(0f, 1f) +
                0.20f * eyesOpen +
                0.10f * smile +
                0.15f * sizeScore

        if (touchesEdge) score *= 0.5f

        // Penalize frames where another face sits close enough that a generous collage crop
        // around this box would likely bleed into them. "Close" is relative to this face's
        // own box size, since box size scales with distance-to-camera.
        val faceSize = kotlin.math.max(boundingBox.width(), boundingBox.height())
        val neighborDist = nearestNeighborDistancePx
        if (neighborDist != null && faceSize > 0f) {
            val ratio = neighborDist / faceSize
            if (ratio < 3.0f) {
                // Smoothly scale the penalty: right at the crop-bleed boundary (~ratio 1.5)
                // it's severe; by ratio 3 (plenty of clearance) it's negligible.
                val crowding = (1f - (ratio / 3.0f)).coerceIn(0f, 1f)
                score *= (1f - 0.6f * crowding)
            }
        }
        return score
    }
}


data class Tracklet(
    val id: Int,
    val samples: MutableList<FaceSample> = mutableListOf()
) {
    val startMs: Long get() = samples.first().timestampMs
    val endMs: Long get() = samples.last().timestampMs

    /** Mean of the L2-normalized embeddings of the best-quality samples, re-normalized. */
    fun centroidEmbedding(topK: Int = 5): FloatArray {
        val best = samples.sortedByDescending { it.qualityScore() }.take(topK)
        val dim = best.first().embedding.size
        val acc = FloatArray(dim)
        for (s in best) for (i in 0 until dim) acc[i] += s.embedding[i]
        var norm = 0f
        for (i in 0 until dim) { acc[i] /= best.size; norm += acc[i] * acc[i] }
        norm = kotlin.math.sqrt(norm).coerceAtLeast(1e-6f)
        for (i in 0 until dim) acc[i] /= norm
        return acc
    }

    fun bestSample(): FaceSample = samples.maxByOrNull { it.qualityScore() }!!
}


data class Identity(
    val id: Int,
    val tracklets: MutableList<Tracklet> = mutableListOf()
) {
    val appearanceCount: Int get() = tracklets.size

    fun representativeSample(): FaceSample =
        tracklets.map { it.bestSample() }.maxByOrNull { it.qualityScore() }!!

    fun centroidEmbedding(): FloatArray {
        val dim = tracklets.first().centroidEmbedding().size
        val acc = FloatArray(dim)
        for (t in tracklets) {
            val e = t.centroidEmbedding()
            for (i in 0 until dim) acc[i] += e[i]
        }
        var norm = 0f
        for (i in 0 until dim) { acc[i] /= tracklets.size; norm += acc[i] * acc[i] }
        norm = kotlin.math.sqrt(norm).coerceAtLeast(1e-6f)
        for (i in 0 until dim) acc[i] /= norm
        return acc
    }
}


data class VideoResult(
    val videoUri: String,
    val identities: List<Identity>,
    val collageBitmap: Bitmap
)

sealed class ProcessingState {
    data object Idle : ProcessingState()
    data class ExtractingFrames(val done: Int, val total: Int) : ProcessingState()
    data class DetectingFaces(val done: Int, val total: Int) : ProcessingState()
    data object Tracking : ProcessingState()
    data object Clustering : ProcessingState()
    data object BuildingCollage : ProcessingState()
    data class Done(val result: VideoResult) : ProcessingState()
    data class Error(val message: String) : ProcessingState()
}
