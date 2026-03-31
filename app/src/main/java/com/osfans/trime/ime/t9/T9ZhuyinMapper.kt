// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

import com.osfans.trime.core.CandidateItem

/**
 * T9數字到注音符號的映射器
 *
 * 基於 doc/task-02-rime-schema.md 中定義的T9映射表實作數字到注音的轉換邏輯。
 */
object T9ZhuyinMapper {
    // 顯式聲調符號（二、三、四聲和輕聲）
    // 注意：一聲（陰平）在注音中無聲調符號，由 filterCandidatesByTone() 中
    // tone == "ˉ" 時以 candidateTone == null 來匹配
    val TONE_MARKS = setOf('ˊ', 'ˇ', 'ˋ', '˙')

    /** 將 RIME 字符（'0'-'9','a','b'）轉為 T9_MAPPING 的 Int key */
    fun charToKeyIndex(ch: Char): Int? =
        when (ch) {
            in '0'..'9' -> ch - '0'
            'a' -> 10
            'b' -> 11
            else -> null
        }

    /** 將 T9_MAPPING 的 Int key 轉為 RIME 字符 */
    fun keyIndexToChar(index: Int): Char =
        when (index) {
            10 -> 'a'
            11 -> 'b'
            else -> index.digitToChar()
        }

    // T9 12鍵注音映射表（按傳統注音順序分組）
    private val T9_MAPPING =
        mapOf(
            1 to listOf("ㄅ", "ㄉ"),
            2 to listOf("ㄓ", "ㄚ"),
            3 to listOf("ㄞ", "ㄢ", "ㄦ"),
            4 to listOf("ㄆ", "ㄊ", "ㄍ"),
            5 to listOf("ㄐ", "ㄔ", "ㄗ"),
            6 to listOf("ㄧ", "ㄛ", "ㄟ", "ㄣ"),
            7 to listOf("ㄇ", "ㄋ", "ㄎ"),
            8 to listOf("ㄑ", "ㄕ", "ㄘ"),
            9 to listOf("ㄨ", "ㄜ", "ㄠ", "ㄤ"),
            0 to listOf("ㄈ", "ㄌ", "ㄏ"),
            10 to listOf("ㄒ", "ㄖ", "ㄙ"),
            11 to listOf("ㄩ", "ㄝ", "ㄡ", "ㄥ"),
        )

    // 注音符號分類 (用於組合驗證)
    private val CONSONANTS =
        setOf(
            "ㄅ",
            "ㄆ",
            "ㄇ",
            "ㄈ",
            "ㄉ",
            "ㄊ",
            "ㄋ",
            "ㄌ",
            "ㄍ",
            "ㄎ",
            "ㄏ",
            "ㄐ",
            "ㄑ",
            "ㄒ",
            "ㄓ",
            "ㄔ",
            "ㄕ",
            "ㄖ",
            "ㄗ",
            "ㄘ",
            "ㄙ",
        ) // 聲母

    private val MEDIALS = setOf("ㄧ", "ㄨ", "ㄩ") // 介音

    private val FINALS =
        setOf(
            "ㄚ",
            "ㄛ",
            "ㄜ",
            "ㄝ",
            "ㄞ",
            "ㄟ",
            "ㄠ",
            "ㄡ",
            "ㄢ",
            "ㄣ",
            "ㄤ",
            "ㄥ",
            "ㄦ",
        ) // 韻母

    // 介音與韻母的搭配規則
    // key = 介音, value = 該介音可搭配的韻母集合
    private val MEDIAL_FINAL_RULES: Map<String, Set<String>> =
        mapOf(
            // ㄧ（齊齒呼）：可接 ㄚㄝㄠㄡㄢㄣㄤㄥ，不可接 ㄛㄜㄞㄟㄦ
            "ㄧ" to setOf("ㄚ", "ㄝ", "ㄠ", "ㄡ", "ㄢ", "ㄣ", "ㄤ", "ㄥ"),
            // ㄨ（合口呼）：可接 ㄚㄛㄞㄟㄢㄣㄤㄥ，不可接 ㄜㄝㄠㄡㄦ
            "ㄨ" to setOf("ㄚ", "ㄛ", "ㄞ", "ㄟ", "ㄢ", "ㄣ", "ㄤ", "ㄥ"),
            // ㄩ（撮口呼）：只能接 ㄝㄢㄣㄥ
            "ㄩ" to setOf("ㄝ", "ㄢ", "ㄣ", "ㄥ"),
        )

