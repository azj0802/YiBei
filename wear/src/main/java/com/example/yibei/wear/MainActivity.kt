package com.example.yibei.wear

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import com.example.yibei.data.HealthDataHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var tvDate: TextView
    private lateinit var tvWater: TextView
    private lateinit var tvSleep: TextView
    private lateinit var tvSteps: TextView
    private lateinit var tvAi: TextView
    private lateinit var pbAi: ProgressBar
    private lateinit var btnRefresh: Button

    private var wAvg = 0.0; private var sAvg = 0.0; private var stAvg = 0.0
    private var dailyGoal = 2000
    private var startLabel = ""; private var endLabel = ""

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private val df = DateTimeFormatter.ofPattern("MM/dd")

    companion object {
        private const val KEY = "sk-22b4893ed61441c1a506c77238deebdf"
        private const val URL = "https://api.deepseek.com/chat/completions"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("water_prefs", Context.MODE_PRIVATE)
        tvDate = findViewById(R.id.tv_report_date)
        tvWater = findViewById(R.id.tv_data_water)
        tvSleep = findViewById(R.id.tv_data_sleep)
        tvSteps = findViewById(R.id.tv_data_steps)
        tvAi = findViewById(R.id.tv_ai_analysis)
        pbAi = findViewById(R.id.pb_ai_loading)
        btnRefresh = findViewById(R.id.btn_ai_refresh)
        btnRefresh.setOnClickListener { triggerAi() }

        loadData()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private fun loadData() = scope.launch {
        try {
            val today = LocalDate.now()
            dailyGoal = prefs.getInt("daily_goal", 2000)
            startLabel = today.minusDays(7).format(df)
            endLabel = today.minusDays(1).format(df)
            tvDate.text = "$startLabel - $endLabel"

            val waters = mutableListOf<Float>()
            val sleeps = mutableListOf<Float>()
            val steps = mutableListOf<Float>()

            for (i in 7 downTo 1) {
                val d = today.minusDays(i.toLong())
                waters.add(HealthDataHelper.readWaterAmount(prefs, d).toFloat())
                steps.add(HealthDataHelper.readSteps(this@MainActivity, d).toFloat())
                val r = withContext(Dispatchers.Default) {
                    HealthDataHelper.getSleepResult(this@MainActivity, prefs, d)
                }
                sleeps.add(((r.night?.durationMinutes ?: 0L) + (r.nap?.durationMinutes ?: 0L)) / 60f)
            }

            wAvg = waters.average().let { if (it.isNaN()) 0.0 else it }
            sAvg = sleeps.average().let { if (it.isNaN()) 0.0 else it }
            stAvg = steps.average().let { if (it.isNaN()) 0.0 else it }

            val goalMl = formatMl(dailyGoal.toFloat())
            withContext(Dispatchers.Main) {
                tvWater.text = "喝水 日均${formatMl(wAvg.toFloat())} 目标$goalMl 达标${formatPct()}"
                tvSleep.text = "睡眠 日均${String.format("%.1fh", sAvg)}"
                tvSteps.text = "步数 日均${formatSteps(stAvg.toFloat())}"

                val cacheKey = "weekly_ai_${startLabel}_$endLabel"
                val cached = prefs.getString(cacheKey, null)
                if (cached != null) {
                    tvAi.text = cached
                } else {
                    triggerAi()
                }
            }
        } catch (_: Exception) {}
    }

    private fun triggerAi() = scope.launch {
        pbAi.visibility = View.VISIBLE; tvAi.text = ""
        val prompt = "基于上周健康数据（${startLabel}~${endLabel}）生成简短建议（80字内）：" +
                "喝水日均${wAvg.toInt()}ml（目标${dailyGoal}ml/${formatPct()}）" +
                "睡眠日均${String.format("%.1fh", sAvg)}" +
                "步数日均${stAvg.toInt()}步"

        val text = try {
            withContext(Dispatchers.IO) { callDeepSeek(prompt) } ?: "AI 暂不可用"
        } catch (_: Exception) { "分析失败" }

        prefs.edit().putString("weekly_ai_${startLabel}_$endLabel", text).apply()
        pbAi.visibility = View.GONE; tvAi.text = text
    }

    private fun callDeepSeek(prompt: String): String? {
        val conn = (java.net.URL(URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $KEY")
            doOutput = true
            connectTimeout = 10000; readTimeout = 20000
        }
        val body = JSONObject().apply {
            put("model", "deepseek-chat")
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "user"); put("content", prompt) })
            })
            put("max_tokens", 200); put("temperature", 0.7)
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        if (conn.responseCode != 200) return null
        val json = JSONObject(conn.inputStream.bufferedReader().readText())
        val choices = json.getJSONArray("choices")
        return if (choices.length() > 0) choices.getJSONObject(0).getJSONObject("message").getString("content").trim() else null
    }

    private fun formatMl(v: Float) = if (v >= 1000f) String.format("%.1fL", v / 1000f) else String.format("%.0fml", v)
    private fun formatSteps(v: Float) = if (v >= 10000f) String.format("%.1f万", v / 10000f) else String.format("%.0f", v)
    private fun formatPct() = if (dailyGoal > 0) String.format("%.0f%%", (wAvg / dailyGoal * 100).coerceAtMost(999.0)) else "--"
}
