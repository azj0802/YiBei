package com.example.yibei.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

class WaterWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val waveBottomPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val waveTopPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pathBottom = Path()
    private val pathTop = Path()

    var waterLevel = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private var startTime = 0L
    private var running = false
    private var bgColor = Color.parseColor("#F2F8FD")
    private var waveColorTop = Color.parseColor("#B8DCF5")
    private var waveColorBottom = Color.parseColor("#CCE5FA")

    init {
        waveBottomPaint.color = waveColorBottom
        waveBottomPaint.style = Paint.Style.FILL
        waveTopPaint.color = waveColorTop
        waveTopPaint.style = Paint.Style.FILL
    }

    fun applyThemeColors(bg: Int, top: Int, bottom: Int) {
        bgColor = bg
        waveColorTop = top
        waveColorBottom = bottom
        waveTopPaint.color = top
        waveBottomPaint.color = bottom
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startTime = System.currentTimeMillis()
        running = true
        loop()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        running = false
    }

    private fun loop() {
        if (!running) return
        invalidate()
        postOnAnimation { loop() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        canvas.drawColor(bgColor)

        val waterTop = h * (1f - waterLevel)
        val amplitude = 10f + waterLevel * 28f

        val t = (System.currentTimeMillis() - startTime) / 1000f
        val segments = 100
        val dx = w / segments

        // 下层波浪
        pathBottom.reset()
        pathBottom.moveTo(0f, h)
        for (i in 0..segments) {
            val x = i * dx
            val y = waterTop + amplitude * 1.1f * sin(x / w * 2.4f * Math.PI + t * 1.8f).toFloat()
            pathBottom.lineTo(x, y)
        }
        pathBottom.lineTo(w, h)
        pathBottom.close()
        canvas.drawPath(pathBottom, waveBottomPaint)

        // 上层波浪
        pathTop.reset()
        pathTop.moveTo(0f, h)
        for (i in 0..segments) {
            val x = i * dx
            val y = waterTop + amplitude * 0.6f * sin(x / w * 3.5f * Math.PI - t * 2.4f).toFloat()
            pathTop.lineTo(x, y)
        }
        pathTop.lineTo(w, h)
        pathTop.close()
        canvas.drawPath(pathTop, waveTopPaint)
    }
}
