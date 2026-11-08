package com.parapo.prototype.scanner

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.parapo.prototype.BuildConfig
import com.parapo.prototype.data.RouteBundleStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Compiled only with parapoIsolatedUiTest; real fixtures are transferred separately. */
class PhoneOcrDiagnosticActivity : Activity() {
    private val reader = SignTextReader()
    private val bitmaps = mutableListOf<Bitmap>()
    private lateinit var content: LinearLayout
    private val root by lazy { File(filesDir, "ocr-fixtures") }
    private val catalog by lazy {
        val fixtureContext = object : android.content.ContextWrapper(this) {
            override fun getFilesDir() = File(root, "catalog").apply { mkdirs() }
        }
        requireNotNull(RouteBundleStore(fixtureContext).load())
    }
    private lateinit var samples: JSONArray
    private lateinit var output: File
    private var stopped = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        setContentView(ScrollView(this).apply { addView(content) })
        val runId = intent.getStringExtra("run_id") ?: System.currentTimeMillis().toString()
        require(runId.matches(Regex("[0-9]+")))
        output = File(root, "ocr-results-$runId.jsonl")
        require(output.createNewFile()) { "Diagnostic output already exists" }
        samples = JSONArray(File(root, "manifest.json").readText())
        line("Local still-crop diagnostic — ${SignTextReader.VERSION}. Results: ${output.name}")
        readSample(0)
    }

    private fun line(text: String) = TextView(this).also { it.text = text; it.textSize = 16f; content.addView(it) }

    private fun readSample(index: Int) {
        if (stopped) return
        if (index == samples.length()) {
            File(root, "complete-${intent.getStringExtra("run_id")}").writeText(output.name)
            line("Diagnostic complete. These still images do not measure live performance.")
            return
        }
        val sample = samples.getJSONObject(index)
        val path = File(root, sample.getString("crop_file")).canonicalFile
        require(path.parentFile == root.canonicalFile && path.length() <= 20_000_000) { "Invalid fixture path/size" }
        val digest = MessageDigest.getInstance("SHA-256").digest(path.readBytes()).joinToString("") { "%02x".format(it) }
        require(digest == sample.getString("crop_sha256")) { "Fixture checksum differs" }
        val bitmap = requireNotNull(BitmapFactory.decodeFile(path.path))
        bitmaps += bitmap
        line("${sample.getString("sample_id")} · ${sample.getJSONArray("box")}")
        content.addView(ImageView(this).apply {
            setImageBitmap(bitmap); adjustViewBounds = true
            contentDescription = "Actual sign crop ${sample.getString("sample_id")}"
        }, LinearLayout.LayoutParams(-1, 220))
        val actual = line("ML Kit: reading…")
        // Expected labels are displayed only; the reader receives bitmap pixels alone.
        line("Expected transcription (${sample.optString("transcription_status")}): ${sample.optString("expected_text").ifEmpty { "UNKNOWN" }}")
        val started = SystemClock.elapsedRealtimeNanos()
        reader.read(bitmap).addOnCompleteListener { task ->
            if (stopped) return@addOnCompleteListener
            val duration = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
            val raw = if (task.isSuccessful) task.result.text else ""
            val error = if (task.isSuccessful) null else (
                task.exception?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: "OCR task failed or was cancelled"
            )
            actual.text = "ML Kit verbatim:\n${error?.let { "ERROR: $it" } ?: raw.ifEmpty { "EMPTY" }}"
            val resolution = ScannerDecisions.routeFromText(raw, catalog)
            val codes = ScannerDecisions.printedCodes(raw)
            line("Parsed code: $codes · Route interpretation: $resolution · OCR ${"%.1f".format(duration)} ms")
            val record = JSONObject(sample.toString()).put("raw_text", raw)
                .put("input_width", bitmap.width).put("input_height", bitmap.height)
                .put("reader_input_policy", SignTextReader.INPUT_POLICY)
                .put("recognition_input_width", maxOf(SignTextReader.MINIMUM_SIDE, bitmap.width))
                .put("recognition_input_height", maxOf(SignTextReader.MINIMUM_SIDE, bitmap.height))
                .put("text_block_count", if (task.isSuccessful) task.result.textBlocks.size else 0)
                .put("text_line_count", if (task.isSuccessful) task.result.textBlocks.sumOf { it.lines.size } else 0)
                .put("error", error ?: JSONObject.NULL).put("ocr_duration_ms", duration)
                .put("printed_codes", JSONArray(codes)).put("normalized_codes", JSONArray(codes.map(ScannerDecisions::normalizedCode)))
                .put("route_resolution", resolution.toString()).put("ocr_version", SignTextReader.VERSION)
                .put("app_version", BuildConfig.VERSION_NAME).put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
                .put("android_fingerprint", Build.FINGERPRINT).put("android_api", Build.VERSION.SDK_INT)
                .put("evidence_kind", "still_crop_phone")
                .put("measurement_boundary", "reader call to completion; includes minimum-size padding and ML Kit; excludes decode/display")
            output.appendText("$record\n")
            readSample(index + 1)
        }
    }

    override fun onDestroy() {
        stopped = true
        reader.close()
        // Completed UI bitmaps are released by the destroyed view tree; no live camera data is saved.
        bitmaps.clear()
        super.onDestroy()
    }
}
