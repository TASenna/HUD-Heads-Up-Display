package com.taydox.hudtool

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class PerspectiveGridView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode { NONE, GRID, ISOMETRIC, ONE_POINT, TWO_POINT, THREE_POINT, CURVILINEAR }

    var mode = Mode.NONE
        set(value) {
            field = value
            invalidate()
        }

    var gridColor = 0xAA00C0BF.toInt()
        set(value) {
            field = value
            linePaint.color = value
            invalidate()
        }

    var density = 15
        set(value) {
            field = value.coerceIn(5, 40)
            invalidate()
        }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = gridColor
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xCCFFFFFF.toInt()
    }

    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xFF00C0BF.toInt()
    }

    // Interactive vanishing points (normalized 0..1)
    var vp1 = PointF(0.5f, 0.5f)
        set(value) { field = value; invalidate() }
    var vp2 = PointF(0.1f, 0.5f)
        set(value) { field = value; invalidate() }
    var vp3 = PointF(0.9f, 0.5f)
        set(value) { field = value; invalidate() }
    var vpVertical = PointF(0.5f, 0.9f)
        set(value) { field = value; invalidate() }

    private var activeHandleIndex = -1 
    private val handleRadius = 30f

    override fun onDraw(canvas: Canvas) {
        if (mode == Mode.NONE) return

        val w = width.toFloat()
        val h = height.toFloat()

        when (mode) {
            Mode.GRID -> drawCartesianGrid(canvas, w, h)
            Mode.ISOMETRIC -> drawIsometricGrid(canvas, w, h)
            Mode.ONE_POINT -> drawOnePointGrid(canvas, w, h)
            Mode.TWO_POINT -> drawTwoPointGrid(canvas, w, h)
            Mode.THREE_POINT -> drawThreePointGrid(canvas, w, h)
            Mode.CURVILINEAR -> drawCurvilinearGrid(canvas, w, h)
            else -> {}
        }

        // Draw draggable handles for the artist
        if (!isLocked) drawHandles(canvas, w, h)
    }

    var isLocked = false
        set(value) { field = value; invalidate() }

    private fun drawCartesianGrid(canvas: Canvas, w: Float, h: Float) {
        val stepX = w / density
        val stepY = h / density
        for (i in 0..density) {
            canvas.drawLine(i * stepX, 0f, i * stepX, h, linePaint)
            canvas.drawLine(0f, i * stepY, w, i * stepY, linePaint)
        }
    }

    private fun drawIsometricGrid(canvas: Canvas, w: Float, h: Float) {
        val step = w / density
        val angle = Math.toRadians(30.0)
        val tan30 = tan(angle).toFloat()

        for (i in -density * 2..density * 2) {
            val offset = i * step
            // 30 degree lines
            canvas.drawLine(0f, offset, w, offset + w * tan30, linePaint)
            // 150 degree lines
            canvas.drawLine(w, offset, 0f, offset + w * tan30, linePaint)
            // Vertical lines
            canvas.drawLine(offset, 0f, offset, h, linePaint)
        }
    }

    private fun drawOnePointGrid(canvas: Canvas, w: Float, h: Float) {
        val p = toScreen(vp1, w, h)
        // Radiating lines
        for (i in 0 until 360 step (360 / density)) {
            val rad = Math.toRadians(i.toDouble())
            canvas.drawLine(p.x, p.y, p.x + cos(rad).toFloat() * w * 2, p.y + sin(rad).toFloat() * h * 2, linePaint)
        }
        // Concentric squares (receding plane)
        for (i in 1..density) {
            val scale = (i.toFloat() / density).pow(2)
            val rw = w * scale
            val rh = h * scale
            canvas.drawRect(p.x - rw, p.y - rh, p.x + rw, p.y + rh, linePaint)
        }
    }

    private fun drawTwoPointGrid(canvas: Canvas, w: Float, h: Float) {
        val pL = toScreen(vp2, w, h)
        val pR = toScreen(vp3, w, h)
        
        // Horizon
        canvas.drawLine(-w, pL.y, w * 2, pL.y, linePaint)

        // Convergence lines
        for (i in -density..density) {
            val angle = (i.toFloat() / density) * 90f
            val rad = Math.toRadians(angle.toDouble())
            val dx = cos(rad).toFloat() * w * 2
            val dy = sin(rad).toFloat() * h * 2
            canvas.drawLine(pL.x, pL.y, pL.x + dx, pL.y + dy, linePaint)
            canvas.drawLine(pR.x, pR.y, pR.x - dx, pR.y + dy, linePaint)
        }
    }

    private fun drawThreePointGrid(canvas: Canvas, w: Float, h: Float) {
        drawTwoPointGrid(canvas, w, h)
        val pV = toScreen(vpVertical, w, h)
        for (i in -density..density) {
            val angle = (i.toFloat() / density) * 45f
            val rad = Math.toRadians((90f + angle).toDouble())
            canvas.drawLine(pV.x, pV.y, pV.x + cos(rad).toFloat() * h * 2, pV.y - sin(rad).toFloat() * h * 2, linePaint)
        }
    }

    private fun drawCurvilinearGrid(canvas: Canvas, w: Float, h: Float) {
        val center = toScreen(vp1, w, h)
        for (i in 1..density) {
            val radius = (w / density) * i
            canvas.drawCircle(center.x, center.y, radius, linePaint)
        }
        for (i in 0 until 360 step (360 / density)) {
            val rad = Math.toRadians(i.toDouble())
            canvas.drawLine(center.x, center.y, center.x + cos(rad).toFloat() * w, center.y + sin(rad).toFloat() * h, linePaint)
        }
    }

    private fun drawHandles(canvas: Canvas, w: Float, h: Float) {
        val points = when (mode) {
            Mode.ONE_POINT, Mode.CURVILINEAR -> listOf(vp1)
            Mode.TWO_POINT -> listOf(vp2, vp3)
            Mode.THREE_POINT -> listOf(vp2, vp3, vpVertical)
            else -> emptyList()
        }
        points.forEach {
            val p = toScreen(it, w, h)
            canvas.drawCircle(p.x, p.y, handleRadius, handlePaint)
            canvas.drawCircle(p.x, p.y, handleRadius, handleStrokePaint)
        }
    }

    private fun toScreen(p: PointF, w: Float, h: Float) = PointF(p.x * w, p.y * h)
    private fun toNorm(x: Float, y: Float) = PointF(x / width, y / height)

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isLocked) return false
        val x = event.x
        val y = event.y
        val w = width.toFloat()
        val h = height.toFloat()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                performClick()
                val points = listOf(vp1, vp2, vp3, vpVertical)
                activeHandleIndex = -1
                for (i in points.indices) {
                    val p = toScreen(points[i], w, h)
                    if (sqrt((x - p.x).pow(2) + (y - p.y).pow(2)) < handleRadius * 2) {
                        activeHandleIndex = i
                        return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeHandleIndex != -1) {
                    val norm = toNorm(x, y)
                    when (activeHandleIndex) {
                        0 -> vp1 = norm
                        1 -> { vp2 = norm; if (mode == Mode.TWO_POINT) vp3.y = norm.y }
                        2 -> { vp3 = norm; if (mode == Mode.TWO_POINT) vp2.y = norm.y }
                        3 -> vpVertical = norm
                    }
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> activeHandleIndex = -1
        }
        return true
    }
}
