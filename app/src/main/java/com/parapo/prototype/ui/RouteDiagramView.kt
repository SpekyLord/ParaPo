package com.parapo.prototype.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.parapo.prototype.data.GeometryPoint
import com.parapo.prototype.R

class RouteDiagramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    var points: List<GeometryPoint> = emptyList()
        set(value) { field = value; invalidate() }
    var routes: List<List<GeometryPoint>> = emptyList()
        set(value) { field = value; invalidate() }
    var boardingSequence: Int? = null
        set(value) { field = value; invalidate() }
    var alightingSequence: Int? = null
        set(value) { field = value; invalidate() }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.blue); strokeWidth = dp(4f); style = Paint.Style.STROKE }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val paths = routes.ifEmpty { listOf(points) }
        val allPoints = paths.filter { it.size >= 2 }.flatten()
        if (allPoints.isEmpty()) return
        val pad = dp(24f)
        val minX = allPoints.minOf { it.longitude }
        val maxX = allPoints.maxOf { it.longitude }
        val minY = allPoints.minOf { it.latitude }
        val maxY = allPoints.maxOf { it.latitude }
        val spanX = (maxX - minX).takeIf { it > 0.0 } ?: 1.0
        val spanY = (maxY - minY).takeIf { it > 0.0 } ?: 1.0
        val scale = minOf((width - 2 * pad) / spanX.toFloat(), (height - 2 * pad) / spanY.toFloat())
        val usedWidth = spanX.toFloat() * scale
        val usedHeight = spanY.toFloat() * scale
        val left = (width - usedWidth) / 2f
        val top = (height - usedHeight) / 2f
        fun x(point: GeometryPoint) = left + ((point.longitude - minX) / spanX).toFloat() * usedWidth
        fun y(point: GeometryPoint) = height - top - ((point.latitude - minY) / spanY).toFloat() * usedHeight
        paths.forEachIndexed { index, route ->
            if (route.size < 2) return@forEachIndexed
            val path = Path().apply {
                moveTo(x(route.first()), y(route.first()))
                route.drop(1).forEach { lineTo(x(it), y(it)) }
            }
            linePaint.color = context.getColor(if (index % 2 == 0) R.color.blue else R.color.outcome_red)
            canvas.drawPath(path, linePaint)
        }
        if (routes.isNotEmpty()) return
        if (boardingSequence == null && alightingSequence == null) {
            dotPaint.color = context.getColor(R.color.blue)
            canvas.drawCircle(x(points.first()), y(points.first()), dp(7f), dotPaint)
            canvas.drawCircle(x(points.last()), y(points.last()), dp(7f), dotPaint)
        } else {
            boardingSequence?.let { sequence -> points.firstOrNull { it.stopOccurrenceSequence == sequence } }?.let {
                dotPaint.color = context.getColor(R.color.marigold)
                canvas.drawCircle(x(it), y(it), dp(7f), dotPaint)
            }
            alightingSequence?.let { sequence -> points.firstOrNull { it.stopOccurrenceSequence == sequence } }?.let {
                dotPaint.color = context.getColor(R.color.outcome_green)
                canvas.drawCircle(x(it), y(it), dp(7f), dotPaint)
            }
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
