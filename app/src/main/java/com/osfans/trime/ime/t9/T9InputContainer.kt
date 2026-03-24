// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.core.graphics.toColorInt
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.constraintlayout.topToBottomOf
import splitties.views.dsl.core.add
import splitties.views.dsl.core.wrapContent
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
        private lateinit var textInputArea: T9TextInputView
        private lateinit var candidateBar: T9CandidateBar
        private lateinit var confirmButton: T9ConfirmButton
        private lateinit var rightPanel: LinearLayout // 右側面板：聲調鍵 + 確認鍵
        private lateinit var candidateWrapper: FrameLayout // 候選詞欄包裝器（含漸層遮罩）
        private lateinit var candidateGradient: View // 候選詞欄右側漸層遮罩
        private lateinit var eventHandler: T9InputEventHandler

        // 聲調鍵 TextView（key = 聲調符號，value = TextView）
        private val toneKeys = mutableMapOf<String, TextView>()
        private val TONE_SYMBOLS = listOf("ˊ", "ˇ", "ˋ", "˙")

        // 配置參數
        private lateinit var theme: Theme
        private lateinit var rimeSession: RimeSession
        private lateinit var service: TrimeInputMethodService

        init {
            id = generateViewId()
            setBackgroundColor("#1B224D".toColorInt())
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
                this.theme = theme
                this.rimeSession = rimeSession
                this.service = service

                createComponents()
                setupLayout()
                setupEventHandling()
                updateThemes()
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Critical error during T9InputContainer setup")
                throw e
            }
        }

        /**
         * 創建所有T9組件
         */
        private fun createComponents() {
            try {
                // 初始化 T9 字詞資料載入器
                T9CharDataLoader.init(context)

                candidateBar =
                    T9CandidateBar(context).apply {
                        id = generateViewId()
                        visibility = VISIBLE
                    }

                // 候選詞欄右側漸層遮罩（透明→底色，提示可橫滑）
                candidateGradient =
                    object : View(context) {
                        override fun onTouchEvent(event: MotionEvent?): Boolean = false
                    }.apply {
                        background =
                            GradientDrawable(
                                GradientDrawable.Orientation.LEFT_RIGHT,
                                intArrayOf(Color.TRANSPARENT, "#1B224D".toColorInt()),
                            )
                        isClickable = false
                        isFocusable = false
                        visibility = GONE
                    }
                candidateWrapper =
                    FrameLayout(context).apply {
                        id = generateViewId()
                        addView(
                            candidateBar,
                            FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT,
                            ),
                        )
                        addView(
                            candidateGradient,
                            FrameLayout.LayoutParams(
                                dp(24),
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                Gravity.END,
                            ),
                        )
                    }

                contextDisplay =
                    ContextDisplayArea(context).apply {
                        id = generateViewId()
                    }

                textInputArea =
                    T9TextInputView(context).apply {
                        id = generateViewId()
                    }
                t9Keyboard =
                    T9KeyboardView(context).apply {
                        id = generateViewId()
                    }

                confirmButton =
                    T9ConfirmButton(context).apply {
                        id = generateViewId()
                    }

                // 建立右側面板：聲調鍵 + 確認鍵垂直排列
                rightPanel = createRightPanel()

                eventHandler = T9InputEventHandler(rimeSession, contextDisplay, textInputArea, candidateBar, service)
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
                add(
                    candidateWrapper,
                    lParams(0, dp(50)) {
                        topOfParent(dp(28))
                        startOfParent(dp(115)) // 左邊緣維持原位
                        endOfParent(dp(16)) // 右邊延伸至接近圓弧邊緣
                    },
                )

                Timber.d("$TAG: Adding text input area to layout...")
                add(
                    textInputArea,
                    lParams(0, wrapContent) {
                        // 文字輸入區域：左右各32dp margin，高度由內容決定
                        topToBottomOf(candidateWrapper, dp(2)) // 距離候選詞區域2dp
                        startOfParent(dp(32)) // 左邊距32dp
                        endOfParent(dp(32)) // 右邊距32dp
                    },
                )

                Timber.d("$TAG: Adding context display to layout...")
                add(
                    contextDisplay,
                    lParams(dp(64), wrapContent) {
                        // 寬度64dp，高度wrap-content，頂部錨定在文字輸入框下方
                        topToBottomOf(textInputArea, dp(8))
                        startOfParent(dp(16)) // 添加leftMargin=16dp
                    },
                )

                Timber.d("$TAG: Adding right panel to layout...")
                add(
                    rightPanel,
                    lParams(dp(56), wrapContent) {
                        // 56dp寬，高度 wrap_content，頂部錨定在文字輸入框下方
                        topToBottomOf(textInputArea, dp(8))
                        endOfParent(dp(24))
                    },
                )

                Timber.d("$TAG: Adding T9 keyboard to layout...")
                add(
                    t9Keyboard,
                    lParams(0, 0) {
                        // 修改：T9KeyboardView 位於 textInputArea 下方
                        topToBottomOf(textInputArea, dp(8)) // 距離文字輸入區域8dp
                        bottomOfParent(dp(16)) // 添加bottomMargin=16dp
                        startToEndOf(contextDisplay, dp(2)) // 緊鄰ContextDisplay，2dp間距
                        endToStartOf(rightPanel, dp(2)) // 與右側面板保持2dp間距
                    },
                )
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
                t9Keyboard.setup(theme, rimeSession, eventHandler)

                confirmButton.setOnClickListener {
                    eventHandler.onConfirmPress()
                }

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

                // 候選詞欄滾動時更新漸層遮罩
                candidateBar.addOnScrollListener(
                    object : RecyclerView.OnScrollListener() {
                        override fun onScrolled(
                            recyclerView: RecyclerView,
                            dx: Int,
                            dy: Int,
                        ) {
                            updateCandidateGradient()
                        }
                    },
                )
                candidateBar.onCandidatesUpdated = { updateCandidateGradient() }

                contextDisplay.setStateChangeListener(
                    object : ContextDisplayArea.StateChangeListener {
                        override fun onPunctuationClick(punctuation: String) {
                            handlePunctuationInput(punctuation)
                        }

                        override fun onStateChanged(newState: ContextDisplayArea.State) {
                            // 狀態變化處理（如果需要）
                            Timber.d("$TAG: ContextDisplayArea 狀態變更: $newState")
                        }
                    },
                )

                // 設置注音選擇監聽器（新增）
                contextDisplay.setZhuyinSelectionListener(
                    object : ZhuyinSelectionListener {
                        override fun onZhuyinSelected(
                            digit: Int,
                            zhuyinIndex: Int,
                            zhuyin: String,
                        ) {
                            Timber.d("$TAG: 注音選擇確認 - digit=$digit, index=$zhuyinIndex, zhuyin=$zhuyin")
                            eventHandler.onZhuyinSelected(digit, zhuyinIndex, zhuyin)
                        }

                        override fun onZhuyinPreviewChanged(
                            digit: Int,
                            zhuyinIndex: Int,
                        ) {
                            Timber.d("$TAG: 注音預覽變更 - digit=$digit, index=$zhuyinIndex")
                            // 可選：預覽變更時的處理（目前僅記錄日誌）
                        }
                    },
                )

                // 設置聲調鍵點擊事件
                for ((tone, toneView) in toneKeys) {
                    toneView.setOnClickListener {
                        InputFeedbackManager.keyPressVibrate(toneView)
                        InputFeedbackManager.keyPressSound()
                        eventHandler.onToneKeyPress(tone)
                    }
                }

                // 設置聲調過濾變更回呼（更新高亮狀態）
                eventHandler.onToneFilterChanged = { activeTone ->
                    setActiveTone(activeTone)
                }

                // 設置合法聲調變更回呼（動態顯示/隱藏聲調鍵）
                eventHandler.onValidTonesChanged = { validTones ->
                    updateVisibleToneKeys(validTones)
                }

                // 設置文字輸入框刪除按鈕點擊事件
                textInputArea.onDeleteClickListener = {
                    eventHandler.onBackspacePress()
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error setting up T9 event handling")
                throw e
            }
        }

        /**
         * 更新所有組件的主題
         */
        private fun updateThemes() {
            try {
                contextDisplay.updateTheme(theme)
                textInputArea.updateTheme(theme)
                // candidateBar不再需要updateTheme，因為已移除Theme依賴
                confirmButton.updateTheme(theme)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to update component themes")
            }
        }

        /**
         * 建立右側面板：確認鍵（✓）在最上方 + 動態聲調鍵
         */
        private fun createRightPanel(): LinearLayout {
            return LinearLayout(context).apply {
                id = generateViewId()
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL

                // 確認鍵（固定在最上方，加大觸控區域）
                addView(confirmButton, LinearLayout.LayoutParams(dp(56), dp(48)))
                // 聲調鍵（根據候選字動態顯示/隱藏，與確認鍵保持間距）
                for ((index, tone) in TONE_SYMBOLS.withIndex()) {
                    val lp = LinearLayout.LayoutParams(dp(56), dp(28))
                    lp.topMargin = if (index == 0) dp(12) else dp(6) // 確認鍵間距12dp，聲調間距6dp
                    addView(createToneKey(tone), lp)
                }
            }
        }

        /**
         * 建立單個聲調鍵 TextView
         */
        private fun createToneKey(tone: String): TextView {
            return TextView(context).apply {
                text = tone
                textSize = 30f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                isHapticFeedbackEnabled = true
                // 儲存到 map 以便後續更新高亮
                toneKeys[tone] = this
            }
        }

        /**
         * 更新聲調鍵高亮狀態
         *
         * @param activeTone 當前選中的聲調（null 表示無選中）
         */
        private fun setActiveTone(activeTone: String?) {
            for ((tone, toneView) in toneKeys) {
                if (tone == activeTone) {
                    toneView.setBackgroundColor("#00BCD4".toColorInt())
                    toneView.setTypeface(null, Typeface.BOLD)
                } else {
                    toneView.setBackgroundColor(Color.TRANSPARENT)
                    toneView.setTypeface(null, Typeface.NORMAL)
                }
            }
        }

        /**
         * 根據合法聲調集合動態顯示/隱藏聲調鍵
         *
         * @param validTones 當前候選字中存在的聲調集合
         */
        private fun updateVisibleToneKeys(validTones: Set<String>) {
            for ((tone, toneView) in toneKeys) {
                toneView.visibility = if (tone in validTones) VISIBLE else GONE
            }
            Timber.d("$TAG: 動態聲調鍵更新，可見: $validTones")
        }

        /**
         * 更新候選詞欄右側漸層遮罩
         */
        private fun updateCandidateGradient() {
            val hasMore = candidateBar.getCandidateCount() > 0 && candidateBar.canScrollRight()
            candidateGradient.visibility = if (hasMore) VISIBLE else GONE
        }

        /**
         * 處理標點符號輸入
         *
         * 將標點符號追加到文字輸入區（與候選字選擇行為一致）
         */
        private fun handlePunctuationInput(punctuation: String) {
            try {
                // 將標點符號追加到文字輸入區
                textInputArea.appendCandidate(punctuation)

                // 清空輸入狀態（但保留文字輸入區內容）
                contextDisplay.clearInput()
                candidateBar.clearCandidates()
            } catch (e: Exception) {
                Timber.e(e, "$TAG: 處理標點符號輸入時發生錯誤: $punctuation")
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
            textInputArea.clear()
        }

        /**
         * 設置鍵盤是否啟用
         */
        fun setKeyboardEnabled(enabled: Boolean) {
            t9Keyboard.setKeyboardEnabled(enabled)
            candidateBar.setCandidateBarEnabled(enabled)
            contextDisplay.isEnabled = enabled
            confirmButton.isEnabled = enabled
            for ((_, toneView) in toneKeys) {
                toneView.isEnabled = enabled
            }
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
         * 取得文字輸入區域實例
         */
        fun getTextInputArea(): T9TextInputView = textInputArea

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

        /**
         * 設置文字輸入框內容（用於從原輸入框載入內容）
         */
        fun setTextInputContent(text: String) {
            textInputArea.setText(text)
        }

        /**
         * 獲取文字輸入框內容
         */
        fun getTextInputContent(): String = textInputArea.getText()

        /**
         * 追加候選詞到文字輸入框
         */
        fun appendCandidateToInput(candidateText: String) {
            textInputArea.appendCandidate(candidateText)
        }
    }