    // 聲母與介音的搭配規則（基於漢語音韻學互補分佈）
    // key = 聲母, value = 該聲母可搭配的介音集合
    private val CONSONANT_MEDIAL_RULES: Map<String, Set<String>> =
        buildMap {
            // ㄅㄆㄇ（雙唇音）：可接 ㄧ、ㄨ，不可接 ㄩ
            for (c in listOf("ㄅ", "ㄆ", "ㄇ")) put(c, setOf("ㄧ", "ㄨ"))
            // ㄈ（唇齒音）：只能接 ㄨ
            put("ㄈ", setOf("ㄨ"))
            // ㄉㄊ（舌尖音）：可接 ㄧ、ㄨ，不可接 ㄩ
            for (c in listOf("ㄉ", "ㄊ")) put(c, setOf("ㄧ", "ㄨ"))
            // ㄋㄌ（鼻/邊音）：三個介音都可接
            for (c in listOf("ㄋ", "ㄌ")) put(c, setOf("ㄧ", "ㄨ", "ㄩ"))
            // ㄍㄎㄏ（舌根音）：只能接 ㄨ，不可接 ㄧ、ㄩ（顎化規則）
            for (c in listOf("ㄍ", "ㄎ", "ㄏ")) put(c, setOf("ㄨ"))
            // ㄐㄑㄒ（舌面音）：只能接 ㄧ、ㄩ，不可接 ㄨ（互補分佈）
            for (c in listOf("ㄐ", "ㄑ", "ㄒ")) put(c, setOf("ㄧ", "ㄩ"))
            // ㄓㄔㄕㄖ（翹舌音）：只能接 ㄨ
            for (c in listOf("ㄓ", "ㄔ", "ㄕ", "ㄖ")) put(c, setOf("ㄨ"))
            // ㄗㄘㄙ（平舌音）：只能接 ㄨ
            for (c in listOf("ㄗ", "ㄘ", "ㄙ")) put(c, setOf("ㄨ"))
        }

    /**
     * 將數字序列映射為可能的注音組合
     *
     * @param digitSequence 數字序列，如 "123"
     * @return 可能的注音組合列表，如 ["ㄅㄧㄢ", "ㄉㄚㄦ", ...]
     */
    fun mapToZhuyinCombinations(digitSequence: String): List<String> {
        if (digitSequence.isEmpty()) return emptyList()

        return generateAllCombinations(digitSequence)
            .filter { isValidZhuyinCombination(it) }
            .sortedByDescending { getZhuyinScore(it) }
            .take(20) // 限制數量避免過多組合
    }

    /**
     * 生成所有可能的注音組合 (遞歸生成)
     */
    private fun generateAllCombinations(digitSequence: String): List<String> {
        if (digitSequence.isEmpty()) return listOf("")

        val firstKey = charToKeyIndex(digitSequence.first()) ?: return emptyList()
        val remainingDigits = digitSequence.drop(1)

        val firstZhuyinOptions = T9_MAPPING[firstKey] ?: return emptyList()
        val remainingCombinations = generateAllCombinations(remainingDigits)

        val combinations = mutableListOf<String>()

        for (zhuyin in firstZhuyinOptions) {
            if (remainingCombinations.isEmpty()) {
                combinations.add(zhuyin)
            } else {
                for (remaining in remainingCombinations) {
                    combinations.add(zhuyin + remaining)
                }
            }
        }

        return combinations
    }

