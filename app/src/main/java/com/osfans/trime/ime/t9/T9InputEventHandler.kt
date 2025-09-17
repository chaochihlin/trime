// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * T9輸入事件處理器
 *
 * 負責處理T9鍵盤的所有輸入事件，包括：
 * - 數字鍵輸入處理
 * - 確認鍵處理
 * - 語言切換
 * - RIME引擎交互
 * - UI狀態更新
 *
 * @param rimeSession RIME會話實例
 * @param contextDisplay 情境顯示區域
 * @param candidateBar 候選詞列
 */
class T9InputEventHandler(
    private val rimeSession: RimeSession,
    private val contextDisplay: ContextDisplayArea,
    private val candidateBar: T9CandidateBar,
    private val service: com.osfans.trime.ime.core.TrimeInputMethodService,
) : T9KeyboardView.T9KeyboardActionListener {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val TAG = "T9InputEventHandler"

        // T9數字對應的RIME按鍵碼
        private val NUMBER_TO_KEYCODE =
            mapOf(
                0 to '0'.code,
                1 to '1'.code,
                2 to '2'.code,
                3 to '3'.code,
                4 to '4'.code,
                5 to '5'.code,
                6 to '6'.code,
                7 to '7'.code,
                8 to '8'.code,
                9 to '9'.code,
            )
    }

    /**
     * 處理數字鍵按下事件
     */
    override fun onNumberKeyPress(number: Int) {
        Timber.d("$TAG: Number key pressed: $number")

        try {
            // 發送到RIME引擎
            val keyCode = NUMBER_TO_KEYCODE[number] ?: return

            rimeSession.launchOnReady { rime ->
                rime.processKey(keyCode, 0u)

                // 在主線程更新UI
                coroutineScope.launch {
                    updateUIAfterInput()
                }
            }
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
            rimeSession.launchOnReady { rime ->
                // 提交當前組合
                rime.commitComposition()

                // 在主線程更新UI
                coroutineScope.launch {
                    clearInputState()
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
            rimeSession.launchOnReady { rime ->
                // 切換到下一個輸入方案
                val currentSchema = "luna_pinyin" // 暫時硬編碼，之後可從主題獲取
                Timber.d("$TAG: Current schema: $currentSchema")

                // 這裡可以實現方案切換邏輯
                // 暫時先清空當前輸入
                rime.clearComposition()

                // 在主線程更新UI
                coroutineScope.launch {
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
            rimeSession.launchOnReady { rime ->
                rime.selectCandidate(index)

                // 在主線程更新UI
                coroutineScope.launch {
                    updateUIAfterInput()
                }
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
            rimeSession.launchOnReady { rime ->
                // 直接提交標點符號
                service.commitText(punctuation)

                // 在主線程更新UI
                coroutineScope.launch {
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
            rimeSession.launchOnReady { rime ->
                // 正確檢查組合狀態
                val hasComposition =
                    try {
                        !rime.compositionCached.preedit.isNullOrEmpty()
                    } catch (e: Exception) {
                        Timber.w(e, "$TAG: Error checking composition state")
                        false
                    }

                if (hasComposition) {
                    rime.commitComposition()
                } else {
                    service.commitText(" ")
                }

                // 在主線程更新UI
                coroutineScope.launch {
                    clearInputState()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error processing space input")
        }
    }

    /**
     * 輸入後更新UI狀態
     */
    private fun updateUIAfterInput() {
        try {
            rimeSession.launchOnReady { rime ->
                // 正確獲取當前組合文字
                val composition =
                    try {
                        rime.compositionCached.preedit ?: ""
                    } catch (e: Exception) {
                        Timber.w(e, "$TAG: Error getting composition text")
                        ""
                    }

                coroutineScope.launch {
                    // 更新情境顯示
                    if (composition.isNotEmpty()) {
                        contextDisplay.updateInputSequence(composition)
                    } else {
                        contextDisplay.clearInput()
                    }

                    // 更新候選詞
                    updateCandidates()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error updating UI after input")
        }
    }

    /**
     * 更新候選詞列表
     */
    private fun updateCandidates() {
        try {
            rimeSession.launchOnReady { rime ->
                val candidates =
                    try {
                        // 正確獲取候選詞，使用適當的頁面索引
                        rime.getCandidates(0, 7) // 獲取最多7個候選詞 (與T9CandidateBar.MAX_VISIBLE_CANDIDATES一致)
                    } catch (e: Exception) {
                        Timber.w(e, "$TAG: Error getting candidates from RIME")
                        emptyArray()
                    }

                coroutineScope.launch {
                    if (candidates.isNotEmpty()) {
                        // 轉換為List適合CompactCandidateModule的格式
                        val candidateItems = candidates.toList()

                        Timber.d("$TAG: Updating CandidateBar with ${candidateItems.size} candidates: ${candidateItems.map { it.text }}")

                        // 更新候選詞列
                        candidateBar.updateCandidates(candidateItems)
                    } else {
                        Timber.d("$TAG: No candidates available, clearing CandidateBar")
                        candidateBar.clearCandidates()
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error updating candidates")
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
     * 處理退格鍵（如果需要）
     */
    fun onBackspacePress() {
        Timber.d("$TAG: Backspace pressed")

        try {
            rimeSession.launchOnReady { rime ->
                val hasComposition =
                    try {
                        !rime.compositionCached.preedit.isNullOrEmpty()
                    } catch (e: Exception) {
                        Timber.w(e, "$TAG: Error checking composition state")
                        false
                    }

                if (hasComposition) {
                    // 如果有組合，刪除最後一個字符
                    rime.processKey(0xFF08, 0u) // BackSpace keycode
                } else {
                    // 如果沒有組合，發送退格到應用程式
                    // 這會由InputMethodService處理
                }

                // 在主線程更新UI
                coroutineScope.launch {
                    updateUIAfterInput()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error processing backspace")
        }
    }

    /**
     * 重置處理器狀態
     */
    fun reset() {
        coroutineScope.launch {
            clearInputState()
        }
    }
}
