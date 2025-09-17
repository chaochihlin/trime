// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

/**
 * T9數字到注音符號的映射器
 *
 * 基於 doc/task-02-rime-schema.md 中定義的T9映射表實作數字到注音的轉換邏輯。
 */
object T9ZhuyinMapper {
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
     * 基本規則：聲母 + 介音(可選) + 韻母
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
                    // 聲母不能在韻母後
                    if (hasFinal) return false
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

        // 基本規則檢查
        if (consonantCount > 1 || medialCount > 1 || finalCount > 1) {
            return false
        }

        // 必須有韻母，聲母可選
        return hasFinal
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
}
