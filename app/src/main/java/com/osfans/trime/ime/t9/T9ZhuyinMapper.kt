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
    // 聲調符號（用於從 comment 中去除）
    private val TONE_MARKS = setOf('ˊ', 'ˇ', 'ˋ', '˙')
    // T9數字到注音符號的映射表 (來自 task-02-rime-schema.md)
    private val T9_MAPPING =
        mapOf(
            1 to listOf("ㄅ", "ㄉ", "ㄚ"), // 聲母: ㄅㄉ, 韻母: ㄚ
            2 to listOf("ㄍ", "ㄐ", "ㄞ", "ㄧ"), // 聲母: ㄍㄐ, 韻母: ㄞ, 介音: ㄧ
            3 to listOf("ㄓ", "ㄗ", "ㄢ", "ㄦ"), // 聲母: ㄓㄗ, 韻母: ㄢㄦ
            4 to listOf("ㄆ", "ㄊ", "ㄛ"), // 聲母: ㄆㄊ, 韻母: ㄛ
            5 to listOf("ㄎ", "ㄑ", "ㄟ", "ㄨ"), // 聲母: ㄎㄑ, 韻母: ㄟ, 介音: ㄨ
            6 to listOf("ㄔ", "ㄘ", "ㄣ"), // 聲母: ㄔㄘ, 韻母: ㄣ
            7 to listOf("ㄇ", "ㄋ", "ㄜ", "ㄝ"), // 聲母: ㄇㄋ, 韻母: ㄜㄝ
            8 to listOf("ㄏ", "ㄒ", "ㄠ", "ㄩ"), // 聲母: ㄏㄒ, 韻母: ㄠ, 介音: ㄩ
            9 to listOf("ㄕ", "ㄙ", "ㄤ", "ㄥ"), // 聲母: ㄕㄙ, 韻母: ㄤㄥ
            0 to listOf("ㄈ", "ㄌ", "ㄡ", "ㄖ"), // 聲母: ㄈㄌㄖ, 韻母: ㄡ
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

        val firstDigit = digitSequence.first().toString().toIntOrNull() ?: return emptyList()
        val remainingDigits = digitSequence.drop(1)

        val firstZhuyinOptions = T9_MAPPING[firstDigit] ?: return emptyList()
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
                    // 聲母不能在介音或韻母後
                    if (hasMedial || hasFinal) return false
                }
                charStr in MEDIALS -> {
                    medialCount++
                    hasMedial = true
                    // 介音不能在韻母後
                    if (hasFinal) return false
                }
                charStr in FINALS -> {
                    finalCount++
                    hasFinal = true
                }
                else -> return false // 未知符號
            }
        }

        // 基本規則檢查：每種類型最多一個
        if (consonantCount > 1 || medialCount > 1 || finalCount > 1) {
            return false
        }

        // 有效組合：必須有介音或韻母（聲母可選）
        return hasMedial || hasFinal
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
    private fun isCommonCombination(combination: String): Boolean {
        val commonCombinations =
            setOf(
                // 常用聲母+韻母
                "ㄅㄚ",
                "ㄉㄚ",
                "ㄍㄚ",
                "ㄇㄚ",
                "ㄋㄚ",
                "ㄌㄚ",
                "ㄓㄚ",
                "ㄔㄚ",
                "ㄕㄚ",
                "ㄗㄚ",
                "ㄘㄚ",
                "ㄙㄚ",
                // 單獨介音
                "ㄧ",
                "ㄨ",
                "ㄩ",
                // 常用韻母
                "ㄚ",
                "ㄛ",
                "ㄜ",
                "ㄞ",
                "ㄟ",
                "ㄠ",
                "ㄡ",
                "ㄢ",
                "ㄣ",
                "ㄤ",
                "ㄥ",
                "ㄦ",
                // 常用完整組合
                "ㄅㄧㄢ",
                "ㄉㄧㄢ",
                "ㄍㄧㄢ", // 邊、點、間
                "ㄏㄠ",
                "ㄇㄠ",
                "ㄋㄠ", // 好、毛、腦
                "ㄍㄨㄛ",
                "ㄏㄨㄛ",
                "ㄓㄨㄛ", // 國、火、桌
            )

        return commonCombinations.contains(combination)
    }

    /**
     * 取得指定數字對應的所有注音符號
     */
    fun getZhuyinForDigit(digit: Int): List<String> = T9_MAPPING[digit] ?: emptyList()

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
     * T9 數字鍵常用字映射表
     *
     * 為每個 T9 數字鍵提供常用字列表，當 RIME 返回的候選詞不足時由前端補充。
     * 這解決了 RIME abbrev 規則對某些數字不生效的問題。
     *
     * T9 映射：
     * 1: ㄅㄉㄚ  2: ㄍㄐㄞㄧ  3: ㄓㄗㄢㄦ
     * 4: ㄆㄊㄛ  5: ㄎㄑㄟㄨ  6: ㄔㄘㄣ
     * 7: ㄇㄋㄜㄝ 8: ㄏㄒㄠㄩ  9: ㄕㄙㄤㄥ
     * 0: ㄈㄌㄡㄖ
     */
    private val T9_DIGIT_CHARS: Map<Int, List<CandidateItem>> =
        mapOf(
            // 數字 1: ㄅㄉㄚ
            1 to listOf(
                CandidateItem("不", "注音:ㄅㄨˋ"),
                CandidateItem("的", "注音:ㄉㄜ˙"),
                CandidateItem("大", "注音:ㄉㄚˋ"),
                CandidateItem("到", "注音:ㄉㄠˋ"),
                CandidateItem("但", "注音:ㄉㄢˋ"),
                CandidateItem("阿", "注音:ㄚ"),
                CandidateItem("啊", "注音:ㄚ"),
            ),
            // 數字 2: ㄍㄐㄞㄧ
            2 to listOf(
                CandidateItem("個", "注音:ㄍㄜ˙"),
                CandidateItem("國", "注音:ㄍㄨㄛˊ"),
                CandidateItem("過", "注音:ㄍㄨㄛˋ"),
                CandidateItem("就", "注音:ㄐㄧㄡˋ"),
                CandidateItem("經", "注音:ㄐㄧㄥ"),
                CandidateItem("愛", "注音:ㄞˋ"),
                CandidateItem("一", "注音:ㄧ"),
                CandidateItem("以", "注音:ㄧˇ"),
                CandidateItem("也", "注音:ㄧㄝˇ"),
                CandidateItem("要", "注音:ㄧㄠˋ"),
            ),
            // 數字 3: ㄓㄗㄢㄦ
            3 to listOf(
                CandidateItem("這", "注音:ㄓㄜˋ"),
                CandidateItem("中", "注音:ㄓㄨㄥ"),
                CandidateItem("之", "注音:ㄓ"),
                CandidateItem("在", "注音:ㄗㄞˋ"),
                CandidateItem("自", "注音:ㄗˋ"),
                CandidateItem("子", "注音:ㄗˇ"),
                CandidateItem("安", "注音:ㄢ"),
                CandidateItem("而", "注音:ㄦˊ"),
                CandidateItem("二", "注音:ㄦˋ"),
            ),
            // 數字 4: ㄆㄊㄛ
            4 to listOf(
                CandidateItem("他", "注音:ㄊㄚ"),
                CandidateItem("她", "注音:ㄊㄚ"),
                CandidateItem("天", "注音:ㄊㄧㄢ"),
                CandidateItem("同", "注音:ㄊㄨㄥˊ"),
                CandidateItem("喔", "注音:ㄛ"),
                CandidateItem("哦", "注音:ㄛ"),
            ),
            // 數字 5: ㄎㄑㄟㄨ
            5 to listOf(
                CandidateItem("可", "注音:ㄎㄜˇ"),
                CandidateItem("看", "注音:ㄎㄢˋ"),
                CandidateItem("開", "注音:ㄎㄞ"),
                CandidateItem("去", "注音:ㄑㄩˋ"),
                CandidateItem("起", "注音:ㄑㄧˇ"),
                CandidateItem("其", "注音:ㄑㄧˊ"),
                CandidateItem("前", "注音:ㄑㄧㄢˊ"),
                CandidateItem("欸", "注音:ㄟ"),
                CandidateItem("五", "注音:ㄨˇ"),
                CandidateItem("我", "注音:ㄨㄛˇ"),
                CandidateItem("為", "注音:ㄨㄟˋ"),
            ),
            // 數字 6: ㄔㄘㄣ
            6 to listOf(
                CandidateItem("出", "注音:ㄔㄨ"),
                CandidateItem("成", "注音:ㄔㄥˊ"),
                CandidateItem("從", "注音:ㄘㄨㄥˊ"),
                CandidateItem("恩", "注音:ㄣ"),
            ),
            // 數字 7: ㄇㄋㄜㄝ
            7 to listOf(
                CandidateItem("們", "注音:ㄇㄣ˙"),
                CandidateItem("沒", "注音:ㄇㄟˊ"),
                CandidateItem("面", "注音:ㄇㄧㄢˋ"),
                CandidateItem("那", "注音:ㄋㄚˋ"),
                CandidateItem("能", "注音:ㄋㄥˊ"),
                CandidateItem("你", "注音:ㄋㄧˇ"),
                CandidateItem("年", "注音:ㄋㄧㄢˊ"),
                CandidateItem("餓", "注音:ㄜˋ"),
                CandidateItem("鵝", "注音:ㄜˊ"),
            ),
            // 數字 8: ㄏㄒㄠㄩ
            8 to listOf(
                CandidateItem("和", "注音:ㄏㄜˊ"),
                CandidateItem("好", "注音:ㄏㄠˇ"),
                CandidateItem("很", "注音:ㄏㄣˇ"),
                CandidateItem("恨", "注音:ㄏㄣˋ"),
                CandidateItem("狠", "注音:ㄏㄣˇ"),
                CandidateItem("會", "注音:ㄏㄨㄟˋ"),
                CandidateItem("後", "注音:ㄏㄡˋ"),
                CandidateItem("還", "注音:ㄏㄞˊ"),
                CandidateItem("下", "注音:ㄒㄧㄚˋ"),
                CandidateItem("小", "注音:ㄒㄧㄠˇ"),
                CandidateItem("想", "注音:ㄒㄧㄤˇ"),
                CandidateItem("現", "注音:ㄒㄧㄢˋ"),
                CandidateItem("新", "注音:ㄒㄧㄣ"),
                CandidateItem("心", "注音:ㄒㄧㄣ"),
                CandidateItem("信", "注音:ㄒㄧㄣˋ"),
                CandidateItem("些", "注音:ㄒㄧㄝ"),
                CandidateItem("學", "注音:ㄒㄩㄝˊ"),
                CandidateItem("行", "注音:ㄒㄧㄥˊ"),
                CandidateItem("奧", "注音:ㄠˋ"),
                CandidateItem("於", "注音:ㄩˊ"),
                CandidateItem("魚", "注音:ㄩˊ"),
            ),
            // 數字 9: ㄕㄙㄤㄥ
            9 to listOf(
                CandidateItem("是", "注音:ㄕˋ"),
                CandidateItem("說", "注音:ㄕㄨㄛ"),
                CandidateItem("上", "注音:ㄕㄤˋ"),
                CandidateItem("時", "注音:ㄕˊ"),
                CandidateItem("生", "注音:ㄕㄥ"),
                CandidateItem("事", "注音:ㄕˋ"),
                CandidateItem("所", "注音:ㄙㄨㄛˇ"),
                CandidateItem("三", "注音:ㄙㄢ"),
                CandidateItem("思", "注音:ㄙ"),
                CandidateItem("四", "注音:ㄙˋ"),
            ),
            // 數字 0: ㄈㄌㄡㄖ
            0 to listOf(
                CandidateItem("發", "注音:ㄈㄚ"),
                CandidateItem("法", "注音:ㄈㄚˇ"),
                CandidateItem("分", "注音:ㄈㄣ"),
                CandidateItem("來", "注音:ㄌㄞˊ"),
                CandidateItem("了", "注音:ㄌㄜ˙"),
                CandidateItem("裡", "注音:ㄌㄧˇ"),
                CandidateItem("歐", "注音:ㄡ"),
                CandidateItem("人", "注音:ㄖㄣˊ"),
                CandidateItem("如", "注音:ㄖㄨˊ"),
                CandidateItem("然", "注音:ㄖㄢˊ"),
            ),
        )

    /**
     * 單韻母/單介音字映射表（保留用於向後兼容）
     */
    private val SINGLE_VOWEL_CHARS: Map<String, List<CandidateItem>> =
        mapOf(
            "ㄚ" to listOf(CandidateItem("阿", "注音:ㄚ"), CandidateItem("啊", "注音:ㄚ")),
            "ㄛ" to listOf(CandidateItem("喔", "注音:ㄛ"), CandidateItem("哦", "注音:ㄛ")),
            "ㄜ" to listOf(CandidateItem("餓", "注音:ㄜ"), CandidateItem("鵝", "注音:ㄜ")),
            "ㄞ" to listOf(CandidateItem("愛", "注音:ㄞ"), CandidateItem("哀", "注音:ㄞ")),
            "ㄟ" to listOf(CandidateItem("欸", "注音:ㄟ")),
            "ㄠ" to listOf(CandidateItem("奧", "注音:ㄠ"), CandidateItem("凹", "注音:ㄠ")),
            "ㄡ" to listOf(CandidateItem("歐", "注音:ㄡ"), CandidateItem("偶", "注音:ㄡ")),
            "ㄢ" to listOf(CandidateItem("安", "注音:ㄢ"), CandidateItem("暗", "注音:ㄢ")),
            "ㄣ" to listOf(CandidateItem("恩", "注音:ㄣ")),
            "ㄤ" to listOf(CandidateItem("骯", "注音:ㄤ")),
            "ㄦ" to listOf(CandidateItem("而", "注音:ㄦ"), CandidateItem("二", "注音:ㄦ")),
            "ㄧ" to listOf(CandidateItem("一", "注音:ㄧ"), CandidateItem("以", "注音:ㄧ")),
            "ㄨ" to listOf(CandidateItem("五", "注音:ㄨ"), CandidateItem("物", "注音:ㄨ")),
            "ㄩ" to listOf(CandidateItem("於", "注音:ㄩ"), CandidateItem("魚", "注音:ㄩ")),
        )

    /**
     * 補充候選詞
     *
     * 當 RIME 返回的候選詞不足時，從預建的 T9 數字鍵映射表中補充常用字。
     * 這解決了 RIME abbrev 規則對某些數字不生效的問題。
     *
     * @param candidates RIME 返回的候選詞列表
     * @param digit 當前輸入的數字鍵 (0-9)
     * @return 補充後的候選詞列表
     */
    fun supplementCandidates(
        candidates: List<CandidateItem>,
        digit: Int,
    ): List<CandidateItem> {
        // 找出候選詞中已存在的文字（用於去重）
        val existingTexts = candidates.map { it.text }.toSet()

        // 從 T9 數字鍵映射表中補充候選詞
        val supplemented = candidates.toMutableList()
        T9_DIGIT_CHARS[digit]?.let { digitChars ->
            for (char in digitChars) {
                // 避免重複添加
                if (char.text !in existingTexts) {
                    supplemented.add(char)
                }
            }
        }

        return supplemented
    }

    /**
     * 多鍵輸入時補充候選詞
     *
     * 根據數字序列計算有效的注音組合，然後從 T9_DIGIT_CHARS 中找出匹配的候選詞。
     * 這解決了多鍵輸入時 RIME 可能不返回某些注音組合候選詞的問題。
     *
     * @param candidates RIME 返回的候選詞列表
     * @param digitSequence 數字序列，如 "86"
     * @return 補充後的候選詞列表
     */
    fun supplementMultiKeyCandidates(
        candidates: List<CandidateItem>,
        digitSequence: String,
    ): List<CandidateItem> {
        if (digitSequence.length < 2) return candidates

        // 計算有效的注音組合
        val validCombinations = mapToZhuyinCombinations(digitSequence)
        if (validCombinations.isEmpty()) return candidates

        // 找出候選詞中已存在的文字（用於去重）
        val existingTexts = candidates.map { it.text }.toMutableSet()

        val supplemented = candidates.toMutableList()

        // 從 T9_DIGIT_CHARS 的所有數字中收集候選詞
        for (digit in digitSequence.mapNotNull { it.toString().toIntOrNull() }.toSet()) {
            T9_DIGIT_CHARS[digit]?.forEach { candidateItem ->
                // 跳過已存在的
                if (candidateItem.text in existingTexts) return@forEach

                // 檢查候選詞的注音是否匹配任一有效組合
                val candidateZhuyin = extractZhuyinFromComment(candidateItem.comment)
                if (candidateZhuyin != null) {
                    // 檢查是否匹配任一有效注音組合（前綴匹配）
                    val matches = validCombinations.any { validZhuyin ->
                        candidateZhuyin.startsWith(validZhuyin) || validZhuyin.startsWith(candidateZhuyin)
                    }
                    if (matches) {
                        supplemented.add(candidateItem)
                        existingTexts.add(candidateItem.text)
                    }
                }
            }
        }

        return supplemented
    }

    /**
     * 檢查指定注音是否有對應的單韻母/單介音字
     *
     * @param zhuyin 注音符號，如 "ㄠ"
     * @return 是否有對應的補充字
     */
    fun hasSingleVowelChars(zhuyin: String): Boolean = SINGLE_VOWEL_CHARS.containsKey(zhuyin)

    /**
     * 取得指定注音對應的單韻母/單介音字
     *
     * @param zhuyin 注音符號，如 "ㄠ"
     * @return 對應的候選詞列表，若無則返回空列表
     */
    fun getSingleVowelChars(zhuyin: String): List<CandidateItem> = SINGLE_VOWEL_CHARS[zhuyin] ?: emptyList()
}
