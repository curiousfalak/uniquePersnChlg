package com.example.uniquepersnchlg.pipeline

import android.content.Context
import android.net.Uri
import com.example.facecollage.pipeline.FaceDetectorWrapper
import com.example.facecollage.pipeline.FaceEmbedder
import com.example.facecollage.pipeline.FrameExtractor
import com.example.facecollage.pipeline.Tracker
import com.example.uniquepersnchlg.data.*
import com.example.uniquepersnchlg.util.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Orchestrates the full pipeline for one video: extraction -> detection -> embedding ->
 * tracking -> clustering -> representative shot -> collage. Every heavy step runs on
 * Dispatchers.Default/IO; only the final state update touches anything UI-observed, and that's
 * done via StateFlow so Compose can collect it safely regardless of thread.
 */
class VideoProcessor(private val context: Context) {

    private val frameExtractor = FrameExtractor(context)
    private val faceDetector = FaceDetectorWrapper()
    private val embedder = FaceEmbedder(context)
    private val tracker = Tracker()
    private val clusterer = IdentityClusterer()
    private val collageComposer = CollageComposer()

    private val _state = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val state: StateFlow<ProcessingState> = _state.asStateFlow()

    suspend fun process(videoUri: Uri, videoLabel: String) {
        withContext(Dispatchers.Default) {
            try {
                _state.value = ProcessingState.ExtractingFrames(0, 1)
                val frames = frameExtractor.extract(videoUri) { done, total ->
                    _state.value = ProcessingState.ExtractingFrames(done, total)
                }
                if (frames.isEmpty()) {
                    _state.value = ProcessingState.Error("Could not read any frames from this video.")
                    return@withContext
                }

                val samplesByFrame = mutableListOf<List<FaceSample>>()
                for ((i, frame) in frames.withIndex()) {
                    _state.value = ProcessingState.DetectingFaces(i + 1, frames.size)
                    val rawFaces = faceDetector.detect(frame.bitmap)
                    // NMS: ML Kit occasionally emits two overlapping detections for one physical
                    // face (more likely with LANDMARK_MODE_ALL + enableTracking()). Left
                    // unfiltered, each duplicate spawns its own parallel tracklet later, which
                    // then shows up as a duplicate-looking "person" in the final collage even
                    // though visually it's the same face in the same frames. Suppress before
                    // any embedding work is done on them (also saves TFLite inference calls).
                    val faces = suppressDuplicateDetections(rawFaces)

                    val rawSamples = faces.mapNotNull { face ->
                        val box = android.graphics.RectF(face.boundingBox)
                        // Discard boxes that fall (mostly) outside the frame - detector edge noise.
                        if (box.width() <= 0 || box.height() <= 0) return@mapNotNull null

                        val leftEyeLm = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE)
                        val rightEyeLm = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE)
                        val leftEye = leftEyeLm?.position?.let { it.x to it.y }
                        val rightEye = rightEyeLm?.position?.let { it.x to it.y }

                        val chip = BitmapUtils.alignedFaceChip(frame.bitmap, box, leftEye, rightEye)
                        val embedding = embedder.embed(chip)
                        val sharpness = BitmapUtils.sharpnessScore(frame.bitmap, box)
                        val touchesEdge = BitmapUtils.touchesEdge(box, frame.bitmap.width, frame.bitmap.height)

                        FaceSample(
                            frameIndex = frame.index,
                            timestampMs = frame.timestampMs,
                            frameBitmap = frame.bitmap,
                            boundingBox = box,
                            embedding = embedding,
                            headEulerAngleY = face.headEulerAngleY,
                            headEulerAngleZ = face.headEulerAngleZ,
                            leftEyeOpenProb = face.leftEyeOpenProbability,
                            rightEyeOpenProb = face.rightEyeOpenProbability,
                            smilingProb = face.smilingProbability,
                            sharpness = sharpness,
                            touchesEdge = touchesEdge,
                            frameWidth = frame.bitmap.width,
                            frameHeight = frame.bitmap.height
                        )
                    }

                    // Now that every face in this frame is known, fill in each sample's distance
                    // to its nearest neighbor in the SAME frame - needed to down-weight crowded
                    // frames when picking a representative shot (see FaceSample.qualityScore()).
                    val samples = rawSamples.map { s ->
                        val cx = s.boundingBox.centerX()
                        val cy = s.boundingBox.centerY()
                        var nearest: Float? = null
                        for (other in rawSamples) {
                            if (other === s) continue
                            val dx = other.boundingBox.centerX() - cx
                            val dy = other.boundingBox.centerY() - cy
                            val d = kotlin.math.sqrt(dx * dx + dy * dy)
                            if (nearest == null || d < nearest!!) nearest = d
                        }
                        if (nearest != null) s.copy(nearestNeighborDistancePx = nearest) else s
                    }
                    samplesByFrame.add(samples)
                }

                _state.value = ProcessingState.Tracking
                val tracklets = tracker.buildTracklets(samplesByFrame)

                if (tracklets.isEmpty()) {
                    _state.value = ProcessingState.Error("No clearly visible faces were found.")
                    return@withContext
                }
                android.util.Log.d("VideoProcessor", "Built ${tracklets.size} tracklets from ${frames.size} sampled frames.")
                logPairwiseSimilarities(tracklets)

                _state.value = ProcessingState.Clustering
                val identities = clusterer.cluster(tracklets)
                android.util.Log.d(
                    "VideoProcessor",
                    "Clustered into ${identities.size} identities: " +
                            identities.joinToString { "id=${it.id} appearances=${it.appearanceCount}" }
                )

                _state.value = ProcessingState.BuildingCollage
                val collage = collageComposer.compose(identities, videoLabel)

                _state.value = ProcessingState.Done(
                    VideoResult(videoUri = videoUri.toString(), identities = identities, collageBitmap = collage)
                )
            } catch (e: Exception) {
                _state.value = ProcessingState.Error(e.message ?: "Processing failed.")
            }
        }
    }

    /**
     * Suppresses duplicate/overlapping detections within a single frame (simple greedy NMS on
     * IoU). ML Kit's face detector can occasionally return two boxes for one physical face,
     * especially with LANDMARK_MODE_ALL + enableTracking(); left alone, each duplicate becomes
     * its own tracklet and later shows up as a separate "person" for someone who only appears
     * once. There's no per-face confidence score exposed by the ML Kit Face API, so ties are
     * broken by keeping the larger box (usually the better-centered / less clipped detection).
     */
    private fun suppressDuplicateDetections(
        faces: List<com.google.mlkit.vision.face.Face>,
        iouThreshold: Float = 0.75f
    ): List<com.google.mlkit.vision.face.Face> {
        val sorted = faces.sortedByDescending { it.boundingBox.width().toLong() * it.boundingBox.height() }
        val kept = mutableListOf<com.google.mlkit.vision.face.Face>()
        for (candidate in sorted) {
            val candidateBox = android.graphics.RectF(candidate.boundingBox)
            val overlapsKept = kept.any { existing ->
                val existingBox = android.graphics.RectF(existing.boundingBox)
                iou(candidateBox, existingBox) > iouThreshold
            }
            if (!overlapsKept) kept.add(candidate)
        }
        return kept
    }

    private fun iou(a: android.graphics.RectF, b: android.graphics.RectF): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        if (right <= left || bottom <= top) return 0f
        val inter = (right - left) * (bottom - top)
        val union = a.width() * a.height() + b.width() * b.height() - inter
        return if (union <= 0f) 0f else inter / union
    }

    /**
     * Logs the full tracklet x tracklet cosine-similarity matrix before clustering runs. This
     * is the evidence-based way to pick IdentityClusterer's threshold: rather than guessing a
     * single number and re-testing blind, look at the actual numbers - if same-person pairs and
     * different-person pairs are cleanly separated (e.g. same-person > 0.6, different-person <
     * 0.4), any threshold in that gap works and the bug is elsewhere (e.g. duplicate detections,
     * fixed above). If there's no clean gap - same-person pairs scoring as low as different-
     * person pairs - no threshold value can fix it and the embedding pipeline itself (alignment,
     * model preprocessing, or the model choice) needs to be revisited.
     */
    private fun logPairwiseSimilarities(tracklets: List<Tracklet>) {
        if (tracklets.size < 2) return
        val sb = StringBuilder("Pairwise tracklet best-frame scores (id:startMs-endMs) - this is the exact metric IdentityClusterer uses:\n")
        for (i in tracklets.indices) {
            for (j in i + 1 until tracklets.size) {
                val a = tracklets[i]
                val b = tracklets[j]
                val sim = IdentityClusterer.bestPairScore(a, b)
                sb.append("  T${a.id}[${a.startMs}-${a.endMs}] <-> T${b.id}[${b.startMs}-${b.endMs}]: %.3f\n".format(sim))
            }
        }
        android.util.Log.d("VideoProcessor", sb.toString())
    }

    fun reset() {
        _state.value = ProcessingState.Idle
    }

    fun close() {
        faceDetector.close()
        embedder.close()
    }
}
