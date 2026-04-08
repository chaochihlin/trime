// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

import com.osfans.trime.core.CandidateItem
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.daemon.RimeSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import splitties.systemservices.inputMethodManager
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

    // 每個按鍵對應的 half 值（與 digitSequence 同步增減）
    private val digitHalves = mutableListOf<Int>()

    // 重送數字序列的協程 Job（確保只有一個在跑，避免併發競態）
    private var resendJob: Job? = null

    // 目前顯示的注音組合（用於判斷刪除時是否應直接清空）
    private var lastShownCombinations: List<String> = emptyList()

    // ==================== 前端過濾機制（方案 E）====================
    // 緩存完整的候選詞列表，用於前端過濾
    private var cachedCandidates: List<CandidateItem> = emptyList()

    // 當前選中的注音過濾條件（null 表示顯示全部）
    private var currentZhuyinFilter: String? = null

    // 當前聲調過濾（null 表示不過濾）
    private var currentToneFilter: String? = null

    // 目前顯示在候選詞列的實際候選詞（經過過濾後的結果）
    private var displayedCandidates: List<CandidateItem> = emptyList()

    // 多音字聲調映射：text → 所有聲調集合（null 代表一聲）
    // 用於解決 RIME uniquifier 移除後的多音字聲調過濾
    private var multiToneMap: Map<String, Set<String?>> = emptyMap()

    // 聲調變更監聽器（用於通知 UI 更新高亮狀態）
    var onToneFilterChanged: ((String?) -> Unit)? = null

    // 合法聲調變更監聽器（用於動態顯示/隱藏聲調鍵）
    var onValidTonesChanged: ((Set<String>) -> Unit)? = null

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
    override fun onNumberKeyPress(
        number: Int,
        half: Int,
    ) {
        Timber.d("$TAG: 短按數字鍵: $number (half=$half) - 觸發注音符號輸入")

        try {
            // 追蹤輸入序列（用 RIME 字符：0-9 為數字，10→'a'，11→'b'）
            val rimeChar = T9ZhuyinMapper.keyIndexToChar(number)
            digitSequence.append(rimeChar)
            digitHalves.add(half)
            Timber.d("$TAG: 目前數字序列: $digitSequence (長度: ${digitSequence.length})")

            // 發送按鍵到 RIME 引擎，注音選擇器將在 handleRimeMessage 中更新
            coroutineScope.launch {
                rimeSession.runOnReady {
                    val keyCode = rimeChar.code
                    Timber.d("$TAG: 發送按鍵碼 $keyCode ('$rimeChar') 到 RIME 引擎處理注音輸入")

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

        return try {
            onDigitInput(number)
            true
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 處理長按數字鍵時發生錯誤: $number")
            false
        }
    }

    /**
     * 直接輸入數字字符（不經 RIME），長按注音鍵和數字列按鈕共用
     */
    override fun onDigitInput(digit: Int) {
        Timber.d("$TAG: 直接輸入數字: $digit")
        textInputArea.appendCandidate(digit.toString())
    }

    /**
     * 處理確認鍵按下事件 - 新邏輯：提交文字輸入框內容
     */
    override fun onConfirmPress() {
        Timber.d("$TAG: Confirm button pressed")

        try {
            // 獲取文字輸入框內容
            val textContent = textInputArea.getText()
            Timber.d("$TAG: T9 textContent='$textContent'")

            // 先清除宿主 App 的既有文字（不論 T9 輸入框是否為空）
            val ic = service.currentInputConnection
            if (ic != null) {
                val beforeCursor = ic.getTextBeforeCursor(1000, 0)
                if (!beforeCursor.isNullOrEmpty()) {
                    Timber.d("$TAG: 刪除宿主 App 游標前 ${beforeCursor.length} 個字符")
                    ic.deleteSurroundingText(beforeCursor.length, 0)
                }
                val afterCursor = ic.getTextAfterCursor(1000, 0)
                if (!afterCursor.isNullOrEmpty()) {
                    Timber.d("$TAG: 刪除宿主 App 游標後 ${afterCursor.length} 個字符")
                    ic.deleteSurroundingText(0, afterCursor.length)
                }
            }

            // 提交 T9 輸入框內容（如果有的話）
            if (textContent.isNotEmpty()) {
                service.commitText(textContent)
                Timber.d("$TAG: 提交文字內容: $textContent")
            }

            // 清空文字輸入框
            textInputArea.clear()

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
        Timber.d("$TAG: Language switch pressed - showing input method picker")
        inputMethodManager.showInputMethodPicker()
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

                    // Guard: 丟棄遲到的 RIME 回應（用戶已清空輸入）
                    if (digitSequence.isEmpty()) {
                        Timber.w("$TAG: GUARD_STALE_RESPONSE: 丟棄遲到的 RIME 回應 (${menu.candidates.size} 個候選字)")
                        return
                    }

                    // 更新候選詞列和注音選擇器
                    if (menu.candidates.isNotEmpty()) {
                        // 將 Rime 的 Candidate 轉換為 UI 需要的 CandidateItem
                        var candidateItems =
                            menu.candidates.map { rimeCandidate ->
                                CandidateItem(text = rimeCandidate.text, comment = rimeCandidate.comment ?: "")
                            }

                        // 建立多音字聲調映射（RIME 每字只回傳一筆，由字典靜態映射補充）
                        multiToneMap = buildToneMap(candidateItems)

                        // 根據按鍵次數決定顯示方式
                        val currentDigitCount = digitSequence.length
                        Timber.d("$TAG: 目前數字序列長度: $currentDigitCount")

                        // 【方案 2】補充候選詞
                        var multiKeyCombinations: List<String>? = null
                        if (currentDigitCount == 1 && digitSequence.isNotEmpty()) {
                            // 單鍵輸入：以 t9_chars.json 策劃順序為主，解決 RIME 精確匹配優先問題
                            val firstDigit = T9ZhuyinMapper.charToKeyIndex(digitSequence[0])
                            if (firstDigit != null) {
                                val originalCount = candidateItems.size
                                candidateItems = T9ZhuyinMapper.prioritizedCandidates(candidateItems, firstDigit)
                                Timber.d("$TAG: 單鍵優先排序，RIME $originalCount 個 → 合併後 ${candidateItems.size} 個")
                            }
                        } else if (currentDigitCount >= 2) {
                            // 多鍵輸入：計算有效注音組合（只算一次，後續共用）
                            multiKeyCombinations = T9ZhuyinMapper.mapToZhuyinCombinations(digitSequence.toString(), digitHalves)
                            val allowedSymbols = collectAllowedSymbols()
                            val originalCount = candidateItems.size
                            candidateItems =
                                T9ZhuyinMapper.prioritizedMultiKeyCandidates(candidateItems, multiKeyCombinations, allowedSymbols)
                            Timber.d("$TAG: 多鍵優先排序，RIME $originalCount 個 → 合併後 ${candidateItems.size} 個")
                        }

                        // 【方案 E】緩存完整候選詞列表，用於前端過濾
                        cachedCandidates = candidateItems
                        currentZhuyinFilter = null // 重置注音過濾
                        currentToneFilter = null // 重置聲調過濾
                        onToneFilterChanged?.invoke(null)
                        Timber.d("$TAG: 緩存 ${candidateItems.size} 個候選詞")

                        if (currentDigitCount == 1 && digitSequence.isNotEmpty()) {
                            // 第一次按鍵：顯示該數字對應的個別注音符號（考慮 half 拆分）
                            val firstDigit = T9ZhuyinMapper.charToKeyIndex(digitSequence[0])
                            if (firstDigit != null) {
                                val firstHalf = digitHalves.firstOrNull() ?: 0
                                val individualZhuyins = T9ZhuyinMapper.getZhuyinForDigitHalf(firstDigit, firstHalf)
                                Timber.d("$TAG: 第一次按鍵，顯示個別注音 (half=$firstHalf): $individualZhuyins")
                                showAndTrackCombinations(individualZhuyins)
                            }
                        } else {
                            // 第二次以上：從已計算的注音組合中過濾有效項
                            var zhuyinCombinations =
                                multiKeyCombinations ?: T9ZhuyinMapper.mapToZhuyinCombinations(digitSequence.toString(), digitHalves)
                            zhuyinCombinations =
                                zhuyinCombinations.filter { combo ->
                                    T9ZhuyinMapper.filterCandidatesByZhuyinPrefix(candidateItems, combo).isNotEmpty()
                                }

                            if (zhuyinCombinations.isNotEmpty()) {
                                Timber.d("$TAG: 根據數字序列 '$digitSequence' 計算注音組合: $zhuyinCombinations")
                                showAndTrackCombinations(zhuyinCombinations)
                            } else {
                                // 若無有效組合，直接從按鍵序列計算允許的個別注音
                                val individualSymbols = collectAllowedSymbols().toList()
                                Timber.d("$TAG: 無有效組合，從按鍵序列計算個別注音: $individualSymbols")
                                showAndTrackCombinations(individualSymbols)
                            }
                        }
                        // 透過 applyFilters 更新顯示（合法聲調計算）
                        applyFilters()
                        Timber.d("$TAG: 更新候選詞列，共 ${cachedCandidates.size} 個候選詞: ${displayedCandidates.take(3).map { it.text }}")
                    } else {
                        // RIME 返回 0 個候選詞，但仍需處理 UI 顯示
                        currentZhuyinFilter = null

                        if (digitSequence.isNotEmpty()) {
                            val currentDigitCount = digitSequence.length
                            if (currentDigitCount == 1) {
                                // 單鍵：顯示該數字對應的個別注音符號
                                val firstDigit = T9ZhuyinMapper.charToKeyIndex(digitSequence[0])
                                if (firstDigit != null) {
                                    val firstHalf = digitHalves.firstOrNull() ?: 0
                                    val individualZhuyins = T9ZhuyinMapper.getZhuyinForDigitHalf(firstDigit, firstHalf)
                                    Timber.d("$TAG: RIME 無候選詞，但根據數字 '$firstDigit' 顯示注音 (half=$firstHalf): $individualZhuyins")
                                    showAndTrackCombinations(individualZhuyins)

                                    // 【關鍵修復】即使 RIME 無候選詞，也使用前端策劃候選字
                                    val supplementedCandidates = T9ZhuyinMapper.prioritizedCandidates(emptyList(), firstDigit)
                                    if (supplementedCandidates.isNotEmpty()) {
                                        cachedCandidates = supplementedCandidates
                                        applyFilters()
                                        Timber.d("$TAG: 前端補充了 ${supplementedCandidates.size} 個候選詞")
                                    } else {
                                        cachedCandidates = emptyList()
                                        displayedCandidates = emptyList()
                                        candidateBar.clearCandidates()
                                        onValidTonesChanged?.invoke(emptySet())
                                    }
                                }
                            } else {
                                // 多鍵：計算有效注音組合
                                val zhuyinCombinations = T9ZhuyinMapper.mapToZhuyinCombinations(digitSequence.toString(), digitHalves)
                                if (zhuyinCombinations.isNotEmpty()) {
                                    Timber.d("$TAG: RIME 無候選詞，根據數字序列 '$digitSequence' 計算注音組合: $zhuyinCombinations")
                                    showAndTrackCombinations(zhuyinCombinations)
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
            digitHalves.clear()

            // 清空緩存
            cachedCandidates = emptyList()
            displayedCandidates = emptyList()
            multiToneMap = emptyMap()
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
            digitHalves.clear()

            // 清空緩存
            cachedCandidates = emptyList()
            displayedCandidates = emptyList()
            multiToneMap = emptyMap()
            currentZhuyinFilter = null
            currentToneFilter = null
            onToneFilterChanged?.invoke(null)
            onValidTonesChanged?.invoke(emptySet())

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

    /** 顯示注音組合並追蹤，用於退格時判斷是否直接清空 */
    private fun showAndTrackCombinations(combinations: List<String>) {
        lastShownCombinations = combinations
        contextDisplay.showZhuyinCombinations(combinations)
    }

    /** 從 digitSequence + digitHalves 計算去重的 half-filtered 注音符號列表 */
    private fun collectAllowedSymbols(): LinkedHashSet<String> {
        val symbols = linkedSetOf<String>()
        for (i in digitSequence.indices) {
            val key = T9ZhuyinMapper.charToKeyIndex(digitSequence[i]) ?: continue
            val half = digitHalves.getOrElse(i) { 0 }
            symbols.addAll(T9ZhuyinMapper.getZhuyinForDigitHalf(key, half))
        }
        return symbols
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

        // 計算並通知合法聲調（基於注音過濾後、聲調過濾前的候選字）
        val validTones = computeValidTones(filtered)
        onValidTonesChanged?.invoke(validTones)

        // 再套用聲調過濾（嚴格模式：空結果也套用）
        if (currentToneFilter != null) {
            val toneFiltered = T9ZhuyinMapper.filterCandidatesByTone(filtered, currentToneFilter!!, multiToneMap)
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

    private fun computeValidTones(candidates: List<CandidateItem>): Set<String> {
        val tones = mutableSetOf<String>()
        for (candidate in candidates) {
            for (tone in T9ZhuyinMapper.resolveTones(candidate, multiToneMap)) {
                tones.add(tone ?: T9ZhuyinMapper.FIRST_TONE_SYMBOL) // null = 一聲（陰平）
            }
        }
        return tones
    }

    /**
     * 從候選詞列表建立多音字聲調映射表
     * RIME translator 每字只回傳一個讀音，因此用字典靜態映射補充完整的多音字聲調。
     */
    private fun buildToneMap(candidates: List<CandidateItem>): Map<String, Set<String?>> {
        val map = mutableMapOf<String, MutableSet<String?>>()
        val dictToneMap = T9CharDataLoader.getDictToneMap()
        for (c in candidates) {
            val tones = map.getOrPut(c.text) { mutableSetOf() }
            tones.add(T9ZhuyinMapper.extractToneFromComment(c.comment))
            // 從字典靜態映射補充該字的所有聲調
            dictToneMap[c.text]?.let { tones.addAll(it) }
        }
        return map
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
                // 若注音組合清單全是單一注音（已是最基本狀態），直接清空全部
                val allSingleSymbol =
                    lastShownCombinations.isNotEmpty() &&
                        lastShownCombinations.all { it.length <= 1 }
                if (allSingleSymbol) {
                    Timber.d("$TAG: 注音清單全為單一符號，直接清空全部")
                    clearInputStateAndRime()
                    return
                }

                // 情境 1：刪除最後一個數字
                val removedDigit = digitSequence.last()
                digitSequence.deleteAt(digitSequence.length - 1)
                if (digitHalves.isNotEmpty()) digitHalves.removeAt(digitHalves.lastIndex)
                Timber.d("$TAG: 刪除數字 '$removedDigit'，剩餘序列: '$digitSequence'")

                if (digitSequence.isEmpty()) {
                    // 數字已全部刪除，清除所有狀態
                    Timber.d("$TAG: 數字序列已清空，清除輸入狀態")
                    clearInputStateAndRime()
                } else {
                    // 還有剩餘數字，送 BackSpace 給 RIME（避免 clear+resend 造成閃爍）
                    Timber.d("$TAG: 送 BackSpace 到 RIME，剩餘序列: '$digitSequence'")
                    resendJob?.cancel()
                    resendJob =
                        coroutineScope.launch {
                            rimeSession.runOnReady {
                                processKey(0xff08, 0u) // XK_BackSpace
                            }
                        }
                }
            } else if (textInputArea.hasContent()) {
                // 情境 2：刪除游標前一個字
                textInputArea.deleteCharacterBeforeCursor()
                Timber.d("$TAG: 從文字輸入框刪除游標前一個字符")
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
     * 清除輸入狀態並重置 RIME 引擎
     * 用於一次性清除所有輸入中狀態
     */
    private fun clearInputStateAndRime() {
        try {
            // 取消進行中的重送協程，避免遲到的 RIME 回應覆蓋清除後的 UI
            resendJob?.cancel()
            resendJob = null

            // 清除 UI 狀態（注音列表回到標點符號、清空候選字）
            contextDisplay.clearInput()
            candidateBar.clearCandidates()

            // 重置 T9 輸入邏輯
            t9InputLogic.reset()

            // 重置數字序列
            digitSequence.clear()
            digitHalves.clear()

            // 清空緩存（防止 resend 遲到回應透過 applyFilters 復活）
            cachedCandidates = emptyList()
            displayedCandidates = emptyList()
            lastShownCombinations = emptyList()
            currentZhuyinFilter = null
            currentToneFilter = null
            onToneFilterChanged?.invoke(null)
            onValidTonesChanged?.invoke(emptySet())

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
            digitHalves.clear()

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
            digitHalves.clear()
            Timber.d("$TAG: 處理器已重置，數字序列已清空")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error resetting handler")
        }
    }
}
