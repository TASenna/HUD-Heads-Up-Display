package com.taydox.hudtool

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class FloatingSquaresView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.0f
        color = 0xFF4A4A4A.toInt()
    }

    private var accentColor = 0x00C0BF

    fun setGridColor(color: Int) {
        gridPaint.color = color
        invalidate()
    }

    fun setAccentColor(color: Int) {
        accentColor = color and 0xFFFFFF
        invalidate()
    }

    private val squarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
    }

    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private data class AnimatedSquare(
        val x: Float, val y: Float, val size: Float,
        var alpha: Float = 0f, var animator: ValueAnimator? = null
    )

    private data class Ripple(
        val x: Float, val y: Float,
        var radius: Float = 0f, var alpha: Float = 0.7f
    )

    private val squares = mutableListOf<AnimatedSquare>()
    private val ripples = mutableListOf<Ripple>()
    private val gridSpacing = 29f
    private val rng = Random(System.currentTimeMillis())

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            squares.forEach { it.animator?.cancel() }
            squares.clear()
            repeat(158) {
                val size = rng.nextFloat() * (w * 0.027f) + (w * 0.006f)
                val x = rng.nextFloat() * (w * 1.05f) - w * 0.02f
                val y = rng.nextFloat() * (h * 1.05f) - h * 0.02f
                val sq = AnimatedSquare(x, y, size)
                squares += sq
                startSquareAnimation(sq)
            }
        }
    }

    private fun startSquareAnimation(sq: AnimatedSquare) {
        val animator = ValueAnimator.ofFloat(0f, 0.7f, 0f).apply {
            duration = rng.nextLong(4000, 10000)
            startDelay = rng.nextLong(0, 8000)
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                sq.alpha = it.animatedValue as Float
                invalidate()
            }
        }
        sq.animator = animator
        animator.start()
    }

    fun triggerRipple(x: Float, y: Float) {
        val ripple = Ripple(x, y)
        val maxRadius = width.coerceAtLeast(height) * 0.15f
        val animator = ValueAnimator.ofFloat(0f, maxRadius).apply {
            duration = 1100
            addUpdateListener { anim ->
                ripple.radius = anim.animatedValue as Float
                ripple.alpha = 0.7f * (1f - anim.animatedFraction)
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    ripples.remove(ripple)
                    invalidate()
                }
            })
        }
        ripples.add(ripple)
        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        var x = 0f
        while (x <= w) { canvas.drawLine(x, 0f, x, h, gridPaint); x += gridSpacing }
        var y = 0f
        while (y <= h) { canvas.drawLine(0f, y, w, y, gridPaint); y += gridSpacing }

        for (sq in squares) {
            squarePaint.color = (((sq.alpha * 255).toInt() shl 24) or accentColor)
            canvas.drawRect(sq.x, sq.y, sq.x + sq.size, sq.y + sq.size, squarePaint)
        }

        for (ripple in ripples) {
            ripplePaint.color = (((ripple.alpha * 255).toInt() shl 24) or accentColor)
            canvas.drawCircle(ripple.x, ripple.y, ripple.radius, ripplePaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        squares.forEach { it.animator?.cancel() }
    }
}