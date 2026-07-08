package com.example.yibei.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class RingProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress = 0f      // 0..100
    private var valueText = "--"
    private var labelText = ""
    private var subLabel = ""
    private var ringColor = Color.parseColor("#4CAF50")
    private var bgColor = Color.parseColor("#E0E0E0")

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
    }

    private val valueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#999999")
    }

    private val subLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#AAAAAA")
    }

    fun setData(progress: Float, valueText: String, label: String, subLabel: String, color: Int) {
        this.progress = progress.coerceIn(0f, 100f)
        this.valueText = valueText
        this.labelText = label
        this.subLabel = subLabel
        this.ringColor = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val strokeW = min(width, height) * 0.12f
        bgPaint.strokeWidth = strokeW
        bgPaint.color = bgColor
        fgPaint.strokeWidth = strokeW
        fgPaint.color = ringColor
        glowPaint.strokeWidth = strokeW
        glowPaint.color = ringColor

        val cx = width / 2f
        val cy = height / 2f
        val radius = (min(width, height) - strokeW) / 2f

        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        // 背景圆环
        canvas.drawArc(rect, 135f, 270f, false, bgPaint)

        // 前景圆弧
        val sweepAngle = (progress / 100f) * 270f

        if (sweepAngle > 0) {
            // 发光效果
            glowPaint.alpha = 60
            canvas.drawArc(rect, 135f, sweepAngle, false, glowPaint)
            glowPaint.alpha = 255

            canvas.drawArc(rect, 135f, sweepAngle, false, fgPaint)
        }

        // 数值文字
        valueTextPaint.textSize = radius * 0.55f
        valueTextPaint.color = ringColor
        canvas.drawText(valueText, cx, cy - radius * 0.08f, valueTextPaint)

        // 标签
        labelPaint.textSize = radius * 0.28f
        canvas.drawText(labelText, cx, cy + radius * 0.35f, labelPaint)

        // 副标签
        subLabelPaint.textSize = radius * 0.20f
        canvas.drawText(subLabel, cx, cy + radius * 0.65f, subLabelPaint)
    }
}
