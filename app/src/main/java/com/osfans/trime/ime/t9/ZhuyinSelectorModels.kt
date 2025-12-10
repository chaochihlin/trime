// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

/**
 * 注音選項資料項目
 *
 * @param zhuyin 注音符號 (如 "ㄏ")
 * @param index 在該數字鍵映射中的索引 (0-3)
 * @param isSelected 是否為當前選中項
 */
data class ZhuyinItem(
    val zhuyin: String,
    val index: Int,
    var isSelected: Boolean = false,
)

/**
 * 注音選擇事件監聽器
 *
 * 用於接收用戶在注音選擇器中的選擇事件
 */
interface ZhuyinSelectionListener {
    /**
     * 當用戶確認選擇注音時回調
     *
     * @param digit 對應的數字鍵 (0-9)
     * @param zhuyinIndex 選擇的注音在該鍵映射中的索引
     * @param zhuyin 選擇的注音符號
     */
    fun onZhuyinSelected(
        digit: Int,
        zhuyinIndex: Int,
        zhuyin: String,
    )

    /**
     * 當選中項變化時回調（滾動過程中）
     *
     * 可用於即時預覽或更新 UI 狀態
     *
     * @param digit 對應的數字鍵
     * @param zhuyinIndex 當前顯示的注音索引
     */
    fun onZhuyinPreviewChanged(
        digit: Int,
        zhuyinIndex: Int,
    )
}
