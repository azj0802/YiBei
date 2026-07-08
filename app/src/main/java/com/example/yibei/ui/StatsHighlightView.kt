package com.example.yibei.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class StatsHighlightView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val tvTitle: TextView
    private val tvMax: TextView
    private val tvMin: TextView
    private val tvAvg: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(8), dp(8), dp(8))

        tvMax = createStatItem()
        tvMin = createStatItem()
        tvAvg = createStatItem()

        addView(tvMax, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(tvMin, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(tvAvg, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        tvTitle = TextView(context).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#AAAAAA"))
        }
    }

    fun setData(maxLabel: String, maxValue: String, minLabel: String, minValue: String, avgLabel: String, avgValue: String) {
        setStat(tvMax, "最高", maxValue, Color.parseColor("#FFD54F"))
        setStat(tvMin, "最低", minValue, Color.parseColor("#FF8A80"))
        setStat(tvAvg, "日均", avgValue, Color.parseColor("#81C784"))
    }

    private fun setStat(view: TextView, label: String, value: String, color: Int) {
        view.text = "$label\n$value"
        view.setTextColor(color)
    }

    private fun createStatItem(): TextView = TextView(context).apply {
        gravity = Gravity.CENTER
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(dp(4).toFloat(), 1f)
    }

    private fun dp(px: Int): Int = (px * resources.displayMetrics.density).toInt()
}
