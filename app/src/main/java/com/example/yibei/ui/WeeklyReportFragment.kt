package com.example.yibei.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.yibei.R
import com.example.yibei.data.HealthDataHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

class WeeklyReportFragment : Fragment() {

    private lateinit var prefs: SharedPreferences

    private lateinit var tvReportDate: TextView
    private lateinit var tvDataWater: TextView
    private lateinit var tvDataSleep: TextView
    private lateinit var tvDataSteps: TextView
    private lateinit var tvDailyDetail: TextView

    private lateinit var cardAi: CardView
    private lateinit var tvAiAnalysis: TextView
    private lateinit var pbAiLoading: ProgressBar
    private lateinit var btnAiRefresh: Button

    private var cachedWAvg = 0.0
    private var cachedSAvg = 0.0
    private var cachedStAvg = 0.0
    private var cachedDailyGoal = 2000
    private var cachedStartLabel = ""
    private var cachedEndLabel = ""

    private val labelFormatter7d = DateTimeFormatter.ofPattern("MM/dd")

    companion object {
        private const val DEEPSEEK_API_KEY = "YOUR_DEEPSEEK_API_KEY"
        private const val DEEPSEEK_URL = "https://api.deepseek.com/chat/completions"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_weekly_report, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences("water_prefs", Context.MODE_PRIVATE)

        tvReportDate = view.findViewById(R.id.tv_report_date)
        tvDataWater = view.findViewById(R.id.tv_data_water)
        tvDataSleep = view.findViewById(R.id.tv_data_sleep)
        tvDataSteps = view.findViewById(R.id.tv_data_steps)
        tvDailyDetail = view.findViewById(R.id.tv_daily_detail)
        cardAi = view.findViewById(R.id.card_ai)
        tvAiAnalysis = view.findViewById(R.id.tv_ai_analysis)
        pbAiLoading = view.findViewById(R.id.pb_ai_loading)
        btnAiRefresh = view.findViewById(R.id.btn_ai_refresh)

        btnAiRefresh.setOnClickListener { triggerAiRefresh() }
        cardAi.visibility = View.GONE

        loadWeeklyData()
    }

    private fun loadWeeklyData() {
        lifecycleScope.launch {
            try {
                val today = LocalDate.now()
                val dailyGoal = prefs.getInt("daily_goal", 2000)
                cachedDailyGoal = dailyGoal

                // 自然周：本周一 → 本周日
                val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val thisSunday = thisMonday.plusDays(6)

                // 如果还没到周日，展示上周
                val isCurrentWeekComplete = today.dayOfWeek == DayOfWeek.SUNDAY
                val (weekStart, weekEnd) = if (isCurrentWeekComplete) {
                    thisMonday to thisSunday
                } else {
                    thisMonday.minusDays(7) to thisSunday.minusDays(7)
                }

                cachedStartLabel = weekStart.format(labelFormatter7d)
                cachedEndLabel = weekEnd.format(labelFormatter7d)
                tvReportDate.text = "$cachedStartLabel - $cachedEndLabel (第${weekStart.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)}周)"

                val waters = mutableListOf<Float>()
                val sleeps = mutableListOf<Float>()
                val steps = mutableListOf<Float>()
                val labels = mutableListOf<String>()

                for (i in 0..6) {
                    val date = weekStart.plusDays(i.toLong())
                    labels.add(date.format(labelFormatter7d))
                    waters.add(HealthDataHelper.readWaterAmount(prefs, date).toFloat())
                    steps.add(HealthDataHelper.readSteps(requireContext(), date).toFloat())
                    val result = withContext(Dispatchers.Default) {
                        HealthDataHelper.getSleepResult(requireContext(), prefs, date)
                    }
                    sleeps.add(((result.night?.durationMinutes ?: 0L) + (result.nap?.durationMinutes ?: 0L)) / 60f)
                }

                val wAvg = waters.average().let { if (it.isNaN()) 0.0 else it }
                val sAvg = sleeps.average().let { if (it.isNaN()) 0.0 else it }
                val stAvg = steps.average().let { if (it.isNaN()) 0.0 else it }
                cachedWAvg = wAvg; cachedSAvg = sAvg; cachedStAvg = stAvg

                withContext(Dispatchers.Main) {
                    tvDataWater.text = "喝水  日均 ${formatMl(wAvg.toFloat())}  |  目标 ${formatMl(dailyGoal.toFloat())}  |  达标率 ${formatPct(wAvg, dailyGoal)}"
                    tvDataSleep.text = "睡眠  日均 ${String.format("%.1fh", sAvg)}"
                    tvDataSteps.text = "步数  日均 ${formatSteps(stAvg.toFloat())}"

                    val detail = buildString {
                        append("日期      喝水      睡眠    步数\n")
                        append("────────────────────────────\n")
                        for (idx in labels.indices) {
                            val w = formatMl(waters[idx]).padStart(6)
                            val s = String.format("%.1fh", sleeps[idx]).padStart(6)
                            val t = formatSteps(steps[idx]).padStart(6)
                            append("${labels[idx]}  $w  $s  $t\n")
                        }
                    }
                    tvDailyDetail.text = detail

                    cardAi.visibility = View.VISIBLE
                    loadAiAnalysis()
                }
            } catch (_: Exception) { }
        }
    }

