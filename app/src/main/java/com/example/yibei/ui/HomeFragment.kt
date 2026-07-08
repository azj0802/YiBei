package com.example.yibei.ui

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.text.TextWatcher
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.yibei.R
import com.example.yibei.databinding.FragmentHomeBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: SharedPreferences
    private var dailyGoal = 2000
    private var todayAmount = 0
    private var customCupAmount = 300

    // 智能计算状态
    private var smartGender = "male"
    private var smartHealth = "healthy"
    private var weatherTemp = 25
    private var weatherHumidity = 60

    // 定位相关
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onLocationPermissionResult(granted) }

    private var locationCallback: ((Boolean) -> Unit)? = null

    // 喝水弹窗提醒
    private val reminderHandler = Handler(Looper.getMainLooper())
    private var reminderRunnable: Runnable? = null

    // 通知权限（Android 13+）
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private fun onLocationPermissionResult(granted: Boolean) {
        locationCallback?.invoke(granted)
        locationCallback = null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences("water_prefs", Context.MODE_PRIVATE)

        loadData()
        updateUI()
        applyDefaultHomeTheme()

        // 创建通知渠道 + 启动提醒
        createNotificationChannel()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        scheduleReminder()

        binding.btnMenu.setOnClickListener { showBottomSheet() }

        binding.btnCustomCup.setOnClickListener {
            addWater(customCupAmount)
        }

        // 长按激励语触发弹窗测试
        binding.tvMotivation.setOnLongClickListener {
            triggerReminderDialog()
            true
        }
    }

    private fun loadData() {
        dailyGoal = prefs.getInt("daily_goal", 2000)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        todayAmount = prefs.getInt("amount_$today", 0)
        customCupAmount = prefs.getInt("custom_cup_amount", 300)
    }

    private fun updateUI() {
        val progress = (todayAmount.toFloat() / dailyGoal).coerceAtMost(1f)
        binding.tvAmount.text = "$todayAmount"
        binding.tvSubtitle.text = "目标 ${dailyGoal}ml  ·  ${(progress * 100).toInt()}%"

        binding.waterWave.waterLevel = progress

        binding.tvMotivation.text = when {
            todayAmount >= dailyGoal -> "目标达成，继续保持！"
            todayAmount >= dailyGoal * 0.7 -> "还差一点，加油！"
            todayAmount >= dailyGoal * 0.4 -> "进度过半，继续补充水分"
            todayAmount > 0 -> "好的开始，记得持续喝水"
            else -> "今天还没喝水，快喝一杯吧"
        }

        binding.btnCustomCup.text = "+${customCupAmount}ml"
    }

    private fun applyDefaultHomeTheme() {
        // 使用白色主题
        binding.root.setBackgroundColor(0xFFFFFFFF.toInt())
        binding.waterWave.applyThemeColors(0xFFFFFFFF.toInt(), 0xFF4FC3F7.toInt(), 0xFF0277BD.toInt())
        binding.tvAmount.setTextColor(0xFF1976D2.toInt())
        binding.waterWave?.waterLevel = binding.waterWave.waterLevel
    }

    private fun showBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_home_menu, null)
        dialog.setContentView(sheetView)

        // 锁定 BottomSheet 为全展开，禁止拖拽关闭，避免与 ScrollView 滚动冲突
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            val behavior = BottomSheetBehavior.from(bottomSheet!!)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
        }

        // ── 智能饮水计算 收起按钮 ──
        val tvSmartToggle = sheetView.findViewById<TextView>(R.id.tv_smart_toggle)
        tvSmartToggle.setOnClickListener {
            dialog.dismiss()
        }

        val etGoal = sheetView.findViewById<EditText>(R.id.et_goal)
        val etCustom = sheetView.findViewById<EditText>(R.id.et_custom_amount)

        sheetView.findViewById<Button>(R.id.btn_add_200).setOnClickListener {
            addWater(200); dialog.dismiss()
        }
        sheetView.findViewById<Button>(R.id.btn_add_300).setOnClickListener {
            addWater(300); dialog.dismiss()
        }
        sheetView.findViewById<Button>(R.id.btn_add_500).setOnClickListener {
            addWater(500); dialog.dismiss()
        }

        sheetView.findViewById<Button>(R.id.btn_custom_amount).setOnClickListener {
            val amount = etCustom.text.toString().toIntOrNull()
            if (amount != null && amount in 1..5000) {
                addWater(amount)
                etCustom.text?.clear()
                dialog.dismiss()
            } else {
                Snackbar.make(sheetView, "请输入有效水量（1-5000ml）", Snackbar.LENGTH_SHORT).show()
            }
        }

        sheetView.findViewById<Button>(R.id.btn_set_goal).setOnClickListener {
            val goal = etGoal.text.toString().toIntOrNull()
            if (goal != null && goal in 500..10000) {
                dailyGoal = goal
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val editor = prefs.edit()
                editor.putInt("daily_goal", goal)
                // 清零当日饮水和记录
                todayAmount = 0
                editor.putInt("amount_$today", 0)
                val recordCount = prefs.getInt("record_count_$today", 0)
                for (i in 0 until recordCount) {
                    editor.remove("record_time_${today}_$i")
                    editor.remove("record_amount_${today}_$i")
                }
                editor.putInt("record_count_$today", 0)
                editor.apply()
                updateUI()
                // 目标重置后重新开始弹窗提醒
                cancelReminder()
                scheduleReminder()
                dialog.dismiss()
            } else {
                Snackbar.make(sheetView, "请输入500-10000之间的数值", Snackbar.LENGTH_SHORT).show()
            }
        }

        // ── 自定义杯子设置 ──
        val etCupAmount = sheetView.findViewById<EditText>(R.id.et_cup_amount)
        etCupAmount.setText(customCupAmount.toString())

        sheetView.findViewById<Button>(R.id.btn_set_cup).setOnClickListener {
            val cup = etCupAmount.text.toString().toIntOrNull()
            if (cup != null && cup in 1..5000) {
                customCupAmount = cup
                prefs.edit().putInt("custom_cup_amount", cup).apply()
                updateUI()
                dialog.dismiss()
            } else {
                Snackbar.make(sheetView, "请输入有效毫升数（1-5000）", Snackbar.LENGTH_SHORT).show()
            }
        }

        // ── 喝水记录 ──
        val tvHistory = sheetView.findViewById<TextView>(R.id.tv_drink_history)
        tvHistory.text = loadDrinkHistory()

        // ── 智能饮水计算 ──
        setupSmartCalculator(sheetView)

        dialog.show()
    }

    private fun setupSmartCalculator(sheetView: View) {
        // 恢复上次保存的数据
        smartGender = prefs.getString("smart_gender", "male") ?: "male"
        smartHealth = prefs.getString("smart_health", "healthy") ?: "healthy"
        weatherTemp = prefs.getInt("weather_temp", 25)
        weatherHumidity = prefs.getInt("weather_humidity", 60)

        // 性别切换
        val btnMale = sheetView.findViewById<TextView>(R.id.btn_male)
        val btnFemale = sheetView.findViewById<TextView>(R.id.btn_female)

        fun updateGenderUI() {
            if (smartGender == "male") {
                btnMale.setBackgroundResource(R.drawable.tab_active_bg)
                btnMale.setTextColor(Color.WHITE)
                btnFemale.setBackgroundResource(R.drawable.tab_inactive_bg)
                btnFemale.setTextColor(0xFF666666.toInt())
            } else {
                btnFemale.setBackgroundResource(R.drawable.tab_active_bg)
                btnFemale.setTextColor(Color.WHITE)
                btnMale.setBackgroundResource(R.drawable.tab_inactive_bg)
                btnMale.setTextColor(0xFF666666.toInt())
            }
        }
        updateGenderUI()

        btnMale.setOnClickListener {
            smartGender = "male"; updateGenderUI()
            prefs.edit().putString("smart_gender", "male").apply()
        }
        btnFemale.setOnClickListener {
            smartGender = "female"; updateGenderUI()
            prefs.edit().putString("smart_gender", "female").apply()
        }

        // 健康状态切换
        val btnHealthy = sheetView.findViewById<TextView>(R.id.btn_healthy)
        val btnChronic = sheetView.findViewById<TextView>(R.id.btn_chronic)

        fun updateHealthUI() {
            if (smartHealth == "healthy") {
                btnHealthy.setBackgroundResource(R.drawable.tab_active_bg)
                btnHealthy.setTextColor(Color.WHITE)
                btnChronic.setBackgroundResource(R.drawable.tab_inactive_bg)
                btnChronic.setTextColor(0xFF666666.toInt())
            } else {
                btnChronic.setBackgroundResource(R.drawable.tab_active_bg)
                btnChronic.setTextColor(Color.WHITE)
                btnHealthy.setBackgroundResource(R.drawable.tab_inactive_bg)
                btnHealthy.setTextColor(0xFF666666.toInt())
            }
        }
        updateHealthUI()

        btnHealthy.setOnClickListener {
            smartHealth = "healthy"; updateHealthUI()
            prefs.edit().putString("smart_health", "healthy").apply()
        }
        btnChronic.setOnClickListener {
            smartHealth = "chronic"; updateHealthUI()
            prefs.edit().putString("smart_health", "chronic").apply()
        }

        // SeekBar 绑定
        val seekAge = sheetView.findViewById<SeekBar>(R.id.seek_age)
        val tvAge = sheetView.findViewById<TextView>(R.id.tv_age)
        seekAge.progress = prefs.getInt("smart_age", 29)
        seekAge.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { tvAge.text = "${p + 1}岁" }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {
                prefs.edit().putInt("smart_age", s!!.progress).apply()
            }
        })

        val seekWeight = sheetView.findViewById<SeekBar>(R.id.seek_weight)
        val tvWeight = sheetView.findViewById<TextView>(R.id.tv_weight)
        seekWeight.progress = prefs.getInt("smart_weight", 28)
        seekWeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { tvWeight.text = "${p + 40}kg" }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {
                prefs.edit().putInt("smart_weight", s!!.progress).apply()
            }
        })

        val seekHeight = sheetView.findViewById<SeekBar>(R.id.seek_height)
        val tvHeight = sheetView.findViewById<TextView>(R.id.tv_height)
        seekHeight.progress = prefs.getInt("smart_height", 30)
        seekHeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { tvHeight.text = "${p + 140}cm" }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {
                prefs.edit().putInt("smart_height", s!!.progress).apply()
            }
        })

        // 工作类型 Spinner
        val spinnerWork = sheetView.findViewById<Spinner>(R.id.spinner_work)
        val workItems = arrayOf("久坐（办公室/司机等）", "中度（走动/轻体力）", "重度（体力活/工人等）")
        spinnerWork.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, workItems).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerWork.setSelection(prefs.getInt("smart_work", 1))
        spinnerWork.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                prefs.edit().putInt("smart_work", pos).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 饮食习惯 Spinner
        val spinnerDiet = sheetView.findViewById<Spinner>(R.id.spinner_diet)
        val dietItems = arrayOf("清淡（少盐少油）", "正常", "偏咸 / 重口味", "高蛋白（肉蛋奶为主）")
        spinnerDiet.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, dietItems).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerDiet.setSelection(prefs.getInt("smart_diet", 1))
        spinnerDiet.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                prefs.edit().putInt("smart_diet", pos).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 天气按钮
        val btnWeather = sheetView.findViewById<Button>(R.id.btn_weather)
        val tvWeatherInfo = sheetView.findViewById<TextView>(R.id.tv_weather_info)
        val etCity = sheetView.findViewById<EditText>(R.id.et_city)
        val mainHandler = Handler(Looper.getMainLooper())

        // 恢复城市文本
        val savedCity = prefs.getString("smart_city", "") ?: ""
        etCity.setText(savedCity)
        etCity.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                prefs.edit().putString("smart_city", s.toString()).apply()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnWeather.setOnClickListener {
            val city = etCity.text.toString().trim()
            if (city.isEmpty()) {
                tvWeatherInfo.text = "请先输入城市名"
                return@setOnClickListener
            }
            tvWeatherInfo.text = "⏳ 获取中..."
            btnWeather.isEnabled = false

            Thread {
                val result = fetchWeather(city)
                mainHandler.post {
                    btnWeather.isEnabled = true
                    if (result != null) {
                        weatherTemp = result.first
                        weatherHumidity = result.second
                        prefs.edit().putInt("weather_temp", weatherTemp)
                            .putInt("weather_humidity", weatherHumidity).apply()
                        tvWeatherInfo.text = "${result.first}℃ · 湿度${result.second}%"
                    } else {
                        tvWeatherInfo.text = "获取失败，使用默认 25℃ / 湿度60%"
                        weatherTemp = 25
                        weatherHumidity = 60
                    }
                }
            }.start()
        }

        // 定位按钮
        val btnLocation = sheetView.findViewById<Button>(R.id.btn_location)
        btnLocation.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                requestLocation(etCity, tvWeatherInfo, btnWeather, mainHandler)
            } else {
                locationCallback = { granted ->
                    if (granted) {
                        requestLocation(etCity, tvWeatherInfo, btnWeather, mainHandler)
                    } else {
                        Snackbar.make(binding.root, "需要定位权限才能获取城市", Snackbar.LENGTH_SHORT).show()
                    }
                }
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        // 结果区域
        val cardResult = sheetView.findViewById<CardView>(R.id.card_result)
        val tvResultMl = sheetView.findViewById<TextView>(R.id.tv_result_ml)
        val tvResultCups = sheetView.findViewById<TextView>(R.id.tv_result_cups)
        val layoutDetail = sheetView.findViewById<LinearLayout>(R.id.layout_detail)
        val tvAdvice = sheetView.findViewById<TextView>(R.id.tv_advice)
        val tvAiBadge = sheetView.findViewById<TextView>(R.id.tv_ai_badge)

        // 计算按钮
        sheetView.findViewById<Button>(R.id.btn_calc).setOnClickListener {
            if (smartHealth == "chronic") {
                showChronicResult(tvResultMl, tvResultCups, layoutDetail, tvAdvice)
                tvAiBadge.visibility = View.GONE
                cardResult.visibility = View.VISIBLE
                return@setOnClickListener
            }

            val age = seekAge.progress + 18
            val weight = (seekWeight.progress + 40).toFloat()
            val height = seekHeight.progress + 140
            val workType = arrayOf("sedentary", "moderate", "heavy")[spinnerWork.selectedItemPosition]
            val dietType = arrayOf("light", "normal", "salty", "highprotein")[spinnerDiet.selectedItemPosition]

            val params = mapOf(
                "gender" to smartGender,
                "age" to age,
                "weight" to weight,
                "height" to height,
                "workType" to workType,
                "temperature" to weatherTemp,
                "humidity" to weatherHumidity,
                "dietType" to dietType
            )

            // 先用公式计算结果，再异步调用 AI 获取饮水建议
            val result = calculateLocal(params)
            showResult(result, tvResultMl, tvResultCups, layoutDetail, tvAdvice)
            tvAiBadge.visibility = View.VISIBLE
            tvAiBadge.text = "由 🐳 DeepSeek 生成建议中…"
            cardResult.visibility = View.VISIBLE

            val resultMl = (result["total_ml"] as? Int) ?: 2000
            val etGoalInCalc = sheetView.findViewById<EditText>(R.id.et_goal)
            etGoalInCalc.setText("$resultMl")

            val paramsWithMl = params.toMutableMap().apply { put("resultMl", resultMl) }

            // 隐藏建议区，等 AI 返回后再显示
            tvAdvice.visibility = View.GONE

            Thread {
                val aiAdvice = callAIForAdvice(paramsWithMl)
                mainHandler.post {
                    tvAdvice.visibility = View.VISIBLE
                    if (aiAdvice != null) {
                        tvAdvice.text = aiAdvice
                        tvAiBadge.text = "由 🐳 DeepSeek 生成"
                    } else {
                        tvAdvice.text = result["advice"] as String
                        tvAiBadge.text = "由 🐳 DeepSeek 生成（失败，使用本地建议）"
                    }
                }
            }.start()
        }
    }

    // ===== 本地计算（基于 HTML 中的算法，不调 API） =====
    private fun calculateLocal(params: Map<String, Any>): Map<String, Any> {
        val gender = params["gender"] as String
        val age = params["age"] as Int
        val weight = params["weight"] as Float
        val workType = params["workType"] as String
        val temperature = params["temperature"] as Int
        val humidity = params["humidity"] as Int
        val dietType = params["dietType"] as String

        // 基础需水量：女性约27ml/kg，男性约30ml/kg
        var base = if (gender == "female") (weight * 27f).toInt() else (weight * 30f).toInt()

        // 年龄>60岁适当减量
        if (age > 60) base = (base * 0.9f).toInt()

        // 活动量调整
        val activity = when (workType) {
            "sedentary" -> 0
            "moderate" -> 300
            "heavy" -> 600
            else -> 300
        }

        // 天气调整：25℃为基准，每高5℃ +200ml
        val weather = ((temperature - 25) / 5f * 200).toInt()
        val humidityAdj = if (humidity > 70) -100 else 0

        // 饮食调整
        val diet = when (dietType) {
            "light" -> -100
            "salty" -> 300
            "highprotein" -> 400
            else -> 0
        }

        val raw = (base + activity + weather + humidityAdj + diet).coerceIn(1400, 4000)
        val total = (Math.round(raw / 100.0) * 100).toInt().coerceIn(1400, 4000)

        val adviceParts = mutableListOf<String>()
        adviceParts.add("建议少量多次饮水，每次200ml左右")
        if (temperature > 30) adviceParts.add("高温天气注意及时补水")
        if (dietType == "salty") adviceParts.add("偏咸饮食需适当增加饮水量")
        if (gender == "female") adviceParts.add("女性建议每日饮水不少于1500ml")

        return mapOf(
            "total_ml" to total,
            "breakdown" to mapOf(
                "base" to "${(Math.round(base / 100.0) * 100).toInt()}",
                "activity" to "$activity",
                "weather" to "${weather + humidityAdj}",
                "diet" to "$diet"
            ),
            "advice" to adviceParts.joinToString("；")
        )
    }

    private fun showChronicResult(
        tvMl: TextView, tvCups: TextView, layoutDetail: LinearLayout, tvAdvice: TextView
    ) {
        tvMl.text = "请遵医嘱"
        tvCups.text = "AI仅供参考"
        layoutDetail.removeAllViews()
        addDetailRow(layoutDetail, "⚠️ 有基础病 · 饮水量需医生根据具体病情制定", "")
        tvAdvice.text = "有基础病（高血压/糖尿病/肾病/心衰等），请务必咨询主治医生确定每日饮水量。AI建议不可替代专业医疗指导。"
    }

    @Suppress("UNCHECKED_CAST")
    private fun showResult(
        result: Map<String, Any>,
        tvMl: TextView, tvCups: TextView, layoutDetail: LinearLayout, tvAdvice: TextView
    ) {
        val ml = result["total_ml"] as Int
        val breakdown = result["breakdown"] as Map<String, String>
        val cups = Math.round(ml / 200f)

        tvMl.text = "$ml"
        tvCups.text = "≈ ${cups} 杯（200ml/杯）"

        layoutDetail.removeAllViews()
        addDetailRow(layoutDetail, "基础需水", "${breakdown["base"]} ml")
        addDetailRow(layoutDetail, "活动调整", "${breakdown["activity"]} ml")
        addDetailRow(layoutDetail, "天气调整", "${breakdown["weather"]} ml")
        addDetailRow(layoutDetail, "饮食调整", "${breakdown["diet"]} ml")
        addDetailRow(layoutDetail, "最终建议", "$ml ml / 日")

        tvAdvice.text = result["advice"] as String
    }

    private fun addDetailRow(container: LinearLayout, label: String, value: String) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4; bottomMargin = 4 }
        }

        val tvLabel = TextView(requireContext()).apply {
            text = label
            textSize = 13f
            setTextColor(0xFF4A6F8F.toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvValue = TextView(requireContext()).apply {
            text = value
            textSize = 13f
            setTextColor(0xFF1A3B55.toInt())
        }

        row.addView(tvLabel)
        row.addView(tvValue)
        container.addView(row)
    }

    // ===== 仅调用 AI 获取饮水建议 =====
    private fun callAIForAdvice(params: Map<String, Any>): String? {
        return try {
            val apiKey = "sk-22b4893ed61441c1a506c77238deebdf"
            val apiUrl = "https://api.deepseek.com/v1/chat/completions"

            val gender = params["gender"] as String
            val age = params["age"] as Int
            val weight = params["weight"] as Float
            val height = params["height"] as Int
            val workType = params["workType"] as String
            val temperature = params["temperature"] as Int
            val humidity = params["humidity"] as Int
            val dietType = params["dietType"] as String
            val resultMl = params["resultMl"] as Int

            val genderText = if (gender == "male") "男" else "女"
            val workText = when (workType) {
                "sedentary" -> "久坐（办公室/司机等）"
                "moderate" -> "中度（走动/轻体力）"
                "heavy" -> "重度（体力活/工人等）"
                else -> "中度（走动/轻体力）"
            }
            val dietText = when (dietType) {
                "light" -> "清淡（少盐少油）"
                "salty" -> "偏咸/重口味"
                "highprotein" -> "高蛋白（肉蛋奶为主）"
                else -> "正常"
            }
            val prompt = """你是一个专业的饮水健康顾问。系统已用公式计算出用户每日建议饮水量为 ${resultMl}ml，请根据以下用户信息给出个性化饮水建议。

用户信息：
- 性别：$genderText
- 年龄：${age}岁
- 体重：${weight}kg
- 身高：${height}cm
- 工作类型：$workText
- 当前气温：${temperature}℃
- 湿度：${humidity}%
- 饮食习惯：$dietText

请直接给出个性化饮水建议（150字以内，不要说"根据您的数据"等套话，直接给建议）。"""

            val jsonBody = JSONObject().apply {
                put("model", "deepseek-chat")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "你是一个专业的饮水健康顾问，只输出纯文本建议，不要JSON。")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("temperature", 0.3)
                put("max_tokens", 300)
            }

            val url = URL(apiUrl)
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 30000
                conn.readTimeout = 30000
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val os = OutputStreamWriter(conn.outputStream)
                os.write(jsonBody.toString())
                os.flush()
                os.close()

                val responseCode = conn.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }
                    return null
                }

                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val responseBody = reader.readText()
                reader.close()

                val responseJson = JSONObject(responseBody)
                responseJson
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ===== wttr.in 天气 API 获取 =====
    private fun fetchWeather(city: String): Pair<Int, Int>? {
        return try {
            val encodedCity = URLEncoder.encode(city, "UTF-8")
            val url = URL("https://wttr.in/$encodedCity?format=j1&lang=zh")
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "MarvisYibei/1.0")

                if (conn.responseCode != HttpURLConnection.HTTP_OK) return null

                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val current = json.getJSONArray("current_condition").getJSONObject(0)
                val temp = current.getString("temp_C").toIntOrNull() ?: return null
                val humidity = current.getString("humidity").toIntOrNull() ?: return null
                Pair(temp, humidity)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }


    // ===== 手机 GPS 定位获取城市 =====
    private fun requestLocation(
        etCity: EditText,
        tvWeatherInfo: TextView,
        btnWeather: Button,
        mainHandler: Handler
    ) {
        tvWeatherInfo.text = "⏳ 定位中..."
        val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager

        try {
            var location: Location? = null
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            for (provider in providers) {
                if (lm.isProviderEnabled(provider)) {
                    val loc = lm.getLastKnownLocation(provider)
                    if (loc != null && (location == null || loc.time > location.time)) {
                        location = loc
                    }
                }
            }

            if (location == null) {
                mainHandler.post {
                    tvWeatherInfo.text = "定位失败，请手动输入城市"
                }
                return
            }

            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            val addresses: MutableList<Address>? = geocoder.getFromLocation(
                location.latitude, location.longitude, 1
            )
            if (addresses.isNullOrEmpty()) {
                mainHandler.post {
                    tvWeatherInfo.text = "无法解析城市，请手动输入"
                }
                return
            }

            val city = addresses[0].locality
                ?: addresses[0].subAdminArea
                ?: addresses[0].adminArea
                ?: "未知城市"

            mainHandler.post {
                etCity.setText(city)
                // 自动触发天气查询
                tvWeatherInfo.text = "⏳ 获取中..."
                btnWeather.isEnabled = false

                Thread {
                    val result = fetchWeather(city)
                    mainHandler.post {
                        btnWeather.isEnabled = true
                        if (result != null) {
                            weatherTemp = result.first
                            weatherHumidity = result.second
                            tvWeatherInfo.text = "${result.first}℃ · 湿度${result.second}%"
                        } else {
                            tvWeatherInfo.text = "获取失败，使用默认 25℃ / 湿度60%"
                            weatherTemp = 25
                            weatherHumidity = 60
                        }
                    }
                }.start()
            }
        } catch (e: SecurityException) {
            mainHandler.post {
                tvWeatherInfo.text = "无定位权限"
            }
        } catch (e: Exception) {
            mainHandler.post {
                tvWeatherInfo.text = "定位失败，请手动输入城市"
            }
        }
    }

    private fun loadDrinkHistory(): String {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val count = prefs.getInt("record_count_$today", 0)
        if (count == 0) return "暂无记录"

        val sb = StringBuilder()
        for (i in 0 until count) {
            val time = prefs.getString("record_time_${today}_$i", "") ?: "--:--"
            val amount = prefs.getInt("record_amount_${today}_$i", 0)
            sb.append("$time  +${amount}ml")
            if (i < count - 1) sb.append("\n")
        }
        return sb.toString()
    }

    private fun addWater(amount: Int) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        todayAmount += amount
        val count = prefs.getInt("record_count_$today", 0)

        prefs.edit()
            .putInt("amount_$today", todayAmount)
            .putInt("record_count_$today", count + 1)
            .putString("record_time_${today}_$count", time)
            .putInt("record_amount_${today}_$count", amount)
            .apply()

        updateUI()
        Snackbar.make(binding.root, "已记录 +${amount}ml", Snackbar.LENGTH_SHORT).show()

        // 达到目标后取消后续提醒
        if (todayAmount >= dailyGoal) {
            cancelReminder()
        }
    }

    private fun scheduleReminder() {
        cancelReminder()
        val delay = (2.5 * 60 * 60 * 1000).toLong() // 2.5小时
        reminderRunnable = object : Runnable {
            override fun run() {
                if (todayAmount >= dailyGoal) return  // 已达标不弹
                sendWaterNotification()
                // 继续下一次
                scheduleReminder()
            }
        }
        reminderHandler.postDelayed(reminderRunnable!!, delay)
    }

    private fun cancelReminder() {
        reminderRunnable?.let { reminderHandler.removeCallbacks(it) }
        reminderRunnable = null
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "water_reminder",
                "一杯提醒",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "定时补水提醒"
            }
            val manager = requireContext().getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun sendWaterNotification() {
        // Android 13+ 需要运行时检查通知权限
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val notification = NotificationCompat.Builder(requireContext(), "water_reminder")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("该补水啦！")
            .setContentText("休息休息 缓一缓 歇一歇")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(requireContext()).notify(1001, notification)
    }

    private fun triggerReminderDialog() {
        sendWaterNotification()
    }

    override fun onDestroyView() {
        cancelReminder()
        super.onDestroyView()
        _binding = null
    }

    // 每次进入喝水页时显示右上角提示，3秒后自动淡出消失
    override fun onResume() {
        super.onResume()
        showOnboardingTip()
    }

    private fun showOnboardingTip() {
        val tip = binding.tvOnboardingTip ?: return
        tip.visibility = View.VISIBLE
        tip.alpha = 1f

        // 3秒后淡出
        tip.postDelayed({
            if (_binding == null) return@postDelayed
            val fadeOut = android.animation.ObjectAnimator.ofFloat(tip, View.ALPHA, 1f, 0f)
                .setDuration(300)
            fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    tip.visibility = View.GONE
                }
            })
            fadeOut.start()
        }, 3000L)
    }
}
