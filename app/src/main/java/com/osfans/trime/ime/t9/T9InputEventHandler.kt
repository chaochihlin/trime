// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

import com.osfans.trime.core.CandidateItem
import com.osfans.trime.daemon.RimeSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * T9輸入事件處理器
 *
 * 負責處理T9鍵盤的所有輸入事件，整合SimpleT9InputLogic提供完整的T9注音輸入功能。
 *
 * @param rimeSession RIME會話實例
 * @param contextDisplay 情境顯示區域
 * @param candidateBar 候選詞列
 * @param service 輸入法服務
 */
class T9InputEventHandler(
    private val rimeSession: RimeSession,
    private val contextDisplay: ContextDisplayArea,
    private val candidateBar: T9CandidateBar,
    private val service: com.osfans.trime.ime.core.TrimeInputMethodService,
) : T9KeyboardView.T9KeyboardActionListener {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    // T9輸入邏輯控制器
    private val t9InputLogic = SimpleT9InputLogic(rimeSession)

    companion object {
        private const val TAG = "T9InputEventHandler"
    }

    init {
        // 設置輸入邏輯狀態監聽器
        t9InputLogic.addStateListener { state ->
            updateUIWithState(state)
        }
    }

    /**
     * 處理數字鍵按下事件
     */
    override fun onNumberKeyPress(number: Int) {
        Timber.d("$TAG: Number key pressed: $number")

        try {
            // 使用新的T9輸入邏輯
            t9InputLogic.processDigit(number)
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error processing number key: $number")
        }
    }

    /**
     * 處理數字鍵長按事件
     */
    override fun onNumberKeyLongPress(number: Int): Boolean {
        Timber.d("$TAG: Number key long pressed: $number")

        try {
            // 長按可能觸發特殊功能，如符號輸入
            when (number) {
                1 -> {
                    // 長按1鍵，輸入標點符號
                    handlePunctuationInput("，")
                    return true
                }
                0 -> {
                    // 長按0鍵，輸入空格
                    handleSpaceInput()
                    return true
                }
                else -> {
                    // 其他數字鍵長按，暫時與短按相同
                    onNumberKeyPress(number)
                    return true
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error processing long press: $number")
            return false
        }
    }

    /**
     * 處理確認鍵按下事件
     */
    override fun onConfirmPress() {
        Timber.d("$TAG: Confirm button pressed")

        try {
            val state = t9InputLogic.getCurrentState()

            if (state.hasCandidates) {
                // 如果有候選詞，選擇第一個
                val selectedCandidate = t9InputLogic.selectCandidate(0)
                if (selectedCandidate != null) {
                    // 提交選中的候選詞
                    service.commitText(selectedCandidate)
                    Timber.d("$TAG: Committed first candidate: $selectedCandidate")
                }
            } else if (state.hasInput) {
                // 如果有輸入但沒有候選詞，清空輸入
                t9InputLogic.reset()
            }

            // 收合虛擬鍵盤
            coroutineScope.launch {
                try {
                    Timber.d("$TAG: Hiding keyboard after confirm")
                    service.requestHideSelf(0)
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: Error hiding keyboard")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error processing confirm press")
        }
    }

    /**
     * 處理語言切換事件
     */
    override fun onLanguageSwitch() {
        Timber.d("$TAG: Language switch pressed")

        try {
            coroutineScope.launch {
                rimeSession.runOnReady {
                    // 切換到下一個輸入方案
                    val currentSchema = "luna_pinyin" // 暫時硬編碼，之後可從主題獲取
                    Timber.d("$TAG: Current schema: $currentSchema")

                    // 這裡可以實現方案切換邏輯
                    // 暫時先清空當前輸入
                    clearComposition()

                    // 在主線程更新UI
                    clearInputState()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error processing language switch")
        }
    }

    /**
     * 處理候選詞選擇事件
     */
    fun onCandidateSelected(index: Int) {
        Timber.d("$TAG: Candidate selected: $index")

        try {
            val selectedCandidate = t9InputLogic.selectCandidate(index)
            if (selectedCandidate != null) {
                // 提交選中的候選詞
                service.commitText(selectedCandidate)
                Timber.d("$TAG: Committed candidate: $selectedCandidate")
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error selecting candidate: $index")
        }
    }

    /**
     * 處理標點符號輸入
     */
    private fun handlePunctuationInput(punctuation: String) {
        try {
            coroutineScope.launch {
                rimeSession.runOnReady {
                    // 直接提交標點符號
                    service.commitText(punctuation)

                    // 在主線程更新UI
                    clearInputState()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error inputting punctuation: $punctuation")
        }
    }

    /**
     * 處理空格輸入
     */
    private fun handleSpaceInput() {
        try {
            coroutineScope.launch {
                rimeSession.runOnReady {
                    // 正確檢查組合狀態
                    val hasComposition =
                        try {
                            !compositionCached.preedit.isNullOrEmpty()
                        } catch (e: Exception) {
                            Timber.w(e, "$TAG: Error checking composition state")
                            false
                        }

                    if (hasComposition) {
                        commitComposition()
                    } else {
                        service.commitText(" ")
                    }

                    // 在主線程更新UI
                    clearInputState()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error processing space input")
        }
    }

    /**
     * 根據T9輸入狀態更新UI
     */
    private fun updateUIWithState(state: T9InputState) {
        try {
            coroutineScope.launch {
                // 更新情境顯示
                if (state.hasInput) {
                    // 顯示數字序列和注音組合
                    val displayText =
                        buildString {
                            append(state.digitSequence)
                            if (state.zhuyinCombinations.isNotEmpty()) {
                                append(" → ")
                                append(state.zhuyinCombinations.take(3).joinToString(", "))
                            }
                        }
                    contextDisplay.updateInputSequence(displayText)
                } else {
                    contextDisplay.clearInput()
                }

                // 更新候選詞
                if (state.hasCandidates) {
                    // 轉換為CandidateItem格式
                    val candidateItems =
                        state.candidates.mapIndexed { index, text ->
                            CandidateItem(text = text, comment = "")
                        }

                    Timber.d("$TAG: Updating CandidateBar with ${candidateItems.size} candidates: ${state.candidates}")
                    candidateBar.updateCandidates(candidateItems)
                } else {
                    Timber.d("$TAG: No candidates available, clearing CandidateBar")
                    candidateBar.clearCandidates()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error updating UI with state")
        }
    }

    /**
     * 清空輸入狀態
     */
    private fun clearInputState() {
        try {
            // 清理情境顯示
            contextDisplay.clearInput()

            // 清空候選詞
            candidateBar.clearCandidates()
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error clearing input state")
        }
    }

    /**
     * 處理退格鍵
     */
    fun onBackspacePress() {
        Timber.d("$TAG: Backspace pressed")

        try {
            if (t9InputLogic.hasInput()) {
                // 如果有T9輸入，刪除最後一個數字
                t9InputLogic.deleteLastDigit()
            } else {
                // 如果沒有T9輸入，發送退格到應用程式
                service.sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DEL)
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error processing backspace")
        }
    }

    /**
     * 重置處理器狀態
     */
    fun reset() {
        try {
            t9InputLogic.reset()
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error resetting handler")
        }
    }
}
