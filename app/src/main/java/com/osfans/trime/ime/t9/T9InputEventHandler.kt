// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

import com.osfans.trime.core.CandidateItem
import com.osfans.trime.core.RimeMessage
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
 * @param preeditArea Preedit顯示區域
 * @param candidateBar 候選詞列
 * @param service 輸入法服務
 */
class T9InputEventHandler(
    private val rimeSession: RimeSession,
    private val contextDisplay: ContextDisplayArea,
    private val preeditArea: T9PreeditView,
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
        // 設置 RIME 訊息流監聽器
        coroutineScope.launch {
            rimeSession.run { messageFlow }.collect { message ->
                handleRimeMessage(message)
            }
        }

        // 監聽 RIME 生命週期狀態變化
        coroutineScope.launch {
            rimeSession.run { stateFlow }.collect { state ->
                Timber.d("$TAG: RIME 生命週期狀態變更: $state")
                when (state) {
                    com.osfans.trime.core.RimeLifecycle.State.READY -> {
                        Timber.i("$TAG: ✅ RIME 引擎已就緒，可以處理輸入")
                    }
                    com.osfans.trime.core.RimeLifecycle.State.STARTING -> {
                        Timber.i("$TAG: 🔄 RIME 引擎正在啟動...")
                    }
                    com.osfans.trime.core.RimeLifecycle.State.STOPPING -> {
                        Timber.i("$TAG: ⏹️ RIME 引擎正在停止...")
                    }
                    com.osfans.trime.core.RimeLifecycle.State.STOPPED -> {
                        Timber.i("$TAG: ❌ RIME 引擎已停止")
                    }
                }
            }
        }
    }

    /**
     * 處理數字鍵短按事件 - 輸入對應的注音符號
     *
     * 透過 RIME 引擎處理數字輸入，讓 RIME 根據當前方案決定對應的注音符號
     */
    override fun onNumberKeyPress(number: Int) {
        Timber.d("$TAG: 短按數字鍵: $number - 觸發注音符號輸入")

        try {
            // 使用 RIME 引擎處理注音輸入
            coroutineScope.launch {
                rimeSession.runOnReady {
                    val keyCode = number.toString().first().code
                    Timber.d("$TAG: 發送按鍵碼 $keyCode 到 RIME 引擎處理注音輸入")

                    val result = processKey(keyCode, 0u)
                    Timber.d("$TAG: RIME 處理結果: $result")

                    // RIME 會透過 messageFlow 自動通知 UI 更新候選詞和組合狀態
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 處理短按數字鍵時發生錯誤: $number")
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
            coroutineScope.launch {
                rimeSession.runOnReady {
                    val menu = menuCached
                    val composition = compositionCached

                    if (menu.candidates.isNotEmpty()) {
                        // 如果有候選詞，選擇第一個
                        if (selectCandidate(0)) {
                            Timber.d("$TAG: 選擇並提交第一個候選詞: ${menu.candidates[0].text}")
                        }
                    } else if (!composition.preedit.isNullOrEmpty()) {
                        // 如果有輸入但沒有候選詞，清空輸入
                        clearComposition()
                        Timber.d("$TAG: 清空組合輸入")
                    }

                    // 收合虛擬鍵盤
                    try {
                        Timber.d("$TAG: 隱藏鍵盤")
                        service.requestHideSelf(0)
                    } catch (e: Exception) {
                        Timber.e(e, "$TAG: 隱藏鍵盤時發生錯誤")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 處理確認鍵時發生錯誤")
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
        Timber.d("$TAG: 選擇候選詞: $index")

        try {
            coroutineScope.launch {
                rimeSession.runOnReady {
                    if (selectCandidate(index)) {
                        val menu = menuCached
                        if (index < menu.candidates.size) {
                            Timber.d("$TAG: 選擇並提交候選詞: ${menu.candidates[index].text}")
                        }

                        // 在主線程更新UI狀態
                        clearInputState()
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 選擇候選詞時發生錯誤: $index")
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
     * 處理 RIME 訊息
     */
    private fun handleRimeMessage(message: RimeMessage<*>) {
        try {
            when (message) {
                is RimeMessage.ResponseMessage -> {
                    val composition = message.data.context.composition
                    val menu = message.data.context.menu

                    Timber.d("$TAG: 接收到 RIME 響應訊息 - preedit: '${composition.preedit}', candidates: ${menu.candidates.size}")

                    // 更新情境顯示區 (預編輯字串)
                    if (!composition.preedit.isNullOrEmpty()) {
                        contextDisplay.updateInputSequence(composition.preedit)
                    } else {
                        contextDisplay.clearInput()
                    }

                    // 更新Preedit區域 (T9序列和注音符號)
                    updatePreeditDisplay(composition.preedit)

                    // 更新候選詞列
                    if (menu.candidates.isNotEmpty()) {
                        // 將 Rime 的 Candidate 轉換為 UI 需要的 CandidateItem
                        val candidateItems =
                            menu.candidates.map { rimeCandidate ->
                                CandidateItem(text = rimeCandidate.text, comment = rimeCandidate.comment ?: "")
                            }
                        candidateBar.updateCandidates(candidateItems)
                        Timber.d("$TAG: 更新候選詞列，共 ${candidateItems.size} 個候選詞: ${candidateItems.take(3).map { it.text }}")
                    } else {
                        candidateBar.clearCandidates()
                        Timber.d("$TAG: 清空候選詞列")
                    }
                }
                else -> {
                    // 其他類型的訊息，我們暫時不處理
                    Timber.v("$TAG: 忽略 RIME 訊息類型: ${message.messageType}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 處理 RIME 訊息時發生錯誤: $message")
        }
    }

    /**
     * 更新Preedit顯示
     */
    private fun updatePreeditDisplay(preeditText: String?) {
        try {
            if (preeditText.isNullOrEmpty()) {
                preeditArea.clear()
                return
            }

            // 嘗試從preedit文字中提取T9數字序列
            val digitSequence = extractT9DigitSequence(preeditText)

            // 將數字序列轉換為注音符號組合
            val zhuyinCombinations = convertT9ToZhuyin(digitSequence)

            // 更新Preedit顯示
            preeditArea.updateContent(digitSequence, zhuyinCombinations)

            Timber.d("$TAG: Preedit更新 - 數字序列: '$digitSequence', 注音: $zhuyinCombinations")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 更新Preedit顯示時發生錯誤")
        }
    }

    /**
     * 從preedit文字中提取T9數字序列
     */
    private fun extractT9DigitSequence(preeditText: String): String {
        // 簡單實現：從preedit中提取數字字符
        return preeditText.filter { it.isDigit() }
    }

    /**
     * 將T9數字序列轉換為注音符號組合
     */
    private fun convertT9ToZhuyin(digitSequence: String): List<String> {
        val t9ToZhuyinMap =
            mapOf(
                '1' to "ㄅㄉㄚ",
                '2' to "ㄍㄐㄞㄧ",
                '3' to "ㄓㄗㄢㄦ",
                '4' to "ㄆㄊㄛ",
                '5' to "ㄎㄑㄟㄨ",
                '6' to "ㄔㄘㄣ",
                '7' to "ㄇㄋㄜㄝ",
                '8' to "ㄏㄒㄠㄩ",
                '9' to "ㄕㄙㄤㄥ",
                '0' to "ㄈㄌㄡㄖ",
            )

        return digitSequence.mapNotNull { digit ->
            t9ToZhuyinMap[digit]
        }
    }

    /**
     * 清空輸入狀態
     */
    private fun clearInputState() {
        try {
            // 清理情境顯示
            contextDisplay.clearInput()

            // 清空Preedit區域
            preeditArea.clear()

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
        Timber.d("$TAG: 按下退格鍵")

        try {
            coroutineScope.launch {
                rimeSession.runOnReady {
                    val composition = compositionCached

                    if (!composition.preedit.isNullOrEmpty()) {
                        // 如果有組合輸入，使用 Rime 的退格處理
                        t9InputLogic.deleteLastDigit()
                    } else {
                        // 如果沒有組合輸入，發送退格到應用程式
                        service.sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DEL)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 處理退格鍵時發生錯誤")
        }
    }

    /**
     * 清除 Preedit 內容
     */
    fun clearPreedit() {
        try {
            Timber.d("$TAG: 清除 Preedit 內容")

            // 重置 T9 輸入邏輯
            t9InputLogic.reset()

            // 清空 UI 狀態
            clearInputState()
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error clearing preedit")
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