    /**
     * 驗證注音組合的合理性
     *
     * 有效的注音組合包括：
     * 1. 聲母 + 韻母 (如 ㄅㄚ)
     * 2. 聲母 + 介音 (如 ㄅㄧ、ㄉㄧ)
     * 3. 聲母 + 介音 + 韻母 (如 ㄅㄧㄢ)
     * 4. 單獨介音 (如 ㄧ、ㄨ、ㄩ)
     * 5. 單獨韻母 (如 ㄚ、ㄛ)
     * 6. 介音 + 韻母 (如 ㄧㄚ)
     */
    private fun isValidZhuyinCombination(combination: String): Boolean {
        if (combination.isEmpty()) return false

        var consonant: String? = null
        var medial: String? = null
        var final_: String? = null
        var hasConsonant = false
        var hasMedial = false
        var hasFinal = false
        var consonantCount = 0
        var medialCount = 0
        var finalCount = 0

        for (char in combination) {
            val charStr = char.toString()
            when {
                charStr in CONSONANTS -> {
                    consonantCount++
                    hasConsonant = true
                    consonant = charStr
                    // 聲母不能在介音或韻母後
                    if (hasMedial || hasFinal) return false
                }
                charStr in MEDIALS -> {
                    medialCount++
                    hasMedial = true
                    medial = charStr
                    // 介音不能在韻母後
                    if (hasFinal) return false
                }
                charStr in FINALS -> {
                    finalCount++
                    hasFinal = true
                    final_ = charStr
                }
                else -> return false // 未知符號
            }
        }

        // 基本規則檢查：每種類型最多一個
        if (consonantCount > 1 || medialCount > 1 || finalCount > 1) {
            return false
        }

        // 有效組合：必須有介音或韻母（聲母可選）
        if (!hasMedial && !hasFinal) return false

        // 聲母-介音搭配規則檢查（漢語音韻學互補分佈）
        if (consonant != null && medial != null) {
            val allowedMedials = CONSONANT_MEDIAL_RULES[consonant]
            if (allowedMedials != null && medial !in allowedMedials) {
                return false
            }
        }

        // 介音-韻母搭配規則檢查
        if (medial != null && final_ != null) {
            val allowedFinals = MEDIAL_FINAL_RULES[medial]
            if (allowedFinals != null && final_ !in allowedFinals) {
                return false
            }
        }

        // ㄐㄑㄒ 必須搭配介音（不能直接接韻母，如 *ㄐㄚ 不合法）
        if (consonant != null && consonant in setOf("ㄐ", "ㄑ", "ㄒ") && !hasMedial) {
            return false
        }

        return true
    }

    /**
     * 計算注音組合的合理性分數 (用於排序)
     */
    private fun getZhuyinScore(combination: String): Double {
        var score = 1.0

        val hasConsonant = combination.any { it.toString() in CONSONANTS }
        val hasMedial = combination.any { it.toString() in MEDIALS }
        val hasFinal = combination.any { it.toString() in FINALS }

        // 完整結構加分
        when {
            hasConsonant && hasMedial && hasFinal -> score += 3.0 // 聲母+介音+韻母
            hasConsonant && hasFinal -> score += 2.0 // 聲母+韻母
            hasConsonant && hasMedial -> score += 2.0
            hasFinal -> score += 1.0 // 僅韻母
        }

        // 常用組合加分
        if (isCommonCombination(combination)) {
            score += 1.0
        }

        return score
    }

    /**
     * 檢查是否為常用注音組合
     */
    private val COMMON_COMBINATIONS =
        setOf(
            "ㄅㄚ", "ㄉㄚ", "ㄍㄚ", "ㄇㄚ", "ㄋㄚ", "ㄌㄚ",
            "ㄓㄚ", "ㄔㄚ", "ㄕㄚ", "ㄗㄚ", "ㄘㄚ", "ㄙㄚ",
            "ㄧ", "ㄨ", "ㄩ",
            "ㄚ", "ㄛ", "ㄜ", "ㄞ", "ㄟ", "ㄠ", "ㄡ", "ㄢ", "ㄣ", "ㄤ", "ㄥ", "ㄦ",
            "ㄅㄧㄢ", "ㄉㄧㄢ", "ㄐㄧㄢ", "ㄏㄠ", "ㄇㄠ", "ㄋㄠ",
            "ㄍㄨㄛ", "ㄏㄨㄛ", "ㄓㄨㄛ",
        )

