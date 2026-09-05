package com.example.facecollage.pipeline

import android.content.Context
import android.net.Uri

import com.example.facecollage.util.BitmapUtils
import com.example.uniquepersnchlg.data.model.FaceSample
import com.example.uniquepersnchlg.data.model.ProcessingState
import com.example.uniquepersnchlg.data.model.VideoResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext


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
                    val faces = faceDetector.detect(frame.bitmap)
                    val samples = faces.mapNotNull { face ->
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
                    samplesByFrame.add(samples)
                }

                _state.value = ProcessingState.Tracking
                val tracklets = tracker.buildTracklets(samplesByFrame)

                if (tracklets.isEmpty()) {
                    _state.value = ProcessingState.Error("No clearly visible faces were found.")
                    return@withContext
                }

                _state.value = ProcessingState.Clustering
                val identities = clusterer.cluster(tracklets)

                _state.value = ProcessingState.BuildingCollage
                val collage = collageComposer.compose(identities, videoLabel)

                _state.value = ProcessingState.Done(
                    VideoResult(
                        videoUri = videoUri.toString(),
                        identities = identities,
                        collageBitmap = collage
                    )
                )
            } catch (e: Exception) {
                _state.value = ProcessingState.Error(e.message ?: "Processing failed.")
            }
        }
    }

    fun reset() {
        _state.value = ProcessingState.Idle
    }

    fun close() {
        faceDetector.close()
        embedder.close()
    }
}
