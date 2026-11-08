package com.parapo.prototype.scanner

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScannerInferenceTest {
    @Test fun bundledDetectorLoadsAndRunsOnDevice() {
        val ready = CountDownLatch(1)
        val failure = AtomicReference<String?>(null)
        val pipeline = ScannerPipeline(ApplicationProvider.getApplicationContext(), object : ScannerPipeline.Listener {
            override fun onModelReady(modelHash: String) = ready.countDown()
            override fun onModelUnavailable(reason: String) {
                failure.set(reason)
                ready.countDown()
            }
            override fun onDetections(sessionId: Long, contextKey: String, boxes: List<SignBox>, selectedBox: SignBox?, targetId: String?, captureElapsedMs: Long, frameWidth: Int, frameHeight: Int) = Unit
            override fun onText(sessionId: Long, contextKey: String, targetId: String, captureElapsedMs: Long, text: String) = Unit
            override fun onFailure() { failure.set("Inference failed") }
        })
        try {
            assertTrue("Timed out loading bundled ONNX detector", ready.await(30, TimeUnit.SECONDS))
            assertNull(failure.get())
            val bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
            val detections = pipeline.runDetectorForEvaluation(bitmap)
            bitmap.recycle()
            assertNotNull(detections)
            assertTrue(detections.size <= 300)
        } finally {
            pipeline.close()
        }
    }
}
