// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import splitties.dimensions.dp

/**
 * T9情境顯示區域
 *
 * 左側垂直區域，用於動態顯示不同狀態的內容：
 * - 待輸入狀態：顯示標點符號 (，。？)
 * - 輸入中狀態：顯示當前注音序列
 * - 注音選擇狀態：顯示可選的注音符號列表（新增）
 *
 * 功能特性：
 * - 狀態自動切換
 * - 流暢的切換動畫
 * - 點擊標點符號直接輸入
 * - 滑動+點擊選擇注音符號（新增）
 * - 圓形螢幕優化佈局
 *
 * @param context Android上下文
 * @param attrs 屬性集
 */
class ContextDisplayArea
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : FrameLayout(context, attrs) {
        enum class State {
            IDLE, // 待輸入狀態：顯示標點符號
            INPUT, // 輸入中狀態：顯示注音序列（保留向後兼容）
            ZHUYIN_SELECT, // 注音選擇狀態：顯示可滾動注音列表（新增）
        }

        // 狀態切換監聽器
        interface StateChangeListener {
            fun onPunctuationClick(punctuation: String)

            fun onStateChanged(newState: State)
        }

        private var stateChangeListener: StateChangeListener? = null
        private var currentState = State.IDLE
        private var theme: Theme? = null

        // 待輸入狀態視圖
        private val idleStateView = IdleStateView(context)

        // 輸入中狀態視圖
        private val inputStateView = InputStateView(context)

        // 注音選擇器視圖（新增）
        private val zhuyinSelectorView = ZhuyinSelectorView(context)

        // 注音選擇監聽器
        private var zhuyinSelectionListener: ZhuyinSelectionListener? = null

        companion object {
            // 預設標點符號
            private val DEFAULT_PUNCTUATIONS = listOf("，", "。", "？")
            private const val TAG = "ContextDisplayArea"
        }

        init {
            setupLayout()
            switchToState(State.IDLE)
        }

        /**
         * 設置佈局
         */
        private fun setupLayout() {
            // 設置容器屬性（寬度從 60dp 增加到 64dp 以適應注音選擇器）
            layoutParams = LayoutParams(dp(64), LayoutParams.MATCH_PARENT)

            // 添加三個狀態視圖
            addView(
                idleStateView,
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT,
                ),
            )

            addView(
                inputStateView,
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT,
                ),
            )

            // 添加注音選擇器視圖（新增）
            addView(
                zhuyinSelectorView,
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT,
                ),
            )
            zhuyinSelectorView.visibility = View.GONE

            // 設置標點符號點擊監聽器
            idleStateView.setOnPunctuationClickListener { punctuation ->
                stateChangeListener?.onPunctuationClick(punctuation)
            }

            // 設置注音選擇器監聽器（新增）
            zhuyinSelectorView.setSelectionListener(
                object : ZhuyinSelectionListener {
                    override fun onZhuyinSelected(
                        digit: Int,
                        zhuyinIndex: Int,
                        zhuyin: String,
                    ) {
                        zhuyinSelectionListener?.onZhuyinSelected(digit, zhuyinIndex, zhuyin)
                    }

                    override fun onZhuyinPreviewChanged(
                        digit: Int,
                        zhuyinIndex: Int,
                    ) {
                        zhuyinSelectionListener?.onZhuyinPreviewChanged(digit, zhuyinIndex)
                    }
                },
            )
        }

        /**
         * 切換到指定狀態
         */
        fun switchToState(state: State) {
            if (currentState == state) return

            val previousState = currentState
            currentState = state

            when (state) {
                State.IDLE -> {
                    idleStateView.visibility = View.VISIBLE
                    inputStateView.visibility = View.GONE
                    zhuyinSelectorView.visibility = View.GONE
                    idleStateView.alpha = 0f
                    idleStateView
                        .animate()
                        .alpha(1f)
                        .setDuration(200)
                        .start()
                }
                State.INPUT -> {
                    idleStateView.visibility = View.GONE
                    inputStateView.visibility = View.VISIBLE
                    zhuyinSelectorView.visibility = View.GONE
                    inputStateView.alpha = 0f
                    inputStateView
                        .animate()
                        .alpha(1f)
                        .setDuration(200)
                        .start()
                }
                State.ZHUYIN_SELECT -> {
                    idleStateView.visibility = View.GONE
                    inputStateView.visibility = View.GONE
                    zhuyinSelectorView.visibility = View.VISIBLE
                    zhuyinSelectorView.alpha = 0f
                    zhuyinSelectorView
                        .animate()
                        .alpha(1f)
                        .setDuration(200)
                        .start()
                }
            }

            stateChangeListener?.onStateChanged(state)
        }

        /**
         * 更新輸入序列顯示
         */
        fun updateInputSequence(zhuyinSequence: String) {
            inputStateView.updateSequence(zhuyinSequence)
            if (currentState != State.INPUT) {
                switchToState(State.INPUT)
            }
        }

        /**
         * 清空輸入並返回待機狀態
         */
        fun clearInput() {
            inputStateView.clearSequence()
            switchToState(State.IDLE)
        }

        /**
         * 設置狀態變化監聽器
         */
        fun setStateChangeListener(listener: StateChangeListener) {
            this.stateChangeListener = listener
        }

        /**
         * 更新主題樣式
         */
        fun updateTheme(theme: Theme) {
            this.theme = theme
            idleStateView.updateTheme(theme)
            inputStateView.updateTheme(theme)
        }

        /**
         * 取得當前狀態
         */
        fun getCurrentState(): State = currentState

        // ==================== 注音選擇器 API（新增）====================

        /**
         * 顯示注音選擇器
         *
         * 切換到 ZHUYIN_SELECT 狀態，並顯示指定數字鍵對應的注音選項。
         *
         * @param digit 數字鍵 (0-9)
         * @param preselectedIndex 預選的注音索引，預設為 0
         */
        fun showZhuyinSelector(
            digit: Int,
            preselectedIndex: Int = 0,
        ) {
            zhuyinSelectorView.showZhuyinForDigit(digit, preselectedIndex)
            switchToState(State.ZHUYIN_SELECT)
        }

        /**
         * 取得當前選中的注音符號
         */
        fun getSelectedZhuyin(): String = zhuyinSelectorView.getSelectedZhuyin()

        /**
         * 取得當前選中的注音索引
         */
        fun getSelectedZhuyinIndex(): Int = zhuyinSelectorView.getSelectedIndex()

        /**
         * 取得當前顯示的數字鍵
         */
        fun getCurrentDigit(): Int = zhuyinSelectorView.getCurrentDigit()

        /**
         * 設置注音選擇監聽器
         */
        fun setZhuyinSelectionListener(listener: ZhuyinSelectionListener) {
            this.zhuyinSelectionListener = listener
        }

        /**
         * 清空注音選擇器並返回待機狀態
         */
        fun clearZhuyinSelector() {
            zhuyinSelectorView.clear()
            switchToState(State.IDLE)
        }

        /**
         * 顯示注音組合列表（新 API）
         *
         * 用於顯示從 RIME 候選詞提取的注音組合。
         *
         * @param combinations 注音組合列表，如 ["ㄏㄠ", "ㄏㄞ", "ㄒㄧ"]
         * @param preselectedIndex 預選索引，預設 0
         */
        fun showZhuyinCombinations(
            combinations: List<String>,
            preselectedIndex: Int = 0,
        ) {
            if (combinations.isEmpty()) return

            zhuyinSelectorView.showCombinations(combinations, preselectedIndex)
            switchToState(State.ZHUYIN_SELECT)
        }

        // ==================== 內部類別 ====================

        /**
         * 待輸入狀態視圖
         */
        private class IdleStateView(
            context: Context,
        ) : LinearLayout(context) {
            private val punctuationButtons = mutableListOf<TextView>()
            private var onPunctuationClickListener: ((String) -> Unit)? = null

            init {
                setupLayout()
                createPunctuationButtons()
            }

            private fun setupLayout() {
                orientation = VERTICAL
                gravity = Gravity.CENTER

                // 設置內距
                setPadding(dp(4), dp(8), dp(4), dp(8))
            }

            private fun createPunctuationButtons() {
                DEFAULT_PUNCTUATIONS.forEach { punctuation ->
                    val button =
                        TextView(context).apply {
                            text = punctuation
                            textSize = 16f
                            setTextColor(Color.GRAY)
                            gravity = Gravity.CENTER

                            // 設置點擊區域
                            setPadding(dp(8), dp(12), dp(8), dp(12))
                            isClickable = true
                            isFocusable = true

                            // 設置背景（圓角矩形）
                            background = createRippleDrawable()

                            setOnClickListener {
                                onPunctuationClickListener?.invoke(punctuation)
                                // 觸覺回饋
                                InputFeedbackManager.keyPressVibrate(this)
                            }
                        }

                    addView(
                        button,
                        LayoutParams(
                            LayoutParams.MATCH_PARENT,
                            LayoutParams.WRAP_CONTENT,
                        ).apply {
                            topMargin = if (punctuationButtons.isNotEmpty()) dp(8) else 0
                        },
                    )

                    punctuationButtons.add(button)
                }
            }

            private fun createRippleDrawable(): android.graphics.drawable.Drawable {
                val shape =
                    android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = dp(8).toFloat()
                        setColor(Color.TRANSPARENT)
                        setStroke(dp(1), Color.parseColor("#444444"))
                    }
                return shape
            }

            fun setOnPunctuationClickListener(listener: (String) -> Unit) {
                this.onPunctuationClickListener = listener
            }

            fun updateTheme(theme: Theme) {
                try {
                    val punctuationTextColor =
                        ColorManager.getColor("punctuation_text_color")

                    punctuationButtons.forEach { button ->
                        button.setTextColor(punctuationTextColor)
                    }
                } catch (e: Exception) {
                    // 使用默認顏色
                    punctuationButtons.forEach { button ->
                        button.setTextColor(Color.GRAY)
                    }
                }
            }
        }

        /**
         * 輸入中狀態視圖
         */
        private class InputStateView(
            context: Context,
        ) : LinearLayout(context) {
            private val sequenceDisplay = TextView(context)

            init {
                setupLayout()
            }

            private fun setupLayout() {
                orientation = VERTICAL
                gravity = Gravity.CENTER

                // 設置內距
                setPadding(dp(4), dp(8), dp(4), dp(8))

                // 添加序列顯示
                sequenceDisplay.apply {
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    maxLines = 4

                    // 設置內距
                    setPadding(dp(4), dp(8), dp(4), dp(4))
                }

                addView(
                    sequenceDisplay,
                    LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(8)
                    },
                )
            }

            fun updateSequence(sequence: String) {
                sequenceDisplay.text = sequence
            }

            fun clearSequence() {
                sequenceDisplay.text = ""
            }

            fun updateTheme(theme: Theme) {
                try {
                    val inputSequenceTextColor =
                        ColorManager.getColor("input_sequence_text_color")
                    sequenceDisplay.setTextColor(inputSequenceTextColor)
                    val sequenceTextSize = theme.generalStyle.keyLongTextSize.takeIf { it > 0 } ?: 14f
                    sequenceDisplay.textSize = sequenceTextSize
                } catch (e: Exception) {
                    // 使用默認顏色
                    sequenceDisplay.setTextColor(Color.WHITE)
                }
            }
        }
    }
