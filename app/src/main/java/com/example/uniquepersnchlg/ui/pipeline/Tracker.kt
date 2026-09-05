package com.example.facecollage.pipeline

import android.graphics.RectF

import com.example.uniquepersnchlg.data.model.FaceSample
import com.example.uniquepersnchlg.data.model.Tracklet
import kotlin.collections.filter


class Tracker(
    private val iouThreshold: Float = 0.25f,
    private val embeddingThreshold: Float = 0.55f,
    private val maxMissedFrames: Int = 3,
    private val minQualityToStartOrExtend: Float = 0.18f
) {

    private data class OpenTrack(
        val tracklet: Tracklet,
        var lastFrameIndex: Int,
        var lastBox: RectF,
        var lastEmbedding: FloatArray,
        var missedFrames: Int = 0
    )

    /**
     * @param samplesByFrame all detected faces, already grouped by the frame they came from,
     *        in increasing frame order.
     */
    fun buildTracklets(samplesByFrame: List<List<FaceSample>>): List<Tracklet> {
        val open = mutableListOf<OpenTrack>()
        val finished = mutableListOf<Tracklet>()
        var nextId = 0

        for (frameSamples in samplesByFrame) {
            val usable = frameSamples.filter { it.qualityScore() >= minQualityToStartOrExtend }
            val matchedOpenIds = mutableSetOf<Int>()

            // Greedy best-match assignment: for each detection this frame, find the best open
            // track by a combined IoU + embedding-similarity score.
            for (sample in usable) {
                var bestTrack: OpenTrack? = null
                var bestScore = -1f
                for (track in open) {
                    if (System.identityHashCode(track) in matchedOpenIds) continue
                    val iou = iou(track.lastBox, sample.boundingBox)
                    val sim = cosineSim(track.lastEmbedding, sample.embedding)
                    if (iou < iouThreshold && sim < embeddingThreshold) continue
                    // Weighted combination: IoU dominates for adjacent frames (cheap, reliable),
                    // embedding similarity is the tie-breaker / recovers from fast motion.
                    val score = 0.6f * iou + 0.4f * sim
                    if (score > bestScore) {
                        bestScore = score
                        bestTrack = track
                    }
                }

                if (bestTrack != null) {
                    bestTrack.tracklet.samples.add(sample)
                    bestTrack.lastFrameIndex = sample.frameIndex
                    bestTrack.lastBox = sample.boundingBox
                    bestTrack.lastEmbedding = sample.embedding
                    bestTrack.missedFrames = 0
                    matchedOpenIds.add(System.identityHashCode(bestTrack))
                } else {
                    val t = Tracklet(id = nextId++).apply { samples.add(sample) }
                    open.add(OpenTrack(t, sample.frameIndex, sample.boundingBox, sample.embedding))
                }
            }

            // Age out unmatched tracks; close ones that have been missing too long.
            val stillOpen = mutableListOf<OpenTrack>()
            for (track in open) {
                if (System.identityHashCode(track) in matchedOpenIds) {
                    stillOpen.add(track)
                } else {
                    track.missedFrames++
                    if (track.missedFrames > maxMissedFrames) {
                        if (track.tracklet.samples.size >= MIN_TRACKLET_LENGTH) {
                            finished.add(track.tracklet)
                        }
                    } else {
                        stillOpen.add(track)
                    }
                }
            }
            open.clear()
            open.addAll(stillOpen)
        }

        for (track in open) {
            if (track.tracklet.samples.size >= MIN_TRACKLET_LENGTH) finished.add(track.tracklet)
        }

        return finished
    }

    private fun iou(a: RectF, b: RectF): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        if (right <= left || bottom <= top) return 0f
        val inter = (right - left) * (bottom - top)
        val union = a.width() * a.height() + b.width() * b.height() - inter
        return if (union <= 0f) 0f else inter / union
    }

    companion object {
        // Require at least this many sampled frames in a row before we trust a tracklet as a
        // real "appearance" rather than a single spurious detection.
        const val MIN_TRACKLET_LENGTH = 2
    }
}
