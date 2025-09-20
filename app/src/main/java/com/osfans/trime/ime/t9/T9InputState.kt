// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

/**
 * T9輸入狀態數據類
 *
 * 封裝T9輸入過程中的所有狀態信息，包括當前數字序列、候選詞列表、預編輯文字等。
 */
data class T9InputState(
    /** 當前輸入的數字序列 (如: "123") */
    val digitSequence: String = "",
    /** 當前的候選詞列表 */
    val candidates: List<String> = emptyList(),
    /** 是否正在輸入中 */
    val isInputting: Boolean = false,
    /** 當前可能的注音組合列表 (用於調試和顯示) */
    val zhuyinCombinations: List<String> = emptyList(),
    /** 預編輯文字 - 顯示在預編輯區域的文字 */
    val preeditText: String = "",
    /** 注音顯示文字 - 格式化後的注音符號文字 */
    val zhuyinDisplayText: String = "",
) {
    /** 是否有輸入內容 */
    val hasInput: Boolean
        get() = digitSequence.isNotEmpty()

    /** 是否有候選詞 */
    val hasCandidates: Boolean
        get() = candidates.isNotEmpty()

    /** 是否有預編輯文字 */
    val hasPreeditText: Boolean
        get() = preeditText.isNotEmpty()

    /** 重置到初始狀態 */
    fun reset(): T9InputState = T9InputState()

    /** 添加數字到序列 */
    fun addDigit(digit: Int): T9InputState {
        require(digit in 0..9) { "數字必須在0-9範圍內: $digit" }

        return copy(
            digitSequence = digitSequence + digit.toString(),
            isInputting = true,
        )
    }

    /** 刪除最後一個數字 */
    fun removeLastDigit(): T9InputState {
        if (digitSequence.isEmpty()) {
            return reset()
        }

        val newSequence = digitSequence.dropLast(1)
        return if (newSequence.isEmpty()) {
            reset()
        } else {
            copy(
                digitSequence = newSequence,
                isInputting = true,
            )
        }
    }

    /** 更新候選詞和注音組合 */
    fun updateCandidatesAndZhuyin(
        newCandidates: List<String>,
        newZhuyinCombinations: List<String>,
    ): T9InputState =
        copy(
            candidates = newCandidates,
            zhuyinCombinations = newZhuyinCombinations,
            preeditText = formatPreeditText(),
            zhuyinDisplayText = formatZhuyinDisplayText(newZhuyinCombinations),
        )

    /** 更新預編輯文字相關內容 */
    fun updatePreeditContent(
        newPreeditText: String = preeditText,
        newZhuyinDisplayText: String = zhuyinDisplayText,
    ): T9InputState =
        copy(
            preeditText = newPreeditText,
            zhuyinDisplayText = newZhuyinDisplayText,
        )

    /** 格式化預編輯文字 */
    private fun formatPreeditText(): String {
        return if (digitSequence.isNotEmpty()) {
            digitSequence.toCharArray().joinToString(" ")
        } else {
            ""
        }
    }

    /** 格式化注音顯示文字 */
    private fun formatZhuyinDisplayText(zhuyinList: List<String>): String {
        return when {
            zhuyinList.isEmpty() -> ""
            zhuyinList.size == 1 -> zhuyinList[0]
            zhuyinList.size <= 3 -> zhuyinList.joinToString(" / ")
            else -> "${zhuyinList.take(2).joinToString(" / ")}..."
        }
    }
}
