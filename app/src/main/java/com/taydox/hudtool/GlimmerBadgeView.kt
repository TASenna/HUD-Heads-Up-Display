package com.taydox.hudtool

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class GlimmerBadgeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var text: String = "PRO"
        set(value) { field = value; invalidate() }

    var badgeColor: Int = 0xFFFFD700.toInt() // Gold
        set(value) { field = value; invalidate() }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF121212.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val glimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var glimmerOffset = -1f
    private val animator: ValueAnimator = ValueAnimator.ofFloat(-1f, 2f).apply {
        duration = 2500
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            glimmerOffset = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val radius = 4f * resources.displayMetrics.density
        
        // Draw Gold Background
        bgPaint.color = badgeColor
        canvas.drawRoundRect(0f, 0f, w, h, radius, radius, bgPaint)

        // Draw Glimmer Streak
        val cx = glimmerOffset * w
        val sweepGradient = LinearGradient(
            cx - w * 0.2f, 0f,
            cx + w * 0.2f, 0f,
            intArrayOf(
                Color.TRANSPARENT,
                0x33FFFFFF,
                0xEEFFFFFF.toInt(),
                0x33FFFFFF,
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.4f, 0.5f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        glimmerPaint.shader = sweepGradient
        canvas.drawRoundRect(0f, 0f, w, h, radius, radius, glimmerPaint)

        // Draw Text
        textPaint.textSize = h * 0.55f
        val xPos = w / 2
        val yPos = (h / 2) - ((textPaint.descent() + textPaint.ascent()) / 2)
        canvas.drawText(text, xPos, yPos, textPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }
}
