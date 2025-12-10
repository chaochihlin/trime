// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.graphics.toColorInt
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import splitties.dimensions.dp

/**
 * T9本地文字輸入框組件
 *
 * 用於直接輸入和累積候選詞的文字輸入框，支援：
 * - 直接顯示和編輯文字內容
 * - 候選詞累積功能
 * - 內建刪除按鈕
 * - 圓形螢幕優化
 * - 主題樣式適配
 * - 從原輸入框載入內容
 *
 * @param context Android上下文
 * @param attrs 屬性集
 */
class T9TextInputView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : LinearLayout(context, attrs) {
        // 文字輸入框
        private val editText =
            EditText(context).apply {
                textSize = 20f // 針對圓形螢幕優化的字體大小
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                typeface = Typeface.DEFAULT
                setPadding(dp(8), dp(2), dp(4), dp(2))
                background = null
                isSingleLine = true // 單行模式
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

        // 文字變化監聽器
        var onTextChangedListener: ((String) -> Unit)? = null

        // 主題引用
        private var theme: Theme? = null

        companion object {
            private const val TAG = "T9TextInputView"
        }

        init {
            setupView()
            setupListeners()
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

            // 背景顏色（20% 黑色）
            setBackgroundColor("#33000000".toColorInt()) // 20% 透明度黑色

            // 添加輸入框和刪除按鈕
            addView(editText, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(deleteButton)
        }

        /**
         * 設置事件監聽器
         */
        private fun setupListeners() {
            // 設置刪除按鈕點擊事件 - 只通知外部，由外部決定是否刪除
            deleteButton.setOnClickListener {
                onDeleteClickListener?.invoke()
            }

            // 監聽文字變化
            editText.addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int,
                    ) {}

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int,
                    ) {}

                    override fun afterTextChanged(s: Editable?) {
                        onTextChangedListener?.invoke(s?.toString() ?: "")
                    }
                },
            )
        }

        /**
         * 追加候選詞到輸入框末尾
         */
        fun appendCandidate(candidateText: String) {
            val currentText = editText.text.toString()
            val newText = currentText + candidateText
            editText.setText(newText)
            editText.setSelection(newText.length) // 移動游標到末尾
        }

        /**
         * 設置輸入框內容（用於從原輸入框載入內容）
         */
        fun setText(text: String) {
            editText.setText(text)
            editText.setSelection(text.length) // 移動游標到末尾
        }

        /**
         * 獲取當前輸入框內容
         */
        fun getText(): String = editText.text.toString()

        /**
         * 刪除最後一個字符
         */
        fun deleteLastCharacter() {
            val currentText = editText.text.toString()
            if (currentText.isNotEmpty()) {
                val newText = currentText.substring(0, currentText.length - 1)
                editText.setText(newText)
                editText.setSelection(newText.length)
            }
        }

        /**
         * 清空輸入框內容
         */
        fun clear() {
            editText.setText("")
        }

        /**
         * 檢查是否有內容
         */
        fun hasContent(): Boolean = editText.text.toString().isNotEmpty()

        /**
         * 設置輸入框啟用狀態
         */
        fun setInputEnabled(enabled: Boolean) {
            editText.isEnabled = enabled
            deleteButton.isEnabled = enabled
        }

        /**
         * 設置焦點
         */
        fun requestInputFocus() {
            editText.requestFocus()
        }

        /**
         * 移除焦點
         */
        fun clearInputFocus() {
            editText.clearFocus()
        }

        /**
         * 更新主題樣式
         */
        fun updateTheme(theme: Theme) {
            this.theme = theme

            try {
                // 更新文字顏色
                val textColor =
                    ColorManager.getColor("input_text_color")
                        ?: ColorManager.getColor("text_color")
                        ?: Color.WHITE
                editText.setTextColor(textColor)

                // 更新提示文字顏色
                val hintColor = "#80FFFFFF".toColorInt() // 半透明白色
                editText.setHintTextColor(hintColor)

                // 更新背景顏色（使用 20% 黑色）
                val backgroundColor =
                    ColorManager.getColor("input_background_color")
                        ?: Color.parseColor("#33000000") // 20% 透明度黑色
                setBackgroundColor(backgroundColor)

                // 更新字體
                val font =
                    FontManager.getTypeface("input_font")
                        ?: FontManager.getTypeface("text_font")
                        ?: Typeface.DEFAULT
                editText.typeface = font

                // 更新字體大小
                val fontSize = theme.generalStyle.textSize.takeIf { it > 0 } ?: 14f
                editText.textSize = fontSize

                // 更新刪除按鈕主題
                deleteButton.updateTheme(theme)
            } catch (e: Exception) {
                // 如果主題配置失敗，使用默認樣式
                editText.setTextColor(Color.WHITE)
                editText.setHintTextColor("#80FFFFFF".toColorInt())
                setBackgroundColor("#33000000".toColorInt())
                editText.typeface = Typeface.DEFAULT
            }
        }

        /**
         * 為了兼容性而保留的 TextView 屬性
         */
        @Deprecated("Use getText() instead", ReplaceWith("getText()"))
        var text: CharSequence
            get() = editText.text
            set(value) {
                editText.setText(value)
            }
    }
