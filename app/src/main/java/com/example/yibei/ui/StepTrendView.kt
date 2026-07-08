package com.example.yibei.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class StepTrendView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val stepsData = mutableListOf<Int>()
    private val labels = mutableListOf<String>()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#334CAF50")
        style = Paint.Style.FILL
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f
        color = Color.parseColor("#999999")
        textAlign = Paint.Align.CENTER
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f
        color = Color.parseColor("#333333")
        textAlign = Paint.Align.CENTER
    }

    fun setData(data: List<Int>, dayLabels: List<String>) {
        stepsData.clear()
        labels.clear()
        stepsData.addAll(data)
        labels.addAll(dayLabels)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (stepsData.isEmpty()) return

        val paddingLeft = 40f
        val paddingRight = 16f
        val paddingTop = 16f
        val paddingBottom = 28f
        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom

        val maxSteps = (stepsData.maxOrNull() ?: 1).coerceAtLeast(1)
        val points = mutableListOf<Pair<Float, Float>>()

        for (i in stepsData.indices) {
            val x = paddingLeft + (i.toFloat() / (stepsData.size - 1).coerceAtLeast(1)) * chartWidth
            val y = paddingTop + chartHeight - (stepsData[i].toFloat() / maxSteps) * chartHeight
            points.add(x to y)
        }

        // 绘制填充区域
        if (points.size >= 2) {
            val fillPath = Path()
            fillPath.moveTo(points[0].first, paddingTop + chartHeight)
            for (p in points) {
                fillPath.lineTo(p.first, p.second)
            }
            fillPath.lineTo(points.last().first, paddingTop + chartHeight)
            fillPath.close()
            canvas.drawPath(fillPath, fillPaint)
        }

        // 绘制折线
        if (points.size >= 2) {
            val linePath = Path()
            linePath.moveTo(points[0].first, points[0].second)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val cx1 = (prev.first + curr.first) / 2
                val cx2 = (prev.first + curr.first) / 2
                linePath.cubicTo(cx1, prev.second, cx2, curr.second, curr.first, curr.second)
            }
            canvas.drawPath(linePath, linePaint)
        }

        // 绘制数据点
        for (p in points) {
            canvas.drawCircle(p.first, p.second, 6f, dotPaint)
        }

        // 绘制底部标签
        for (i in labels.indices) {
            val x = paddingLeft + (i.toFloat() / (stepsData.size - 1).coerceAtLeast(1)) * chartWidth
            canvas.drawText(labels[i], x, height - 4f, textPaint)
        }

        // 绘制顶部数值
        for (i in stepsData.indices) {
            val x = paddingLeft + (i.toFloat() / (stepsData.size - 1).coerceAtLeast(1)) * chartWidth
            val y = points[i].second - 14f
            if (stepsData[i] > 0) {
                canvas.drawText(formatSteps(stepsData[i]), x, y, valuePaint)
            }
        }
    }

    private fun formatSteps(steps: Int): String {
        return if (steps >= 10000) {
            String.format("%.1f万", steps / 10000.0)
        } else {
            steps.toString()
        }
    }
}