    private fun isCommonCombination(combination: String): Boolean = combination in COMMON_COMBINATIONS

    /**
     * 取得指定數字對應的所有注音符號
     */
    fun getZhuyinForDigit(digit: Int): List<String> = T9_MAPPING[digit] ?: emptyList()

    /**
     * 取得指定數字對應的注音符號，支援拆分顯示
     * @param half 0=完整, 1=前半（前2個）, 2=後半（後2個）
     */
    fun getZhuyinForDigitHalf(
        digit: Int,
        half: Int,
    ): List<String> {
        val all = getZhuyinForDigit(digit)
        return when {
            half == 1 && all.size > 2 -> all.take(2)
            half == 2 && all.size > 2 -> all.drop(2)
            else -> all
        }
    }

    // ==================== 反向映射：注音 -> 數字 ====================

    // 反向映射表：注音 -> 數字（延遲初始化）
    private val ZHUYIN_TO_DIGIT: Map<String, Int> by lazy {
        val map = mutableMapOf<String, Int>()
        T9_MAPPING.forEach { (digit, zhuyins) ->
            zhuyins.forEach { zhuyin ->
                map[zhuyin] = digit
            }
        }
        map
    }

    /**
     * 取得注音符號對應的數字鍵
     *
     * @param zhuyin 單個注音符號，如 "ㄏ"
     * @return 對應的數字鍵 (0-9)，若無對應則返回 null
     */
    fun getDigitForZhuyin(zhuyin: String): Int? = ZHUYIN_TO_DIGIT[zhuyin]

    // ==================== 從候選詞提取注音組合 ====================

    /**
     * 從候選詞 comment 提取注音（去除聲調）
     *
     * @param comment 格式為 "注音:ㄏㄠˇ" 或空
     * @return 去除聲調後的注音，如 "ㄏㄠ"，或 null
     */
    fun extractZhuyinFromComment(comment: String?): String? {
        if (comment.isNullOrBlank()) return null

        // 解析 "注音:ㄏㄠˇ" 格式
        val prefix = "注音:"
        val zhuyinPart =
            if (comment.startsWith(prefix)) {
                comment.substring(prefix.length)
            } else {
                // 嘗試直接解析（可能沒有前綴）
                comment
            }

        // 去除聲調符號
        val withoutTones = zhuyinPart.filter { it !in TONE_MARKS }

        // 驗證是否為有效注音（至少包含一個注音符號）
        return if (withoutTones.isNotEmpty() && withoutTones.all { isZhuyinChar(it) }) {
            withoutTones
        } else {
            null
        }
    }

    /**
     * 從候選詞列表提取去重的注音組合
     *
     * @param candidates RIME 候選詞列表
     * @param maxCount 最大數量，預設 8
     * @return 按詞頻排序的注音組合列表（候選詞順序即詞頻）
     */
    fun extractUniqueZhuyinCombinations(
        candidates: List<CandidateItem>,
        maxCount: Int = 8,
    ): List<String> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<String>()

        for (candidate in candidates) {
            val zhuyin = extractZhuyinFromComment(candidate.comment) ?: continue

            // 去重
            if (zhuyin !in seen) {
                seen.add(zhuyin)
                result.add(zhuyin)

                // 達到上限則停止
                if (result.size >= maxCount) break
            }
        }

