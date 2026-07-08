package com.example.yibei.ui.onboarding

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import com.example.yibei.ui.WaterWaveView

/**
 * 首次打开引导覆盖层
 *
 * 流程：
 *   Step 1 → 全屏水波背景 + "你好"（淡入+缩放）→ 点击 →
 *   Step 2 → 全屏水波背景 + "欢迎来到一杯"（淡入+缩放）→ 点击 →
 *   引导层整体淡出消失 →
 *   回调通知 MainActivity 在首页右上角显示 Dialog 提示（3秒后自动消失）
 */
class OnboardingOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** 引导完成回调（引导层消失后触发，用于在首页显示提示弹窗） */
    var onFinished: (() -> Unit)? = null

    private val stepContainer: FrameLayout
    private var currentStep = 0

    // ── 全屏水波背景 ──
    private lateinit var waveView: WaterWaveView

    // ── 文字视图 ──
    private lateinit var tvText: TextView

    init {
        isClickable = true
        isFocusable = true

        stepContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        }
        addView(stepContainer)

        buildStepViews()
        showStep(0)
    }

    // ======================== 构建各步骤视图 ========================

    private fun buildStepViews() {
        // ---- 全屏水波背景（与喝水页同款） ----
        waveView = WaterWaveView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
            waterLevel = 0.55f
        }
        stepContainer.addView(waveView)

        // ---- 居中文字（显示在水波上方） ----
        tvText = TextView(context).apply {
            textSize = 36f
            setTextColor(Color.parseColor("#1A1A2E"))
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            setShadowLayer(dp(6).toFloat(), 0f, dp(2).toFloat(), 0x40FFFFFF)
        }
        stepContainer.addView(tvText)
    }

    // ======================== 步骤切换 ========================

    private fun showStep(step: Int) {
        currentStep = step
        when (step) {
            0 -> showTextPage("你好")
            1 -> showTextPage("欢迎来到一杯")
            else -> finishOnboarding()
        }
    }

    private fun showTextPage(text: String) {
        tvText.visibility = VISIBLE
        tvText.text = text
        tvText.alpha = 0f
        tvText.scaleX = 0.5f
        tvText.scaleY = 0.5f

        val animIn = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(tvText, View.ALPHA, 0f, 1f).setDuration(600),
                ObjectAnimator.ofFloat(tvText, View.SCALE_X, 0.5f, 1f).setDuration(700),
                ObjectAnimator.ofFloat(tvText, View.SCALE_Y, 0.5f, 1f).setDuration(700)
            )
            interpolator = DecelerateInterpolator(1.8f)
        }
        animIn.start()

        setOnClickListener {
            val fadeOut = ObjectAnimator.ofFloat(tvText, View.ALPHA, 1f, 0f).setDuration(250)
            fadeOut.addListener(onEnd = { showStep(currentStep + 1) })
            fadeOut.start()
        }
    }

    // ======================== 结束引导：整体淡出 ========================

    private fun finishOnboarding() {
        setOnClickListener(null)

        val fadeOut = ObjectAnimator.ofFloat(this@OnboardingOverlay, View.ALPHA, 1f, 0f)
            .setDuration(350)
        fadeOut.addListener(onEnd = {
            visibility = GONE
            onFinished?.invoke()
        })
        fadeOut.start()
    }

    // ======================== 工具方法 ========================

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    /** Animator listener helper */
    private inline fun android.animation.Animator.addListener(
        crossinline onEnd: () -> Unit
    ): android.animation.Animator.AnimatorListener {
        val listener = object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                onEnd()
            }
        }
        addListener(listener)
        return listener
    }
}
