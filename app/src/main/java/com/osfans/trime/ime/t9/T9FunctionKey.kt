// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.widget.Button
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import splitties.dimensions.dp

/**
 * T9功能鍵組件
 *
 * 用於顯示語言切換、符號等功能按鈕，包含：
 * - 自定義文字顯示
 * - 按壓視覺回饋
 * - 觸覺回饋
 * - 主題樣式適配
 *
 * @param context Android上下文
 * @param attrs 屬性集
 */
class T9FunctionKey
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : Button(context, attrs) {
        // 正常狀態背景
        private val normalDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#2e2e2e"))
                setStroke(dp(1), Color.parseColor("#444444"))
            }

        // 按壓狀態背景
        private val pressedDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#4e4e4e"))
                setStroke(dp(1), Color.parseColor("#666666"))
            }

        init {
            setupButton()
            setupTouchHandling()
        }

        /**
         * 設置按鈕基本屬性
         */
        private fun setupButton() {
            // 移除背景
            background = null

            // 設置文字樣式
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT

            // 設置內距
            setPadding(dp(8), dp(4), dp(8), dp(4))

            // 啟用觸覺回饋
            isHapticFeedbackEnabled = true

            // 設置點擊屬性
            isClickable = true
            isFocusable = true

            // 取消文字全大寫
            isAllCaps = false
        }

        /**
         * 設置觸控處理
         */
        private fun setupTouchHandling() {
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 按下效果
                        if (isEnabled) {
                            scaleX = 0.95f
                            scaleY = 0.95f

                            // 觸覺回饋
                            InputFeedbackManager.keyPressVibrate(this)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // 釋放效果
                        scaleX = 1.0f
                        scaleY = 1.0f

                        if (event.action == MotionEvent.ACTION_UP && isEnabled) {
                            performClick()
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        /**
         * 設置按鍵啟用狀態
         */
        override fun setEnabled(enabled: Boolean) {
            super.setEnabled(enabled)

            alpha = if (enabled) 1.0f else 0.5f
            isClickable = enabled
            isFocusable = enabled
        }

        /**
         * 播放按鍵音效
         */
        private fun playKeySound() {
            try {
                InputFeedbackManager.keyPressSound()
            } catch (e: Exception) {
                // 忽略音效播放錯誤
            }
        }

        /**
         * 模擬按鍵按下效果（用於測試）
         */
        fun simulatePress() {
            if (!isEnabled) return

            scaleX = 0.95f
            scaleY = 0.95f

            postDelayed({
                scaleX = 1.0f
                scaleY = 1.0f
            }, 100)

            performClick()
        }

        /**
         * 設置功能鍵文字
         */
        fun setFunctionText(text: String) {
            this.text = text
        }

        /**
         * 設置功能鍵圖標
         */
        fun setFunctionIcon(resourceId: Int) {
            try {
                setCompoundDrawablesWithIntrinsicBounds(resourceId, 0, 0, 0)
                compoundDrawablePadding = dp(4)
            } catch (e: Exception) {
                // 如果設置失敗，僅使用文字
            }
        }

        override fun performClick(): Boolean {
            if (isEnabled) {
                playKeySound()
            }
            return super.performClick()
        }
    }
