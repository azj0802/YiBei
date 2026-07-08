package com.example.yibei.ui.auth

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.example.yibei.R
import com.example.yibei.data.UserManager

class LoginRegisterActivity : AppCompatActivity() {

    private lateinit var etEmailPhone: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnSubmit: Button
    private lateinit var tvSwitch: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var layoutConfirmPassword: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var cardForm: CardView

    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_register)
        UserManager.init(this)

        initViews()
        setupListeners()
        updateUI()
    }

    private fun initViews() {
        etEmailPhone = findViewById(R.id.et_email_phone)
        etPassword = findViewById(R.id.et_password)
        etConfirmPassword = findViewById(R.id.et_confirm_password)
        btnSubmit = findViewById(R.id.btn_submit)
        tvSwitch = findViewById(R.id.tv_switch)
        tvTitle = findViewById(R.id.tv_title)
        tvSubtitle = findViewById(R.id.tv_subtitle)
        layoutConfirmPassword = findViewById(R.id.layout_confirm_password)
        progressBar = findViewById(R.id.progress_bar)
        cardForm = findViewById(R.id.card_form)

        // 输入监听
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateForm()
            }
        }
        etEmailPhone.addTextChangedListener(textWatcher)
        etPassword.addTextChangedListener(textWatcher)
        etConfirmPassword.addTextChangedListener(textWatcher)
    }

    private fun setupListeners() {
        btnSubmit.setOnClickListener { onSubmit() }
        tvSwitch.setOnClickListener { toggleMode() }
    }

    private fun toggleMode() {
        isLoginMode = !isLoginMode
        updateUI()
        // 清空输入
        etEmailPhone.text.clear()
        etPassword.text.clear()
        etConfirmPassword.text.clear()
        validateForm()
    }

    private fun updateUI() {
        if (isLoginMode) {
            tvTitle.text = "欢迎回来"
            tvSubtitle.text = "登录以同步您的健康数据"
            btnSubmit.text = "登录"
            tvSwitch.text = "还没有账号？立即注册"
            layoutConfirmPassword.visibility = View.GONE
        } else {
            tvTitle.text = "创建账号"
            tvSubtitle.text = "注册以开始记录健康生活"
            btnSubmit.text = "注册"
            tvSwitch.text = "已有账号？立即登录"
            layoutConfirmPassword.visibility = View.VISIBLE
        }
        validateForm()
    }

    private fun validateForm() {
        val emailPhone = etEmailPhone.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()

        var isValid = emailPhone.isNotEmpty() && password.length >= 6
        if (!isLoginMode) {
            isValid = isValid && confirmPassword == password
        }

        btnSubmit.isEnabled = isValid
        btnSubmit.alpha = if (isValid) 1f else 0.5f
    }

    private fun onSubmit() {
        val emailPhone = etEmailPhone.text.toString().trim()
        val password = etPassword.text.toString()

        if (isLoginMode) {
            // 测试账号直接登录
            if (emailPhone == "admin" && password == "123456") {
                handleLoginSuccess(emailPhone)
                return
            }
            // 其他账号模拟网络请求
            showLoading(true)
            Handler(Looper.getMainLooper()).postDelayed({
                showLoading(false)
                Toast.makeText(this, "账号或密码错误", Toast.LENGTH_SHORT).show()
            }, 1500)
        } else {
            showLoading(true)
            Handler(Looper.getMainLooper()).postDelayed({
                showLoading(false)
                handleRegisterSuccess(emailPhone)
            }, 1500)
        }
    }

    private fun handleLoginSuccess(emailPhone: String) {
        UserManager.markLogin()
        UserManager.setName("测试用户")
        UserManager.setEmailPhone(emailPhone)
        UserManager.initAvatarColor("测试用户")
        Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    private fun handleRegisterSuccess(emailPhone: String) {
        // 注册成功后自动登录
        UserManager.markLogin()
        val name = extractNameFromEmailPhone(emailPhone)
        UserManager.setName(name)
        UserManager.setEmailPhone(emailPhone)
        UserManager.initAvatarColor(name)
        
        Toast.makeText(this, "注册成功，已自动登录", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    private fun extractNameFromEmailPhone(input: String): String {
        return if (input.contains("@")) {
            // 邮箱：取 @ 前面的部分
            input.substringBefore("@")
        } else {
            // 手机号或其他
            "用户${input.takeLast(4)}"
        }.take(8) // 限制长度
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnSubmit.visibility = if (show) View.GONE else View.VISIBLE
        cardForm.alpha = if (show) 0.5f else 1f
        cardForm.isEnabled = !show
    }

    companion object {
        const val REQUEST_CODE = 1001
    }
}