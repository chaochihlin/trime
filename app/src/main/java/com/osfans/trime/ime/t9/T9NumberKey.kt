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
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import splitties.dimensions.dp
import timber.log.Timber

/**
 * T9數字鍵組件
 *
 * 專為T9輸入法設計的數字鍵，支援：
 * - 主要數字顯示
 * - 注音符號提示
 * - 按壓視覺回饋
 * - 觸覺震動回饋
 * - 主題樣式適配
 *
 * @param context Android上下文
 * @param attrs 屬性集
 */
class T9NumberKey
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : LinearLayout(context, attrs) {
        companion object {
            private const val TAG = "T9NumberKey"
        }

        /**
         * 按鍵顯示模式
         */
        enum class KeyDisplayMode {
            NORMAL, // 正常狀態
            ZHUYIN, // 注音輸入模式（短按）
            DIGIT, // 數字輸入模式（長按）
        }

        // 注音提示TextView（現在作為主要顯示）
        private val hintTextView =
            TextView(context).apply {
                textSize = 12f // 調小注音符號字體適合手錶螢幕
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 2
                typeface = Typeface.DEFAULT_BOLD // 加粗注音符號
            }

        // 數字顯示TextView（現在作為次要顯示）
        private val numberTextView =
            TextView(context).apply {
                textSize = 14f // 增大數字字體
                setTextColor(Color.parseColor("#CCCCCC")) // 更亮的灰色
                gravity = Gravity.CENTER
            }

        // 背景Drawable
        private val backgroundDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.TRANSPARENT)
            }

        // 短按（注音模式）按壓狀態背景
        private val zhuyinPressedDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#33FFFFFF")) // 淡白色背景表示注音模式
            }

        // 長按（數字模式）按壓狀態背景
        private val digitPressedDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#33FFFF00")) // 淡黃色背景表示數字模式
            }

        var keyNumber: Int = 0
            set(value) {
                field = value
                numberTextView.text = value.toString()
            }

        var keyHints: String = ""
            set(value) {
                field = value
                hintTextView.text = value
                hintTextView.visibility = if (value.isNotEmpty()) View.VISIBLE else View.GONE
            }

        init {
            setupLayout()
            setupBackground()
            setupTouchHandling()
            setupMargin()
        }

        /**
         * 設置佈局
         */
        private fun setupLayout() {
            orientation = VERTICAL
            gravity = Gravity.CENTER

            // 設置內距（減少垂直內距讓元素更緊湊）
            setPadding(dp(4), dp(2), dp(4), dp(2))

            // 添加注音符號TextView（現在作為主要顯示，放在上方）
            addView(
                hintTextView,
                LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.CENTER
                    // 移除 weight，讓內容決定高度
                },
            )

            // 添加數字TextView（現在作為次要顯示，放在下方）
            addView(
                numberTextView,
                LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.CENTER
                    // 減少頂部邊距
                },
            )
        }

        /**
         * 設置背景
         */
        private fun setupBackground() {
            background = backgroundDrawable
            isClickable = true
            isFocusable = true

            // 啟用觸覺回饋
            isHapticFeedbackEnabled = true
        }

        /**
         * 設置邊距
         */
        private fun setupMargin() {
            val params =
                layoutParams as? android.view.ViewGroup.MarginLayoutParams
                    ?: android.view.ViewGroup.MarginLayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    )
            params.setMargins(dp(1), dp(1), dp(1), dp(1))
            layoutParams = params
        }

        /**
         * 設置觸控處理
         */
        private fun setupTouchHandling() {
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 短按視覺回饋（注音模式）
                        updateKeyDisplay(isPressed = true, mode = KeyDisplayMode.ZHUYIN)
                        InputFeedbackManager.keyPressVibrate(this)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // 釋放效果
                        updateKeyDisplay(isPressed = false, mode = KeyDisplayMode.NORMAL)

                        if (event.action == MotionEvent.ACTION_UP) {
                            performClick()
                        }
                        true
                    }
                    else -> false
                }
            }

            // 設置長按監聽器來處理數字模式視覺回饋
            setOnLongClickListener {
                // 長按視覺回饋（數字模式）
                updateKeyDisplay(isPressed = true, mode = KeyDisplayMode.DIGIT)
                InputFeedbackManager.keyPressVibrate(this, true) // 長按震動
                false // 返回 false 讓事件繼續傳遞給父級處理器
            }
        }

        /**
         * 更新按鍵顯示狀態
         */
        fun updateKeyDisplay(
            isPressed: Boolean,
            mode: KeyDisplayMode,
        ) {
            try {
                when {
                    isPressed && mode == KeyDisplayMode.ZHUYIN -> {
                        // 短按狀態 - 注音模式
                        background = zhuyinPressedDrawable
                        scaleX = 0.95f
                        scaleY = 0.95f
                        // 突出顯示注音符號
                        hintTextView.setTextColor(Color.CYAN)
                        numberTextView.setTextColor(Color.parseColor("#CCCCCC"))
                    }
                    isPressed && mode == KeyDisplayMode.DIGIT -> {
                        // 長按狀態 - 數字模式
                        background = digitPressedDrawable
                        scaleX = 0.90f
                        scaleY = 0.90f
                        // 突出顯示數字
                        hintTextView.setTextColor(Color.parseColor("#CCCCCC"))
                        numberTextView.setTextColor(Color.YELLOW)
                    }
                    else -> {
                        // 正常狀態
                        background = backgroundDrawable
                        scaleX = 1.0f
                        scaleY = 1.0f
                        // 恢復正常顏色
                        hintTextView.setTextColor(Color.WHITE)
                        numberTextView.setTextColor(Color.parseColor("#CCCCCC"))
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: 更新按鍵顯示狀態時發生錯誤")
            }
        }

        /**
         * 更新樣式
         */
        fun updateStyle() {
            try {
                // 更新注音符號文字顏色 - 強制設為白色
                hintTextView.setTextColor(Color.WHITE)

                // 更新數字文字顏色（更亮的灰色）
                val hintTextColor = Color.parseColor("#CCCCCC")
                numberTextView.setTextColor(hintTextColor)

                // 更新背景顏色 - 透明底色
                backgroundDrawable.apply {
                    setColor(Color.TRANSPARENT)
                }

                // 更新按壓狀態顏色 - 透明底色
                zhuyinPressedDrawable.apply {
                    setColor(Color.TRANSPARENT)
                }
                digitPressedDrawable.apply {
                    setColor(Color.TRANSPARENT)
                }

                // 固定字體大小，不使用主題設定
                hintTextView.textSize = 12f // 注音符號
                numberTextView.textSize = 14f // 數字標籤，稍大更清晰

                invalidate()
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to update theme for key $keyNumber")
                hintTextView.setTextColor(Color.WHITE)
                numberTextView.setTextColor(Color.parseColor("#CCCCCC"))
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
            background = zhuyinPressedDrawable
            scaleX = 0.95f
            scaleY = 0.95f

            postDelayed({
                background = backgroundDrawable
                scaleX = 1.0f
                scaleY = 1.0f
            }, 100)

            performClick()
        }

        override fun performClick(): Boolean {
            playKeySound()
            return super.performClick()
        }
    }
