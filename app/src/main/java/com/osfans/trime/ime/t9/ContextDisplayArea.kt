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
 *
 * 功能特性：
 * - 狀態自動切換
 * - 流暢的切換動畫
 * - 點擊標點符號直接輸入
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
            IDLE, // 待輸入狀態
            INPUT, // 輸入中狀態
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

        companion object {
            // 預設標點符號
            private val DEFAULT_PUNCTUATIONS = listOf("，", "。", "？")
        }

        init {
            setupLayout()
            switchToState(State.IDLE)
        }

        /**
         * 設置佈局
         */
        private fun setupLayout() {
            // 設置容器屬性
            layoutParams = LayoutParams(dp(60), LayoutParams.MATCH_PARENT)

            // 添加兩個狀態視圖
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

            // 設置標點符號點擊監聽器
            idleStateView.setOnPunctuationClickListener { punctuation ->
                stateChangeListener?.onPunctuationClick(punctuation)
            }
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
                    inputStateView.alpha = 0f
                    inputStateView
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
                            ?: Color.GRAY

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
            private val titleText = TextView(context)

            init {
                setupLayout()
            }

            private fun setupLayout() {
                orientation = VERTICAL
                gravity = Gravity.CENTER

                // 設置內距
                setPadding(dp(4), dp(8), dp(4), dp(8))

                // 添加標題
                titleText.apply {
                    text = "注音"
                    textSize = 10f
                    setTextColor(Color.parseColor("#CCCCCC"))
                    gravity = Gravity.CENTER
                }

                addView(
                    titleText,
                    LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT,
                    ),
                )

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
                            ?: Color.WHITE
                    val titleTextColor =
                        ColorManager.getColor("context_title_text_color")
                            ?: Color.parseColor("#CCCCCC")

                    sequenceDisplay.setTextColor(inputSequenceTextColor)
                    titleText.setTextColor(titleTextColor)

                    val sequenceTextSize = theme.generalStyle.keyLongTextSize.takeIf { it > 0 } ?: 14f
                    sequenceDisplay.textSize = sequenceTextSize
                } catch (e: Exception) {
                    // 使用默認顏色
                    sequenceDisplay.setTextColor(Color.WHITE)
                    titleText.setTextColor(Color.parseColor("#CCCCCC"))
                }
            }
        }
    }
