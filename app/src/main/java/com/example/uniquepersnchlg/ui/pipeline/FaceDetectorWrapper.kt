package com.example.facecollage.pipeline

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await


class FaceDetectorWrapper {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setMinFaceSize(0.10f)
        .enableTracking() // cheap extra signal; we still do our own cross-frame tracking
        .build()

    private val detector = FaceDetection.getClient(options)

    suspend fun detect(bitmap: Bitmap): List<Face> {
        val input = InputImage.fromBitmap(bitmap, 0)
        return try {
            detector.process(input).await()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun close() = detector.close()
}
