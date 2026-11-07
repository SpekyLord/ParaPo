package com.parapo.prototype.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import com.parapo.prototype.scanner.SignBox
import com.parapo.prototype.scanner.intersectionOverUnion
import com.parapo.prototype.scanner.toPreviewBox
import kotlin.math.max

class ScannerOverlayView(context: Context) : View(context) {
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(14)
        style = Paint.Style.FILL
    }
    private val reticlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(2)
    }
    private var boxes: List<SignBox> = emptyList()
    private var selected: SignBox? = null
    private var frameWidth = 0
    private var frameHeight = 0
    var onSelectBox: ((SignBox) -> Unit)? = null

    init {
        isClickable = true
        contentDescription = "Sign detections. Tap one outline to select a sign."
    }

    fun update(boxes: List<SignBox>, selected: SignBox?, frameWidth: Int, frameHeight: Int) {
        this.boxes = boxes
        this.selected = selected
        this.frameWidth = frameWidth
        this.frameHeight = frameHeight
        contentDescription = when {
            selected != null -> "One sign selected. Use the Select centered sign button to change it."
            boxes.isEmpty() -> "No sign detected. Aim at one stationary sign."
            else -> "${boxes.size} sign candidates. Tap one outline or select the centered sign."
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        canvas.drawCircle(centerX, centerY, dp(42), reticlePaint)
        canvas.drawLine(centerX - dp(54), centerY, centerX - dp(30), centerY, reticlePaint)
        canvas.drawLine(centerX + dp(30), centerY, centerX + dp(54), centerY, reticlePaint)
        canvas.drawLine(centerX, centerY - dp(54), centerX, centerY - dp(30), reticlePaint)
        canvas.drawLine(centerX, centerY + dp(30), centerX, centerY + dp(54), reticlePaint)

        boxes.forEachIndexed { index, box ->
            val mapped = toPreviewBox(box, frameWidth, frameHeight, width, height)
            val isSelected = selected?.let { intersectionOverUnion(it, box) > 0.9f } == true
            boxPaint.color = if (isSelected) Color.rgb(0, 180, 70) else Color.rgb(255, 193, 7)
            canvas.drawRect(mapped.left, mapped.top, mapped.right, mapped.bottom, boxPaint)
            if (isSelected) canvas.drawText("Selected sign", mapped.left, max(dp(18), mapped.top - dp(6)), labelPaint)
            else canvas.drawText("Sign ${index + 1}", mapped.left, max(dp(18), mapped.top - dp(6)), labelPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val framePoint = viewToFrame(event.x, event.y) ?: return true
        val hitSlop = dp(24) * max(frameWidth.toFloat() / max(width, 1), frameHeight.toFloat() / max(height, 1))
        val candidates = boxes.filter { box ->
            framePoint.first in (box.left - hitSlop)..(box.right + hitSlop) &&
                framePoint.second in (box.top - hitSlop)..(box.bottom + hitSlop)
        }
        val ranked = candidates.sortedBy(SignBox::area)
        val first = ranked.firstOrNull()
        val second = ranked.getOrNull(1)
        val similarlySizedOverlap = first != null && second != null &&
            intersectionOverUnion(first, second) > 0.5f &&
            first.area / second.area.coerceAtLeast(1f) > 0.8f
        if (!similarlySizedOverlap) first?.let { onSelectBox?.invoke(it) }
        performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun viewToFrame(x: Float, y: Float): Pair<Float, Float>? {
        if (frameWidth <= 0 || frameHeight <= 0 || width <= 0 || height <= 0) return null
        val scale = max(width.toFloat() / frameWidth, height.toFloat() / frameHeight)
        val offsetX = (width - frameWidth * scale) / 2f
        val offsetY = (height - frameHeight * scale) / 2f
        return ((x - offsetX) / scale) to ((y - offsetY) / scale)
    }

    private fun dp(value: Int): Float = value * resources.displayMetrics.density
}
