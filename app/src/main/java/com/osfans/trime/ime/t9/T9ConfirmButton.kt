// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import splitties.dimensions.dp

/**
 * T9確認按鈕
 *
 * 簡化的 IconButton 風格，包含：
 * - 透明背景
 * - 可主題化圖標
 * - 漣漪動畫效果
 * - 觸覺回饋和音效
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
        private var theme: Theme? = null

        init {
            setupButton()
        }

        /**
         * 設置按鈕基本屬性
         */
        private fun setupButton() {
            // 設置確認圖標
            try {
                setImageResource(R.drawable.ic_baseline_check_circle_24)
                scaleType = ScaleType.CENTER
            } catch (e: Exception) {
                // 如果找不到圖標，保持默認
            }

            // 設置尺寸 - IconButton 標準尺寸
            minimumWidth = dp(48)
            minimumHeight = dp(48)

            // 設置透明背景，移除白底
            background = ContextCompat.getDrawable(context, android.R.color.transparent)

            // 設置漣漪效果
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                try {
                    val colorStateList = ContextCompat.getColorStateList(context, android.R.color.darker_gray)
                    if (colorStateList != null) {
                        val ripple = RippleDrawable(colorStateList, null, null)
                        background = ripple
                    }
                } catch (e: Exception) {
                    // 如果漣漪效果失敗，保持透明背景
                    background = ContextCompat.getDrawable(context, android.R.color.transparent)
                }
            }

            // 啟用觸覺回饋
            isHapticFeedbackEnabled = true

            // 設置點擊監聽器以添加音效和觸覺回饋
            setOnClickListener {
                performConfirmClick()
            }
        }

        /**
         * 處理確認按鈕點擊
         */
        private fun performConfirmClick() {
            if (isEnabled) {
                // 觸覺回饋
                InputFeedbackManager.keyPressVibrate(this)
                // 音效
                playKeySound()
            }
        }

        /**
         * 更新主題樣式
         */
        fun updateTheme(theme: Theme) {
            this.theme = theme

            try {
                // 更新圖標顏色
                val iconColor =
                    ColorManager.getColor("confirm_button_icon_color")
                        ?: Color.WHITE // 預設為白色，在深色背景上顯示較好
                setColorFilter(iconColor)
            } catch (e: Exception) {
                // 如果主題色彩獲取失敗，使用白色
                setColorFilter(Color.WHITE)
            }
        }

        /**
         * 設置按鈕啟用狀態
         */
        override fun setEnabled(enabled: Boolean) {
            super.setEnabled(enabled)
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
            // 注意：音效和觸覺回饋已在 performConfirmClick() 中處理
            // 這裡不再重複處理，避免雙重觸發
            return super.performClick()
        }
    }
