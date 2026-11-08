package com.parapo.prototype.scanner

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class DetectorParityTest {
    @Test fun actualArtifactMatchesPreparedTensorAndBitmapBoundaries() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue("Use isolated test package", context.packageName.endsWith(".uitest"))
        val fixture = InstrumentationRegistry.getArguments().getString("parityFixtureZip")
        assumeTrue("Transfer android-fixtures.zip and supply parityFixtureZip", fixture != null)
        require(fixture!!.matches(Regex("/data/local/tmp/[a-zA-Z0-9._-]+\\.zip")))
        val root = File(context.filesDir, "parity-${System.currentTimeMillis()}").apply { mkdirs() }.canonicalFile
        instrumentation.uiAutomation.executeShellCommand("cat $fixture").use { descriptor ->
            ZipInputStream(android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)).use { zip ->
                var total = 0L
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val destination = File(root, entry.name).canonicalFile
                    require(destination.parentFile == root && !entry.isDirectory)
                    destination.outputStream().use { out ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= 80_000_000)
                            out.write(buffer, 0, count)
                        }
                    }
                }
            }
        }
        fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        fun floats(file: File): FloatArray {
            require(file.length() % 4 == 0L && file.length() < 20_000_000)
            val buffer = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            return FloatArray(buffer.remaining()).also(buffer::get)
        }
        fun save(name: String, values: FloatArray) {
            val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            values.forEach(buffer::putFloat)
            File(root, name).writeBytes(buffer.array())
        }
        val manifest = JSONObject(File(root, "manifest.json").readText())
        val model = File(root, "model.onnx")
        require(hash(model) == manifest.getString("model_sha256"))
        val pipeline = ScannerPipeline(context, object : ScannerPipeline.Listener {
            override fun onModelReady(modelHash: String) = Unit
            override fun onModelUnavailable(reason: String) = Unit
            override fun onDetections(sessionId: Long, contextKey: String, boxes: List<SignBox>, selectedBox: SignBox?, targetId: String?, captureElapsedMs: Long, frameWidth: Int, frameHeight: Int) = Unit
            override fun onText(sessionId: Long, contextKey: String, targetId: String, captureElapsedMs: Long, text: String) = Unit
            override fun onFailure(sessionId: Long, contextKey: String, targetId: String?) = Unit
        })
        val failures = mutableListOf<String>()
        try {
            OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(2)
                OrtEnvironment.getEnvironment().createSession(model.path, options).use { session ->
                    val samples = manifest.getJSONArray("samples")
                    for (index in 0 until samples.length()) {
                        val sample = samples.getJSONObject(index)
                        val id = sample.getString("sample_id")
                        fun checked(key: String): File {
                            val file = File(root, sample.getString(key)).canonicalFile
                            require(file.parentFile == root && hash(file) == sample.getString("${key}_sha256"))
                            return file
                        }
                        val input = floats(checked("input"))
                        require(input.size == 3 * 320 * 320 && input.all { it.isFinite() && it in 0f..1f })
                        val expected = floats(checked("output"))
                        val bitmap = requireNotNull(BitmapFactory.decodeFile(checked("image").path))
                        val phoneInput = try { pipeline.floatInput(bitmap) } finally { bitmap.recycle() }
                        save("$id-android-input.f32", phoneInput)
                        val expectedBoxes = sample.getJSONArray("expected_detections")
                        for ((boundary, tensorInput) in listOf("identical_tensor" to input, "android_bitmap" to phoneInput)) {
                            OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), FloatBuffer.wrap(tensorInput), longArrayOf(1, 3, 320, 320)).use { tensor ->
                                session.run(mapOf(session.inputNames.first() to tensor)).use { output ->
                                    val value = output[0].value
                                    val channels = ((value as Array<*>)[0] as Array<*>).map { it as FloatArray }
                                    val raw = channels.flatMap { it.toList() }.toFloatArray()
                                    save("$id-$boundary-android-output.f32", raw)
                                    val boxes = decodeYoloOutput(value, sample.getInt("width"), sample.getInt("height"))
                                    val unmatched = boxes.toMutableList()
                                    var matches = boxes.size == expectedBoxes.length()
                                    if (boundary == "android_bitmap" && !input.contentEquals(phoneInput)) matches = false
                                    for (j in 0 until expectedBoxes.length()) {
                                        val expectedBox = expectedBoxes.getJSONObject(j)
                                        val coords = expectedBox.getJSONArray("box")
                                        val match = unmatched.indexOfFirst { detection ->
                                            val actual = listOf(detection.box.left, detection.box.top, detection.box.right, detection.box.bottom)
                                            actual.indices.all { kotlin.math.abs(actual[it] - coords.getDouble(it)) <= 1.5 } &&
                                                kotlin.math.abs(detection.score - expectedBox.getDouble("score")) <= .02
                                        }
                                        if (match < 0) matches = false else unmatched.removeAt(match)
                                    }
                                    val rawDelta = if (raw.size == expected.size) raw.indices.maxOf { kotlin.math.abs(raw[it] - expected[it]) } else Float.POSITIVE_INFINITY
                                    if (boundary == "identical_tensor" && (raw.size != expected.size || channels.size != 5 ||
                                            channels[4].indices.any { kotlin.math.abs(channels[4][it] - expected[4 * channels[4].size + it]) > .02f } ||
                                            rawDelta > 1.5f * minOf(320f / sample.getInt("width"), 320f / sample.getInt("height")))) matches = false
                                    val record = JSONObject().put("sample_id", id).put("boundary", boundary)
                                        .put("model_sha256", manifest.getString("model_sha256"))
                                        .put("device", android.os.Build.FINGERPRINT).put("runtime", ScannerPipeline.RUNTIME_VERSION)
                                        .put("max_input_delta", input.indices.maxOf { kotlin.math.abs(input[it] - phoneInput[it]) })
                                        .put("max_raw_delta", if (rawDelta.isFinite()) rawDelta else JSONObject.NULL)
                                        .put("passed", matches).put("detections", JSONArray(boxes.map { d -> JSONObject()
                                            .put("box", JSONArray(listOf(d.box.left, d.box.top, d.box.right, d.box.bottom))).put("score", d.score.toDouble()) }))
                                    File(root, "android-results.jsonl").appendText("$record\n")
                                    if (!matches) failures += "$id/$boundary"
                                }
                            }
                        }
                    }
                }
            }
        } finally { pipeline.close() }
        assertTrue("Parity failed: $failures; retained dumps: $root", failures.isEmpty())
    }
}
