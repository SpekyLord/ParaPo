package com.parapo.prototype.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Input is an upright, source-resolution sign crop. Caller retains it until completion. */
class SignTextReader : AutoCloseable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun read(crop: Bitmap): Task<Text> {
        val input = minimumSizeInput(crop)
        return try {
            recognizer.process(InputImage.fromBitmap(input, 0)).also { task ->
                if (input !== crop) task.addOnCompleteListener { input.recycle() }
            }
        } catch (error: Exception) {
            if (input !== crop) input.recycle()
            throw error
        }
    }

    internal fun minimumSizeInput(crop: Bitmap): Bitmap {
        // ML Kit rejects inputs below 32px on either side; padding preserves the captured characters.
        if (crop.width >= MINIMUM_SIDE && crop.height >= MINIMUM_SIDE) return crop
        val padded = Bitmap.createBitmap(maxOf(MINIMUM_SIDE, crop.width), maxOf(MINIMUM_SIDE, crop.height), Bitmap.Config.ARGB_8888)
            .apply { density = crop.density }
        Canvas(padded).apply {
            drawColor(Color.rgb(114, 114, 114))
            drawBitmap(crop, ((padded.width - crop.width) / 2).toFloat(), ((padded.height - crop.height) / 2).toFloat(), null)
        }
        return padded
    }

    override fun close() = recognizer.close()

    companion object {
        const val VERSION = "ML Kit bundled Latin 16.0.1"
        const val INPUT_POLICY = "minimum-32-gray-padding-v1"
        const val MINIMUM_SIDE = 32
    }
}
