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
    @Test fun smallCropsMeetMlKitMinimumWithoutResizingOriginalPixels() {
        val reader = SignTextReader()
        val small = Bitmap.createBitmap(43, 23, Bitmap.Config.ARGB_8888).apply {
            density = 320
            eraseColor(Color.rgb(10, 20, 30))
        }
        val large = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        try {
            val input = reader.minimumSizeInput(small)
            try {
                org.junit.Assert.assertEquals(43, input.width)
                org.junit.Assert.assertEquals(32, input.height)
                org.junit.Assert.assertEquals(Color.rgb(114, 114, 114), input.getPixel(0, 0))
                org.junit.Assert.assertEquals(small.getPixel(0, 0), input.getPixel(0, 4))
                org.junit.Assert.assertEquals(small.getPixel(42, 22), input.getPixel(42, 26))
                org.junit.Assert.assertSame(large, reader.minimumSizeInput(large))
            } finally { input.recycle() }
            assertNotNull(com.google.android.gms.tasks.Tasks.await(reader.read(small), 30, TimeUnit.SECONDS))
            assertTrue(!small.isRecycled)
            org.junit.Assert.assertEquals(23, small.height)
        } finally { reader.close(); small.recycle(); large.recycle() }
    }
    @Test fun sharedCatalogInterpretationCases() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fixtureContext = object : android.content.ContextWrapper(context) {
            override fun getFilesDir() = java.io.File(context.cacheDir, "shared-route-cases-${System.nanoTime()}").apply { mkdirs() }
        }
        val catalog = requireNotNull(com.parapo.prototype.data.RouteBundleStore(fixtureContext).load())
        val fixtures = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
        val cases = org.json.JSONArray(fixtures.open("route-text-cases.json").bufferedReader().use { it.readText() })
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val result = ScannerDecisions.routeFromText(case.getString("text"), catalog)
            val status = when (result) {
                is RouteTextResolution.Found -> "found"
                is RouteTextResolution.Ambiguous -> "ambiguous"
                RouteTextResolution.Unsupported -> "unsupported"
                RouteTextResolution.Unreadable -> "unreadable"
            }
            org.junit.Assert.assertEquals(case.toString(), case.getString("status"), status)
            org.junit.Assert.assertEquals(case.optString("route_id", ""), (result as? RouteTextResolution.Found)?.routeId.orEmpty())
        }
    }
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
            override fun onFailure(sessionId: Long, contextKey: String, targetId: String?) { failure.set("Inference failed") }
        }, enableDetector = true)
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
