package com.example.yibei.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class MiniTrendView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bars = mutableListOf<Float>()
    private val labels = mutableListOf<String>()

    // 睡眠时间叠加线
    private var sleepStarts: List<Float> = emptyList()
    private var sleepEnds: List<Float> = emptyList()
    private var hasSleepOverlay = false

    private var barColor = Color.parseColor("#4CAF50")
    private var fillColor = Color.parseColor("#224CAF50")
    private var maxColor = Color.parseColor("#FFD54F")
    private var minColor = Color.parseColor("#FF8A80")

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val fillLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 16f
        color = Color.parseColor("#AAAAAA")
        textAlign = Paint.Align.CENTER
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f
        textAlign = Paint.Align.CENTER
    }

    data class TrendStats(
        val maxValue: Float,
        val minValue: Float,
        val avgValue: Float,
        val maxIndex: Int,
        val minIndex: Int
    )

    var onStatsReady: ((TrendStats) -> Unit)? = null

    fun setData(data: List<Float>, dayLabels: List<String>, colorHex: String = "#4CAF50") {
        bars.clear()
        labels.clear()
        bars.addAll(data)
        labels.addAll(dayLabels)
        barColor = Color.parseColor(colorHex)
        fillColor = adjustAlpha(colorHex, 0.18f)
        hasSleepOverlay = false

        val valid = bars.filter { it > 0 }
        if (valid.isNotEmpty()) {
            val maxVal = valid.maxOrNull() ?: 0f
            val minVal = valid.minOrNull() ?: 0f
            val avgVal = valid.average().toFloat()
            val maxIdx = bars.indexOf(maxVal)
            val minIdx = bars.indexOf(minVal)
            onStatsReady?.invoke(TrendStats(maxVal, minVal, avgVal, maxIdx, minIdx))
        }

        invalidate()
    }

    /** 叠加睡眠入睡/起床时间折线，值已归一化到 0-12 范围 */
    fun setSleepTimeOverlay(startTimes: List<Float>, endTimes: List<Float>) {
        sleepStarts = startTimes
        sleepEnds = endTimes
        hasSleepOverlay = startTimes.isNotEmpty() && endTimes.isNotEmpty()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (bars.isEmpty()) return

        val pl = 12f
        val pr = 12f
        val pt = 30f
        val pb = 22f
        val cw = width - pl - pr
        val ch = height - pt - pb

        val maxVal = bars.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val minIdx = bars.indexOf(bars.filter { it > 0 }.minOrNull() ?: 0f)
        val maxIdx = bars.indexOf(bars.maxOrNull() ?: 0f)

        val n = bars.size
        val barSlot = cw / n
        val barW = barSlot * 0.35f

        // 画折线填充
        if (n >= 2) {
            val linePath = android.graphics.Path()
            for (i in bars.indices) {
                val cx = pl + barSlot * i + barSlot / 2
                val barH = (bars[i] / maxVal * ch).coerceAtLeast(2f)
                val cy = pt + ch - barH
                if (i == 0) linePath.moveTo(cx, cy) else linePath.lineTo(cx, cy)
            }
            val fillPath = android.graphics.Path(linePath)
            fillPath.lineTo(pl + barSlot * (n - 1) + barSlot / 2, pt + ch)
            fillPath.lineTo(pl + barSlot / 2, pt + ch)
            fillPath.close()

            val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fillColor
                style = Paint.Style.FILL
            }
            canvas.drawPath(fillPath, fillP)

            fillLinePaint.color = barColor
            fillLinePaint.strokeWidth = 2.5f
            canvas.drawPath(linePath, fillLinePaint)
        }

        // 画柱状图
        for (i in bars.indices) {
            val cx = pl + barSlot * i + barSlot / 2
            val lx = cx - barW / 2
            val barH = (bars[i] / maxVal * ch).coerceAtLeast(2f)
            val ly = pt + ch - barH

            barPaint.color = when {
                i == maxIdx && bars[i] > 0 -> maxColor
                i == minIdx && bars[i] > 0 && maxIdx != minIdx -> minColor
                else -> barColor
            }

            canvas.drawRoundRect(lx, ly, lx + barW, pt + ch, 2f, 2f, barPaint)

            // 数值标注
            if (bars[i] > 0) {
                val text = formatValue(bars[i])
                valuePaint.color = barPaint.color
                canvas.drawText(text, cx, ly - 6f, valuePaint)
            }
        }

        // 底部标签
        for (i in labels.indices) {
            val cx = pl + barSlot * i + barSlot / 2
            canvas.drawText(labels[i], cx, height - 2f, labelPaint)
        }

        // 睡眠时间叠加折线
        if (hasSleepOverlay && bars.isNotEmpty()) {
            val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#64B5F6")
                strokeWidth = 2.5f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 4f), 0f)
            }
            val endPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FFB74D")
                strokeWidth = 2.5f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 4f), 0f)
            }
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
            val labelTimePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 13f
                textAlign = Paint.Align.CENTER
            }

            val startPath = android.graphics.Path()
            val endPath = android.graphics.Path()
            var firstStart = true
            var firstEnd = true

            for (i in bars.indices) {
                val cx = pl + barSlot * i + barSlot / 2
                val startI = sleepStarts.getOrElse(i) { -1f }
                val endI = sleepEnds.getOrElse(i) { -1f }

                if (startI >= 0) {
                    val sy = pt + ch - (startI / maxVal * ch).coerceIn(0f, ch)
                    if (firstStart) { startPath.moveTo(cx, sy); firstStart = false }
                    else startPath.lineTo(cx, sy)

                    // 入睡时点
                    val r = 5f
                    canvas.drawCircle(cx, sy, r, startPaint.apply { style = Paint.Style.FILL; pathEffect = null })
                    startPaint.style = Paint.Style.STROKE
                    startPaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 4f), 0f)

                    // 入睡时间标签
                    val hour = startI.toInt()
                    val min = ((startI - hour) * 60).toInt()
                    labelTimePaint.color = Color.parseColor("#64B5F6")
                    canvas.drawText(String.format("%02d:%02d", hour, min), cx, sy - 10f, labelTimePaint)
                }

                if (endI >= 0) {
                    val ey = pt + ch - (endI / maxVal * ch).coerceIn(0f, ch)
                    if (firstEnd) { endPath.moveTo(cx, ey); firstEnd = false }
                    else endPath.lineTo(cx, ey)

                    val r = 5f
                    canvas.drawCircle(cx, ey, r, endPaint.apply { style = Paint.Style.FILL; pathEffect = null })
                    endPaint.style = Paint.Style.STROKE
                    endPaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 4f), 0f)

                    // 起床时间标签  
                    val wakeHour = (endI + 5).toInt()
                    val wakeMin = ((endI - endI.toInt()) * 60).toInt()
                    labelTimePaint.color = Color.parseColor("#FFB74D")
                    canvas.drawText(String.format("%02d:%02d", wakeHour, wakeMin), cx, ey - 10f, labelTimePaint)
                }
            }

            canvas.drawPath(startPath, startPaint)
            canvas.drawPath(endPath, endPaint)
        }
    }

    private fun formatValue(v: Float): String {
        return if (v >= 10000) String.format("%.1f万", v / 10000f)
        else if (v >= 1000) String.format("%.0f", v)
        else if (v >= 1) String.format("%.1f", v)
        else v.toString()
    }

    private fun adjustAlpha(hex: String, factor: Float): Int {
        val color = Color.parseColor(hex)
        val a = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }
}
