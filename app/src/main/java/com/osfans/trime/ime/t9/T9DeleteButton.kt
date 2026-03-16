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
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import splitties.dimensions.dp

/**
 * T9刪除按鈕
 *
 * Material Design 風格的退格按鈕，包含：
 * - 透明背景 + 漣漪效果
 * - backspace vector icon
 * - 觸覺回饋和音效
 *
 * @param context Android上下文
 * @param attrs 屬性集
 */
class T9DeleteButton
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : ImageButton(context, attrs) {
        private var theme: Theme? = null

        companion object {
            private const val TAG = "T9DeleteButton"
        }

        init {
            setupButton()
        }

        /**
         * 設置按鈕基本屬性
         */
        private fun setupButton() {
            // 設置退格圖標
            setImageResource(R.drawable.ic_baseline_backspace_24)
            scaleType = ScaleType.CENTER
            setColorFilter(Color.WHITE)

            // 設置尺寸
            minimumWidth = dp(32)
            minimumHeight = dp(32)

            // 設置透明背景
            background = ContextCompat.getDrawable(context, android.R.color.transparent)

            // 設置漣漪效果
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                try {
                    val colorStateList = ContextCompat.getColorStateList(context, android.R.color.darker_gray)
                    if (colorStateList != null) {
                        background = RippleDrawable(colorStateList, null, null)
                    }
                } catch (e: Exception) {
                    background = ContextCompat.getDrawable(context, android.R.color.transparent)
                }
            }

            // 啟用觸覺回饋
            isHapticFeedbackEnabled = true
        }

        /**
         * 更新主題樣式
         */
        fun updateTheme(theme: Theme) {
            this.theme = theme
            setColorFilter(Color.WHITE)
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
            InputFeedbackManager.keyPressVibrate(this)
            playKeySound()
            return super.performClick()
        }
    }
