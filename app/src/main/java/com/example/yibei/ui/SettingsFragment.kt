package com.example.yibei.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.yibei.LocaleHelper
import com.example.yibei.R
import com.example.yibei.data.MockDataGenerator
import com.example.yibei.data.UserManager
import com.example.yibei.ui.utils.DialogStyler
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.time.LocalDate

class SettingsFragment : Fragment() {

    private lateinit var prefs: SharedPreferences
    private lateinit var tvName: TextView
    private lateinit var viewAvatar: ImageView
    private lateinit var tvDarkMode: TextView
    private lateinit var tvVibration: TextView
    private lateinit var tvLanguage: TextView
    private lateinit var tvNotificationStatus: TextView
    private lateinit var tvLocationStatus: TextView
    private lateinit var tvHealthStatus: TextView
    private lateinit var tvVersion: TextView
    private lateinit var tvCacheSize: TextView

    private val avatarPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { saveAvatarImage(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences("water_prefs", Context.MODE_PRIVATE)
        UserManager.init(requireContext())

        tvName = view.findViewById(R.id.tv_settings_name)
        viewAvatar = view.findViewById(R.id.view_avatar_preview)
        tvDarkMode = view.findViewById(R.id.tv_dark_mode)
        tvVibration = view.findViewById(R.id.tv_vibration)
        tvLanguage = view.findViewById(R.id.tv_language)
        tvNotificationStatus = view.findViewById(R.id.tv_notification_status)
        tvLocationStatus = view.findViewById(R.id.tv_location_status)
        tvHealthStatus = view.findViewById(R.id.tv_health_status)
        tvVersion = view.findViewById(R.id.tv_version)
        tvCacheSize = view.findViewById(R.id.tv_cache_size)

        view.findViewById<View>(R.id.tv_settings_back).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 账号设置
        view.findViewById<View>(R.id.item_edit_name).setOnClickListener { showEditNameDialog() }
        view.findViewById<View>(R.id.item_edit_avatar).setOnClickListener {
            avatarPickerLauncher.launch("image/*")
        }

        // 通用设置
        view.findViewById<View>(R.id.item_dark_mode).setOnClickListener { showDarkModeDialog() }
        view.findViewById<View>(R.id.item_vibration).setOnClickListener { toggleVibration() }
        view.findViewById<View>(R.id.item_language).setOnClickListener { showLanguageDialog() }

        // 权限管理
        view.findViewById<View>(R.id.item_notification_permission).setOnClickListener { checkNotificationPermission() }
        view.findViewById<View>(R.id.item_location_permission).setOnClickListener { checkLocationPermission() }
        view.findViewById<View>(R.id.item_health_permission).setOnClickListener { checkHealthPermission() }

        // 数据与存储
        view.findViewById<View>(R.id.item_check_update).setOnClickListener { checkForUpdate() }
        view.findViewById<View>(R.id.item_clear_cache).setOnClickListener { clearCache() }
        view.findViewById<View>(R.id.item_clear_data).setOnClickListener { showClearDataDialog() }
        view.findViewById<View>(R.id.item_generate_mock)?.setOnClickListener { generateMockData() }

        // 其他
        view.findViewById<View>(R.id.item_about).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AboutFragment())
                .addToBackStack("about")
                .commit()
        }
        view.findViewById<View>(R.id.item_privacy).setOnClickListener { openPrivacyPolicy() }
        view.findViewById<View>(R.id.item_user_agreement).setOnClickListener { openUserAgreement() }

        // 退出登录
        view.findViewById<View>(R.id.btn_logout).setOnClickListener { showLogoutDialog() }

