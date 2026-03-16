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
 * @param textInputArea 文字輸入區域
 * @param candidateBar 候選詞列
 * @param service 輸入法服務
 */
class T9InputEventHandler(
    private val rimeSession: RimeSession,
    private val contextDisplay: ContextDisplayArea,
    private val textInputArea: T9TextInputView,
    private val candidateBar: T9CandidateBar,
    private val service: com.osfans.trime.ime.core.TrimeInputMethodService,
) : T9KeyboardView.T9KeyboardActionListener {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    // T9輸入邏輯控制器
    private val t9InputLogic = SimpleT9InputLogic(rimeSession)

    // 追蹤目前輸入的數字序列（用於判斷顯示個別注音或組合注音）
    private val digitSequence = StringBuilder()

    // ==================== 前端過濾機制（方案 E）====================
    // 緩存完整的候選詞列表，用於前端過濾
    private var cachedCandidates: List<CandidateItem> = emptyList()

    // 當前選中的注音過濾條件（null 表示顯示全部）
    private var currentZhuyinFilter: String? = null

    // 當前聲調過濾（null 表示不過濾）
    private var currentToneFilter: String? = null

    // 目前顯示在候選詞列的實際候選詞（經過過濾後的結果）
    private var displayedCandidates: List<CandidateItem> = emptyList()

    // 聲調變更監聽器（用於通知 UI 更新高亮狀態）
    var onToneFilterChanged: ((String?) -> Unit)? = null

    companion object {
        private const val TAG = "T9InputEventHandler"
        // 佔位提示的 comment 標記，用於區分不可選取的提示項目
        private const val HINT_MARKER = "__hint__"
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
     * 透過 RIME 引擎處理數字輸入，讓 RIME 根據當前方案決定對應的注音符號。
     * 注音選擇器將在 RIME 回應後，根據按鍵次數決定顯示方式：
     * - 第一次按鍵：顯示該數字對應的個別注音符號
     * - 第二次以上：顯示從候選詞提取的注音組合
     */
    override fun onNumberKeyPress(number: Int) {
        Timber.d("$TAG: 短按數字鍵: $number - 觸發注音符號輸入")

        try {
            // 追蹤數字序列
            digitSequence.append(number)
            Timber.d("$TAG: 目前數字序列: $digitSequence (長度: ${digitSequence.length})")

            // 發送按鍵到 RIME 引擎，注音選擇器將在 handleRimeMessage 中更新
            coroutineScope.launch {
                rimeSession.runOnReady {
                    val keyCode = number.toString().first().code
                    Timber.d("$TAG: 發送按鍵碼 $keyCode 到 RIME 引擎處理注音輸入")

                    val result = processKey(keyCode, 0u)
                    Timber.d("$TAG: RIME 處理結果: $result")

                    // RIME 會透過 messageFlow 自動通知 UI 更新候選詞和注音選擇器
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 處理短按數字鍵時發生錯誤: $number")
        }
    }

    /**
     * 處理注音選擇事件（來自 ContextDisplayArea 的注音選擇器）
     *
     * 【方案 A：純視覺提示】
     * 由於 RIME script_translator 的精確匹配優先行為，
     * 當只有一個候選詞時無法進行有效過濾。
     *
     * 因此採用純視覺提示方案：
     * 1. 點擊注音時只做視覺標記（ZhuyinSelectorView 已處理）
     * 2. 不改變候選詞顯示
     * 3. 提供視覺反饋讓用戶知道選擇了哪個注音
     *
     * @param digit 對應的數字鍵 (0-9)，組合模式下為 -1
     * @param zhuyinIndex 選擇的注音在列表中的索引
     * @param zhuyin 選擇的注音符號或組合
     */
    fun onZhuyinSelected(
        digit: Int,
        zhuyinIndex: Int,
        zhuyin: String,
    ) {
        Timber.d("$TAG: 注音選擇（純視覺提示）- digit=$digit, index=$zhuyinIndex, zhuyin='$zhuyin'")

        if (zhuyin.isEmpty()) {
            Timber.w("$TAG: 注音為空，忽略選擇")
            return
        }

        // 設定注音過濾條件
        currentZhuyinFilter = zhuyin
        Timber.d("$TAG: 設定注音過濾: '$zhuyin', 候選詞數: ${cachedCandidates.size}, 聲調過濾: '$currentToneFilter'")

        // 套用過濾（包含注音 + 聲調的 AND 邏輯）
        if (cachedCandidates.isNotEmpty()) {
            applyFilters()
        } else {
            Timber.d("$TAG: 無候選詞，僅提供視覺提示")
        }
    }

    /**
     * 將注音字串轉換為 T9 數字序列
     *
     * @param zhuyin 注音字串，如 "ㄏㄠ"
     * @return T9 數字序列，如 "88"
     */
    private fun zhuyinToT9Digits(zhuyin: String): String =
        zhuyin
            .mapNotNull { char ->
                T9ZhuyinMapper.getDigitForZhuyin(char.toString())?.toString()
            }.joinToString("")

    /**
     * 處理數字鍵長按事件 - 直接輸入數字字符
     *
     * 繞過 RIME 引擎，直接將數字提交到當前輸入目標
     */
    override fun onNumberKeyLongPress(number: Int): Boolean {
        Timber.d("$TAG: 長按數字鍵: $number - 直接輸入數字字符")

        try {
            val digitText = number.toString()

            // 將數字追加到文字輸入框中（與候選字選擇行為一致）
            textInputArea.appendCandidate(digitText)
            Timber.d("$TAG: ✅ 成功輸入數字到文字輸入框: $digitText")
            return true
        } catch (e: Exception) {
            Timber.e(e, "$TAG: ❌ 處理長按數字鍵時發生錯誤: $number")
            return false
        }
    }

    // 注意：滑動手勢已移除，聲調由 RIME 引擎自動推測
    // 原有的 onSwipeUp/Down/Left/Right 和 sendToneInput 方法已刪除

    /**
     * 處理確認鍵按下事件 - 新邏輯：提交文字輸入框內容
     */
    override fun onConfirmPress() {
        Timber.d("$TAG: Confirm button pressed")

        try {
            // 獲取文字輸入框內容
            val textContent = textInputArea.getText()

            if (textContent.isNotEmpty()) {
                // FIX: 防止重複輸入問題
                // 因為 T9 輸入框在啟動時會載入原輸入框的內容，所以提交時需要先清除原內容
                // 否則會導致內容重複 (例如: 原本是"A", T9載入"A", 用戶輸入"B"變成"AB", 提交後變成"AAB")
                val ic = service.currentInputConnection
                if (ic != null) {
                    // 嘗試獲取游標前的文字（最多1000個字符）
                    // 假設游標在文字末尾，這將刪除所有現有文字
                    val beforeCursor = ic.getTextBeforeCursor(1000, 0)
                    if (!beforeCursor.isNullOrEmpty()) {
                        Timber.d("$TAG: 刪除游標前 ${beforeCursor.length} 個字符以防止重複")
                        ic.deleteSurroundingText(beforeCursor.length, 0)
                    }
                }

                // 提交文字輸入框內容到目標應用程式
                service.commitText(textContent)
                Timber.d("$TAG: 提交文字內容: $textContent")

                // 清空文字輸入框
                textInputArea.clear()
            }

            // 清除所有輸入狀態
            coroutineScope.launch {
                rimeSession.runOnReady {
                    clearComposition()
                }
            }
            clearInputState()

            // 收合虛擬鍵盤
            try {
                Timber.d("$TAG: 隱藏鍵盤")
                service.requestHideSelf(0)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: 隱藏鍵盤時發生錯誤")
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
     * 處理候選詞選擇事件 - 本地累積模式
     *
     * 使用 cachedCandidates（UI 顯示的候選詞）而非 menuCached（RIME 原生緩存），
     * 因為候選詞可能經過前端補充或過濾，兩者內容不一致。
     */
    fun onCandidateSelected(index: Int) {
        Timber.d("$TAG: 選擇候選詞: $index, 顯示大小: ${displayedCandidates.size}, 緩存大小: ${cachedCandidates.size}")

        try {
            // 使用 displayedCandidates（經過濾後的實際顯示列表），確保選到的是用戶看到的候選詞
            if (index < displayedCandidates.size) {
                val selectedCandidate = displayedCandidates[index]
                Timber.d("$TAG: 選擇候選詞: ${selectedCandidate.text}")

                // 直接將候選詞追加到文字輸入框中
                textInputArea.appendCandidate(selectedCandidate.text)
                Timber.d("$TAG: 已追加候選詞到輸入框")

                // 清除 RIME 狀態並準備下一次輸入
                coroutineScope.launch {
                    rimeSession.runOnReady {
                        clearComposition()
                    }
                }

                // 清除 UI 狀態（但保留 textInputArea 內容）
                clearInputStateExceptText()
            } else {
                Timber.w("$TAG: 無效的候選詞索引: $index, 顯示大小: ${displayedCandidates.size}")
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

                    // 注意：新模式下情境顯示區由注音選擇器控制，不再顯示 preedit

                    // 更新候選詞列和注音選擇器
                    if (menu.candidates.isNotEmpty()) {
                        // 將 Rime 的 Candidate 轉換為 UI 需要的 CandidateItem
                        var candidateItems =
                            menu.candidates.map { rimeCandidate ->
                                CandidateItem(text = rimeCandidate.text, comment = rimeCandidate.comment ?: "")
                            }

                        // 根據按鍵次數決定顯示方式
                        val currentDigitCount = digitSequence.length
                        Timber.d("$TAG: 目前數字序列長度: $currentDigitCount")

                        // 【方案 2】補充候選詞
                        if (currentDigitCount == 1 && digitSequence.isNotEmpty()) {
                            // 單鍵輸入：以 t9_chars.json 策劃順序為主，解決 RIME 精確匹配優先問題
                            val firstDigit = digitSequence[0].toString().toIntOrNull()
                            if (firstDigit != null) {
                                val originalCount = candidateItems.size
                                candidateItems = T9ZhuyinMapper.prioritizedCandidates(candidateItems, firstDigit)
                                Timber.d("$TAG: 單鍵優先排序，RIME $originalCount 個 → 合併後 ${candidateItems.size} 個")
                            }
                        } else if (currentDigitCount >= 2) {
                            // 多鍵輸入：根據有效注音組合補充候選詞
                            val originalCount = candidateItems.size
                            candidateItems = T9ZhuyinMapper.supplementMultiKeyCandidates(candidateItems, digitSequence.toString())
                            if (candidateItems.size > originalCount) {
                                Timber.d("$TAG: 多鍵補充了 ${candidateItems.size - originalCount} 個候選詞")
                            }
                        }

                        // 【方案 E】緩存完整候選詞列表，用於前端過濾
                        cachedCandidates = candidateItems
                        currentZhuyinFilter = null // 重置過濾條件
                        currentToneFilter = null // 重置聲調過濾
                        onToneFilterChanged?.invoke(null)
                        Timber.d("$TAG: 緩存 ${candidateItems.size} 個候選詞")

                        if (currentDigitCount == 1 && digitSequence.isNotEmpty()) {
                            // 第一次按鍵：顯示該數字對應的個別注音符號
                            val firstDigit = digitSequence[0].toString().toIntOrNull()
                            if (firstDigit != null) {
                                val individualZhuyins = T9ZhuyinMapper.getZhuyinForDigit(firstDigit)
                                Timber.d("$TAG: 第一次按鍵，顯示個別注音: $individualZhuyins")
                                contextDisplay.showZhuyinCombinations(individualZhuyins)
                            }
                        } else {
                            // 第二次以上：根據數字序列計算所有有效的注音組合
                            val zhuyinCombinations = T9ZhuyinMapper.mapToZhuyinCombinations(digitSequence.toString())
                            if (zhuyinCombinations.isNotEmpty()) {
                                Timber.d("$TAG: 根據數字序列 '$digitSequence' 計算注音組合: $zhuyinCombinations")
                                contextDisplay.showZhuyinCombinations(zhuyinCombinations)
                            } else {
                                // 若無有效組合，則從候選詞提取（備援方案）
                                val fallbackCombinations = T9ZhuyinMapper.extractUniqueZhuyinCombinations(candidateItems)
                                Timber.d("$TAG: 無有效組合，從候選詞提取: $fallbackCombinations")
                                contextDisplay.showZhuyinCombinations(fallbackCombinations)
                            }
                        }

                        displayedCandidates = candidateItems
                        candidateBar.updateCandidates(candidateItems)
                        Timber.d("$TAG: 更新候選詞列，共 ${candidateItems.size} 個候選詞: ${candidateItems.take(3).map { it.text }}")
                    } else {
                        // RIME 返回 0 個候選詞，但仍需處理 UI 顯示
                        currentZhuyinFilter = null

                        if (digitSequence.isNotEmpty()) {
                            val currentDigitCount = digitSequence.length
                            if (currentDigitCount == 1) {
                                // 單鍵：顯示該數字對應的個別注音符號
                                val firstDigit = digitSequence[0].toString().toIntOrNull()
                                if (firstDigit != null) {
                                    val individualZhuyins = T9ZhuyinMapper.getZhuyinForDigit(firstDigit)
                                    Timber.d("$TAG: RIME 無候選詞，但根據數字 '$firstDigit' 顯示注音: $individualZhuyins")
                                    contextDisplay.showZhuyinCombinations(individualZhuyins)

                                    // 【關鍵修復】即使 RIME 無候選詞，也使用前端策劃候選字
                                    val supplementedCandidates = T9ZhuyinMapper.prioritizedCandidates(emptyList(), firstDigit)
                                    if (supplementedCandidates.isNotEmpty()) {
                                        cachedCandidates = supplementedCandidates
                                        displayedCandidates = supplementedCandidates
                                        candidateBar.updateCandidates(supplementedCandidates)
                                        Timber.d(
                                            "$TAG: 前端補充了 ${supplementedCandidates.size} 個候選詞: ${supplementedCandidates.take(
                                                3,
                                            ).map { it.text }}",
                                        )
                                    } else {
                                        cachedCandidates = emptyList()
                                        displayedCandidates = emptyList()
                                        candidateBar.clearCandidates()
                                    }
                                }
                            } else {
                                // 多鍵：計算有效注音組合
                                val zhuyinCombinations = T9ZhuyinMapper.mapToZhuyinCombinations(digitSequence.toString())
                                if (zhuyinCombinations.isNotEmpty()) {
                                    Timber.d("$TAG: RIME 無候選詞，根據數字序列 '$digitSequence' 計算注音組合: $zhuyinCombinations")
                                    contextDisplay.showZhuyinCombinations(zhuyinCombinations)
                                } else {
                                    contextDisplay.clearInput()
                                    Timber.d("$TAG: RIME 無候選詞，且無有效注音組合")
                                }
                                cachedCandidates = emptyList()
                                displayedCandidates = emptyList()
                                candidateBar.clearCandidates()
                            }
                        } else {
                            cachedCandidates = emptyList()
                            displayedCandidates = emptyList()
                            candidateBar.clearCandidates()
                            contextDisplay.clearInput()
                            Timber.d("$TAG: 清空候選詞列和注音選擇器")
                        }
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
                // 注意：新模式下不再更新Preedit區域
                return
            }

            // 嘗試從preedit文字中提取T9數字序列
            val digitSequence = extractT9DigitSequence(preeditText)

            // 將數字序列轉換為注音符號組合
            val zhuyinCombinations = convertT9ToZhuyin(digitSequence)

            // 更新Preedit顯示
            // 注意：新模式下不再更新Preedit區域

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

            // 清空文字輸入區域
            textInputArea.clear()

            // 清空候選詞
            candidateBar.clearCandidates()

            // 重置數字序列
            digitSequence.clear()

            // 【方案 E】清空緩存
            cachedCandidates = emptyList()
            displayedCandidates = emptyList()
            currentZhuyinFilter = null
            currentToneFilter = null
            onToneFilterChanged?.invoke(null)

            Timber.d("$TAG: 清空輸入狀態，數字序列和緩存已重置")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error clearing input state")
        }
    }

    /**
     * 清空輸入狀態但保留文字輸入框內容
     */
    private fun clearInputStateExceptText() {
        try {
            // 清理情境顯示
            contextDisplay.clearInput()

            // 清空候選詞
            candidateBar.clearCandidates()

            // 重置數字序列（準備下一次輸入）
            digitSequence.clear()

            // 【方案 E】清空緩存
            cachedCandidates = emptyList()
            displayedCandidates = emptyList()
            currentZhuyinFilter = null
            currentToneFilter = null
            onToneFilterChanged?.invoke(null)

            Timber.d("$TAG: 清空輸入狀態（保留文字），數字序列和緩存已重置")

            // 注意：不清空 textInputArea，保留用戶累積的文字
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error clearing input state except text")
        }
    }

    /**
     * 處理聲調鍵按下事件（toggle 邏輯）
     *
     * @param tone 聲調符號（"ˊ", "ˇ", "ˋ", "˙"）
     */
    fun onToneKeyPress(tone: String) {
        Timber.d("$TAG: 聲調鍵按下: '$tone', 目前過濾: '$currentToneFilter'")

        // 無候選詞時忽略
        if (cachedCandidates.isEmpty()) {
            Timber.d("$TAG: 無候選詞，忽略聲調鍵")
            return
        }

        // Toggle 邏輯：再按同一聲調鍵取消過濾
        if (currentToneFilter == tone) {
            currentToneFilter = null
            Timber.d("$TAG: 取消聲調過濾")
            // 恢復完整候選詞（可能仍有注音過濾）
            applyFilters()
            onToneFilterChanged?.invoke(null)
            return
        }

        // 切換到新聲調
        currentToneFilter = tone
        Timber.d("$TAG: 設定聲調過濾: '$tone'")
        applyFilters()
        onToneFilterChanged?.invoke(tone)
    }

    /**
     * 套用所有過濾條件（注音 + 聲調）到候選詞
     *
     * 過濾策略：嚴格 AND 邏輯，注音過濾 → 聲調過濾。
     * 當過濾結果為空時，仍然顯示空結果（不回退到未過濾狀態），
     * 確保用戶選擇的過濾條件得到忠實執行。
     */
    private fun applyFilters() {
        var filtered = cachedCandidates

        // 先套用注音過濾（嚴格模式：空結果也套用）
        if (currentZhuyinFilter != null) {
            val zhuyinFiltered = T9ZhuyinMapper.filterCandidatesByZhuyinPrefix(filtered, currentZhuyinFilter!!)
            Timber.d("$TAG: 注音過濾 '$currentZhuyinFilter': ${filtered.size} → ${zhuyinFiltered.size}")
            filtered = zhuyinFiltered
        }

        // 再套用聲調過濾（嚴格模式：空結果也套用）
        if (currentToneFilter != null) {
            val toneFiltered = T9ZhuyinMapper.filterCandidatesByTone(filtered, currentToneFilter!!)
            Timber.d("$TAG: 聲調過濾 '$currentToneFilter': ${filtered.size} → ${toneFiltered.size}")
            filtered = toneFiltered
        }

        displayedCandidates = filtered
        if (filtered.isEmpty() && (currentZhuyinFilter != null || currentToneFilter != null)) {
            // 過濾後無結果時，顯示佔位提示（不可選取）
            // displayedCandidates 維持空列表，確保點擊時 onCandidateSelected 不會送出提示文字
            val hint = CandidateItem(text = "無候選字", comment = HINT_MARKER)
            candidateBar.updateCandidates(listOf(hint))
        } else {
            candidateBar.updateCandidates(filtered)
        }
    }

    /**
     * 處理退格鍵 - 逐一刪除邏輯：
     * 1. 有數字序列時 → 刪除最後一個數字，重新計算候選詞
     * 2. 沒有數字序列但有文字輸入框內容時 → 刪除最後一個字
     * 3. 都沒有時 → 發送 DEL 到應用程式
     */
    fun onBackspacePress() {
        Timber.d("$TAG: 按下退格鍵，目前數字序列: '$digitSequence'")

        try {
            if (digitSequence.isNotEmpty()) {
                // 情境 1：刪除最後一個數字
                val removedDigit = digitSequence.last()
                digitSequence.deleteAt(digitSequence.length - 1)
                Timber.d("$TAG: 刪除數字 '$removedDigit'，剩餘序列: '$digitSequence'")

                if (digitSequence.isEmpty()) {
                    // 數字已全部刪除，清除所有狀態
                    Timber.d("$TAG: 數字序列已清空，清除輸入狀態")
                    clearInputStateAndRime()
                } else {
                    // 還有剩餘數字，重新發送到 RIME 引擎以更新候選詞
                    Timber.d("$TAG: 重新發送數字序列到 RIME: '$digitSequence'")
                    resendDigitSequenceToRime()
                }
            } else if (textInputArea.hasContent()) {
                // 情境 2：刪除文字輸入框的最後一個字
                textInputArea.deleteLastCharacter()
                Timber.d("$TAG: 從文字輸入框刪除最後一個字符")
            } else {
                // 情境 3：發送退格到應用程式
                service.sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DEL)
                Timber.d("$TAG: 發送退格鍵到應用程式")
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 處理退格鍵時發生錯誤")
        }
    }

    /**
     * 重新發送當前數字序列到 RIME 引擎
     * 用於退格後重新計算候選詞
     */
    private fun resendDigitSequenceToRime() {
        // 複製當前數字序列以避免協程執行期間的競態條件
        val sequenceCopy = digitSequence.toString()
        coroutineScope.launch {
            rimeSession.runOnReady {
                // 先清除 RIME 的當前輸入
                clearComposition()
                Timber.d("$TAG: 已清除 RIME 組合輸入")

                // 逐一發送數字序列中的每個數字
                for (digit in sequenceCopy) {
                    val keyCode = digit.code
                    val result = processKey(keyCode, 0u)
                    Timber.d("$TAG: 重新發送數字 '$digit' (keyCode=$keyCode)，結果: $result")
                }
                // RIME 會透過 messageFlow 自動通知 UI 更新候選詞和注音選擇器
            }
        }
    }

    /**
     * 清除輸入狀態並重置 RIME 引擎
     * 用於一次性清除所有輸入中狀態
     */
    private fun clearInputStateAndRime() {
        try {
            // 清除 UI 狀態（注音列表回到標點符號、清空候選字）
            contextDisplay.clearInput()
            candidateBar.clearCandidates()

            // 重置 T9 輸入邏輯
            t9InputLogic.reset()

            // 重置數字序列
            digitSequence.clear()
            Timber.d("$TAG: 清除輸入狀態並重置 RIME，數字序列已重置")

            // 清除 RIME 引擎的組合輸入
            coroutineScope.launch {
                rimeSession.runOnReady {
                    clearComposition()
                    Timber.d("$TAG: RIME 組合輸入已清除")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 清除輸入狀態時發生錯誤")
        }
    }

    /**
     * 清除文字輸入內容
     */
    fun clearTextInput() {
        try {
            Timber.d("$TAG: 清除文字輸入內容")

            // 重置 T9 輸入邏輯
            t9InputLogic.reset()

            // 重置數字序列
            digitSequence.clear()

            // 清空 UI 狀態
            clearInputState()
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error clearing text input")
        }
    }

    /**
     * 重置處理器狀態
     */
    fun reset() {
        try {
            t9InputLogic.reset()
            digitSequence.clear()
            Timber.d("$TAG: 處理器已重置，數字序列已清空")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error resetting handler")
        }
    }
}
