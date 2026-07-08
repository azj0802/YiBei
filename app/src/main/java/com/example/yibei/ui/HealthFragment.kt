package com.example.yibei.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.yibei.R
import com.example.yibei.data.HealthDataHelper
import com.example.yibei.data.HealthDataHelper.AwakeGap
import com.example.yibei.data.HealthDataHelper.NapSleep
import com.example.yibei.data.HealthDataHelper.NightSleep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HealthFragment : Fragment() {

    private var _rootView: View? = null

    private lateinit var tvDate: TextView
    private lateinit var ivDateLeft: ImageView
    private lateinit var ivDateRight: ImageView
    private lateinit var cardPermissionGuide: CardView
    private lateinit var btnRequestPermission: Button
    private lateinit var cardSteps: CardView
    private lateinit var tvStepsCount: TextView
    private lateinit var pbSteps: ProgressBar
    private lateinit var tvStepsPercent: TextView
    private lateinit var tvStepsGoal: TextView
    private lateinit var tvCalories: TextView
    private lateinit var ivStepsGoalEdit: ImageView
    private lateinit var viewStepTrend: StepTrendView
    private lateinit var viewSleepTrend: SleepTrendView
    private lateinit var cardSleep: CardView
    private lateinit var tvSleepDuration: TextView
    private lateinit var tvSleepScore: TextView
    private lateinit var tvSleepStart: TextView
    private lateinit var tvSleepEnd: TextView
    private lateinit var ivSleepEdit: ImageView
    private lateinit var tvSleepSource: TextView
    private lateinit var viewSleepStages: SleepStageView
    private lateinit var cardAi: CardView
    private lateinit var tvAiAnalysis: TextView
    private lateinit var pbAiLoading: ProgressBar
    private lateinit var btnAiRefresh: Button

    private var lastStepsError: String? = null
    private var stepGoal = 8000
    private var currentDate = LocalDate.now()
    private lateinit var prefs: SharedPreferences

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    companion object {
        private const val DEEPSEEK_API_KEY = "YOUR_DEEPSEEK_API_KEY"
        private const val DEEPSEEK_URL = "https://api.deepseek.com/chat/completions"
        private const val REQ_ACTIVITY_RECOGNITION = 1001
    }

    // ==================== 生命周期 ====================

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_health, container, false)
        _rootView = view
        initViews(view)
        return view
    }

    private fun initViews(view: View) {
        tvDate = view.findViewById(R.id.tv_date)
        ivDateLeft = view.findViewById(R.id.iv_date_left)
        ivDateRight = view.findViewById(R.id.iv_date_right)
        cardPermissionGuide = view.findViewById(R.id.card_permission_guide)
        btnRequestPermission = view.findViewById(R.id.btn_request_permission)
        cardSteps = view.findViewById(R.id.card_steps)
        tvStepsCount = view.findViewById(R.id.tv_steps_count)
        pbSteps = view.findViewById(R.id.pb_steps)
        tvStepsPercent = view.findViewById(R.id.tv_steps_percent)
        tvStepsGoal = view.findViewById(R.id.tv_steps_goal)
        ivStepsGoalEdit = view.findViewById(R.id.iv_steps_goal_edit)
        tvCalories = view.findViewById(R.id.tv_calories)
        viewStepTrend = view.findViewById(R.id.view_step_trend)
        viewSleepTrend = view.findViewById(R.id.view_sleep_trend)
        cardSleep = view.findViewById(R.id.card_sleep)
        tvSleepDuration = view.findViewById(R.id.tv_sleep_duration)
        tvSleepScore = view.findViewById(R.id.tv_sleep_score)
        tvSleepStart = view.findViewById(R.id.tv_sleep_start)
        tvSleepEnd = view.findViewById(R.id.tv_sleep_end)
        ivSleepEdit = view.findViewById(R.id.iv_sleep_edit)
        tvSleepSource = view.findViewById(R.id.tv_sleep_source)
        viewSleepStages = view.findViewById(R.id.view_sleep_stages)
        cardAi = view.findViewById(R.id.card_ai)
        tvAiAnalysis = view.findViewById(R.id.tv_ai_analysis)
        pbAiLoading = view.findViewById(R.id.pb_ai_loading)
        btnAiRefresh = view.findViewById(R.id.btn_ai_refresh)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = requireContext().getSharedPreferences("sleep_manual", Context.MODE_PRIVATE)
        stepGoal = prefs.getInt("step_goal", 8000)

        updateDateDisplay()

        ivDateLeft.setOnClickListener {
            currentDate = currentDate.minusDays(1)
            updateDateDisplay()
            loadData()
        }

        ivDateRight.setOnClickListener {
            if (currentDate < LocalDate.now()) {
                currentDate = currentDate.plusDays(1)
                updateDateDisplay()
                loadData()
            }
        }

        ivSleepEdit.setOnClickListener { showSleepEditDialog() }
        ivSleepEdit.setOnLongClickListener {
            clearSleepManual(currentDate)
            lifecycleScope.launch { loadSleepData() }
            android.widget.Toast.makeText(requireContext(), "已恢复为自动推断", android.widget.Toast.LENGTH_SHORT).show()
            true
        }

        ivStepsGoalEdit.setOnClickListener { showStepGoalDialog() }

        btnRequestPermission.setOnClickListener {
            if (!hasUsageStatsPermission()) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } else if (!hasActivityRecognitionPermission()) {
                requestPermissions(
                    arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                    REQ_ACTIVITY_RECOGNITION
                )
            }
        }

        btnAiRefresh.setOnClickListener { triggerAiAnalysis() }

        lifecycleScope.launch { checkAndLoadData() }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { checkAndLoadData() }
    }

    // ==================== 权限 ====================

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = requireContext().getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            requireContext().packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun hasActivityRecognitionPermission(): Boolean {
        val granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACTIVITY_RECOGNITION)
        return granted == PackageManager.PERMISSION_GRANTED
    }

    private fun allPermissionsGranted(): Boolean =
        hasUsageStatsPermission() && hasActivityRecognitionPermission()

    private suspend fun checkAndLoadData() {
        if (allPermissionsGranted()) {
            showDataCards()
            loadData()
        } else {
            showPermissionGuide()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQ_ACTIVITY_RECOGNITION) {
            lifecycleScope.launch { checkAndLoadData() }
        }
    }

    private fun showPermissionGuide() {
        cardPermissionGuide.visibility = View.VISIBLE
        cardSteps.visibility = View.GONE
        cardSleep.visibility = View.GONE
        cardAi.visibility = View.GONE
    }

    private fun showDataCards() {
        cardPermissionGuide.visibility = View.GONE
        cardSteps.visibility = View.VISIBLE
        cardSleep.visibility = View.VISIBLE
        cardAi.visibility = View.VISIBLE
    }

    // ==================== 日期导航 ====================

    private fun updateDateDisplay() {
        val today = LocalDate.now()
        val text = when {
            currentDate == today -> "${dateFormatter.format(currentDate)}（今天）"
            currentDate == today.minusDays(1) -> "${dateFormatter.format(currentDate)}（昨天）"
            else -> dateFormatter.format(currentDate)
        }
        tvDate.text = text
        ivDateRight.alpha = if (currentDate >= today) 0.3f else 1.0f
        ivDateRight.isEnabled = currentDate < today
    }

    private fun loadData() {
        lifecycleScope.launch {
            try {
                loadStepsData()
                loadSleepData()
                loadTrendData()
                loadSleepTrendData()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ==================== 步数（MIUI ContentProvider） ====================

    private suspend fun loadStepsData() {
        val steps = readSteps(currentDate)
        val percent = (steps.toFloat() / stepGoal * 100).toInt().coerceAtMost(100)
        val calories = (steps * 0.04).toInt()

        withContext(Dispatchers.Main) {
            if (steps == 0L && lastStepsError != null) {
                tvStepsCount.text = "错误"
                tvStepsPercent.text = lastStepsError
                tvStepsGoal.text = ""
                tvCalories.text = ""
            } else {
                tvStepsCount.text = formatNumber(steps)
                tvStepsPercent.text = "${percent}%"
                tvStepsGoal.text = "目标 ${formatNumber(stepGoal.toLong())} 步"
                tvCalories.text = "约 ${formatNumber(calories.toLong())} kcal"
            }
            pbSteps.progress = percent
        }
    }

    private fun readSteps(date: LocalDate): Long {
        return HealthDataHelper.readSteps(requireContext(), date)
    }

    // ==================== 趋势 ====================

    private suspend fun loadTrendData() {
        val dayLabels = mutableListOf<String>()
        val stepsList = mutableListOf<Int>()

        for (i in 6 downTo 0) {
            val date = currentDate.minusDays(i.toLong())
            dayLabels.add(date.format(DateTimeFormatter.ofPattern("MM/dd")))
            stepsList.add(readSteps(date).toInt())
        }

        withContext(Dispatchers.Main) {
            viewStepTrend.setData(stepsList, dayLabels)
        }
    }

    private suspend fun loadSleepTrendData() {
        val dataList = withContext(Dispatchers.Default) {
            val list = mutableListOf<SleepTrendView.DayData>()
            for (i in 6 downTo 0) {
                val date = currentDate.minusDays(i.toLong())
                val label = date.format(DateTimeFormatter.ofPattern("MM/dd"))

                val result = HealthDataHelper.getSleepResult(requireContext(), prefs, date)
                val nightMin = result.night?.durationMinutes ?: 0L
                val napMin = result.nap?.durationMinutes ?: 0L

                list.add(SleepTrendView.DayData(label, nightMin, napMin))
            }
            list
        }

        withContext(Dispatchers.Main) {
            viewSleepTrend.setData(dataList)
        }
    }

    // ==================== 睡眠（屏幕息屏推断） ====================

    private fun getSleepManual(date: LocalDate): HealthDataHelper.NightSleep? {
        return HealthDataHelper.getSleepManual(prefs, date)
    }

    private fun saveSleepManual(date: LocalDate, startMs: Long, endMs: Long) {
        HealthDataHelper.saveSleepManual(prefs, date, startMs, endMs)
    }

    private fun clearSleepManual(date: LocalDate) {
        HealthDataHelper.clearSleepManual(prefs, date)
    }

    private suspend fun loadSleepData() {
        val result = withContext(Dispatchers.Default) {
            HealthDataHelper.getSleepResult(requireContext(), prefs, currentDate)
        }

        withContext(Dispatchers.Main) {
            tvSleepSource.text = if (result.source == HealthDataHelper.SleepSource.MANUAL)
                getString(R.string.sleep_manual) else getString(R.string.sleep_inferred)

            val stageList = mutableListOf<SleepStage>()
            val sleepColor = requireContext().resources.getColor(R.color.sleep_deep, null)
            val awakeColor = requireContext().resources.getColor(R.color.sleep_awake, null)
            val napColor = requireContext().resources.getColor(R.color.sleep_light, null)

            // 夜间睡眠
            val night = result.night
            if (night != null && night.durationMinutes > 0) {
                val h = night.durationMinutes / 60
                val m = night.durationMinutes % 60
                tvSleepDuration.text = "${h}h ${m}m"
                tvSleepStart.text = night.startTime.atZone(ZoneId.systemDefault()).format(timeFormatter)
                tvSleepEnd.text = night.endTime.atZone(ZoneId.systemDefault()).format(timeFormatter)

                stageList.add(SleepStage("睡眠", night.durationMinutes, sleepColor))
                for (gap in night.awakeSegments) {
                    stageList.add(SleepStage("中断", gap.durationMinutes, awakeColor))
                }
            } else {
                tvSleepDuration.text = "无数据"
                tvSleepStart.text = "--"
                tvSleepEnd.text = "--"
            }

            // 午睡
            val nap = result.nap
            if (nap != null) {
                val h = nap.durationMinutes / 60
                val m = nap.durationMinutes % 60
                stageList.add(SleepStage("午睡 ${h}h${m}m", nap.durationMinutes, napColor))
            }

            viewSleepStages.setStages(stageList)

            // 评分
            if (night != null && night.durationMinutes > 0) {
                val awakeTotal = night.awakeSegments.sumOf { it.durationMinutes }
                val score = HealthDataHelper.calcSleepScore(night.durationMinutes, awakeTotal)
                tvSleepScore.text = "${score}分"
                tvSleepScore.visibility = View.VISIBLE
                val scoreColor = when {
                    score >= 80 -> android.graphics.Color.parseColor("#4CAF50")
                    score >= 60 -> android.graphics.Color.parseColor("#FF9800")
                    else -> android.graphics.Color.parseColor("#F44336")
                }
                tvSleepScore.background.setTint(scoreColor)
            } else {
                tvSleepScore.visibility = View.GONE
            }
        }
    }

    /** 手动修改睡眠时间对话框 */
    private fun showSleepEditDialog() {
        val night = getSleepManual(currentDate)
            ?: HealthDataHelper.inferNightSleep(requireContext(), currentDate)

        val defaultStartH: Int
        val defaultStartM: Int
        val defaultEndH: Int
        val defaultEndM: Int

        if (night != null && night.durationMinutes > 0) {
            val startLocal = night.startTime.atZone(ZoneId.systemDefault())
            val endLocal = night.endTime.atZone(ZoneId.systemDefault())
            defaultStartH = startLocal.hour
            defaultStartM = startLocal.minute
            defaultEndH = endLocal.hour
            defaultEndM = endLocal.minute
        } else {
            defaultStartH = 23; defaultStartM = 0
            defaultEndH = 7; defaultEndM = 0
        }

        TimePickerDialog(requireContext(),
            { _, startH, startM ->
                TimePickerDialog(requireContext(),
                    { _, endH, endM ->
                        saveSleepFromPickers(currentDate, startH, startM, endH, endM)
                        lifecycleScope.launch { loadSleepData() }
                    },
                    defaultEndH, defaultEndM, true
                ).apply { setTitle("醒来时间") }.show()
            },
            defaultStartH, defaultStartM, true
        ).apply { setTitle("入睡时间") }.show()
    }

    private fun saveSleepFromPickers(date: LocalDate, startH: Int, startM: Int, endH: Int, endM: Int) {
        val sleepDate = if (startH >= 12) date.minusDays(1) else date
        val startMs = sleepDate.atTime(startH, startM).toInstant(java.time.ZoneOffset.ofHours(8)).toEpochMilli()
        val endMs = date.atTime(endH, endM).toInstant(java.time.ZoneOffset.ofHours(8)).toEpochMilli()
        if (endMs <= startMs) {
            android.widget.Toast.makeText(requireContext(), "醒来时间必须晚于入睡时间", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        saveSleepManual(date, startMs, endMs)
    }

    private fun showStepGoalDialog() {
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.setText(stepGoal.toString())
        input.setSelection(input.text.length)

        AlertDialog.Builder(requireContext())
            .setTitle("设置每日步数目标")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val s = input.text.toString().trim()
                if (s.isNotEmpty()) {
                    val newGoal = s.toIntOrNull()
                    if (newGoal != null && newGoal > 0 && newGoal <= 100000) {
                        stepGoal = newGoal
                        prefs.edit().putInt("step_goal", newGoal).apply()
                        tvStepsGoal.text = "目标 ${formatNumber(stepGoal.toLong())} 步"
                        lifecycleScope.launch { loadStepsData() }
                    } else {
                        android.widget.Toast.makeText(requireContext(), "请输入 1 ~ 100000 的有效目标", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== AI 分析 ====================

    private fun triggerAiAnalysis() {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                pbAiLoading.visibility = View.VISIBLE
                tvAiAnalysis.text = ""
            }

            try {
                val steps = readSteps(currentDate)
                val sleepResult = HealthDataHelper.getSleepResult(requireContext(), prefs, currentDate)
                val night = sleepResult.night
                val nap = sleepResult.nap

                val prompt = buildString {
                    append("请对以下健康数据进行综合分析，给出简短的健康建议（150字以内）：\n")
                    append("- 步数：${formatNumber(steps)} 步（目标 ${stepGoal} 步）\n")
                    if (night != null && night.durationMinutes > 0) {
                        val h = night.durationMinutes / 60
                        val m = night.durationMinutes % 60
                        append("- 夜间睡眠：${h}小时${m}分钟（通过屏幕息屏推断）\n")
                        if (night.awakeSegments.isNotEmpty()) {
                            append("- 夜间中断：${night.awakeSegments.size}次\n")
                        }
                    } else {
                        append("- 夜间睡眠：无数据\n")
                    }
                    if (nap != null) {
                        val h = nap.durationMinutes / 60
                        val m = nap.durationMinutes % 60
                        append("- 午睡：${h}小时${m}分钟\n")
                    }
                }

                val result = callDeepSeek(prompt)
                withContext(Dispatchers.Main) {
                    pbAiLoading.visibility = View.GONE
                    tvAiAnalysis.text = result ?: "AI 分析暂时不可用，请稍后重试"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pbAiLoading.visibility = View.GONE
                    tvAiAnalysis.text = "获取分析失败：${e.message}"
                }
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
                put("max_tokens", 300)
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

    private fun formatNumber(num: Long): String = String.format("%,d", num)

    override fun onDestroyView() {
        super.onDestroyView()
        _rootView = null
    }
}
