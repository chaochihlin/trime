// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

/**
 * 鍵盤尺寸資料類別
 *
 * 存储鍵盤布局計算後的尺寸相關參數，包括行寬度權重、默認寬度、縮放倍數等。
 * 此類別用於在鍵盤縪製過程中傳遞已計算好的尺寸參數。
 *
 * @property rowWidthTotalWeight 每行的總寬度權重對應
 * @property defaultWidth 默認的單位寬度值（像素）
 * @property multiplier 分割模式的空间比例系數
 * @property scaledHeight 縮放後每行的高度列表（像素）
 * @property scaledVerticalGap 縮放後的垂直間隙（像素）
 *
 * @since 1.0
 */
data class KeyboardSize(
    val rowWidthTotalWeight: Map<Int, Float>,
    val defaultWidth: Float,
    val multiplier: Float,
    val scaledHeight: List<Int>,
    val scaledVerticalGap: Int,
)
