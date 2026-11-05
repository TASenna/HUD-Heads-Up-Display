package com.taydox.hudtool

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class BorderTimerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress: Float = 1f // 0.0 to 1.0
    private val strokeWidth = 3f * resources.displayMetrics.density
    
    private val rect = RectF()
    private val path = Path()

    private val cyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00C0BF.toInt()
        style = Paint.Style.STROKE
        this.strokeWidth = this@BorderTimerView.strokeWidth
    }

    private val grayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3A3A3A.toInt()
        style = Paint.Style.STROKE
        this.strokeWidth = this@BorderTimerView.strokeWidth
    }

    fun setProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        rect.set(strokeWidth / 2, strokeWidth / 2, w - strokeWidth / 2, h - strokeWidth / 2)
        
        // Draw background gray border
        canvas.drawRect(rect, grayPaint)

        // Draw Cyan progress border (Anticlockwise)
        val p = 2 * (rect.width() + rect.height())
        val targetLen = progress * p
        
        path.reset()
        path.moveTo(rect.right, rect.top)
        
        var currentLen = 0f
        
        // 1. Top Edge (Right to Left)
        val topLen = rect.width()
        if (currentLen + topLen <= targetLen) {
            path.lineTo(rect.left, rect.top)
            currentLen += topLen
        } else {
            path.lineTo(rect.right - (targetLen - currentLen), rect.top)
            currentLen = targetLen
        }

        // 2. Left Edge (Top to Bottom)
        if (currentLen < targetLen) {
            val leftLen = rect.height()
            if (currentLen + leftLen <= targetLen) {
                path.lineTo(rect.left, rect.bottom)
                currentLen += leftLen
            } else {
                path.lineTo(rect.left, rect.top + (targetLen - currentLen))
                currentLen = targetLen
            }
        }

        // 3. Bottom Edge (Left to Right)
        if (currentLen < targetLen) {
            val bottomLen = rect.width()
            if (currentLen + bottomLen <= targetLen) {
                path.lineTo(rect.right, rect.bottom)
                currentLen += bottomLen
            } else {
                path.lineTo(rect.left + (targetLen - currentLen), rect.bottom)
                currentLen = targetLen
            }
        }

        // 4. Right Edge (Bottom to Top)
        if (currentLen < targetLen) {
            val rightLen = rect.height()
            if (currentLen + rightLen <= targetLen) {
                path.lineTo(rect.right, rect.top)
            } else {
                path.lineTo(rect.right, rect.bottom - (targetLen - currentLen))
            }
        }

        canvas.drawPath(path, cyanPaint)
    }
}
