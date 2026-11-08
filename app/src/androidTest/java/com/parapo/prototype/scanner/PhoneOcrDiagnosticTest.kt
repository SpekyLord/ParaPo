package com.parapo.prototype.scanner

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipInputStream

class PhoneOcrDiagnosticTest {
    @Test fun readLocalReviewedCropsWithoutLoadingDetector() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue("Use -PparapoIsolatedUiTest", context.packageName.endsWith(".uitest"))
        val fixture = InstrumentationRegistry.getArguments().getString("ocrFixtureZip")
        assumeTrue("Transfer fixtures.zip and supply ocrFixtureZip; real phone samples are optional", fixture != null)
        require(fixture!!.matches(Regex("/data/local/tmp/[a-zA-Z0-9._-]+\\.zip")))
        val root = File(context.filesDir, "ocr-fixtures").apply { mkdirs() }.canonicalFile
        instrumentation.uiAutomation.executeShellCommand("cat $fixture").use { descriptor ->
            ZipInputStream(android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)).use { zip ->
                var total = 0L
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val file = File(root, entry.name).canonicalFile
                    require(file.parentFile == root && !entry.isDirectory && !file.name.startsWith("ocr-results"))
                    file.outputStream().use { out ->
                        val bytes = ByteArray(8192)
                        while (true) {
                            val count = zip.read(bytes)
                            if (count < 0) break
                            total += count
                            require(total <= 20_000_000) { "Fixture archive is too large" }
                            out.write(bytes, 0, count)
                        }
                    }
                }
            }
        }
        val run = System.currentTimeMillis().toString()
        val expected = org.json.JSONArray(File(root, "manifest.json").readText()).length()
        require(expected in 1..64) { "Keep the local OCR probe bounded" }
        val intent = Intent().setClassName(context.packageName, "com.parapo.prototype.scanner.PhoneOcrDiagnosticActivity")
            .putExtra("run_id", run)
        ActivityScenario.launch<android.app.Activity>(intent).use {
            val complete = File(root, "complete-$run")
            val deadline = System.currentTimeMillis() + 120_000
            while (!complete.exists() && System.currentTimeMillis() < deadline) Thread.sleep(200)
            assertTrue("Diagnostic timed out", complete.exists())
            val records = File(root, complete.readText()).readLines().map { org.json.JSONObject(it) }
            assertEquals(expected, records.size)
            assertTrue(records.all { it.has("raw_text") && it.has("error") && it.has("ocr_duration_ms") && it.has("text_block_count") })
        }
    }
}
