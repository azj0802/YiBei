package com.example.yibei.ui.utils

import android.app.AlertDialog
import android.content.DialogInterface
import android.graphics.Color

/**
 * 为 Settings 弹窗应用最简样式：使用固定的默认蓝色
 */
object DialogStyler {

    fun style(alert: AlertDialog) {
        alert.setOnShowListener { dialog ->
            val d = dialog as AlertDialog
            // 使用默认蓝色主题
            val primaryColor = Color.parseColor("#2196F3")
            listOf(DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEGATIVE, DialogInterface.BUTTON_NEUTRAL).forEach { which ->
                d.getButton(which)?.setTextColor(primaryColor)
            }
        }
    }
}
