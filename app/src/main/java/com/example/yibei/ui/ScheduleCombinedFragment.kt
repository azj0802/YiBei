package com.example.yibei.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.yibei.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ScheduleCombinedFragment : Fragment() {

    private var recycler: RecyclerView? = null
    private var tvEmpty: TextView? = null
    private var fab: FloatingActionButton? = null
    private var fabGame: View? = null
    private var fabSchedule: View? = null
    private var overlay: View? = null
    private var isFabExpanded = false
    private lateinit var prefs: SharedPreferences
    private lateinit var gamePrefs: SharedPreferences
    private val dataItems = mutableListOf<CombinedItem>()
    private lateinit var adapter: CombinedAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null

    // -- Calendar --
    private var calendarRecycler: RecyclerView? = null
    private var tvMonth: TextView? = null
    private var calendarAdapter: CalendarAdapter? = null
    private var calendarYear = 0
    private var calendarMonth = 0
    private var selectedDate: String? = null  // yyyy-MM-dd, null = show all
    private var isWeekView = false
    private var calendarCollapsed = true
    private var weekOffset = 0  // 周视图偏移量（以周为单位）
    private val allSchedules = mutableListOf<CombinedItem.Schedule>()

    // -- sections tracking --
    private var scheduleHeaderPos = -1
    private var gameHeaderPos = -1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_schedule_combined, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recycler = view.findViewById(R.id.recycler_combined)
        tvEmpty = view.findViewById(R.id.tv_empty)
        fab = view.findViewById(R.id.fab_add)
        fabGame = view.findViewById(R.id.fab_game)
        fabSchedule = view.findViewById(R.id.fab_schedule)
        overlay = view.findViewById(R.id.overlay)
        prefs = requireContext().getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE)
        gamePrefs = requireContext().getSharedPreferences("game_prefs", Context.MODE_PRIVATE)

        createNotificationChannel()

        adapter = CombinedAdapter(dataItems,
            onScheduleClick = { pos, item -> showAddScheduleDialog(item) },
            onScheduleLongClick = { pos, item -> testScheduleNotify(item) },
            onGameStart = { onStartTimer(it) },
            onGameClick = { pos, item -> showAddGameDialog(item) },
            onGameLongClick = { pos, item -> testGameNotify(item) },
            onAddSchedule = { showAddScheduleDialog() },
            onAddGame = { showAddGameDialog() }
        )

        recycler!!.layoutManager = LinearLayoutManager(requireContext())
        recycler!!.adapter = adapter

        loadData()
        fab!!.setOnClickListener { toggleFabMenu() }
        overlay!!.setOnClickListener { collapseFab() }
        view.findViewById<View>(R.id.fab_game_btn).setOnClickListener { collapseFab(); showAddGameDialog() }
        view.findViewById<View>(R.id.fab_schedule_btn).setOnClickListener { collapseFab(); showAddScheduleDialog() }
        setupSwipeDelete()

        // 初始化日历
        setupCalendar(view)

        countdownRunnable = object : Runnable {
            override fun run() {
                adapter.notifyDataSetChanged()
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        countdownRunnable?.let { handler.post(it) }
    }

    override fun onPause() {
        super.onPause()
        countdownRunnable?.let { handler.removeCallbacks(it) }
    }

    // ==================== CALENDAR ====================

    private fun setupCalendar(view: View) {
        val calView = view.findViewById<View>(R.id.calendar_view)
        calendarRecycler = calView.findViewById(R.id.recycler_calendar)
        tvMonth = calView.findViewById(R.id.tv_month)
        val btnPrev = calView.findViewById<ImageButton>(R.id.btn_prev)
        val btnNext = calView.findViewById<ImageButton>(R.id.btn_next)
        val rgMode = calView.findViewById<RadioGroup>(R.id.rg_view_mode)

        val today = Calendar.getInstance()
        calendarYear = today.get(Calendar.YEAR)
        calendarMonth = today.get(Calendar.MONTH)

        calendarAdapter = CalendarAdapter { date ->
            selectedDate = if (selectedDate == date) null else date
            rebuildList()
            updateCalendarGrid()
        }
        calendarRecycler?.layoutManager = GridLayoutManager(requireContext(), 7)
        calendarRecycler?.adapter = calendarAdapter

        btnPrev.setOnClickListener {
            if (isWeekView) {
                weekOffset--
            } else {
                calendarMonth--
                if (calendarMonth < 0) { calendarMonth = 11; calendarYear-- }
            }
            updateCalendarGrid()
        }
        btnNext.setOnClickListener {
            if (isWeekView) {
                weekOffset++
            } else {
                calendarMonth++
                if (calendarMonth > 11) { calendarMonth = 0; calendarYear++ }
            }
            updateCalendarGrid()
        }

        rgMode.setOnCheckedChangeListener { _, checkedId ->
            isWeekView = checkedId == R.id.rb_week
            if (isWeekView) weekOffset = 0
            updateCalendarGrid()
        }

        // 收起/展开按钮
        val calendarBody = calView.findViewById<View>(R.id.calendar_body)
        val btnCollapse = calView.findViewById<TextView>(R.id.btn_collapse)
        // 默认收起
        calendarBody.visibility = View.GONE
        btnCollapse.text = "▼ 展开日历"
        btnCollapse.setOnClickListener {
            calendarCollapsed = !calendarCollapsed
            calendarBody.visibility = if (calendarCollapsed) View.GONE else View.VISIBLE
            btnCollapse.text = if (calendarCollapsed) "▼ 展开日历" else "▲ 收起日历"
        }

        updateCalendarGrid()
    }

    private fun updateCalendarGrid() {
        val cal = Calendar.getInstance()
        cal.set(calendarYear, calendarMonth, 1)

        val todayCal = Calendar.getInstance()
        val todayStr = String.format("%04d-%02d-%02d",
            todayCal.get(Calendar.YEAR), todayCal.get(Calendar.MONTH) + 1, todayCal.get(Calendar.DAY_OF_MONTH))

        val monthName = String.format("%d年%02d月", calendarYear, calendarMonth + 1)
        tvMonth?.text = monthName

        // 获取每个日期的最高重要性（转换为日历级别：0=无, 1=低, 2=中, 3=高）
        val scheduleDateImportance = mutableMapOf<String, Int>()
        for (s in allSchedules) {
            val calImportance = s.importance + 1  // 0->1, 1->2, 2->3
            val cur = scheduleDateImportance[s.date] ?: 0
            if (calImportance > cur) scheduleDateImportance[s.date] = calImportance
        }

        val days = mutableListOf<CalendarAdapter.DayItem>()

        if (isWeekView) {
            // 周视图：以今天为基准，按 weekOffset 偏移
            val targetCal = Calendar.getInstance()
            targetCal.add(Calendar.WEEK_OF_YEAR, weekOffset)
            // 同步标题月份
            calendarYear = targetCal.get(Calendar.YEAR)
            calendarMonth = targetCal.get(Calendar.MONTH)
            tvMonth?.text = String.format("%d年%02d月", calendarYear, calendarMonth + 1)

            val dayOfWeek = targetCal.get(Calendar.DAY_OF_WEEK)
            val startCal = targetCal.clone() as Calendar
            startCal.add(Calendar.DAY_OF_MONTH, -(dayOfWeek - Calendar.SUNDAY))

            for (i in 0 until 7) {
                val d = startCal.clone() as Calendar
                d.add(Calendar.DAY_OF_MONTH, i)
                val dateStr = String.format("%04d-%02d-%02d",
                    d.get(Calendar.YEAR), d.get(Calendar.MONTH) + 1, d.get(Calendar.DAY_OF_MONTH))
                days.add(CalendarAdapter.DayItem(
                    date = dateStr,
                    day = d.get(Calendar.DAY_OF_MONTH),
                    isCurrentMonth = d.get(Calendar.MONTH) == calendarMonth,
                    isToday = dateStr == todayStr,
                    isSelected = dateStr == selectedDate,
                    importance = scheduleDateImportance[dateStr] ?: 0
                ))
            }
            // 周视图只显示一行
            calendarRecycler?.layoutParams = calendarRecycler?.layoutParams?.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
        } else {
            // 月视图：显示整月
            val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

            // 填充月初空白
            val prevCal = cal.clone() as Calendar
            prevCal.add(Calendar.MONTH, -1)
            val daysInPrevMonth = prevCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            for (i in 0 until firstDayOfWeek - 1) {
                val day = daysInPrevMonth - (firstDayOfWeek - 2) + i
                val dateStr = String.format("%04d-%02d-%02d",
                    prevCal.get(Calendar.YEAR), prevCal.get(Calendar.MONTH) + 1, day)
                days.add(CalendarAdapter.DayItem(
                    date = dateStr, day = day,
                    isCurrentMonth = false, isToday = dateStr == todayStr,
                    isSelected = dateStr == selectedDate,
                    importance = scheduleDateImportance[dateStr] ?: 0
                ))
            }

            // 当月日期
            for (day in 1..daysInMonth) {
                val dateStr = String.format("%04d-%02d-%02d", calendarYear, calendarMonth + 1, day)
                days.add(CalendarAdapter.DayItem(
                    date = dateStr, day = day,
                    isCurrentMonth = true, isToday = dateStr == todayStr,
                    isSelected = dateStr == selectedDate,
                    importance = scheduleDateImportance[dateStr] ?: 0
                ))
            }

            // 填充月末空白至6行
            val remaining = 42 - days.size
            val nextCal = cal.clone() as Calendar
            nextCal.add(Calendar.MONTH, 1)
            for (i in 1..remaining) {
                val dateStr = String.format("%04d-%02d-%02d",
                    nextCal.get(Calendar.YEAR), nextCal.get(Calendar.MONTH) + 1, i)
                days.add(CalendarAdapter.DayItem(
                    date = dateStr, day = i,
                    isCurrentMonth = false, isToday = dateStr == todayStr,
                    isSelected = dateStr == selectedDate,
                    importance = scheduleDateImportance[dateStr] ?: 0
                ))
            }
        }

        calendarAdapter?.submitList(days)
    }

    override fun onDestroyView() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        dataItems.filterIsInstance<CombinedItem.Game>().forEach { it.handler?.removeCallbacksAndMessages(null) }
        super.onDestroyView()
        recycler = null
        tvEmpty = null
        fab = null
        calendarRecycler = null
        tvMonth = null
        calendarAdapter = null
    }

    // ==================== DATA LOADING ====================

    private fun loadData() {
        dataItems.clear()
        allSchedules.clear()
        allSchedules.addAll(loadSchedules())
        loadGames()
        rebuildList()
        updateCalendarGrid()
    }

    private fun loadSchedules(): MutableList<CombinedItem.Schedule> {
        val list = mutableListOf<CombinedItem.Schedule>()
        val json = prefs.getString("schedules", "[]") ?: "[]"
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(CombinedItem.Schedule(
                id = obj.getLong("id"),
                title = obj.getString("title"),
                note = obj.optString("note", ""),
                time = obj.getString("time"),
                date = obj.getString("date"),
                reminderHour = obj.optInt("reminderHour", -1),
                reminderMinute = obj.optInt("reminderMinute", -1),
                reminderType = obj.optInt("reminderType", 0),
                importance = obj.optInt("importance", 0)
            ))
        }
        list.sortBy { "${it.date} ${it.time}" }
        return list
    }

    private fun loadGames(): MutableList<CombinedItem.Game> {
        val list = mutableListOf<CombinedItem.Game>()
        val json = gamePrefs.getString("games", "[]") ?: "[]"
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val game = CombinedItem.Game(
                id = obj.getLong("id"),
                name = obj.getString("name"),
                staminaCap = obj.getInt("staminaCap"),
                recoveryMinutes = obj.getInt("recoveryMinutes"),
                startTime = obj.optLong("startTime", 0),
                handler = null,
                reminderType = obj.optInt("reminderType", 0),
                reminderDate = obj.optString("reminderDate", ""),
                reminderTime = obj.optString("reminderTime", ""),
                reminderHour = obj.optInt("reminderHour", -1),
                reminderMinute = obj.optInt("reminderMinute", -1)
            )
            if (game.startTime > 0) {
                val elapsed = System.currentTimeMillis() - game.startTime
                val totalMs = game.staminaCap.toLong() * game.recoveryMinutes * 60 * 1000
                if (elapsed < totalMs) {
                    scheduleNotification(game)
                }
            }
            list.add(game)
        }
        return list
    }

    private fun rebuildList() {
        dataItems.clear()
        val schedules = if (selectedDate != null) {
            allSchedules.filter { it.date == selectedDate }.toMutableList()
        } else {
            allSchedules.toMutableList()
        }
        schedules.sortBy { "${it.date} ${it.time}" }
        val games = loadGames()

        // 日程在前，二游便笺在后
        scheduleHeaderPos = dataItems.size
        val headerLabel = if (selectedDate != null) "日程 ($selectedDate)" else "日程"
        dataItems.add(CombinedItem.SectionHeader(headerLabel, schedules.size))

        for (s in schedules) {
            dataItems.add(s)
        }

        gameHeaderPos = dataItems.size
        dataItems.add(CombinedItem.SectionHeader("二游便笺", games.size))

        for (g in games) {
            dataItems.add(g)
        }

        adapter.notifyDataSetChanged()
        updateVisibility()
    }

    private fun updateVisibility() {
        val rv = recycler ?: return
        val tv = tvEmpty ?: return
        val hasContent = dataItems.any { it is CombinedItem.Schedule || it is CombinedItem.Game }
        if (hasContent) {
            tv.visibility = View.GONE
            rv.visibility = View.VISIBLE
        } else {
            tv.visibility = View.VISIBLE
            rv.visibility = View.GONE
        }
    }

    // ==================== SAVE ====================

    private fun saveSchedules() {
        val arr = JSONArray()
        for (item in allSchedules) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("title", item.title)
            obj.put("note", item.note)
            obj.put("time", item.time)
            obj.put("date", item.date)
            obj.put("reminderHour", item.reminderHour)
            obj.put("reminderMinute", item.reminderMinute)
            obj.put("reminderType", item.reminderType)
            obj.put("importance", item.importance)
            arr.put(obj)
        }
        prefs.edit().putString("schedules", arr.toString()).apply()
        updateCalendarGrid()
    }

    private fun saveGames() {
        val arr = JSONArray()
        for (item in dataItems.filterIsInstance<CombinedItem.Game>()) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("name", item.name)
            obj.put("staminaCap", item.staminaCap)
            obj.put("recoveryMinutes", item.recoveryMinutes)
            obj.put("startTime", item.startTime)
            obj.put("reminderType", item.reminderType)
            obj.put("reminderDate", item.reminderDate)
            obj.put("reminderTime", item.reminderTime)
            obj.put("reminderHour", item.reminderHour)
            obj.put("reminderMinute", item.reminderMinute)
            arr.put(obj)
        }
        gamePrefs.edit().putString("games", arr.toString()).apply()
    }

    private fun updateGameReminderDisplay(tv: TextView, date: String, time: String) {
        tv.text = if (date.isNotEmpty() && time.isNotEmpty()) "$date $time" else "不设置"
    }

    // ==================== FAB ANIMATION ====================

    private fun toggleFabMenu() {
        if (isFabExpanded) collapseFab() else expandFab()
    }

    private fun expandFab() {
        isFabExpanded = true
        overlay!!.visibility = View.VISIBLE
        overlay!!.alpha = 0f
        overlay!!.animate().alpha(1f).setDuration(200).start()

        fab!!.animate()
            .rotation(45f)
            .setDuration(200)
            .start()
        fab!!.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFE53935.toInt())

        showFabItem(fabGame!!, 0)
        showFabItem(fabSchedule!!, 60)
    }

    private fun collapseFab() {
        isFabExpanded = false
        overlay!!.animate().alpha(0f).setDuration(200).withEndAction {
            overlay!!.visibility = View.GONE
        }.start()

        fab!!.animate()
            .rotation(0f)
            .setDuration(200)
            .start()
        fab!!.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF2196F3.toInt())

        hideFabItem(fabGame!!)
        hideFabItem(fabSchedule!!)
    }

    private fun showFabItem(view: View, delay: Long) {
        view.visibility = View.VISIBLE
        view.scaleX = 0f
        view.scaleY = 0f
        view.alpha = 0f
        view.animate()
            .scaleX(1f).scaleY(1f)
            .alpha(1f)
            .setDuration(200)
            .setStartDelay(delay)
            .start()
    }

    private fun hideFabItem(view: View) {
        view.animate()
            .scaleX(0f).scaleY(0f)
            .alpha(0f)
            .setDuration(150)
            .withEndAction { view.visibility = View.GONE }
            .start()
    }

    // ==================== ADD DIALOGS ====================

    private fun showAddScheduleDialog(editItem: CombinedItem.Schedule? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_schedule, null)
        val etTitle = dialogView.findViewById<android.widget.EditText>(R.id.etScheduleTitle)
        val etNote = dialogView.findViewById<android.widget.EditText>(R.id.etScheduleNote)
        val btnPickDate = dialogView.findViewById<Button>(R.id.btnPickDate)
        val btnPickTime = dialogView.findViewById<Button>(R.id.btnPickTime)
        val btnReminder = dialogView.findViewById<Button>(R.id.btnReminder)
        val tvDate = dialogView.findViewById<TextView>(R.id.tvPickedDate)
        val tvTime = dialogView.findViewById<TextView>(R.id.tvPickedTime)
        val tvReminder = dialogView.findViewById<TextView>(R.id.tvPickedReminder)
        val rgType = dialogView.findViewById<RadioGroup>(R.id.rgReminderType)
        val rbApp = dialogView.findViewById<RadioButton>(R.id.rbAppNotification)
        val rbCalendar = dialogView.findViewById<RadioButton>(R.id.rbSystemCalendar)
        val llReminderRow = dialogView.findViewById<LinearLayout>(R.id.llReminderRow)

        var pickedDate = editItem?.date ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        var pickedTime = editItem?.time ?: "09:00"
        var reminderHour = editItem?.reminderHour ?: -1
        var reminderMinute = editItem?.reminderMinute ?: -1
        var reminderType = editItem?.reminderType ?: 0
        var importance = editItem?.importance ?: 0

        if (editItem != null) {
            etTitle.setText(editItem.title)
            etNote.setText(editItem.note)
            if (editItem.reminderType == 1) rbCalendar.isChecked = true
        }
        tvDate.text = pickedDate
        tvTime.text = pickedTime
        tvReminder.text = if (reminderHour >= 0) String.format("%02d:%02d", reminderHour, reminderMinute) else "不提醒"

        // 重要性选择
        val rgImportance = dialogView.findViewById<RadioGroup>(R.id.rgImportance)
        when (importance) {
            1 -> rgImportance.check(R.id.rb_importance_medium)
            2 -> rgImportance.check(R.id.rb_importance_high)
            else -> rgImportance.check(R.id.rb_importance_low)
        }
        rgImportance.setOnCheckedChangeListener { _, checkedId ->
            importance = when (checkedId) {
                R.id.rb_importance_high -> 2
                R.id.rb_importance_medium -> 1
                else -> 0
            }
        }

        // 系统日历模式隐藏提醒时间行
        if (reminderType == 1) llReminderRow.visibility = View.GONE

        rgType.setOnCheckedChangeListener { _, id ->
            reminderType = if (id == R.id.rbSystemCalendar) 1 else 0
            llReminderRow.visibility = if (reminderType == 1) View.GONE else View.VISIBLE
        }

        btnPickDate.setOnClickListener {
            val cal = Calendar.getInstance()
            android.app.DatePickerDialog(requireContext(), { _, y, m, d ->
                pickedDate = String.format("%04d-%02d-%02d", y, m + 1, d)
                tvDate.text = pickedDate
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        btnPickTime.setOnClickListener {
            TimePickerDialog(requireContext(), { _, h, m ->
                pickedTime = String.format("%02d:%02d", h, m)
                tvTime.text = pickedTime
            }, 9, 0, true).show()
        }

        btnReminder.setOnClickListener {
            TimePickerDialog(requireContext(), { _, h, m ->
                reminderHour = h; reminderMinute = m
                tvReminder.text = String.format("%02d:%02d", h, m)
            }, reminderHour.coerceAtLeast(8), reminderMinute.coerceAtLeast(0), true).show()
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setTitle(if (editItem != null) "编辑日程" else "添加日程")
            .setPositiveButton("保存") { _, _ ->
                val title = etTitle.text.toString().trim()
                if (title.isEmpty()) {
                    Snackbar.make(requireView(), "请输入日程标题", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // 系统日历：弹出系统日历添加界面
                if (reminderType == 1) {
                    val cal = Calendar.getInstance()
                    val parts = pickedDate.split("-")
                    val timeParts = pickedTime.split(":")
                    if (parts.size == 3 && timeParts.size == 2) {
                        cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(),
                            timeParts[0].toInt(), timeParts[1].toInt())
                    }
                    val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                        data = android.provider.CalendarContract.Events.CONTENT_URI
                        putExtra(android.provider.CalendarContract.Events.TITLE, title)
                        putExtra(android.provider.CalendarContract.Events.DESCRIPTION, etNote.text.toString().trim())
                        putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, cal.timeInMillis)
                        putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, cal.timeInMillis + 3600000)
                    }
                    startActivity(intent)
                }

                if (editItem != null) {
                    editItem.title = title
                    editItem.note = etNote.text.toString().trim()
                    editItem.time = pickedTime
                    editItem.date = pickedDate
                    editItem.reminderHour = reminderHour
                    editItem.reminderMinute = reminderMinute
                    editItem.reminderType = reminderType
                    editItem.importance = importance
                } else {
                    allSchedules.add(CombinedItem.Schedule(
                        id = System.currentTimeMillis(), title = title,
                        note = etNote.text.toString().trim(), time = pickedTime, date = pickedDate,
                        reminderHour = reminderHour, reminderMinute = reminderMinute,
                        reminderType = reminderType,
                        importance = importance
                    ))
                }
                saveSchedules()
                rebuildList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAddGameDialog(editItem: CombinedItem.Game? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_game, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etGameName)
        val etCap = dialogView.findViewById<TextInputEditText>(R.id.etStaminaCap)
        val etRecovery = dialogView.findViewById<TextInputEditText>(R.id.etRecoveryMinutes)
        val rgType = dialogView.findViewById<RadioGroup>(R.id.rgReminderType)
        val rbCalendar = dialogView.findViewById<RadioButton>(R.id.rbSystemCalendar)
        val llReminderRow = dialogView.findViewById<LinearLayout>(R.id.llReminderRow)
        val tvReminderTime = dialogView.findViewById<TextView>(R.id.tvPickedReminderTime)
        val btnPickReminderTime = dialogView.findViewById<Button>(R.id.btnPickReminderTime)
        val tvDialogTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)

        var reminderType = editItem?.reminderType ?: 0
        var reminderDate = editItem?.reminderDate ?: ""
        var reminderTime = editItem?.reminderTime ?: ""
        var reminderHour = editItem?.reminderHour ?: -1
        var reminderMinute = editItem?.reminderMinute ?: -1

        if (editItem != null) {
            etName.setText(editItem.name)
            etCap.setText(editItem.staminaCap.toString())
            etRecovery.setText(editItem.recoveryMinutes.toString())
            if (editItem.reminderType == 1) rbCalendar.isChecked = true
            tvDialogTitle.text = "该「${editItem.name}」启动了！"
        }

        etName.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val name = s.toString().trim()
                tvDialogTitle.text = if (name.isNotEmpty()) "该「$name」启动了！" else "二游便笺"
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        updateGameReminderDisplay(tvReminderTime, reminderDate, reminderTime)

        if (reminderType == 1) llReminderRow.visibility = View.GONE

        rgType.setOnCheckedChangeListener { _, id ->
            reminderType = if (id == R.id.rbSystemCalendar) 1 else 0
            llReminderRow.visibility = if (reminderType == 1) View.GONE else View.VISIBLE
        }

        btnPickReminderTime.setOnClickListener {
            val cal = Calendar.getInstance()
            android.app.DatePickerDialog(requireContext(), { _, y, m, d ->
                reminderDate = String.format("%04d-%02d-%02d", y, m + 1, d)
                TimePickerDialog(requireContext(), { _, h, min ->
                    reminderTime = String.format("%02d:%02d", h, min)
                    reminderHour = h; reminderMinute = min
                    updateGameReminderDisplay(tvReminderTime, reminderDate, reminderTime)
                }, 9, 0, true).show()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString().trim()
                val capStr = etCap.text.toString().trim()
                val recoveryStr = etRecovery.text.toString().trim()
                if (name.isEmpty() || capStr.isEmpty() || recoveryStr.isEmpty()) {
                    Snackbar.make(requireView(), "请填写完整信息", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val cap = capStr.toIntOrNull()
                val recovery = recoveryStr.toIntOrNull()
                if (cap == null || cap <= 0 || recovery == null || recovery <= 0) {
                    Snackbar.make(requireView(), "体力上限和恢复时间必须为正整数", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // 系统日历：弹出系统日历添加界面
                if (reminderType == 1 && reminderDate.isNotEmpty() && reminderTime.isNotEmpty()) {
                    val cal = Calendar.getInstance()
                    val parts = reminderDate.split("-")
                    val timeParts = reminderTime.split(":")
                    if (parts.size == 3 && timeParts.size == 2) {
                        cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(),
                            timeParts[0].toInt(), timeParts[1].toInt())
                    }
                    val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                        data = android.provider.CalendarContract.Events.CONTENT_URI
                        putExtra(android.provider.CalendarContract.Events.TITLE, "【体力已满】$name")
                        putExtra(android.provider.CalendarContract.Events.DESCRIPTION,
                            "${name}体力上限${cap}格，每${recovery}分钟恢复1格")
                        putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, cal.timeInMillis)
                        putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, cal.timeInMillis + 3600000)
                    }
                    startActivity(intent)
                }

                if (editItem != null) {
                    editItem.name = name
                    editItem.staminaCap = cap
                    editItem.recoveryMinutes = recovery
                    editItem.handler?.removeCallbacksAndMessages(null)
                    editItem.handler = null
                    editItem.startTime = 0
                    editItem.reminderType = reminderType
                    editItem.reminderDate = reminderDate
                    editItem.reminderTime = reminderTime
                    editItem.reminderHour = reminderHour
                    editItem.reminderMinute = reminderMinute
                } else {
                    dataItems.add(CombinedItem.Game(
                        id = System.currentTimeMillis(), name = name,
                        staminaCap = cap, recoveryMinutes = recovery,
                        startTime = 0, handler = null,
                        reminderType = reminderType,
                        reminderDate = reminderDate, reminderTime = reminderTime,
                        reminderHour = reminderHour, reminderMinute = reminderMinute
                    ))
                }
                saveGames()
                rebuildList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== GAME TIMER ====================

    private fun onStartTimer(game: CombinedItem.Game) {
        game.startTime = System.currentTimeMillis()
        game.handler?.removeCallbacksAndMessages(null)
        game.handler = Handler(Looper.getMainLooper())
        scheduleNotification(game)
        saveGames()
        adapter.notifyDataSetChanged()
        Snackbar.make(requireView(), "「${game.name}」体力恢复计时已开始", Snackbar.LENGTH_SHORT).show()
    }

    private fun scheduleNotification(game: CombinedItem.Game) {
        val totalMs = game.staminaCap.toLong() * game.recoveryMinutes * 60 * 1000
        game.handler?.postDelayed({
            sendFullNotification(game)
            game.startTime = 0
            game.handler?.removeCallbacksAndMessages(null)
            game.handler = null
            saveGames()
            adapter.notifyDataSetChanged()
        }, totalMs)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val gameChannel = NotificationChannel(
                "game_reminder", "游戏体力提醒", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "游戏体力恢复完成提醒" }
            val scheduleChannel = NotificationChannel(
                "schedule_reminder", "日程提醒", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "日程时间提醒" }
            val nm = requireContext().getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(gameChannel)
            nm.createNotificationChannel(scheduleChannel)
        }
    }

    @Suppress("MissingPermission")
    private fun sendFullNotification(game: CombinedItem.Game) {
        val intent = android.content.Intent(requireContext(), com.example.yibei.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", "schedule")
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            requireContext(), game.id.toInt(), intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(requireContext(), "game_reminder")
            .setSmallIcon(R.drawable.ic_water_cup)
            .setContentTitle("该「${game.name}」启动了！")
            .setContentText("点击重置体力计时")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                requireContext(), android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(requireContext()).notify(game.id.toInt(), notification)
        }
    }

    // ==================== LONG PRESS TEST NOTIFY ====================

    private fun testScheduleNotify(item: CombinedItem.Schedule) {
        val nm = requireContext().getSystemService(NotificationManager::class.java)
        val notifId = item.id.toInt()
        val content = "${item.title}  ${item.date} ${item.time}"
        val bigText = if (item.note.isBlank()) content else "$content\n备注：${item.note}"

        val builder = NotificationCompat.Builder(requireContext(), "schedule_reminder")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("日程提醒")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        nm.notify(notifId, builder.build())
        Toast.makeText(requireContext(), "已弹出日程提醒", Toast.LENGTH_SHORT).show()
    }

    private fun testGameNotify(item: CombinedItem.Game) {
        val nm = requireContext().getSystemService(NotificationManager::class.java)
        val notifId = item.id.toInt()

        val intent = android.content.Intent(requireContext(), com.example.yibei.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", "schedule")
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            requireContext(), notifId, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(requireContext(), "game_reminder")
            .setSmallIcon(R.drawable.ic_water_cup)
            .setContentTitle("该「${item.name}」启动了！")
            .setContentText("点击重置体力计时")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        nm.notify(notifId, builder.build())
        Toast.makeText(requireContext(), "该「${item.name}」启动了！", Toast.LENGTH_SHORT).show()
    }

    // ==================== SWIPE DELETE ====================

    private fun setupSwipeDelete() {
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun getSwipeDirs(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                val item = dataItems.getOrNull(vh.adapterPosition) ?: return 0
                return if (item is CombinedItem.Schedule || item is CombinedItem.Game)
                    super.getSwipeDirs(rv, vh) else 0
            }

            override fun getSwipeThreshold(vh: RecyclerView.ViewHolder): Float = 0.3f

            override fun onChildDraw(
                c: android.graphics.Canvas,
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float,
                actionState: Int, isCurrentlyActive: Boolean
            ) {
                val itemView = vh.itemView

                if (dX < 0) {
                    vh.itemView.translationX = dX

                    val paint = android.graphics.Paint().apply {
                        color = 0xFFE53935.toInt()
                        isAntiAlias = true
                    }
                    c.drawRect(
                        itemView.right.toFloat() + dX,
                        itemView.top.toFloat(),
                        itemView.right.toFloat(),
                        itemView.bottom.toFloat(),
                        paint
                    )

                    val textPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 48f
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    val textX = itemView.right - 180f
                    val textY = itemView.top + (itemView.height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
                    c.drawText("松手触发删除", textX, textY, textPaint)
                }

                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            }

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                vh.itemView.translationX = 0f
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val pos = vh.adapterPosition
                val item = dataItems.getOrNull(pos) ?: return
                // 先移除 item 防止复用，延迟刷新让消失动画完成
                dataItems.removeAt(pos)
                adapter.notifyItemRemoved(pos)
                handler.postDelayed({
                    when (item) {
                        is CombinedItem.Schedule -> {
                            allSchedules.remove(item)
                            saveSchedules()
                            rebuildList()
                            Snackbar.make(requireView(), "已删除「${item.title}」", Snackbar.LENGTH_LONG)
                                .setAction("撤销") {
                                    allSchedules.add(item)
                                    saveSchedules()
                                    rebuildList()
                                }.show()
                        }
                        is CombinedItem.Game -> {
                            item.handler?.removeCallbacksAndMessages(null)
                            saveGames()
                            rebuildList()
                            Snackbar.make(requireView(), "已删除「${item.name}」", Snackbar.LENGTH_LONG)
                                .setAction("撤销") {
                                    item.handler = Handler(Looper.getMainLooper())
                                    if (item.startTime > 0) scheduleNotification(item)
                                    dataItems.add(pos, item)
                                    saveGames()
                                    rebuildList()
                                }.show()
                        }
                        else -> {}
                    }
                }, 300L)
            }
        })
        recycler?.let { touchHelper.attachToRecyclerView(it) }
    }
}

// ==================== SEALED ITEM HIERARCHY ====================

sealed class CombinedItem {
    data class SectionHeader(val title: String, val count: Int) : CombinedItem()
    data class Schedule(
        val id: Long, var title: String, var note: String,
        var time: String, var date: String,
        var reminderHour: Int, var reminderMinute: Int,
        var reminderType: Int = 0,  // 0=消息栏提醒, 1=系统日历
        var importance: Int = 0  // 0=低(蓝), 1=中(橙), 2=高(红)
    ) : CombinedItem()
    data class Game(
        val id: Long, var name: String, var staminaCap: Int,
        var recoveryMinutes: Int, var startTime: Long, var handler: Handler?,
        var reminderType: Int = 0, var reminderDate: String = "", var reminderTime: String = "",
        var reminderHour: Int = -1, var reminderMinute: Int = -1
    ) : CombinedItem()
}

// ==================== ADAPTER ====================

class CombinedAdapter(
    private val items: List<CombinedItem>,
    private val onScheduleClick: (Int, CombinedItem.Schedule) -> Unit,
    private val onScheduleLongClick: (Int, CombinedItem.Schedule) -> Unit,
    private val onGameStart: (CombinedItem.Game) -> Unit,
    private val onGameClick: (Int, CombinedItem.Game) -> Unit,
    private val onGameLongClick: (Int, CombinedItem.Game) -> Unit,
    private val onAddSchedule: () -> Unit,
    private val onAddGame: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SECTION = 0
        private const val TYPE_SCHEDULE = 1
        private const val TYPE_GAME = 2
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is CombinedItem.SectionHeader -> TYPE_SECTION
        is CombinedItem.Schedule -> TYPE_SCHEDULE
        is CombinedItem.Game -> TYPE_GAME
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
        TYPE_SECTION -> {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_section_header, parent, false)
            SectionVH(v)
        }
        TYPE_SCHEDULE -> {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_schedule, parent, false)
            ScheduleVH(v)
        }
        else -> {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_game, parent, false)
            GameVH(v)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is CombinedItem.SectionHeader -> bindSection((holder as SectionVH), item)
            is CombinedItem.Schedule -> bindSchedule((holder as ScheduleVH), item)
            is CombinedItem.Game -> bindGame((holder as GameVH), item)
        }
    }

    override fun getItemCount() = items.size

    // --- Section Header ---
    inner class SectionVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvSectionTitle)
        val tvCount: TextView = view.findViewById(R.id.tvSectionCount)
        val btnAdd: TextView = view.findViewById(R.id.btnAdd)
    }

    private fun bindSection(holder: SectionVH, item: CombinedItem.SectionHeader) {
        holder.tvTitle.text = item.title
        holder.tvCount.text = "${item.count}项"
        holder.btnAdd.setOnClickListener {
            when {
                item.title.startsWith("日程") -> onAddSchedule()
                item.title.startsWith("二游便笺") -> onAddGame()
            }
        }
    }

    // --- Schedule ---
    inner class ScheduleVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvScheduleTitle)
        val tvDateTime: TextView = view.findViewById(R.id.tvScheduleDateTime)
        val tvNote: TextView = view.findViewById(R.id.tvScheduleNote)
        val dotImportance: View = view.findViewById(R.id.dot_importance)
    }

    private fun bindSchedule(holder: ScheduleVH, item: CombinedItem.Schedule) {
        val pos = holder.adapterPosition

        holder.tvTitle.text = item.title
        holder.tvDateTime.text = "${item.date}  ${item.time}"
        holder.tvNote.text = item.note
        holder.tvNote.visibility = if (item.note.isBlank()) View.GONE else View.VISIBLE

        // 重要性颜色点
        val dotBg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setSize(24, 24)
            setColor(when (item.importance) {
                2 -> 0xFFE53935.toInt()  // 高-红
                1 -> 0xFFFF9800.toInt()  // 中-橙
                else -> 0xFF2196F3.toInt() // 低-蓝
            })
        }
        holder.dotImportance.background = dotBg

        holder.itemView.setOnClickListener { onScheduleClick(pos, item) }
        holder.itemView.setOnLongClickListener {
            onScheduleLongClick(pos, item)
            true
        }
    }

    // --- Game ---
    inner class GameVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvGameName)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvInfo: TextView = view.findViewById(R.id.tvGameInfo)
        val tvCountdown: TextView = view.findViewById(R.id.tvCountdown)
        val btnStart: Button = view.findViewById(R.id.btnStart)
    }

    private fun bindGame(holder: GameVH, item: CombinedItem.Game) {
        val pos = holder.adapterPosition

        holder.tvName.text = item.name
        holder.tvInfo.text = "体力上限 ${item.staminaCap}  ·  每格 ${item.recoveryMinutes}分钟"

        if (item.startTime > 0) {
            val elapsed = System.currentTimeMillis() - item.startTime
            val totalMs = item.staminaCap.toLong() * item.recoveryMinutes * 60 * 1000
            val remaining = totalMs - elapsed

            if (remaining <= 0) {
                holder.tvStatus.text = "已满"
                holder.tvStatus.setBackgroundResource(R.drawable.tab_active_bg)
                holder.tvCountdown.visibility = View.GONE
                holder.btnStart.text = "重新计时"
                holder.btnStart.setBackgroundColor(0xFF2196F3.toInt())
            } else {
                holder.tvStatus.text = "恢复中"
                holder.tvStatus.setBackgroundResource(R.drawable.tab_inactive_bg)
                val hours = remaining / 3600000
                val minutes = (remaining % 3600000) / 60000
                val seconds = (remaining % 60000) / 1000
                holder.tvCountdown.text = "体力全满将在 ${hours}小时${minutes}分${seconds}秒 后"
                holder.tvCountdown.visibility = View.VISIBLE
                holder.btnStart.text = "重新计时"
                holder.btnStart.setBackgroundColor(0xFF2196F3.toInt())
            }
        } else {
            holder.tvStatus.text = "未开始"
            holder.tvStatus.setBackgroundResource(R.drawable.tab_inactive_bg)
            holder.tvCountdown.visibility = View.GONE
            holder.btnStart.text = "开始计时"
            holder.btnStart.setBackgroundColor(0xFF4CAF50.toInt())
        }

        holder.btnStart.setOnClickListener { onGameStart(item) }
        holder.itemView.setOnClickListener { onGameClick(pos, item) }
        holder.itemView.setOnLongClickListener {
            onGameLongClick(pos, item)
            true
        }
    }
}
