package com.example.yibei

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.yibei.data.UserManager

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapWithLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyDarkModeOnColdStart()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 使用白色主题
        window.statusBarColor = 0xFFFFFFFF.toInt()
        findViewById<android.view.View>(R.id.splash_root)?.let {
            it.setBackgroundColor(0xFFFFFFFF.toInt())
        }
        UserManager.init(this)

        val waterView = findViewById<ImageView>(R.id.iv_water)

        waterView.post {
            waterView.pivotX = waterView.width / 2f
            waterView.pivotY = waterView.height.toFloat()
            waterView.scaleY = 0f

            val fillWater = ObjectAnimator.ofFloat(waterView, "scaleY", 0f, 1f).apply {
                duration = 1500
                startDelay = 300
                interpolator = DecelerateInterpolator()
            }
            fillWater.start()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 2500)
    }

    private fun applyDarkModeOnColdStart() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        when (prefs.getInt("dark_mode", 0)) {
            0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }
}