        return result
    }

    /**
     * 檢查字符是否為注音符號
     */
    private fun isZhuyinChar(char: Char): Boolean {
        val charStr = char.toString()
        return charStr in CONSONANTS || charStr in MEDIALS || charStr in FINALS
    }

    // ==================== 前端過濾功能（方案 E）====================

    /**
     * 根據注音前綴過濾候選詞
     *
     * 當用戶點擊注音選擇器時，根據選擇的注音過濾候選詞列表。
     * 只返回注音以指定前綴開頭的候選詞。
     *
     * @param candidates 完整的候選詞列表
     * @param zhuyinPrefix 注音前綴，如 "ㄏ" 或 "ㄏㄠ"
     * @return 過濾後的候選詞列表
     */
    fun filterCandidatesByZhuyinPrefix(
        candidates: List<CandidateItem>,
        zhuyinPrefix: String,
    ): List<CandidateItem> {
        if (zhuyinPrefix.isEmpty()) return candidates

        return candidates.filter { candidate ->
            val candidateZhuyin = extractZhuyinFromComment(candidate.comment)
            candidateZhuyin != null && candidateZhuyin.startsWith(zhuyinPrefix)
        }
    }

    /**
     * 將候選詞按注音前綴分組
     *
     * 用於在前端維護分組狀態，方便快速過濾。
     *
     * @param candidates 完整的候選詞列表
     * @return Map<注音前綴, 候選詞列表>
     */
    fun groupCandidatesByZhuyinPrefix(candidates: List<CandidateItem>): Map<String, List<CandidateItem>> {
        val grouped = mutableMapOf<String, MutableList<CandidateItem>>()

        for (candidate in candidates) {
            val zhuyin = extractZhuyinFromComment(candidate.comment) ?: continue

            // 使用完整注音作為 key
            val group = grouped.getOrPut(zhuyin) { mutableListOf() }
            group.add(candidate)

            // 同時也按第一個注音字符分組（用於單按數字鍵的情況）
            if (zhuyin.isNotEmpty()) {
                val firstChar = zhuyin.first().toString()
                val firstCharGroup = grouped.getOrPut(firstChar) { mutableListOf() }
                if (candidate !in firstCharGroup) {
                    firstCharGroup.add(candidate)
                }
            }
        }

        return grouped
    }

    /**
     * 取得注音的第一個字符（聲母或介音/韻母）
     *
     * @param zhuyin 完整注音，如 "ㄏㄠ"
     * @return 第一個字符，如 "ㄏ"
     */
    fun getFirstZhuyinChar(zhuyin: String): String? = if (zhuyin.isNotEmpty()) zhuyin.first().toString() else null

    // ==================== 方案 2：前端補充候選詞 ====================

    /**
     * 單鍵輸入時以 t9_chars.json 策劃順序為主的候選詞合併
     *
     * RIME 的精確匹配優先機制會讓韻母字（如「啊」ㄚ）排在聲母字（如「不」ㄅㄨˋ）前面，
     * 即使後者的使用頻率更高。此方法以 t9_chars.json 的人工策劃順序為主，
     * 再附加 RIME 獨有的候選字。
     *
     * @param rimeCandidates RIME 引擎返回的候選詞
     * @param digit 數字鍵 (0-9)
     * @return 以策劃順序為主的候選詞列表
     */
    fun prioritizedCandidates(
        rimeCandidates: List<CandidateItem>,
        digit: Int,
    ): List<CandidateItem> {
        val digitChars = T9CharDataLoader.getDigitChars(digit)
        val result = mutableListOf<CandidateItem>()
        val addedTexts = mutableSetOf<String>()

        // 優先加入 t9_chars.json 的策劃順序候選字
        for (char in digitChars) {
            if (char.text !in addedTexts) {
                // 若 RIME 也有此字，優先使用 RIME 版本（comment 可能更準確）
                val rimeVersion = rimeCandidates.find { it.text == char.text }
                result.add(rimeVersion ?: char)
                addedTexts.add(char.text)
            }
        }

        // 再附加 RIME 獨有的候選字（不在 t9_chars.json 中的）
        for (candidate in rimeCandidates) {
            if (candidate.text !in addedTexts) {
                result.add(candidate)
                addedTexts.add(candidate.text)
            }
        }

        return result
    }

    /**
     * 多鍵輸入的候選字優先排序（t9_chars.json 優先，RIME 獨有排後）
     *
     * 仿照 prioritizedCandidates() 的模式，但針對多鍵輸入：
     * 1. 從 t9_chars.json 中找出所有匹配有效注音組合的候選字，排在前面
     * 2. RIME 獨有的候選字（不在 t9_chars.json 中）排在後面
     *
     * @param rimeCandidates RIME 引擎回傳的候選詞列表
     * @param digitSequence 數字序列，如 "51"
     * @return 重新排序後的候選詞列表
     */
    fun prioritizedMultiKeyCandidates(
        rimeCandidates: List<CandidateItem>,
        validCombinations: List<String>,
    ): List<CandidateItem> {
        if (validCombinations.isEmpty()) return rimeCandidates

        val validSet = validCombinations.toHashSet()

        val rimeByText = rimeCandidates.associateBy { it.text }
        val result = mutableListOf<CandidateItem>()
        val addedTexts = mutableSetOf<String>()

        for (digit in 0..11) {
            for (item in T9CharDataLoader.getDigitChars(digit)) {
                if (item.text in addedTexts) continue
                val zhuyin = extractZhuyinFromComment(item.comment)
                if (zhuyin != null && zhuyin in validSet) {
                    result.add(rimeByText[item.text] ?: item)
                    addedTexts.add(item.text)
                }
            }
        }

        for (candidate in rimeCandidates) {
            if (candidate.text !in addedTexts) {
                result.add(candidate)
                addedTexts.add(candidate.text)
            }
        }

        return result
    }

    // ==================== 聲調過濾功能 ====================

    /**
     * 從候選詞 comment 提取聲調符號
     *
     * @param comment 格式為 "注音:ㄏㄠˇ" 或空
     * @return 聲調符號（"ˊ", "ˇ", "ˋ", "˙"），一聲返回 null
     */
    fun extractToneFromComment(comment: String?): String? {
        if (comment.isNullOrBlank()) return null

        val prefix = "注音:"
        val zhuyinPart =
            if (comment.startsWith(prefix)) {
                comment.substring(prefix.length)
            } else {
                comment
            }

        if (zhuyinPart.isEmpty()) return null

        // 檢查最後一個字符是否為聲調符號
        val lastChar = zhuyinPart.last()
        return if (lastChar in TONE_MARKS) lastChar.toString() else null
    }

    /** 解析候選詞的所有聲調：優先從 toneMap 取得，否則從 comment 提取 */
    fun resolveTones(
        candidate: CandidateItem,
        toneMap: Map<String, Set<String?>> = emptyMap(),
    ): Set<String?> =
        toneMap[candidate.text]
            ?: setOf(extractToneFromComment(candidate.comment))

    fun filterCandidatesByTone(
        candidates: List<CandidateItem>,
        tone: String,
        toneMap: Map<String, Set<String?>> = emptyMap(),
    ): List<CandidateItem> {
        return candidates.filter { candidate ->
            val tones = resolveTones(candidate, toneMap)
            if (tone == "ˉ") null in tones else tone in tones
        }
    }

    /**
     * 檢查指定注音是否有對應的單韻母/單介音字
     *
     * @param zhuyin 注音符號，如 "ㄠ"
     * @return 是否有對應的補充字
     */
    fun hasSingleVowelChars(zhuyin: String): Boolean = T9CharDataLoader.hasSingleVowelChars(zhuyin)

    /**
     * 取得指定注音對應的單韻母/單介音字
     *
     * @param zhuyin 注音符號，如 "ㄠ"
     * @return 對應的候選詞列表，若無則返回空列表
     */
    fun getSingleVowelChars(zhuyin: String): List<CandidateItem> = T9CharDataLoader.getSingleVowelChars(zhuyin)
}
