// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import splitties.dimensions.dp

/**
 * T9預編輯文字顯示組件
 *
 * 專為T9輸入法設計的預編輯文字顯示器，支援：
 * - 數字序列顯示（如 "123"）
 * - 注音符號顯示（如 "ㄅㄉㄚ ㄍㄐㄞ ㄓㄗㄢ"）
 * - 混合模式顯示
 * - 內建刪除按鈕
 * - 圓形螢幕優化
 * - 主題樣式適配
 *
 * @param context Android上下文
 * @param attrs 屬性集
 */
class T9PreeditView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : LinearLayout(context, attrs) {
        companion object {
            // 顯示模式
            enum class DisplayMode {
                DIGITS, // 僅顯示數字：123
                ZHUYIN, // 僅顯示注音：ㄅㄉㄚ ㄍㄐㄞ ㄓㄗㄢ
                MIXED, // 混合顯示：123 (ㄅㄉㄚ ㄍㄐㄞ ㄓㄗㄢ)
            }
        }

        // 文字顯示區域
        private val textView =
            TextView(context).apply {
                textSize = 14f // 針對圓形螢幕優化的字體大小
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                typeface = Typeface.DEFAULT
                setPadding(dp(8), dp(4), dp(4), dp(4))
            }

        // 刪除按鈕
        private val deleteButton =
            T9DeleteButton(context).apply {
                layoutParams =
                    LayoutParams(dp(32), dp(32)).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        marginEnd = dp(4)
                    }
            }

        // 刪除按鈕點擊回調
        var onDeleteClickListener: (() -> Unit)? = null

        // 顯示模式，默認為注音模式
        var displayMode: DisplayMode = DisplayMode.ZHUYIN
            set(value) {
                field = value
                updateDisplay()
            }

        // 當前數字序列
        private var digitSequence: String = ""

        // 當前注音組合
        private var zhuyinCombinations: List<String> = emptyList()

        // 主題引用
        private var theme: Theme? = null

        init {
            setupView()
        }

        /**
         * 設置視圖基本屬性
         */
        private fun setupView() {
            // 設置為水平佈局
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            // 內距設置
            setPadding(0, 0, 0, 0)

            // 背景顏色（調試用）
            setBackgroundColor("#2A2A2A".toColorInt())

            // 添加文字區域和刪除按鈕
            addView(textView, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(deleteButton)

            // 設置刪除按鈕點擊事件
            deleteButton.setOnClickListener {
                onDeleteClickListener?.invoke()
            }
        }

        /**
         * 更新顯示內容
         */
        fun updateContent(
            digitSequence: String,
            zhuyinCombinations: List<String>,
        ) {
            this.digitSequence = digitSequence
            this.zhuyinCombinations = zhuyinCombinations

            updateDisplay()
        }

        /**
         * 更新顯示文字
         */
        private fun updateDisplay() {
            val displayText =
                when (displayMode) {
                    DisplayMode.DIGITS -> formatDigitSequence()
                    DisplayMode.ZHUYIN -> formatZhuyinCombinations()
                    DisplayMode.MIXED -> formatMixedDisplay()
                }

            textView.text = displayText
        }

        /**
         * 格式化數字序列顯示
         */
        private fun formatDigitSequence(): String {
            if (digitSequence.isEmpty()) return ""

            // 在數字之間添加空格以提高可讀性
            return digitSequence.toCharArray().joinToString(" ")
        }

        /**
         * 格式化注音組合顯示
         */
        private fun formatZhuyinCombinations(): String {
            if (zhuyinCombinations.isEmpty()) return ""

            // 顯示第一個注音組合，如果有多個則顯示前幾個
            return when {
                zhuyinCombinations.size == 1 -> zhuyinCombinations[0]
                zhuyinCombinations.size <= 3 -> zhuyinCombinations.joinToString(" / ")
                else -> "${zhuyinCombinations.take(2).joinToString(" / ")}..."
            }
        }

        /**
         * 格式化混合顯示
         */
        private fun formatMixedDisplay(): String {
            val digits = formatDigitSequence()
            val zhuyin = formatZhuyinCombinations()

            return when {
                digits.isEmpty() && zhuyin.isEmpty() -> ""
                digits.isEmpty() -> zhuyin
                zhuyin.isEmpty() -> digits
                else -> "$digits ($zhuyin)"
            }
        }

        /**
         * 更新主題樣式
         */
        fun updateTheme(theme: Theme) {
            this.theme = theme

            try {
                // 更新文字顏色
                val textColor =
                    ColorManager.getColor("preedit_text_color")
                        ?: ColorManager.getColor("text_color")
                        ?: Color.WHITE
                textView.setTextColor(textColor)

                // 更新背景顏色
                val backgroundColor =
                    ColorManager.getColor("preedit_background_color")
                        ?: Color.parseColor("#2A2A2A")
                setBackgroundColor(backgroundColor)

                // 更新字體
                val font =
                    FontManager.getTypeface("preedit_font")
                        ?: FontManager.getTypeface("text_font")
                        ?: Typeface.DEFAULT
                textView.typeface = font

                // 更新字體大小
                val fontSize = theme.generalStyle.textSize.takeIf { it > 0 } ?: 14f
                textView.textSize = (fontSize * 0.9f) // 比一般文字稍小

                // 更新刪除按鈕主題
                deleteButton.updateTheme(theme)
            } catch (e: Exception) {
                // 如果主題配置失敗，使用默認樣式
                textView.setTextColor(Color.WHITE)
                setBackgroundColor("#2A2A2A".toColorInt())
                textView.typeface = Typeface.DEFAULT
            }
        }

        /**
         * 清空顯示內容
         */
        fun clear() {
            digitSequence = ""
            zhuyinCombinations = emptyList()
            updateDisplay()
        }

        /**
         * 檢查是否有內容顯示
         */
        fun hasContent(): Boolean = digitSequence.isNotEmpty() || zhuyinCombinations.isNotEmpty()

        /**
         * 獲取當前顯示的文字
         */
        fun getCurrentDisplayText(): String = textView.text.toString()

        /**
         * 為了兼容性而保留的 TextView 屬性
         */
        @Deprecated("Use updateContent() instead", ReplaceWith("updateContent()"))
        var text: CharSequence
            get() = textView.text
            set(value) {
                textView.text = value
            }
    }
