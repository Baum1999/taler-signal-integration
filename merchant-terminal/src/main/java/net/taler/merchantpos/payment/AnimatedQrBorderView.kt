package net.taler.merchantpos.payment

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import net.taler.merchantpos.R
import androidx.core.view.isVisible

/**
 * Draws a rounded border around the QR code with two animated gradient lines.
 */
class AnimatedQrBorderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val strokeWidthPx = 6f * resources.displayMetrics.density
    private val cornerRadiusPx = 20f * resources.displayMetrics.density
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
    }
    private val borderRect = RectF()
    private val gradientMatrix = Matrix()

    private var gradient: SweepGradient? = null
    private var rotationAngle = 0f

    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 5250L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            rotationAngle = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isVisible && !animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            if (!animator.isStarted) animator.start()
        } else {
            animator.cancel()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val halfStroke = strokeWidthPx / 2f
        borderRect.set(
            halfStroke,
            halfStroke,
            w.toFloat() - halfStroke,
            h.toFloat() - halfStroke,
        )
        gradient = createGradient(w / 2f, h / 2f)
        strokePaint.shader = gradient
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val shader = gradient ?: return
        gradientMatrix.reset()
        gradientMatrix.setRotate(rotationAngle, width / 2f, height / 2f)
        shader.setLocalMatrix(gradientMatrix)
        canvas.drawRoundRect(borderRect, cornerRadiusPx, cornerRadiusPx, strokePaint)
    }

    private fun createGradient(cx: Float, cy: Float): SweepGradient {
        val accent = ContextCompat.getColor(context, R.color.colorPrimary)
        val background = ContextCompat.getColor(context, R.color.colorSurface)
        val softAccent = ColorUtils.blendARGB(background, accent, 0.55f)
        return SweepGradient(
            cx,
            cy,
            intArrayOf(
                background,
                background,
                softAccent,
                accent,
                softAccent,
                background,
                background,
                softAccent,
                accent,
                softAccent,
                background,
                background,
            ),
            floatArrayOf(
                0.00f,
                0.05f,
                0.09f,
                0.12f,
                0.16f,
                0.21f,
                0.50f,
                0.55f,
                0.59f,
                0.62f,
                0.66f,
                1.00f,
            ),
        )
    }
}