        refreshUI()
    }

    private fun refreshUI() {
        // 账号信息
        tvName.text = UserManager.name
        if (UserManager.hasCustomAvatar()) {
            viewAvatar.setImageURI(Uri.fromFile(File(UserManager.avatarPath)))
        } else {
            viewAvatar.setImageResource(R.drawable.bg_avatar_circle)
        }

        // 通用设置
        val darkMode = prefs.getInt("dark_mode", 0)
        tvDarkMode.text = when (darkMode) {
            1 -> "浅色模式"
            2 -> "深色模式"
            else -> "跟随系统"
        }

        val vibration = prefs.getBoolean("vibration_enabled", true)
        tvVibration.text = if (vibration) "开启" else "关闭"
        tvVibration.setTextColor(
            if (vibration) Color.parseColor("#4FC3F7") else Color.parseColor("#999999")
        )

        val language = prefs.getString("language", "zh") ?: "zh"
        tvLanguage.text = when (language) {
            "en" -> "English"
            else -> "简体中文"
        }

        // 权限状态
        tvNotificationStatus.text = if (isNotificationPermissionGranted()) "已授权" else "未授权"
        tvNotificationStatus.setTextColor(
            if (isNotificationPermissionGranted()) Color.parseColor("#4FC3F7") else Color.parseColor("#FF6B6B")
        )

        val locationGranted = isLocationPermissionGranted()
        tvLocationStatus.text = if (locationGranted) "已授权" else "未授权"
        tvLocationStatus.setTextColor(
            if (locationGranted) Color.parseColor("#4FC3F7") else Color.parseColor("#FF6B6B")
        )

        tvHealthStatus.text = if (UserManager.hasLogin()) "已授权" else "未登录"
        tvHealthStatus.setTextColor(
            if (UserManager.hasLogin()) Color.parseColor("#4FC3F7") else Color.parseColor("#FF6B6B")
        )

        // 版本与缓存
        tvVersion.text = "v1.0.0"
        tvCacheSize.text = calculateCacheSize()
    }

    private fun showEditNameDialog() {
        val edit = EditText(requireContext())
        edit.setText(UserManager.name)
        edit.setSelection(edit.text.length)
        edit.hint = "2-8个字"
        DialogStyler.style(AlertDialog.Builder(requireContext())
            .setTitle("修改昵称")
            .setView(edit)
            .setPositiveButton("确定") { _, _ ->
                val newName = edit.text.toString().trim()
                if (newName.isNotEmpty()) {
                    UserManager.setName(newName)
                    UserManager.initAvatarColor(newName)
                    refreshUI()
                }
            }
            .setNegativeButton("取消", null)
            .show())
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
            refreshUI()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showDarkModeDialog() {
        val current = prefs.getInt("dark_mode", 0)
        val items = arrayOf("跟随系统", "浅色模式", "深色模式")
        var selected = current
        DialogStyler.style(AlertDialog.Builder(requireContext())
            .setTitle("深色模式")
            .setSingleChoiceItems(items, selected) { _, which ->
                selected = which
            }
            .setPositiveButton("确定") { _, _ ->
                prefs.edit().putInt("dark_mode", selected).apply()
                applyDarkMode(selected)
                requireActivity().recreate()
            }
            .setNegativeButton("取消", null)
            .show())
    }

    private fun applyDarkMode(mode: Int) {
        when (mode) {
            0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    private fun toggleVibration() {
        val current = prefs.getBoolean("vibration_enabled", true)
        prefs.edit().putBoolean("vibration_enabled", !current).apply()
        refreshUI()
    }

    private fun showLanguageDialog() {
        val current = prefs.getString("language", "zh") ?: "zh"
        val items = arrayOf("简体中文", "English")
        var selected = if (current == "zh") 0 else 1
        DialogStyler.style(AlertDialog.Builder(requireContext())
            .setTitle("语言")
            .setSingleChoiceItems(items, selected) { _, which ->
                selected = which
            }
            .setPositiveButton("确定") { _, _ ->
                val lang = if (selected == 0) "zh" else "en"
                LocaleHelper.setLanguage(requireContext(), lang)
                requireActivity().recreate()
            }
            .setNegativeButton("取消", null)
            .show())
    }

    private fun isLocationPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isNotificationPermissionGranted(): Boolean {
        return NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
    }

    private fun checkNotificationPermission() {
        if (!isNotificationPermissionGranted()) {
            DialogStyler.style(AlertDialog.Builder(requireContext())
                .setTitle("通知权限")
                .setMessage("应用需要通知权限来发送喝水提醒。是否前往设置开启？")
                .setPositiveButton("去开启") { _, _ ->
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                    }
                    startActivity(intent)
                }
                .setNegativeButton("取消", null)
                .show())
        } else {
            Toast.makeText(requireContext(), "通知权限已开启", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkLocationPermission() {
        if (isLocationPermissionGranted()) {
            Toast.makeText(requireContext(), "位置权限已开启", Toast.LENGTH_SHORT).show()
        } else {
            DialogStyler.style(AlertDialog.Builder(requireContext())
                .setTitle("位置权限")
                .setMessage("应用需要位置权限来记录您的活动轨迹。是否前往设置开启？")
                .setPositiveButton("去开启") { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${requireContext().packageName}")
                    }
                    startActivity(intent)
                }
                .setNegativeButton("取消", null)
                .show())
        }
    }

    private fun checkHealthPermission() {
        if (UserManager.hasLogin()) {
            Toast.makeText(requireContext(), "健康数据权限已开启，登录用户可使用健康数据同步功能", Toast.LENGTH_SHORT).show()
        } else {
            DialogStyler.style(AlertDialog.Builder(requireContext())
                .setTitle("健康数据权限")
                .setMessage("健康数据需要登录账号后才能启用。请先登录您的账号。")
                .setPositiveButton("确定", null)
                .show())
        }
    }

    private fun checkForUpdate() {
        DialogStyler.style(AlertDialog.Builder(requireContext())
            .setTitle("检查更新")
            .setMessage("当前版本：v1.0.0\n\n已是最新版本，无需更新。")
            .setPositiveButton("确定", null)
            .show())
    }

    private fun calculateCacheSize(): String {
        val cacheDir = requireContext().cacheDir
        val size = getFolderSize(cacheDir)
        val df = DecimalFormat("#.##")
        return when {
            size < 1024 -> "${size}B"
            size < 1024 * 1024 -> "${df.format(size / 1024.0)} KB"
            else -> "${df.format(size / (1024.0 * 1024.0))} MB"
        }
    }

    private fun getFolderSize(dir: File): Long {
        var size = 0L
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) getFolderSize(file) else file.length()
            }
        }
        return size
    }

    private fun clearCache() {
        DialogStyler.style(AlertDialog.Builder(requireContext())
            .setTitle("清除缓存")
            .setMessage("确定要清除应用缓存吗？")
            .setPositiveButton("确定") { _, _ ->
                val cacheDir = requireContext().cacheDir
                deleteRecursive(cacheDir)
                cacheDir.mkdirs()
                refreshUI()
                Toast.makeText(requireContext(), "缓存已清除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show())
    }

    private fun deleteRecursive(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                deleteRecursive(child)
            }
        }
        if (file != requireContext().cacheDir) {
            file.delete()
        }
    }

    private fun showClearDataDialog() {
        DialogStyler.style(AlertDialog.Builder(requireContext())
            .setTitle("清除全部数据")
            .setMessage("此操作将清除所有本地数据（包括设置、记录等），无法恢复。确定继续吗？")
            .setPositiveButton("确定") { _, _ ->
                prefs.edit().clear().apply()
                UserManager.reset()
                clearCache()
                Toast.makeText(requireContext(), "数据已清除", Toast.LENGTH_SHORT).show()
                refreshUI()
            }
            .setNegativeButton("取消", null)
            .show())
    }

    private fun openPrivacyPolicy() {
        DialogStyler.style(AlertDialog.Builder(requireContext())
            .setTitle("隐私政策")
            .setMessage("一杯 App 尊重并保护您的个人隐私。\n\n" +
                "我们收集的信息：\n" +
                "• 喝水记录、睡眠数据、步数等健康相关信息\n" +
                "• 账号昵称、头像颜色等基本信息\n\n" +
                "数据使用：\n" +
                "• 所有健康数据仅存储在您的设备本地\n" +
                "• 不会将您的个人数据分享给第三方\n" +
                "• AI 分析数据仅用于生成个性化健康建议\n\n" +
                "您可以在设置中随时清除本地数据。")
            .setPositiveButton("知道了", null)
            .show())
    }

    private fun openUserAgreement() {
        DialogStyler.style(AlertDialog.Builder(requireContext())
            .setTitle("用户协议")
            .setMessage("一杯 App 用户服务协议\n\n" +
                "1. 服务说明\n" +
                "一杯是一款个人健康管理工具，帮助您记录和管理日常饮水、睡眠和运动数据。\n\n" +
                "2. 用户责任\n" +
                "用户应提供真实准确的个人信息，妥善保管账号密码。\n\n" +
                "3. 免责声明\n" +
                "本应用提供的健康建议仅供参考，不构成医疗诊断或治疗建议。如有健康问题，请咨询专业医生。\n\n" +
                "4. 协议修改\n" +
                "我们保留随时修改本协议的权利，修改后的协议将在应用内公布。")
            .setPositiveButton("同意", null)
            .show())
    }

    private fun generateMockData() {
        DialogStyler.style(AlertDialog.Builder(requireContext())
            .setTitle("生成模拟数据")
            .setMessage("将生成30天模拟数据（2026-06-09 至 2026-07-08）：\n\n" +
                "• 喝水：日均 1800-2200ml，平稳波动\n" +
                "• 睡眠：夜间 6.5-8h，趋于规律\n" +
                "• 步数：保持原样（从系统读取）\n\n" +
                "确定要生成吗？")
            .setPositiveButton("生成") { _, _ ->
                try {
                    val startDate = LocalDate.of(2026, 6, 9)
                    MockDataGenerator.generateMockData(requireContext(), startDate)

                    // 验证并显示结果
                    val result = MockDataGenerator.verifyData(requireContext(), startDate)

                    DialogStyler.style(AlertDialog.Builder(requireContext())
                        .setTitle("生成成功")
                        .setMessage(result)
                        .setPositiveButton("确定", null)
                        .show())
                } catch (e: Exception) {
                    DialogStyler.style(AlertDialog.Builder(requireContext())
                        .setTitle("生成失败")
                        .setMessage("错误：${e.message}")
                        .setPositiveButton("确定", null)
                        .show())
                }
            }
            .setNegativeButton("取消", null)
            .show())
    }

    private fun showLogoutDialog() {
        DialogStyler.style(AlertDialog.Builder(requireContext())
            .setTitle("退出登录")
            .setMessage("确定要退出当前账号吗？退出后需要重新登录。")
            .setPositiveButton("退出") { _, _ ->
                UserManager.reset()
                parentFragmentManager.popBackStack()
            }
            .setNegativeButton("取消", null)
            .show())
    }
}