package com.example.facecollage.pipeline

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt


class FaceEmbedder(context: Context) {

    companion object {
        private const val MODEL_FILE = "mobilefacenet.tflite"
        const val INPUT_SIZE = 112
        const val EMBEDDING_DIM = 192
    }

    private val interpreter: Interpreter

    init {
        val afd = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(afd.fileDescriptor)
        val buffer = inputStream.channel.map(
            FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength
        )
        val options = Interpreter.Options().apply { setNumThreads(4) }
        interpreter = Interpreter(buffer, options)
    }

    /** [chip] must already be a 112x112 aligned face crop (see BitmapUtils.alignedFaceChip). */
    fun embed(chip: Bitmap): FloatArray {
        val input = bitmapToByteBuffer(chip)
        val output = Array(1) { FloatArray(EMBEDDING_DIM) }
        interpreter.run(input, output)
        return l2Normalize(output[0])
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            buffer.putFloat((r - 127.5f) / 127.5f)
            buffer.putFloat((g - 127.5f) / 127.5f)
            buffer.putFloat((b - 127.5f) / 127.5f)
        }
        buffer.rewind()
        return buffer
    }

    private fun l2Normalize(v: FloatArray): FloatArray {
        var norm = 0f
        for (x in v) norm += x * x
        norm = sqrt(norm).coerceAtLeast(1e-6f)
        return FloatArray(v.size) { v[it] / norm }
    }

    fun close() = interpreter.close()
}

/** Cosine similarity between two L2-normalized embeddings == dot product. */
fun cosineSim(a: FloatArray, b: FloatArray): Float {
    var dot = 0f
    for (i in a.indices) dot += a[i] * b[i]
    return dot
}
