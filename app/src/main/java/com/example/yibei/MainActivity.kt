package com.example.yibei

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import com.example.yibei.databinding.ActivityMainBinding
import com.example.yibei.R
import com.example.yibei.ui.HealthFragment
import com.example.yibei.ui.HomeFragment
import com.example.yibei.ui.ProfileFragment
import com.example.yibei.ui.ScheduleCombinedFragment
import com.example.yibei.ui.onboarding.OnboardingOverlay

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    /** 首次引导覆盖层 */
    private var onboardingOverlay: OnboardingOverlay? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapWithLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 清理旧的多主题设置
        clearThemePreferences()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyDefaultThemeToNav()

        prefs = getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)

        if (savedInstanceState == null) {
            handleIntent(intent)
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> switchFragment(HomeFragment())
                R.id.nav_schedule -> switchFragment(ScheduleCombinedFragment())
                R.id.nav_health -> switchFragment(HealthFragment())
                R.id.nav_profile -> switchFragment(ProfileFragment())
            }
            true
        }

        // 首次打开引导
        checkAndShowOnboarding()
    }

    // ======================== 首次引导 ========================

    private fun checkAndShowOnboarding() {
        val isFirstLaunch = prefs.getBoolean("is_first_launch", true)
        if (!isFirstLaunch) return

        // 隐藏底部导航栏
        binding.bottomNav.visibility = android.view.View.GONE

        binding.root.post {
            onboardingOverlay = OnboardingOverlay(this).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )
                onFinished = { onOnboardingFinished() }
            }
            (binding.root as ViewGroup).addView(onboardingOverlay)
        }
    }

    private fun onOnboardingFinished() {
        // 标记已非首次
        prefs.edit().putBoolean("is_first_launch", false).apply()

        // 恢复底部导航栏
        binding.bottomNav.visibility = android.view.View.VISIBLE

        onboardingOverlay = null
    }

    private fun applyDefaultThemeToNav() {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        // 使用默认蓝色主题
        val navColors = intArrayOf(0xFF2196F3.toInt(), 0xFF999999.toInt())
        val colorStateList = android.content.res.ColorStateList(states, navColors)
        binding.bottomNav.itemIconTintList = colorStateList
        binding.bottomNav.itemTextColor = colorStateList
        binding.bottomNav.setBackgroundColor(0xFFFFFFFF.toInt())
    }

    /**
     * 清理旧的多主题相关 SharedPreferences 数据
     */
    private fun clearThemePreferences() {
        try {
            val appPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            appPrefs.edit().remove("theme").apply()
        } catch (e: Exception) {
            // 忽略清理异常
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: android.content.Intent) {
        val tab = intent.getStringExtra("open_tab")
        if (tab == "schedule") {
            binding.bottomNav.selectedItemId = R.id.nav_schedule
        } else {
            switchFragment(HomeFragment())
        }
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
