// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

import com.osfans.trime.daemon.RimeSession
import timber.log.Timber

/**
 * T9 RIME引擎整合輔助工具
 *
 * 負責將注音符號轉換為RIME編碼，並從RIME引擎獲取候選詞。
 * 使用現有的 bopomofo_t9 方案。
 */
object T9RimeHelper {
    private const val TAG = "T9RimeHelper"

    // 注音符號到RIME編碼的映射表 (基於bopomofo_t9方案)
    private val ZHUYIN_TO_RIME =
        mapOf(
            // 聲母
            "ㄅ" to "b",
            "ㄆ" to "p",
            "ㄇ" to "m",
            "ㄈ" to "f",
            "ㄉ" to "d",
            "ㄊ" to "t",
            "ㄋ" to "n",
            "ㄌ" to "l",
            "ㄍ" to "g",
            "ㄎ" to "k",
            "ㄏ" to "h",
            "ㄐ" to "j",
            "ㄑ" to "q",
            "ㄒ" to "x",
            "ㄓ" to "Z",
            "ㄔ" to "C",
            "ㄕ" to "S",
            "ㄖ" to "r",
            "ㄗ" to "z",
            "ㄘ" to "c",
            "ㄙ" to "s",
            // 介音
            "ㄧ" to "i",
            "ㄨ" to "u",
            "ㄩ" to "v",
            // 韻母
            "ㄚ" to "a",
            "ㄛ" to "o",
            "ㄜ" to "e",
            "ㄝ" to "E",
            "ㄞ" to "I",
            "ㄟ" to "U",
            "ㄠ" to "A",
            "ㄡ" to "O",
            "ㄢ" to "M",
            "ㄣ" to "N",
            "ㄤ" to "Y",
            "ㄥ" to "W",
            "ㄦ" to "P",
        )

    /**
     * 將注音符號轉換為RIME編碼
     *
     * @param zhuyin 注音符號，如 "ㄅㄧㄢ"
     * @return RIME編碼，如 "biM"
     */
    fun zhuyinToRimeCode(zhuyin: String): String =
        zhuyin
            .map { zhuyinChar ->
                ZHUYIN_TO_RIME[zhuyinChar.toString()] ?: ""
            }.joinToString("")

    /**
     * 從RIME引擎獲取候選詞
     *
     * @param rimeSession RIME會話
     * @param zhuyinCombinations 注音組合列表
     * @return 候選詞列表
     */
    suspend fun getCandidatesFromRime(
        rimeSession: RimeSession,
        zhuyinCombinations: List<String>,
    ): List<String> {
        val candidates = mutableSetOf<String>() // 使用Set避免重複

        try {
            for (zhuyin in zhuyinCombinations.take(5)) { // 限制處理數量
                val rimeCode = zhuyinToRimeCode(zhuyin)
                if (rimeCode.isEmpty()) continue

                Timber.d("$TAG: Processing zhuyin='$zhuyin' -> rime='$rimeCode'")

                // 使用正確的RimeSession API
                rimeSession.runOnReady {
                    // 清空當前組合並輸入新的RIME編碼
                    clearComposition()

                    // 逐字符發送到RIME
                    for (char in rimeCode) {
                        processKey(char.code, 0u)
                    }

                    // 獲取候選詞
                    val rimeCandidates = getCandidatesFromRimeApi(this)
                    candidates.addAll(rimeCandidates)
                }

                // 如果已經有足夠候選詞，可以提前結束
                if (candidates.size >= 8) break
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error getting candidates from RIME")
        }

        val result = candidates.take(8) // 限制候選詞數量
        Timber.d("$TAG: Final candidates: $result")
        return result
    }

    /**
     * 從RIME API中提取候選詞
     */
    private suspend fun getCandidatesFromRimeApi(rimeApi: com.osfans.trime.core.RimeApi): List<String> =
        try {
            val candidates = mutableListOf<String>()

            // 使用RIME API獲取候選詞
            val candidateItems = rimeApi.getCandidates(0, 10)
            for (item in candidateItems) {
                if (item.text.isNotEmpty()) {
                    candidates.add(item.text)
                }
            }

            candidates
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error extracting candidates from RIME API")
            emptyList()
        }

    /**
     * 檢查RIME引擎是否準備就緒
     */
    fun isRimeReady(rimeSession: RimeSession): Boolean =
        try {
            // 使用RimeSession的isReady屬性
            rimeSession.run { isReady }
        } catch (e: Exception) {
            Timber.w(e, "$TAG: RIME not ready")
            false
        }

    /**
     * 將數字序列直接轉換為可能的RIME編碼列表
     */
    fun digitSequenceToRimeCodes(digitSequence: String): List<String> {
        val zhuyinCombinations = T9ZhuyinMapper.mapToZhuyinCombinations(digitSequence)
        return zhuyinCombinations
            .map { zhuyinToRimeCode(it) }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}
