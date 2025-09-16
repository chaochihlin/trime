package com.osfans.trime.ui.test

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.osfans.trime.R

class InputTestActivity : Activity() {
    private lateinit var inputEditText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 在 setContentView 之前設定視窗屬性
        setupFullscreenWindow()

        setContentView(R.layout.activity_input_test)

        inputEditText = findViewById(R.id.input_edit_text)

        // 自動 focus 到輸入框並顯示鍵盤
        inputEditText.requestFocus()
        inputEditText.postDelayed({
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(inputEditText, InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    private fun setupFullscreenWindow() {
        // 方法 1: 請求隱藏標題
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        // 方法 2: 設定全螢幕 flag
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // 方法 3: 隱藏狀態列和導航列
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 新 API
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // 舊版本 API
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }

        // 方法 4: 確保沒有任何裝飾
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 調試用：記錄視窗設定
        android.util.Log.d("Trime", "InputTestActivity - 全螢幕設定完成")
    }
}
