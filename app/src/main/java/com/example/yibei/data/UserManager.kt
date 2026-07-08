package com.example.yibei.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

object UserManager {

    private const val PREFS_NAME = "user_prefs"
    private const val KEY_NAME = "user_name"
    private const val KEY_AVATAR_COLOR = "avatar_color"
    private const val KEY_CONSECUTIVE_DAYS = "consecutive_days"
    private const val KEY_BEST_CONSECUTIVE = "best_consecutive"
    private const val KEY_TOTAL_DAYS = "total_days"
    private const val KEY_WATER_MASTER = "water_master"
    private const val KEY_HAS_LOGIN = "has_login"
    private const val KEY_DAILY_GOAL = "daily_goal"
    private const val KEY_EMAIL_PHONE = "user_email_phone"
    private const val KEY_AVATAR_PATH = "avatar_path"

    private const val DEFAULT_NAME = "喝水达人"
    private const val DEFAULT_COLOR = "#7C4DFF"

    private lateinit var prefs: SharedPreferences

    private val avatarPalette = listOf(
        "#7C4DFF", "#FF6B6B", "#4FC3F7", "#FFA726",
        "#66BB6A", "#EC407A", "#26A69A", "#AB47BC",
        "#42A5F5", "#EF5350"
    )

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun hasLogin(): Boolean = prefs.getBoolean(KEY_HAS_LOGIN, false)

    fun markLogin() { prefs.edit().putBoolean(KEY_HAS_LOGIN, true).apply() }

    val name: String
        get() = prefs.getString(KEY_NAME, DEFAULT_NAME) ?: DEFAULT_NAME

    fun setName(value: String) { prefs.edit().putString(KEY_NAME, value).apply() }

    val avatarColor: Int
        get() {
            val hex = prefs.getString(KEY_AVATAR_COLOR, DEFAULT_COLOR) ?: DEFAULT_COLOR
            return Color.parseColor(hex)
        }

    fun setAvatarColor(hex: String) { prefs.edit().putString(KEY_AVATAR_COLOR, hex).apply() }

    val avatarColorHex: String
        get() = prefs.getString(KEY_AVATAR_COLOR, DEFAULT_COLOR) ?: DEFAULT_COLOR

    val avatarPaletteList: List<String> get() = avatarPalette

    /** 喝水达人头衔 */
    val title: String
        get() {
            val stored = prefs.getString(KEY_WATER_MASTER, null)
            if (stored != null) return stored
            return computeTitle()
        }

    fun computeTitle(customWaterAvg: Double? = null): String {
        val avg = customWaterAvg
        return when {
            avg != null && avg >= 3000 -> "🏆 金牌水王"
            avg != null && avg >= 2500 -> "🥈 银牌水王"
            avg != null && avg >= 2000 -> "🥉 铜牌水王"
            avg != null && avg >= 1500 -> "💧 饮水能手"
            avg != null && avg >= 1000 -> "🌊 初级水手"
            avg != null -> "🧊 入门菜鸟"
            else -> "🌊 喝水达人"
        }
    }

    fun saveTitle(title: String) { prefs.edit().putString(KEY_WATER_MASTER, title).apply() }

    val consecutiveDays: Int get() = prefs.getInt(KEY_CONSECUTIVE_DAYS, 0)
    fun setConsecutiveDays(v: Int) { prefs.edit().putInt(KEY_CONSECUTIVE_DAYS, v).apply() }

    val bestConsecutive: Int get() = prefs.getInt(KEY_BEST_CONSECUTIVE, 0)
    fun setBestConsecutive(v: Int) { prefs.edit().putInt(KEY_BEST_CONSECUTIVE, v).apply() }

    val totalDays: Int get() = prefs.getInt(KEY_TOTAL_DAYS, 0)
    fun setTotalDays(v: Int) { prefs.edit().putInt(KEY_TOTAL_DAYS, v).apply() }

    val dailyGoal: Int get() = prefs.getInt(KEY_DAILY_GOAL, 2000)
    fun setDailyGoal(v: Int) { prefs.edit().putInt(KEY_DAILY_GOAL, v).apply() }

    // 邮箱/手机号
    val emailPhone: String get() = prefs.getString(KEY_EMAIL_PHONE, "") ?: ""
    fun setEmailPhone(value: String) { prefs.edit().putString(KEY_EMAIL_PHONE, value).apply() }

    // 自定义头像路径
    val avatarPath: String get() = prefs.getString(KEY_AVATAR_PATH, "") ?: ""
    fun setAvatarPath(path: String) { prefs.edit().putString(KEY_AVATAR_PATH, path).apply() }
    fun hasCustomAvatar(): Boolean {
        val path = avatarPath
        return path.isNotEmpty() && java.io.File(path).exists()
    }

    /** 首次头像色：根据姓名hash选一个 */
    fun initAvatarColor(name: String) {
        if (!prefs.contains(KEY_AVATAR_COLOR)) {
            val idx = kotlin.math.abs(name.hashCode()) % avatarPalette.size
            setAvatarColor(avatarPalette[idx])
        }
    }

    fun reset() {
        prefs.edit().clear().apply()
    }
}