    private fun loadAiAnalysis() {
        val weekKey = "weekly_ai_${cachedStartLabel}_$cachedEndLabel"
        val cached = prefs.getString(weekKey, null)
        if (cached != null) {
            tvAiAnalysis.text = cached
        } else {
            triggerAiRefresh()
        }
    }

    private fun triggerAiRefresh() {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                pbAiLoading.visibility = View.VISIBLE
                tvAiAnalysis.text = ""
            }

            val prompt = buildString {
                append("请基于以下上周（${cachedStartLabel} ~ ${cachedEndLabel}）健康数据生成周报总结和建议（200字以内，中文）：\n\n")
                append("【喝水】日均%.0fml（目标%dml），达标率%s\n".format(cachedWAvg, cachedDailyGoal, formatPct(cachedWAvg, cachedDailyGoal)))
                append("【睡眠】日均%.1fh\n".format(cachedSAvg))
                append("【步数】日均%.0f步\n\n".format(cachedStAvg))
                append("请指出关键问题和改善建议。")
            }

            val aiText = try {
                callDeepSeek(prompt) ?: "AI 分析暂时不可用"
            } catch (e: Exception) {
                "分析生成失败"
            }

            val weekKey = "weekly_ai_${cachedStartLabel}_$cachedEndLabel"
            prefs.edit().putString(weekKey, aiText).apply()

            withContext(Dispatchers.Main) {
                pbAiLoading.visibility = View.GONE
                tvAiAnalysis.text = aiText
            }
        }
    }

    private suspend fun callDeepSeek(prompt: String): String? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(DEEPSEEK_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $DEEPSEEK_API_KEY")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000

            val body = JSONObject().apply {
                put("model", "deepseek-chat")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("max_tokens", 400)
                put("temperature", 0.7)
            }

            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode != 200) return@withContext null

            val text = conn.inputStream.bufferedReader().readText()
            val choices = JSONObject(text).getJSONArray("choices")
            if (choices.length() > 0) {
                choices.getJSONObject(0).getJSONObject("message").getString("content").trim()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun formatMl(v: Float): String =
        if (v >= 1000f) String.format("%.1fL", v / 1000f) else String.format("%.0fml", v)

    private fun formatSteps(v: Float): String =
        if (v >= 10000f) String.format("%.1f万", v / 10000f) else String.format("%.0f", v)

    private fun formatPct(avg: Double, goal: Int): String =
        if (goal > 0) String.format("%.0f%%", (avg / goal * 100).coerceAtMost(999.0)) else "--"
}
