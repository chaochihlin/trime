// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.TrimeInputMethodService
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.constraintlayout.topToBottomOf
import splitties.views.dsl.core.add
import timber.log.Timber

/**
 * T9輸入法完整容器
 *
 * 整合所有T9組件的主容器，包括：
 * - T9鍵盤主體
 * - 情境顯示區域
 * - 候選詞水平滾動列
 * - 事件處理系統
 *
 * 專為圓形螢幕設計的完整T9輸入解決方案。
 *
 * @param context Android上下文
 * @param attrs 屬性集
 */
class T9InputContainer
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : ConstraintLayout(context, attrs) {
        // T9組件
        private lateinit var t9Keyboard: T9KeyboardView
        private lateinit var contextDisplay: ContextDisplayArea
        private lateinit var candidateBar: T9CandidateBar
        private lateinit var confirmButton: T9ConfirmButton // Stage 8A: 移至容器管理
        private lateinit var eventHandler: T9InputEventHandler

        // 配置參數
        private lateinit var theme: Theme
        private lateinit var rimeSession: RimeSession
        private lateinit var service: TrimeInputMethodService

        init {
            id = generateViewId()

            // 🔍 視覺除錯：為T9InputContainer添加明顯的背景色
            setBackgroundColor(Color.parseColor("#FF4444")) // 紅色背景
            Timber.d("T9InputContainer: 🔴 T9InputContainer background set to RED for visual debugging")
        }

        companion object {
            private const val TAG = "T9InputContainer"
        }

        /**
         * 初始化T9輸入法容器
         */
        fun setup(
            theme: Theme,
            rimeSession: RimeSession,
            service: TrimeInputMethodService,
        ) {
            try {
                Timber.d("$TAG: Starting T9InputContainer setup")

                this.theme = theme
                this.rimeSession = rimeSession
                this.service = service

                Timber.d("$TAG: Creating T9 components...")
                Timber.d("$TAG: 🎨 Setting visual debugging colors for all T9 components")
                createComponents()

                Timber.d("$TAG: Setting up T9 layout...")
                setupLayout()

                Timber.d("$TAG: Setting up T9 event handling...")
                setupEventHandling()

                Timber.d("$TAG: Updating T9 themes...")
                updateThemes()

                Timber.d("$TAG: T9InputContainer setup completed successfully")
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Critical error during T9InputContainer setup")
                throw e // Re-throw to allow caller to handle
            }
        }

        /**
         * 創建所有T9組件
         */
        private fun createComponents() {
            try {
                Timber.d("$TAG: Creating T9CandidateBar...")
                candidateBar =
                    T9CandidateBar(context).apply {
                        id = generateViewId()
                        // 🔍 視覺除錯：藍色背景for候選詞列
                        setBackgroundColor(Color.parseColor("#4444FF"))
                        visibility = android.view.View.VISIBLE // Stage 13: 確保可見性
                        Timber.d("$TAG: 🔵 T9CandidateBar background set to BLUE and visibility VISIBLE")
                    }
                Timber.d("$TAG: T9CandidateBar created successfully")

                Timber.d("$TAG: Creating ContextDisplayArea...")
                contextDisplay =
                    ContextDisplayArea(context).apply {
                        id = generateViewId()
                        // 🔍 視覺除錯：綠色背景for情境顯示
                        setBackgroundColor(Color.parseColor("#44FF44"))
                        Timber.d("$TAG: 🟢 ContextDisplayArea background set to GREEN")
                    }
                Timber.d("$TAG: ContextDisplayArea created successfully")

                Timber.d("$TAG: Creating T9KeyboardView...")
                t9Keyboard =
                    T9KeyboardView(context).apply {
                        id = generateViewId()
                        // 🔍 視覺除錯：黃色背景for T9鍵盤
                        setBackgroundColor(Color.parseColor("#FFFF44"))
                        Timber.d("$TAG: 🟡 T9KeyboardView background set to YELLOW")
                    }
                Timber.d("$TAG: T9KeyboardView created successfully")

                Timber.d("$TAG: Creating T9ConfirmButton...")
                confirmButton =
                    T9ConfirmButton(context).apply {
                        id = generateViewId()
                        // 🔍 視覺除錯：橘色背景for確認按鈕
                        setBackgroundColor(Color.parseColor("#FF8844"))
                        Timber.d("$TAG: 🟠 T9ConfirmButton background set to ORANGE")
                    }
                Timber.d("$TAG: T9ConfirmButton created successfully")

                Timber.d("$TAG: Creating T9InputEventHandler...")
                eventHandler = T9InputEventHandler(rimeSession, contextDisplay, candidateBar, service)
                Timber.d("$TAG: T9InputEventHandler created successfully")
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error creating T9 components")
                throw e
            }
        }

        /**
         * 設置佈局
         */
        private fun setupLayout() {
            try {
                Timber.d("$TAG: Adding candidate bar to layout...")
                add(
                    candidateBar,
                    lParams(dp(200), dp(40)) {
                        // Stage 13: 固定寬度200dp x 高度40dp，根本解決顯示問題
                        topOfParent(dp(8)) // Stage 11: 減少topMargin到8dp，確保有空間顯示
                        centerHorizontally()
                        // 移除constrainedWidth = true 避免被壓縮為0寬度
                    },
                )

                Timber.d("$TAG: Adding context display to layout...")
                add(
                    contextDisplay,
                    lParams(dp(56), 0) {
                        // Stage 12: 寬度56dp，高度wrap-content
                        topToBottomOf(candidateBar, dp(8)) // Stage 11: 在CandidateBar下方，保持8dp間距
                        bottomOfParent(dp(16)) // Stage 11: 底部邊距16dp
                        startOfParent(dp(16)) // Stage 9: 添加leftMargin=16dp
                    },
                )

                Timber.d("$TAG: Adding confirm button to layout...")
                add(
                    confirmButton,
                    lParams(dp(56), dp(56)) {
                        // Stage 12: 56×56dp，更大觸控區域
                        topToBottomOf(candidateBar, dp(8)) // Stage 11: 在CandidateBar下方，與ContextDisplay對齊
                        bottomOfParent(dp(16)) // Stage 11: 底部邊距16dp
                        endOfParent(dp(16)) // Stage 9: 添加rightMargin=16dp
                    },
                )

                Timber.d("$TAG: Adding T9 keyboard to layout...")
                add(
                    t9Keyboard,
                    lParams(0, 0) {
                        // Stage 8A: 佔滿剩餘空間，移除固定寬度
                        topToBottomOf(candidateBar, dp(8)) // Stage 11: 在CandidateBar下方，與其他組件對齊
                        bottomOfParent(dp(16)) // Stage 9: 添加bottomMargin=16dp
                        startToEndOf(contextDisplay, dp(4)) // Stage 11: 緊鄰ContextDisplay，4dp間距
                        endToStartOf(confirmButton, dp(4)) // Stage 10: 與confirmButton保持4dp間距
                    },
                )

                Timber.d("$TAG: Layout setup completed successfully")
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error setting up T9 layout")
                throw e
            }
        }

        /**
         * 設置事件處理
         */
        private fun setupEventHandling() {
            try {
                Timber.d("$TAG: Setting up T9 keyboard event handler...")
                t9Keyboard.setup(theme, rimeSession, eventHandler)

                Timber.d("$TAG: Setting up confirm button event handler...")
                confirmButton.setOnClickListener {
                    Timber.d("$TAG: Confirm button clicked")
                    eventHandler.onConfirmPress()
                }

                Timber.d("$TAG: Setting up candidate bar click listener...")
                candidateBar.setOnCandidateClickListener(
                    object : T9CandidateBar.OnCandidateClickListener {
                        override fun onCandidateClick(
                            position: Int,
                            candidate: com.osfans.trime.core.CandidateItem,
                        ) {
                            eventHandler.onCandidateSelected(position)
                        }
                    },
                )

                Timber.d("$TAG: Setting up context display state listener...")
                contextDisplay.setStateChangeListener(
                    object : ContextDisplayArea.StateChangeListener {
                        override fun onPunctuationClick(punctuation: String) {
                            // 處理標點符號點擊
                            handlePunctuationInput(punctuation)
                        }

                        override fun onStateChanged(newState: ContextDisplayArea.State) {
                            // 狀態變化處理（如果需要）
                        }
                    },
                )

                Timber.d("$TAG: Event handling setup completed successfully")
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error setting up T9 event handling")
                throw e
            }
        }

        /**
         * 更新所有組件的主題
         */
        private fun updateThemes() {
            contextDisplay.updateTheme(theme)
            candidateBar.updateTheme(theme)
            confirmButton.updateTheme(theme) // Stage 8A: 更新確認按鈕主題
            // t9Keyboard的主題已在setup中設置
        }

        /**
         * 處理標點符號輸入
         */
        private fun handlePunctuationInput(punctuation: String) {
            try {
                service.commitText(punctuation)
                contextDisplay.clearInput()
                candidateBar.clearCandidates()
            } catch (e: Exception) {
                // 忽略錯誤
            }
        }

        /**
         * 處理退格鍵（從外部調用）
         */
        fun handleBackspace() {
            eventHandler.onBackspacePress()
        }

        /**
         * 重置輸入狀態
         */
        fun reset() {
            eventHandler.reset()
        }

        /**
         * 設置鍵盤是否啟用
         */
        fun setKeyboardEnabled(enabled: Boolean) {
            t9Keyboard.setKeyboardEnabled(enabled)
            candidateBar.setCandidateBarEnabled(enabled)
            contextDisplay.isEnabled = enabled
            confirmButton.isEnabled = enabled // Stage 8A: 控制確認按鈕啟用狀態
        }

        /**
         * 取得T9鍵盤實例
         */
        fun getT9Keyboard(): T9KeyboardView = t9Keyboard

        /**
         * 取得情境顯示區域實例
         */
        fun getContextDisplay(): ContextDisplayArea = contextDisplay

        /**
         * 取得候選詞列實例
         */
        fun getCandidateBar(): T9CandidateBar = candidateBar

        /**
         * 取得確認按鈕實例
         */
        fun getConfirmButton(): T9ConfirmButton = confirmButton // Stage 8A: 提供確認按鈕存取

        /**
         * 取得事件處理器實例
         */
        fun getEventHandler(): T9InputEventHandler = eventHandler

        /**
         * 檢查是否有輸入內容
         */
        fun hasInput(): Boolean = contextDisplay.getCurrentState() == ContextDisplayArea.State.INPUT

        /**
         * 檢查是否有候選詞
         */
        fun hasCandidates(): Boolean = candidateBar.getCandidateCount() > 0
    }
