// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ImageButton
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import splitties.dimensions.dp

/**
 * T9圓形確認按鈕
 *
 * 特殊設計的圓形白底確認按鈕，包含：
 * - 圓形白色背景
 * - 黑色勾勾圖標
 * - 按壓視覺效果
 * - 陰影效果
 * - 觸覺回饋
 *
 * @param context Android上下文
 * @param attrs 屬性集
 */
class T9ConfirmButton
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : ImageButton(context, attrs) {
        // 正常狀態背景
        private val normalDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }

        // 按壓狀態背景
        private val pressedDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#e0e0e0"))
            }

        // 禁用狀態背景
        private val disabledDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#cccccc"))
            }

        private var theme: Theme? = null

        init {
            setupButton()
            setupTouchHandling()
        }

        /**
         * 設置按鈕基本屬性
         */
        private fun setupButton() {
            // 設置背景
            background = normalDrawable

            // 設置勾勾圖標 - 使用系統的確認圖標
            try {
                setImageResource(R.drawable.ic_baseline_check_circle_24) // Stage 12: 使用項目內的checkmark圖標
                scaleType = ScaleType.CENTER
            } catch (e: Exception) {
                // 如果找不到圖標，使用文字替代
                setImageDrawable(null)
            }

            // 設置尺寸
            layoutParams?.let {
                it.width = dp(50)
                it.height = dp(50)
            }

            // 啟用觸覺回饋
            isHapticFeedbackEnabled = true

            // 設置點擊屬性
            isClickable = true
            isFocusable = true

            // 設置陰影效果
            elevation = dp(4).toFloat()
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
                            background = pressedDrawable
                            scaleX = 0.9f
                            scaleY = 0.9f
                            elevation = dp(2).toFloat()

                            // 觸覺回饋
                            InputFeedbackManager.keyPressVibrate(this)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // 釋放效果
                        background = if (isEnabled) normalDrawable else disabledDrawable
                        scaleX = 1.0f
                        scaleY = 1.0f
                        elevation = dp(4).toFloat()

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
         * 更新主題樣式
         */
        fun updateTheme(theme: Theme) {
            this.theme = theme

            try {
                // 更新按鈕背景顏色
                val confirmButtonColor =
                    ColorManager.getColor("confirm_button_color")
                        ?: Color.WHITE
                val confirmButtonPressedColor =
                    ColorManager.getColor("confirm_button_pressed_color")
                        ?: Color.parseColor("#e0e0e0")
                val confirmButtonDisabledColor =
                    ColorManager.getColor("confirm_button_disabled_color")
                        ?: Color.parseColor("#cccccc")

                normalDrawable.setColor(confirmButtonColor)
                pressedDrawable.setColor(confirmButtonPressedColor)
                disabledDrawable.setColor(confirmButtonDisabledColor)

                // 更新圖標顏色
                val iconColor =
                    ColorManager.getColor("confirm_button_icon_color")
                        ?: Color.BLACK
                setColorFilter(iconColor)
            } catch (e: Exception) {
                // 如果主題色彩獲取失敗，使用默認顏色
                normalDrawable.setColor(Color.WHITE)
                pressedDrawable.setColor(Color.parseColor("#e0e0e0"))
                disabledDrawable.setColor(Color.parseColor("#cccccc"))
                setColorFilter(Color.BLACK)
            }

            // 刷新當前狀態
            updateButtonState()
        }

        /**
         * 更新按鈕狀態
         */
        private fun updateButtonState() {
            background =
                when {
                    !isEnabled -> disabledDrawable
                    else -> normalDrawable
                }
        }

        /**
         * 設置按鈕啟用狀態
         */
        override fun setEnabled(enabled: Boolean) {
            super.setEnabled(enabled)
            updateButtonState()
            alpha = if (enabled) 1.0f else 0.6f
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
         * 模擬按鈕按下效果（用於測試）
         */
        fun simulatePress() {
            if (!isEnabled) return

            background = pressedDrawable
            scaleX = 0.9f
            scaleY = 0.9f
            elevation = dp(2).toFloat()

            postDelayed({
                background = normalDrawable
                scaleX = 1.0f
                scaleY = 1.0f
                elevation = dp(4).toFloat()
            }, 150)

            performClick()
        }

        /**
         * 設置確認按鈕圖標
         */
        fun setConfirmIcon(resourceId: Int) {
            try {
                setImageResource(resourceId)
            } catch (e: Exception) {
                // 如果設置失敗，保持當前圖標
            }
        }

        /**
         * 隱藏確認按鈕（在特定模式下）
         */
        fun hide() {
            visibility = GONE
            isEnabled = false
        }

        /**
         * 顯示確認按鈕
         */
        fun show() {
            visibility = VISIBLE
            isEnabled = true
        }

        override fun performClick(): Boolean {
            if (isEnabled) {
                playKeySound()
            }
            return super.performClick()
        }
    }
