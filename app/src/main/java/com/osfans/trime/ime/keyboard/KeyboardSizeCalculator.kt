// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.util.appContext
import com.osfans.trime.util.sp
import kotlin.math.abs
import kotlin.math.ceil

/**
 * 鍵盤尺寸計算器
 *
 * 負責根據鍵盤配置和裝置參數，計算鍵盤中每個按鍵的尺寸和位置。
 * 支援分割鍵盤、自動高度調整、多行布局等功能。
 *
 * @param isSplit 是否為分割鍵盤模式
 * @param splitPercent 分割鍵盤的空间百分比
 * @param maxColumns 最大列數
 * @param mAllowedWidth 允許的總寬度（像素）
 * @param keyboardHeight 鍵盤高度（像素）
 * @param keyboardKeyWidth 單鍵默認寬度權重
 * @param keyHeight 單鍵默認高度（像素）
 * @param mDefaultHorizontalGap 默認水平間隙（像素）
 * @param mDefaultVerticalGap 默認垂直間隙（像素）
 * @param autoHeightIndex 自動高度調整的行索引
 *
 * @since 1.0
 */
class KeyboardSizeCalculator(
    isSplit: Boolean,
    splitPercent: Int,
    private val maxColumns: Int,
    private val mAllowedWidth: Int,
    private val keyboardHeight: Int,
    private val keyboardKeyWidth: Float,
    private val keyHeight: Int,
    private val mDefaultHorizontalGap: Int,
    private val mDefaultVerticalGap: Int,
    private val autoHeightIndex: Int,
) {
    /** 分割鍵盤的空间比例，範圍 0.0-1.0 */
    private val splitSpaceRatio: Float = if (isSplit) (splitPercent / 100f) else 0f

    /**
     * 計算鍵盤尺寸參數
     *
     * 根據提供的鍵位列表，計算整個鍵盤的尺寸參數，包括每行的權重分配、
     * 縮放後的高度等。此方法會處理換行邏輯、高度調整等複雜情況。
     *
     * @param keys 鍵位配置列表
     * @return 計算完成的鍵盤尺寸資料
     */
    fun calc(keys: List<TextKeyboard.TextKey>): KeyboardSize {
        var x = mDefaultHorizontalGap / 2
        var y = 0
        var column = 0
        var row = 0

        var rowHeight = keyHeight

        var rawSumHeight = 0
        val rawHeight = ArrayList<Int>()

        var totalKeyWidth = 0f
        var maxColumn = 0
        val rowTotalWeight = HashMap<Int, Float>()

        for (key in keys) {
            val keyWidthWeight =
                if (key.width == 0f && key.click.isNotEmpty()) {
                    keyboardKeyWidth
                } else {
                    key.width
                }
            val widthPx =
                (keyWidthWeight * mAllowedWidth / MAX_TOTAL_WEIGHT).toInt() - mDefaultHorizontalGap
            if (column >= maxColumns || x + widthPx > mAllowedWidth) {
                maxColumn = maxOf(maxColumn, column)
                rowTotalWeight[row] = totalKeyWidth

                x = mDefaultHorizontalGap / 2
                y += mDefaultVerticalGap + rowHeight
                totalKeyWidth = 0f
                column = 0
                row++
                rawSumHeight += rowHeight
                rawHeight.add(rowHeight)
            }

            if (column == 0) {
                rowHeight = if (key.height > 0) appContext.sp(key.height).toInt() else keyHeight
            }
            totalKeyWidth += keyWidthWeight
            if (key.click.isEmpty()) { // 無按鍵事件
                x += widthPx + mDefaultHorizontalGap
                continue // 縮進
            }
            column++
            val rightGap = abs(mAllowedWidth - x - widthPx - mDefaultHorizontalGap / 2)
            x += (
                if (rightGap <= mAllowedWidth / MAX_TOTAL_WEIGHT) {
                    mAllowedWidth - x -
                        mDefaultHorizontalGap / 2
                } else {
                    widthPx
                }
            ) + mDefaultHorizontalGap
        }
        rowTotalWeight[row] = totalKeyWidth

        rawSumHeight += rowHeight
        rawHeight.add(rowHeight)

        val scaledVerticalGap = calculateScaledVerticalGap(rawSumHeight, rawHeight)
        return KeyboardSize(
            rowTotalWeight,
            calculateOneWeightWidthPx(),
            splitSpaceRatio,
            calculateAdjustedHeight(rawSumHeight, rawHeight, scaledVerticalGap),
            scaledVerticalGap.toInt(),
        )
    }

    /**
     * 計算單位權重對應的像素寬度
     *
     * @return 單位權重的像素寬度值
     */
    private fun calculateOneWeightWidthPx(): Float = (mAllowedWidth / (MAX_TOTAL_WEIGHT * (1 + splitSpaceRatio)))

    /**
     * 計算縮放後的垂直間隙
     *
     * 根據鍵盤總高度和原始行高度，計算適當的垂直間隙尺寸。
     *
     * @param rawSumHeight 原始總高度
     * @param rawHeight 原始每行高度列表
     * @return 縮放後的垂直間隙值
     */
    private fun calculateScaledVerticalGap(
        rawSumHeight: Int,
        rawHeight: List<Int>,
    ): Double {
        val scale: Double =
            keyboardHeight.toDouble() / (rawSumHeight + mDefaultVerticalGap * (rawHeight.size + 1))

        return ceil((mDefaultVerticalGap * scale))
    }

    /**
     * 計算調整後的每行高度
     *
     * 根據可用的總高度空间，調整每行的高度，特別處理自動高度行。
     *
     * @param rawSumHeight 原始總高度
     * @param rawHeight 原始每行高度列表
     * @param scaledVerticalGap 縮放後的垂直間隙
     * @return 調整後的每行高度列表
     */
    private fun calculateAdjustedHeight(
        rawSumHeight: Int,
        rawHeight: List<Int>,
        scaledVerticalGap: Double,
    ): List<Int> {
        var remainHeight = keyboardHeight - scaledVerticalGap * (rawHeight.size + 1)

        val scale = remainHeight / rawSumHeight

        var finalAutoHeightIndex = -100
        if (autoHeightIndex < 0) {
            finalAutoHeightIndex = rawHeight.size + autoHeightIndex
            if (finalAutoHeightIndex < 0) finalAutoHeightIndex = 0
        } else if (autoHeightIndex >= rawHeight.size) {
            finalAutoHeightIndex = rawHeight.size - 1
        }

        val newHeight = rawHeight.toMutableList()
        for (i in rawHeight.indices) {
            if (i != finalAutoHeightIndex) {
                val h: Int = (rawHeight[i] * scale).toInt()
                newHeight[i] = h
                remainHeight -= h
            }
        }
        if (remainHeight < 1) {
            if (rawHeight[finalAutoHeightIndex] > 0) remainHeight = 1.0
        }
        newHeight[finalAutoHeightIndex] = remainHeight.toInt()

        return newHeight
    }

    companion object {
        /** 最大總權重值，用於權重計算基準 */
        private const val MAX_TOTAL_WEIGHT = 100
    }
}
