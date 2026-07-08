package com.example.yibei.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.yibei.R

data class SleepStage(
    val name: String,
    val durationMinutes: Long,
    val color: Int
)

class SleepStageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val stages = mutableListOf<SleepStage>()
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        color = 0xFF666666.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f
        color = 0xFF999999.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val barRect = RectF()

    private val defaultColors = mapOf(
        "清醒" to ContextCompat.getColor(context, R.color.sleep_awake),
        "REM" to ContextCompat.getColor(context, R.color.sleep_rem),
        "浅睡" to ContextCompat.getColor(context, R.color.sleep_light),
        "深睡" to ContextCompat.getColor(context, R.color.sleep_deep)
    )

    fun setStages(stageList: List<SleepStage>) {
        stages.clear()
        stages.addAll(stageList)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (stages.isEmpty()) return

        val totalMinutes = stages.sumOf { it.durationMinutes }
        if (totalMinutes == 0L) return

        val barHeight = 20f
        val barTop = 8f
        val paddingHorizontal = 4f
        val barWidth = width - paddingHorizontal * 2

        // 绘制分段条
        var left = paddingHorizontal
        barRect.top = barTop
        barRect.bottom = barTop + barHeight

        for (stage in stages) {
            val segmentWidth = (stage.durationMinutes.toFloat() / totalMinutes.toFloat()) * barWidth
            barRect.left = left
            barRect.right = left + segmentWidth

            barPaint.color = defaultColors[stage.name] ?: stage.color
            canvas.drawRect(barRect, barPaint)

            left += segmentWidth
        }

        // 绘制各阶段名称和时长
        left = paddingHorizontal
        val labelTop = barTop + barHeight + 8f
        val centerY = labelTop + 14f

        // 在分段条下方标注名称，如果段太宽则略过
        val minLabelWidth = 36f
        for (stage in stages) {
            val segmentWidth = (stage.durationMinutes.toFloat() / totalMinutes.toFloat()) * barWidth
            val centerX = left + segmentWidth / 2

            if (segmentWidth >= minLabelWidth) {
                canvas.drawText(stage.name, centerX, centerY, labelPaint)
            }

            left += segmentWidth
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = 56.dpToPx()
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(desiredHeight, MeasureSpec.EXACTLY))
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}
