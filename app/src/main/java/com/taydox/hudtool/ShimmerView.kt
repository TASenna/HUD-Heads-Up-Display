package com.taydox.hudtool

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class ShimmerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var baseColor: Int = Color.TRANSPARENT
        set(value) { field = value; invalidate() }

    private var shimmerOffset = -0.5f
    private var isShimmering = false
    private val cornerRadius = 8f * resources.displayMetrics.density
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animator: ValueAnimator? = null
    private val rect = RectF()

    fun startShimmer() {
        if (isShimmering) return
        isShimmering = true
        animator?.cancel()
        animator = ValueAnimator.ofFloat(-0.5f, 1.5f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                shimmerOffset = it.animatedValue as Float
                invalidate()
            }
        }
        animator?.start()
    }

    fun stopShimmer() {
        isShimmering = false
        animator?.cancel()
        shimmerOffset = -0.5f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        rect.set(0f, 0f, w, h)

        bgPaint.color = baseColor
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

        if (isShimmering) {
            val cx = shimmerOffset * w
            val gradient = LinearGradient(
                cx - w * 0.35f, 0f,
                cx + w * 0.35f, 0f,
                intArrayOf(
                    Color.TRANSPARENT,
                    0x20FFFFFF,
                    0x55FFFFFF,
                    0x20FFFFFF,
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
            shimmerPaint.shader = gradient
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, shimmerPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}