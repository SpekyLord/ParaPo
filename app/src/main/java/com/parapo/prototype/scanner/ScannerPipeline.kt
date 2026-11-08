package com.parapo.prototype.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ScannerPipeline(
    context: Context,
    private val listener: Listener,
    private val enableDetector: Boolean = false,
) : AutoCloseable {
    interface Listener {
        fun onModelReady(modelHash: String)
        fun onModelUnavailable(reason: String)
        fun onDetections(sessionId: Long, contextKey: String, boxes: List<SignBox>, selectedBox: SignBox?, targetId: String?, captureElapsedMs: Long, frameWidth: Int, frameHeight: Int)
        fun onText(sessionId: Long, contextKey: String, targetId: String, captureElapsedMs: Long, text: String)
        fun onFailure(sessionId: Long, contextKey: String, targetId: String?)
        fun onSnapshot(sessionId: Long, contextKey: String, targetId: String, crop: Bitmap) { crop.recycle() }
    }

    private val appContext = context.applicationContext
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val recognizer = SignTextReader()
    private val target = SingleSignTarget()
    private val busy = AtomicBoolean(false)
    @Volatile private var closed = false
    @Volatile private var loading = true
    @Volatile private var modelHash = ""
    @Volatile private var session: OrtSession? = null
    @Volatile private var inputName = ""
    @Volatile private var outputName = ""
    @Volatile private var activeSessionId = 0L
    @Volatile private var activeContextKey = ""
    @Volatile private var snapshotRequest: SignBox? = null
    @Volatile private var snapshotFrozen = false

    init {
        executor.execute {
            if (!enableDetector) {
                loading = false
                return@execute
            }
            try {
                val model = appContext.assets.open(MODEL_ASSET).use { it.readBytes() }
                modelHash = MessageDigest.getInstance("SHA-256").digest(model)
                    .joinToString("") { "%02x".format(it) }
                val loaded = OrtSession.SessionOptions().use { options ->
                    options.setIntraOpNumThreads(2)
                    OrtEnvironment.getEnvironment().createSession(model, options)
                }
                inputName = loaded.inputNames.firstOrNull() ?: error("Missing model input")
                outputName = loaded.outputNames.firstOrNull() ?: error("Missing model output")
                session = loaded
                loading = false
                mainHandler.post { if (!closed) listener.onModelReady(modelHash) }
            } catch (_: Exception) {
                loading = false
                mainHandler.post { if (!closed) listener.onModelUnavailable("Detector model unavailable or invalid.") }
            }
        }
    }

    fun analyzer(): ImageAnalysis.Analyzer = ImageAnalysis.Analyzer(::analyze)

    fun setAnalyzer(analysis: ImageAnalysis) = analysis.setAnalyzer(executor, analyzer())

    @Synchronized
    fun updateContext(sessionId: Long, contextKey: String) {
        activeSessionId = sessionId
        activeContextKey = contextKey
    }

    @Synchronized
    fun selectTarget(box: SignBox): String = target.select(box)

    @Synchronized
    fun selectNearestCenter(boxes: List<SignBox>, width: Int, height: Int): String? {
        if (boxes.isEmpty()) return null
        val centerX = width / 2f
        val centerY = height / 2f
        val ranked = boxes.sortedBy { box ->
            val dx = (box.left + box.right) / 2f - centerX
            val dy = (box.top + box.bottom) / 2f - centerY
            dx * dx + dy * dy
        }
        val first = distanceSquared(ranked[0], centerX, centerY)
        val centeredLimit = min(width, height) * 0.15f
        if (first > centeredLimit * centeredLimit) return null
        if (ranked.size > 1) {
            val second = distanceSquared(ranked[1], centerX, centerY)
            if (second - first < max(width, height) * 16f) return null
        }
        return target.select(ranked.first())
    }

    @Synchronized
    fun clearTarget() = target.clear()

    @Synchronized
    fun requestAlignedRead(region: SignBox): String {
        require(region.left >= 0f && region.top >= 0f && region.right <= 1f && region.bottom <= 1f && region.area > 0f)
        check(!closed && !snapshotFrozen && snapshotRequest == null)
        val id = target.select(region)
        snapshotRequest = region
        return id
    }

    @Synchronized
    fun resumeAlignedPreview() {
        snapshotRequest = null
        snapshotFrozen = false
        target.clear()
    }

    @Synchronized
    fun currentTargetBox(): SignBox? = target.selectedBox

    private fun analyze(image: ImageProxy) {
        if (closed || loading || snapshotFrozen || (!enableDetector && snapshotRequest == null) ||
            (enableDetector && session == null) || !busy.compareAndSet(false, true)) {
            image.close()
            return
        }
        val captureElapsedMs = SystemClock.elapsedRealtime()
        val callbackSession: Long
        val callbackContext: String
        var callbackTarget: String?
        val region: SignBox?
        synchronized(this) {
            callbackSession = activeSessionId
            callbackContext = activeContextKey
            callbackTarget = target.selectedId
            region = snapshotRequest
            snapshotRequest = null
            if (region != null) snapshotFrozen = true
        }
        if (!enableDetector && region == null) {
            image.close()
            busy.set(false)
            return
        }
        var frame: Bitmap? = null
        var imageClosed = false
        var selectedCrop: Bitmap? = null
        try {
            frame = uprightBitmap(image)
            val detections = if (region == null) infer(frame) else emptyList()
            val boxes = detections.map(SignDetection::box)
            val frameWidth = frame.width
            val frameHeight = frame.height
            val (selectedBox, targetId) = synchronized(this) {
                if (region != null) {
                    SignBox(region.left * frameWidth, region.top * frameHeight, region.right * frameWidth, region.bottom * frameHeight) to callbackTarget
                } else if (target.selectedId != callbackTarget) null to callbackTarget
                else target.selectedBox?.let { target.associate(boxes) to target.selectedId } ?: (null to null)
            }
            callbackTarget = targetId
            if (region == null) mainHandler.post {
                if (!closed && synchronized(this) { target.selectedId } == targetId) {
                    listener.onDetections(callbackSession, callbackContext, boxes, selectedBox, targetId, captureElapsedMs, frameWidth, frameHeight)
                }
            }
            selectedCrop = selectedBox?.let { crop(frame, it) }
            frame.recycle()
            frame = null
            image.close()
            imageClosed = true
            if (selectedCrop == null || targetId == null) {
                selectedCrop?.recycle()
                busy.set(false)
                return
            }
            val cropForOcr = requireNotNull(selectedCrop)
            selectedCrop = null
            if (region != null) {
                val displayCrop = cropForOcr.copy(Bitmap.Config.ARGB_8888, false)
                mainHandler.post {
                    if (!closed) listener.onSnapshot(callbackSession, callbackContext, targetId, displayCrop)
                    else displayCrop.recycle()
                }
            }
            try {
                recognizer.read(cropForOcr)
                    .addOnSuccessListener { result ->
                        if (!closed) mainHandler.post {
                            if (!closed) listener.onText(callbackSession, callbackContext, targetId, captureElapsedMs, result.text)
                        }
                    }
                    .addOnFailureListener {
                        if (!closed) mainHandler.post { if (!closed) listener.onFailure(callbackSession, callbackContext, targetId) }
                    }
                    .addOnCompleteListener {
                        cropForOcr.recycle()
                        busy.set(false)
                    }
            } catch (error: Exception) {
                cropForOcr.recycle()
                throw error
            }
        } catch (_: Exception) {
            frame?.recycle()
            selectedCrop?.recycle()
            if (!imageClosed) image.close()
            busy.set(false)
            mainHandler.post { if (!closed) listener.onFailure(callbackSession, callbackContext, callbackTarget) }
        }
    }

    private fun infer(frame: Bitmap): List<SignDetection> {
        val modelSession = session ?: return emptyList()
        val input = floatInput(frame)
        val tensor = OnnxTensor.createTensor(
            OrtEnvironment.getEnvironment(), FloatBuffer.wrap(input), longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong()),
        )
        return tensor.use {
            modelSession.run(mapOf(inputName to tensor)).use { result ->
                val value = result.get(outputName).orElseThrow { IllegalStateException("Missing model output") }.getValue()
                decodeYoloOutput(value, frame.width, frame.height)
            }
        }
    }

    internal fun runDetectorForEvaluation(frame: Bitmap): List<SignDetection> = infer(frame)

    internal fun floatInput(frame: Bitmap): FloatArray {
        val scale = min(INPUT_SIZE.toDouble() / frame.width, INPUT_SIZE.toDouble() / frame.height)
        val resizedWidth = (frame.width * scale).roundToInt()
        val resizedHeight = (frame.height * scale).roundToInt()
        val left = (INPUT_SIZE - resizedWidth) / 2
        val top = (INPUT_SIZE - resizedHeight) / 2
        val source = IntArray(frame.width * frame.height)
        frame.getPixels(source, 0, frame.width, 0, 0, frame.width, frame.height)
        val plane = INPUT_SIZE * INPUT_SIZE
        val tensor = FloatArray(3 * plane) { 114f / 255f }
        // Explicit pixel-center bilinear sampling is shared with the desktop probe; Skia/OpenCV differ.
        for (row in 0 until resizedHeight) {
            val y = (row + 0.5) * frame.height / resizedHeight - 0.5
            val y0 = kotlin.math.floor(y).toInt()
            val dy = y - y0
            for (column in 0 until resizedWidth) {
                val x = (column + 0.5) * frame.width / resizedWidth - 0.5
                val x0 = kotlin.math.floor(x).toInt()
                val dx = x - x0
                val a = source[y0.coerceIn(0, frame.height - 1) * frame.width + x0.coerceIn(0, frame.width - 1)]
                val b = source[y0.coerceIn(0, frame.height - 1) * frame.width + (x0 + 1).coerceIn(0, frame.width - 1)]
                val c = source[(y0 + 1).coerceIn(0, frame.height - 1) * frame.width + x0.coerceIn(0, frame.width - 1)]
                val d = source[(y0 + 1).coerceIn(0, frame.height - 1) * frame.width + (x0 + 1).coerceIn(0, frame.width - 1)]
                for (channel in 0..2) {
                    val shift = 16 - channel * 8
                    fun component(pixel: Int) = ((pixel shr shift) and 255).toDouble()
                    val upper = component(a) * (1 - dx) + component(b) * dx
                    val lower = component(c) * (1 - dx) + component(d) * dx
                    val value = kotlin.math.floor(upper * (1 - dy) + lower * dy + 0.5).toInt().coerceIn(0, 255)
                    tensor[channel * plane + (row + top) * INPUT_SIZE + column + left] = value / 255f
                }
            }
        }
        return tensor
    }

    private fun uprightBitmap(image: ImageProxy): Bitmap {
        val width = image.width
        val height = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val y = yPlane.buffer.duplicate()
        val u = uPlane.buffer.duplicate()
        val v = vPlane.buffer.duplicate()
        val yStart = y.position()
        val uStart = u.position()
        val vStart = v.position()
        val pixels = IntArray(width * height)
        for (row in 0 until height) {
            val yRow = yStart + row * yPlane.rowStride
            val uvRow = uStart + (row / 2) * uPlane.rowStride
            for (column in 0 until width) {
                val luminance = (y.get(yRow + column * yPlane.pixelStride).toInt() and 0xff).toFloat()
                val chromaIndex = uvRow + (column / 2) * uPlane.pixelStride
                val cb = (u.get(chromaIndex).toInt() and 0xff) - 128f
                val cr = (v.get(vStart + (row / 2) * vPlane.rowStride + (column / 2) * vPlane.pixelStride).toInt() and 0xff) - 128f
                val red = (luminance + 1.402f * cr).toInt().coerceIn(0, 255)
                val green = (luminance - 0.344136f * cb - 0.714136f * cr).toInt().coerceIn(0, 255)
                val blue = (luminance + 1.772f * cb).toInt().coerceIn(0, 255)
                pixels[row * width + column] = Color.rgb(red, green, blue)
            }
        }
        val full = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
        val rect = image.cropRect
        val cropped = try {
            Bitmap.createBitmap(full, rect.left, rect.top, rect.width(), rect.height())
        } catch (error: Exception) {
            full.recycle()
            throw error
        }
        if (cropped !== full) full.recycle()
        val rotation = image.imageInfo.rotationDegrees
        if (rotation == 0) return cropped
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return try {
            Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
        } finally {
            cropped.recycle()
        }
    }

    private fun crop(frame: Bitmap, box: SignBox): Bitmap? {
        val clipped = clipBox(box, frame.width.toFloat(), frame.height.toFloat())
        val left = clipped.left.toInt().coerceIn(0, frame.width - 1)
        val top = clipped.top.toInt().coerceIn(0, frame.height - 1)
        val right = clipped.right.toInt().coerceIn(left + 1, frame.width)
        val bottom = clipped.bottom.toInt().coerceIn(top + 1, frame.height)
        val crop = Bitmap.createBitmap(frame, left, top, right - left, bottom - top)
        return try {
            crop.copy(Bitmap.Config.ARGB_8888, false)
        } finally {
            if (crop !== frame) crop.recycle()
        }
    }

    private fun distanceSquared(box: SignBox, centerX: Float, centerY: Float): Float {
        val dx = (box.left + box.right) / 2f - centerX
        val dy = (box.top + box.bottom) / 2f - centerY
        return dx * dx + dy * dy
    }

    override fun close() {
        if (closed) return
        closed = true
        recognizer.close()
        executor.execute {
            session?.close()
            session = null
            executor.shutdown()
        }
        synchronized(this) { target.clear() }
    }

    companion object {
        const val MODEL_ASSET = "route-sign-yolov8n.onnx"
        const val MODEL_VERSION = "YOLOv8n M3 v10; experimental development model"
        const val RUNTIME_VERSION = "ONNX Runtime Android 1.31.1"
        const val OCR_VERSION = SignTextReader.VERSION
        private const val INPUT_SIZE = 320
    }
}
