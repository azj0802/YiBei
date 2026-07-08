package com.example.yibei.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class SleepTrendView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class DayData(
        val label: String,       // "07/01"
        val nightMin: Long,       // 夜间睡眠分钟数
        val napMin: Long          // 午睡分钟数
    )

    private val dataList = mutableListOf<DayData>()

    private val barNightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#42A5F5")
        style = Paint.Style.FILL
    }

    private val barNapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFB74D")
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f
        color = Color.parseColor("#999999")
        textAlign = Paint.Align.CENTER
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f
        color = Color.parseColor("#666666")
        textAlign = Paint.Align.CENTER
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 0.5f
        style = Paint.Style.STROKE
    }

    fun setData(data: List<DayData>) {
        dataList.clear()
        dataList.addAll(data)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (dataList.isEmpty()) return

        val paddingLeft = 50f
        val paddingRight = 20f
        val paddingTop = 30f
        val paddingBottom = 30f
        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom

        val maxTotal = dataList.maxOfOrNull { it.nightMin + it.napMin }?.coerceAtLeast(1L) ?: 1L
        val maxH = maxTotal / 60f

        // 网格线 (每2小时一条)
        for (h in 0..maxH.toInt() step 2) {
            val y = paddingTop + chartHeight * (1 - (h.toFloat() / maxH))
            canvas.drawLine(paddingLeft, y, paddingLeft + chartWidth, y, gridPaint)
        }

        // 柱状图
        val columnCount = dataList.size
        val gapRatio = 0.35f
        val columnWidth = chartWidth / columnCount
        val barWidth = columnWidth * (1 - gapRatio)

        for (i in dataList.indices) {
            val d = dataList[i]
            val columnLeft = paddingLeft + i * columnWidth
            val barLeft = columnLeft + columnWidth * gapRatio / 2

            val totalMin = d.nightMin + d.napMin
            val totalH = totalMin / 60f
            val barTotalHeight = (totalH / maxH * chartHeight).coerceAtMost(chartHeight)

            // 绘制总高度（夜间 + 午睡）
            val barTop = paddingTop + chartHeight - barTotalHeight

            // 午睡在下，夜间在上
            if (d.napMin > 0) {
                val napHeight = (d.napMin / 60f / maxH * chartHeight).coerceAtMost(barTotalHeight)
                canvas.drawRect(barLeft, paddingTop + chartHeight - napHeight,
                    barLeft + barWidth, paddingTop + chartHeight, barNapPaint)
            }
            if (d.nightMin > 0) {
                val nightHeight = (d.nightMin / 60f / maxH * chartHeight).coerceAtMost(barTotalHeight)
                val nightTop = paddingTop + chartHeight - barTotalHeight
                canvas.drawRect(barLeft, nightTop,
                    barLeft + barWidth, nightTop + nightHeight, barNightPaint)
            }

            // 标签
            canvas.drawText(d.label, barLeft + barWidth / 2, height - 4f, labelPaint)

            // 数值（小时）
            if (totalMin > 0) {
                val h = totalMin / 60
                val m = totalMin % 60
                val valText = if (m == 0L) "${h}h" else "${h}.${m / 6}h"
                canvas.drawText(valText, barLeft + barWidth / 2, barTop - 8f, valuePaint)
            }
        }
    }
}
