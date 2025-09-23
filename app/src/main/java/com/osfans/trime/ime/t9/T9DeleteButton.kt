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
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.keyboard.InputFeedbackManager

/**
 * T9刪除按鈕
 *
 * 圓形白色背景的刪除按鈕，包含：
 * - 圓形白色背景
 * - ⌫ 退格符號
 * - 按壓視覺效果
 * - 觸覺回饋
 *
 * @param context Android上下文
 * @param attrs 屬性集
 */
class T9DeleteButton
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : TextView(context, attrs) {
        // 正常狀態背景（透明）
        private val normalDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
            }

        // 按壓狀態背景（半透明白色）
        private val pressedDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor("#40FFFFFF".toColorInt()) // 25% 透明度白色
            }

        private var theme: Theme? = null

        companion object {
            private const val TAG = "T9DeleteButton"
        }

        init {
            setupButton()
            setupTouchHandling()
        }

        /**
         * 設置按鈕基本屬性
         */
        private fun setupButton() {
            // 設置退格符號
            text = "⌫"
            textSize = 28f
            setTextColor(Color.WHITE) // 改為白色字體
            typeface = Typeface.DEFAULT
            gravity = Gravity.CENTER

            // 設置背景
            background = normalDrawable

            // 設置可點擊
            isClickable = true
            isFocusable = true

            // 啟用觸覺回饋
            isHapticFeedbackEnabled = true
        }

        /**
         * 設置觸控處理
         */
        private fun setupTouchHandling() {
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 按下效果
                        background = pressedDrawable
                        scaleX = 0.95f
                        scaleY = 0.95f

                        // 觸覺回饋
                        InputFeedbackManager.keyPressVibrate(this)

                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // 釋放效果
                        background = normalDrawable
                        scaleX = 1.0f
                        scaleY = 1.0f

                        if (event.action == MotionEvent.ACTION_UP) {
                            performClick()
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        /**
         * 更新主題樣式
         */
        fun updateTheme(theme: Theme) {
            this.theme = theme

            try {
                // 使用透明背景和白色文字
                setTextColor(Color.WHITE)

                // 更新背景顏色
                normalDrawable.setColor(Color.TRANSPARENT)
                pressedDrawable.setColor("#40FFFFFF".toColorInt())

                invalidate()
            } catch (e: Exception) {
                // 使用默認顏色
                setTextColor(Color.WHITE)
            }
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

        override fun performClick(): Boolean {
            playKeySound()
            return super.performClick()
        }
    }
