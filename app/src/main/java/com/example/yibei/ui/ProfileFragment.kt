package com.example.yibei.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Outline
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.yibei.R
import com.example.yibei.ui.auth.LoginRegisterActivity
import com.example.yibei.data.HealthDataHelper
import com.example.yibei.data.UserManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

class ProfileFragment : Fragment() {

    private var _rootView: View? = null
    private lateinit var prefs: SharedPreferences

    // 登录结果回调
    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            refreshUserUI()
        }
    }

    // 头像选择回调
    private val avatarPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { saveAvatarImage(it) }
    }

    // 用户信息
    private lateinit var layoutUserInfo: LinearLayout
    private lateinit var avatarFrame: FrameLayout
    private lateinit var ivDefaultAvatar: ImageView
    private lateinit var ivAvatar: ImageView
    private lateinit var tvUserName: TextView
    private lateinit var tvUserTitle: TextView
    private lateinit var btnScan: ImageView
    private lateinit var btnNotification: ImageView
    private lateinit var btnSettings: ImageView

    // 环形进度
    private lateinit var ringWater: RingProgressView
    private lateinit var ringSleep: RingProgressView
    private lateinit var ringSteps: RingProgressView

    // 统计摘要
    private lateinit var statsWater: StatsHighlightView
    private lateinit var statsSleep: StatsHighlightView
    private lateinit var statsSteps: StatsHighlightView

    // 7天趋势
    private lateinit var waterTrend7d: MiniTrendView
    private lateinit var sleepTrend7d: MiniTrendView
    private lateinit var stepsTrend7d: MiniTrendView

    // 30天趋势
    private lateinit var waterTrend30d: MiniTrendView
    private lateinit var sleepTrend30d: MiniTrendView
    private lateinit var stepsTrend30d: MiniTrendView

    // 30天展开入口
    private lateinit var tvWaterExpand30d: TextView
    private lateinit var tvSleepExpand30d: TextView
    private lateinit var tvStepsExpand30d: TextView
    private var water30dExpanded = false
    private var sleep30dExpanded = false
    private var steps30dExpanded = false

    // 目标
    private lateinit var tvWaterGoal: TextView

    // 睡眠时间
    private lateinit var tvEarliestSleep: TextView
    private lateinit var tvLatestSleep: TextView
    private lateinit var tvEarliestWake: TextView
    private lateinit var tvLatestWake: TextView

    // AI
    private lateinit var cardAi: CardView
    private lateinit var tvAiAnalysis: TextView
    private lateinit var pbAiLoading: ProgressBar
    private lateinit var btnAiRefresh: Button

    // 喝水高峰
    private lateinit var tvWaterPeak: TextView

    // 周报
    private lateinit var cardWeeklyReport: CardView
    private lateinit var tvWeeklyRange: TextView
    private lateinit var tvWeeklyWater: TextView
    private lateinit var tvWeeklySleep: TextView
    private lateinit var tvWeeklySteps: TextView

    private var cachedWeeklyWAvg = 0.0
    private var cachedWeeklySAvg = 0.0
    private var cachedWeeklyStAvg = 0.0
    private var cachedWeeklyDailyGoal = 2000
    private var cachedWeeklyStartLabel = ""
    private var cachedWeeklyEndLabel = ""

    private var cachedWater7d: List<Float> = emptyList()
    private var cachedSleep7d: List<Float> = emptyList()
    private var cachedSteps7d: List<Float> = emptyList()
    private var cachedLabels7d: List<String> = emptyList()
    private var cachedWaterAvg = 0f
    private var cachedSleepAvg = 0f
    private var cachedStepsAvg = 0f

    // 30天月度缓存
    private var cachedWater30d: List<Float> = emptyList()
    private var cachedSleep30d: List<Float> = emptyList()
    private var cachedSteps30d: List<Float> = emptyList()
    private var cachedWaterAvg30d = 0f
    private var cachedSleepAvg30d = 0f
    private var cachedStepsAvg30d = 0f
    private var cachedDailyGoal30d = 0

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val labelFormatter7d = DateTimeFormatter.ofPattern("MM/dd")
    private val labelFormatter30d = DateTimeFormatter.ofPattern("M/d")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val beijingZone = ZoneId.of("Asia/Shanghai")

    companion object {
        private const val DEEPSEEK_API_KEY = "YOUR_DEEPSEEK_API_KEY"
        private const val DEEPSEEK_URL = "https://api.deepseek.com/chat/completions"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)
        _rootView = view
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences("water_prefs", Context.MODE_PRIVATE)
        UserManager.init(requireContext())

        // 用户信息
        layoutUserInfo = view.findViewById(R.id.layout_user_info)
        avatarFrame = view.findViewById(R.id.frame_avatar)
        ivDefaultAvatar = view.findViewById(R.id.iv_default_avatar)
        ivAvatar = view.findViewById(R.id.iv_avatar)

        // 圆形裁剪
        val circleOutline = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        ivDefaultAvatar.outlineProvider = circleOutline
        ivDefaultAvatar.clipToOutline = true
        ivAvatar.outlineProvider = circleOutline
        ivAvatar.clipToOutline = true
        tvUserName = view.findViewById(R.id.tv_user_name)
        tvUserTitle = view.findViewById(R.id.tv_user_title)
        btnScan = view.findViewById(R.id.btn_scan)
        btnNotification = view.findViewById(R.id.btn_notification)
        btnSettings = view.findViewById(R.id.btn_settings)

        ringWater = view.findViewById(R.id.ring_water)
        ringSleep = view.findViewById(R.id.ring_sleep)
        ringSteps = view.findViewById(R.id.ring_steps)

        statsWater = view.findViewById(R.id.stats_water)
        statsSleep = view.findViewById(R.id.stats_sleep)
        statsSteps = view.findViewById(R.id.stats_steps)

        waterTrend7d = view.findViewById(R.id.view_water_trend_7d)
        sleepTrend7d = view.findViewById(R.id.view_sleep_trend_7d)
        stepsTrend7d = view.findViewById(R.id.view_steps_trend_7d)

        waterTrend30d = view.findViewById(R.id.view_water_trend_30d)
        sleepTrend30d = view.findViewById(R.id.view_sleep_trend_30d)
        stepsTrend30d = view.findViewById(R.id.view_steps_trend_30d)

        tvWaterExpand30d = view.findViewById(R.id.tv_water_expand_30d)
        tvSleepExpand30d = view.findViewById(R.id.tv_sleep_expand_30d)
        tvStepsExpand30d = view.findViewById(R.id.tv_steps_expand_30d)

        tvWaterGoal = view.findViewById(R.id.tv_water_goal)

        tvEarliestSleep = view.findViewById(R.id.tv_earliest_sleep)
        tvLatestSleep = view.findViewById(R.id.tv_latest_sleep)
        tvEarliestWake = view.findViewById(R.id.tv_earliest_wake)
        tvLatestWake = view.findViewById(R.id.tv_latest_wake)

        cardAi = view.findViewById(R.id.card_ai)
        tvAiAnalysis = view.findViewById(R.id.tv_ai_analysis)
        pbAiLoading = view.findViewById(R.id.pb_ai_loading)
        btnAiRefresh = view.findViewById(R.id.btn_ai_refresh)
        tvWaterPeak = view.findViewById(R.id.tv_water_peak)

        // 周报
        cardWeeklyReport = view.findViewById(R.id.card_weekly_report)
        tvWeeklyRange = view.findViewById(R.id.tv_weekly_range)
        tvWeeklyWater = view.findViewById(R.id.tv_weekly_water)
        tvWeeklySleep = view.findViewById(R.id.tv_weekly_sleep)
        tvWeeklySteps = view.findViewById(R.id.tv_weekly_steps)

        btnAiRefresh.setOnClickListener { triggerAiAnalysis() }

        // 会员入口
        val cardMember: CardView = view.findViewById(R.id.card_member)
        val btnOpenMember: TextView = view.findViewById(R.id.btn_open_member)
        cardMember.setOnClickListener { /* TODO: 会员开通页面 */ }
        btnOpenMember.setOnClickListener { /* TODO: 会员开通页面 */ }

        btnScan.setOnClickListener {
            // 扫码按钮仅作装饰，无实际功能
        }

        btnNotification.setOnClickListener {
            // 消息通知按钮 — 暂无实际功能
        }

        layoutUserInfo.setOnClickListener {
            if (!UserManager.hasLogin()) {
                loginLauncher.launch(Intent(requireContext(), LoginRegisterActivity::class.java))
            }
        }

        btnSettings.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SettingsFragment())
                .addToBackStack("settings")
                .commit()
        }

        cardWeeklyReport.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, WeeklyReportFragment())
                .addToBackStack("weekly_report")
                .commit()
        }

        // 30天趋势展开/折叠
        tvWaterExpand30d.setOnClickListener { toggle30d("water") }
        tvSleepExpand30d.setOnClickListener { toggle30d("sleep") }
        tvStepsExpand30d.setOnClickListener { toggle30d("steps") }

        loadData()
        loadWeeklyReport()
        refreshUserUI()
        applyDefaultProfileTheme()
    }

    override fun onResume() {
        super.onResume()
        if (::prefs.isInitialized) {
            refreshUserUI()
        }
    }

    private fun refreshUserUI() {
        if (UserManager.hasLogin()) {
            // 检查自定义头像
            if (UserManager.hasCustomAvatar()) {
                ivAvatar.setImageURI(Uri.fromFile(File(UserManager.avatarPath)))
                ivAvatar.visibility = View.VISIBLE
                ivDefaultAvatar.visibility = View.GONE
            } else {
                ivDefaultAvatar.visibility = View.VISIBLE
                ivAvatar.visibility = View.GONE
            }
            tvUserName.text = UserManager.name
            tvUserTitle.text = UserManager.title
            tvUserTitle.visibility = View.VISIBLE
            btnScan.visibility = View.VISIBLE
            btnNotification.visibility = View.VISIBLE
            btnSettings.visibility = View.VISIBLE
            layoutUserInfo.setOnClickListener(null)
            layoutUserInfo.isClickable = false
            // 头像点击 → 选择图片
            avatarFrame.setOnClickListener { avatarPickerLauncher.launch("image/*") }
        } else {
            ivDefaultAvatar.visibility = View.VISIBLE
            ivAvatar.visibility = View.GONE
            tvUserName.text = "点击头像登录"
            tvUserTitle.visibility = View.GONE
            btnScan.visibility = View.GONE
            btnNotification.visibility = View.GONE
            btnSettings.visibility = View.GONE
            layoutUserInfo.setOnClickListener {
                loginLauncher.launch(Intent(requireContext(), LoginRegisterActivity::class.java))
            }
            layoutUserInfo.isClickable = true
            avatarFrame.setOnClickListener {
                loginLauncher.launch(Intent(requireContext(), LoginRegisterActivity::class.java))
            }
        }
    }

    private fun applyDefaultProfileTheme() {
        // 使用默认蓝色主题
        layoutUserInfo.background?.let { bg ->
            if (bg is android.graphics.drawable.GradientDrawable) {
                bg.setColors(intArrayOf(0xFF2196F3.toInt(), 0xFF1976D2.toInt()))
            }
        }
        // 默认头像底色
        ivDefaultAvatar.setBackgroundColor(0xFFB3E5FC.toInt())
    }

    private fun saveAvatarImage(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return
            val avatarDir = File(requireContext().filesDir, "avatar")
            if (!avatarDir.exists()) avatarDir.mkdirs()
            val avatarFile = File(avatarDir, "avatar.jpg")
            FileOutputStream(avatarFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            UserManager.setAvatarPath(avatarFile.absolutePath)
            refreshUserUI()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadData() {
        val dailyGoal = prefs.getInt("daily_goal", 2000)
        tvWaterGoal.text = "目标 ${dailyGoal}ml"

        lifecycleScope.launch {
            try {
                val today = LocalDate.now()

                // ========== 7 天数据 ==========
                val labels7d = mutableListOf<String>()
                val water7d = mutableListOf<Float>()
                val sleep7d = mutableListOf<Float>()
                val steps7d = mutableListOf<Float>()
                val sleepStarts = mutableListOf<Instant>()
                val sleepEnds = mutableListOf<Instant>()

                for (i in 6 downTo 0) {
                    val date = today.minusDays(i.toLong())
                    labels7d.add(date.format(labelFormatter7d))
                    water7d.add(HealthDataHelper.readWaterAmount(prefs, date).toFloat())
                    steps7d.add(HealthDataHelper.readSteps(requireContext(), date).toFloat())
                    val result = withContext(Dispatchers.Default) {
                        HealthDataHelper.getSleepResult(requireContext(), prefs, date)
                    }
                    sleep7d.add(((result.night?.durationMinutes ?: 0L) + (result.nap?.durationMinutes ?: 0L)) / 60f)

                    // 收集睡眠起止时间
                    val night = result.night
                    if (night != null) {
                        sleepStarts.add(night.startTime)
                        sleepEnds.add(night.endTime)
                    }
                }

                // ========== 30 天数据 ==========
                val labels30d = mutableListOf<String>()
                val water30d = mutableListOf<Float>()
                val sleep30d = mutableListOf<Float>()
                val steps30d = mutableListOf<Float>()

                for (i in 29 downTo 0) {
                    val date = today.minusDays(i.toLong())
                    // 每5天显示一个标签，节省空间
                    labels30d.add(if (i % 5 == 0 || i == 0) date.format(labelFormatter30d) else "")
                    water30d.add(HealthDataHelper.readWaterAmount(prefs, date).toFloat())
                    steps30d.add(HealthDataHelper.readSteps(requireContext(), date).toFloat())
                    val result = withContext(Dispatchers.Default) {
                        HealthDataHelper.getSleepResult(requireContext(), prefs, date)
                    }
                    sleep30d.add(((result.night?.durationMinutes ?: 0L) + (result.nap?.durationMinutes ?: 0L)) / 60f)
                }

                withContext(Dispatchers.Main) {
                    val validWater = water7d.filter { it > 0 }
                    val validSleep = sleep7d.filter { it > 0 }
                    val validSteps = steps7d.filter { it > 0 }

                    val waterAvg = validWater.average().let { if (it.isNaN()) 0f else it.toFloat() }
                    val sleepAvg = validSleep.average().let { if (it.isNaN()) 0f else it.toFloat() }
                    val stepsAvg = validSteps.average().let { if (it.isNaN()) 0f else it.toFloat() }

                    cachedWater7d = water7d; cachedSleep7d = sleep7d; cachedSteps7d = steps7d
                    cachedLabels7d = labels7d
                    cachedWaterAvg = waterAvg; cachedSleepAvg = sleepAvg; cachedStepsAvg = stepsAvg

                    // 环形进度
                    ringWater.setData(
                        progress = (water7d.last() / dailyGoal * 100f).coerceAtMost(100f),
                        valueText = formatMl(water7d.last()),
                        label = "喝水",
                        subLabel = "目标 ${formatMl(dailyGoal.toFloat())}",
                        color = 0xFF4FC3F7.toInt()
                    )
                    ringSleep.setData(
                        progress = (sleep7d.last() / 8f * 100f).coerceAtMost(100f),
                        valueText = String.format("%.1fh", sleep7d.last()),
                        label = "睡眠",
                        subLabel = "目标 8.0h",
                        color = 0xFF7986CB.toInt()
                    )
                    ringSteps.setData(
                        progress = (steps7d.last() / 10000f * 100f).coerceAtMost(100f),
                        valueText = formatSteps(steps7d.last()),
                        label = "步数",
                        subLabel = "目标 1万步",
                        color = 0xFFFF8A65.toInt()
                    )

                    // 统计摘要
                    statsWater.setData("", formatMl(validWater.maxOrNull() ?: 0f),
                        "", formatMl(validWater.minOrNull() ?: 0f),
                        "", formatMl(waterAvg))
                    statsSleep.setData("", String.format("%.1fh", validSleep.maxOrNull() ?: 0f),
                        "", String.format("%.1fh", validSleep.minOrNull() ?: 0f),
                        "", String.format("%.1fh", sleepAvg))

                    // 睡眠起止时间统计
                    if (sleepStarts.isNotEmpty()) {
                        val minStart = sleepStarts.minByOrNull { it.atZone(beijingZone).toLocalTime() }
                        val maxStart = sleepStarts.maxByOrNull { it.atZone(beijingZone).toLocalTime() }
                        val minEnd = sleepEnds.minByOrNull { it.atZone(beijingZone).toLocalTime() }
                        val maxEnd = sleepEnds.maxByOrNull { it.atZone(beijingZone).toLocalTime() }
                        tvEarliestSleep.text = minStart?.atZone(beijingZone)?.format(timeFormatter) ?: "--:--"
                        tvLatestSleep.text = maxStart?.atZone(beijingZone)?.format(timeFormatter) ?: "--:--"
                        tvEarliestWake.text = minEnd?.atZone(beijingZone)?.format(timeFormatter) ?: "--:--"
                        tvLatestWake.text = maxEnd?.atZone(beijingZone)?.format(timeFormatter) ?: "--:--"
                    } else {
                        tvEarliestSleep.text = "--:--"
                        tvLatestSleep.text = "--:--"
                        tvEarliestWake.text = "--:--"
                        tvLatestWake.text = "--:--"
                    }
                    statsSteps.setData("", formatSteps(validSteps.maxOrNull() ?: 0f),
                        "", formatSteps(validSteps.minOrNull() ?: 0f),
                        "", formatSteps(stepsAvg))

                    // 7天趋势图
                    waterTrend7d.setData(water7d, labels7d, "#4FC3F7")
                    tvWaterPeak.text = HealthDataHelper.getWaterPeakHour(prefs, 7)
                    sleepTrend7d.setData(sleep7d, labels7d, "#7986CB")

                    // 睡眠入睡/起床时间叠加线
                    if (sleepStarts.isNotEmpty()) {
                        val startNorm = sleepStarts.map { inst ->
                            val beijing = inst.atZone(beijingZone)
                            val h = beijing.hour + beijing.minute / 60f
                            // 入睡时间归一化：20:00=0，跨午夜自动+24
                            if (h >= 20f) h - 20f else h + 4f
                        }
                        val endNorm = sleepEnds.map { inst ->
                            val beijing = inst.atZone(beijingZone)
                            val h = beijing.hour + beijing.minute / 60f
                            // 起床时间归一化：05:00=0
                            h - 5f
                        }
                        sleepTrend7d.setSleepTimeOverlay(startNorm, endNorm)
                    }

                    stepsTrend7d.setData(steps7d, labels7d, "#FF8A65")

                    // 30天趋势图
                    waterTrend30d.setData(water30d, labels30d, "#4FC3F7")
                    sleepTrend30d.setData(sleep30d, labels30d, "#7986CB")
                    stepsTrend30d.setData(steps30d, labels30d, "#FF8A65")

                    // 缓存30天数据
                    val validWater30 = water30d.filter { it > 0 }
                    val validSleep30 = sleep30d.filter { it > 0 }
                    val validSteps30 = steps30d.filter { it > 0 }
                    cachedWater30d = water30d; cachedSleep30d = sleep30d; cachedSteps30d = steps30d
                    cachedWaterAvg30d = validWater30.average().let { if (it.isNaN()) 0f else it.toFloat() }
                    cachedSleepAvg30d = validSleep30.average().let { if (it.isNaN()) 0f else it.toFloat() }
                    cachedStepsAvg30d = validSteps30.average().let { if (it.isNaN()) 0f else it.toFloat() }
                    cachedDailyGoal30d = dailyGoal

                    cardAi.visibility = View.VISIBLE
                    triggerAiAnalysisInternal(water30d, sleep30d, steps30d,
                        cachedWaterAvg30d, cachedSleepAvg30d, cachedStepsAvg30d, dailyGoal)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun formatMl(v: Float): String =
        if (v >= 1000f) String.format("%.1fL", v / 1000f) else String.format("%.0fml", v)

    private fun formatSteps(v: Float): String =
        if (v >= 10000f) String.format("%.1f万", v / 10000f) else String.format("%.0f", v)

    private fun triggerAiAnalysis() {
        if (cachedWater30d.isEmpty()) { loadData(); return }
        lifecycleScope.launch {
            triggerAiAnalysisInternal(cachedWater30d, cachedSleep30d, cachedSteps30d,
                cachedWaterAvg30d, cachedSleepAvg30d, cachedStepsAvg30d, cachedDailyGoal30d)
        }
    }

    private suspend fun triggerAiAnalysisInternal(
        waterList: List<Float>, sleepList: List<Float>, stepsList: List<Float>,
        waterAvg: Float, sleepAvg: Float, stepsAvg: Float,
        dailyGoal: Int
    ) {
        withContext(Dispatchers.Main) {
            pbAiLoading.visibility = View.VISIBLE
            tvAiAnalysis.text = ""
        }

        try {
            // 分周统计，提供宏观概览
            val weekCount = (waterList.size + 6) / 7
            val weekSummaries = (0 until weekCount).map { w ->
                val start = w * 7
                val end = minOf(start + 7, waterList.size)
                val wWater = waterList.subList(start, end)
                val wSleep = sleepList.subList(start, end)
                val wSteps = stepsList.subList(start, end)
                Triple(
                    wWater.average().let { if (it.isNaN()) 0.0 else it },
                    wSleep.average().let { if (it.isNaN()) 0.0 else it },
                    wSteps.average().let { if (it.isNaN()) 0.0 else it }
                )
            }

            val prompt = buildString {
                append("请基于以下近30天健康数据做月度宏观分析，关注整体趋势和周期性规律，给出总结和改善建议（200字以内，中文）：\n\n")
                append("【喝水】月均%.0fml（目标%dml/日），达标率约%.0f%%\n".format(waterAvg, dailyGoal, (waterAvg / dailyGoal * 100f).coerceAtMost(200f)))
                append("【睡眠】月均%.1fh\n".format(sleepAvg))
                append("【步数】月均%.0f步\n".format(stepsAvg))
                if (weekSummaries.size >= 2) {
                    append("\n分周趋势：\n")
                    for (i in weekSummaries.indices) {
                        val ws = weekSummaries[i]
                        append("第${i + 1}周：喝水%.0fml / 睡眠%.1fh / 步数%.0f步\n".format(ws.first, ws.second, ws.third))
                    }
                }
                append("\n请从月度视角分析整体健康状态、各维度趋势变化、是否存在规律性波动，并给出改善方向。")
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

    // ==================== 上周健康周报卡片 ====================

    private fun loadWeeklyReport() {
        lifecycleScope.launch {
            try {
                val today = LocalDate.now()
                val dailyGoal = prefs.getInt("daily_goal", 2000)

                // 自然周
                val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val thisSunday = thisMonday.plusDays(6)
                val isCurrentWeekComplete = today.dayOfWeek == DayOfWeek.SUNDAY
                val (weekStart, weekEnd) = if (isCurrentWeekComplete) {
                    thisMonday to thisSunday
                } else {
                    thisMonday.minusDays(7) to thisSunday.minusDays(7)
                }

                val startLabel = weekStart.format(labelFormatter7d)
                val endLabel = weekEnd.format(labelFormatter7d)
                cachedWeeklyStartLabel = startLabel; cachedWeeklyEndLabel = endLabel
                cachedWeeklyDailyGoal = dailyGoal
                val weekLabel = "$startLabel - $endLabel"
                withContext(Dispatchers.Main) { tvWeeklyRange.text = weekLabel }

                val waters = mutableListOf<Float>()
                val sleeps = mutableListOf<Float>()
                val steps = mutableListOf<Float>()

                for (i in 0..6) {
                    val date = weekStart.plusDays(i.toLong())
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
                cachedWeeklyWAvg = wAvg; cachedWeeklySAvg = sAvg; cachedWeeklyStAvg = stAvg

                withContext(Dispatchers.Main) {
                    tvWeeklyWater.text = formatMl(wAvg.toFloat())
                    tvWeeklySleep.text = String.format("%.1fh", sAvg)
                    tvWeeklySteps.text = formatSteps(stAvg.toFloat())
                }
            } catch (_: Exception) { }
        }
    }

    private fun toggle30d(type: String) {
        when (type) {
            "water" -> {
                water30dExpanded = !water30dExpanded
                waterTrend30d.visibility = if (water30dExpanded) View.VISIBLE else View.GONE
                tvWaterExpand30d.text = if (water30dExpanded) "近30天 ▾" else "近30天 ▸"
            }
            "sleep" -> {
                sleep30dExpanded = !sleep30dExpanded
                sleepTrend30d.visibility = if (sleep30dExpanded) View.VISIBLE else View.GONE
                tvSleepExpand30d.text = if (sleep30dExpanded) "近30天 ▾" else "近30天 ▸"
            }
            "steps" -> {
                steps30dExpanded = !steps30dExpanded
                stepsTrend30d.visibility = if (steps30dExpanded) View.VISIBLE else View.GONE
                tvStepsExpand30d.text = if (steps30dExpanded) "近30天 ▾" else "近30天 ▸"
            }
        }
    }

    private fun formatSleepTimeRange(start: Instant?, end: Instant?): String {
        if (start == null || end == null) return "--"
        return "${start.atZone(ZoneId.of("Asia/Shanghai")).format(timeFormatter)} - ${end.atZone(ZoneId.of("Asia/Shanghai")).format(timeFormatter)}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _rootView = null
    }
}
