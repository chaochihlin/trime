/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.popup

import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import androidx.annotation.ColorInt

/**
 * 候選字項目的文字樣式設定物件
 *
 * 此類繼承自 MetricAffectingSpan，用於為彈出視窗中的候選字項目
 * 設定特定的文字顏色、大小和字型。影響文字的繪製和度量。
 *
 * @param color 文字顏色值
 * @param textSize 文字大小，單位為像素
 * @param typeface 文字字型
 */
class CandidateItemSpan(
    @ColorInt
    private val color: Int,
    private val textSize: Float,
    private val typeface: Typeface,
) : MetricAffectingSpan() {
    /**
     * 更新文字繪製狀態
     *
     * 設定文字繪製時的顏色、大小和字型。
     *
     * @param textPaint 文字繪製物件
     */
    override fun updateDrawState(textPaint: TextPaint) {
        textPaint.color = color
        updateState(textPaint)
    }

    /**
     * 更新文字度量狀態
     *
     * 設定文字度量時的大小和字型，但不設定顏色。
     *
     * @param textPaint 文字繪製物件
     */
    override fun updateMeasureState(textPaint: TextPaint) {
        updateState(textPaint)
    }

    /**
     * 內部狀態更新方法
     *
     * 將文字大小和字型應用到繪製物件上。
     *
     * @param textPaint 文字繪製物件
     */
    private fun updateState(textPaint: TextPaint) {
        textPaint.textSize = textSize
        textPaint.typeface = typeface
    }
}
