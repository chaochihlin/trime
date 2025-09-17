// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.circular

import kotlin.math.sqrt

/**
 * 圓形螢幕配置資料類別
 *
 * 定義圓形螢幕（如智慧手錶）的顯示參數和安全區域配置。
 * 用於計算可用顯示區域、安全邊距，以及判斷元件是否位於可見範圍內。
 *
 * @param screenDiameter 螢幕直徑（像素）
 * @param safeMargin 安全邊距（像素），避免元件過於靠近螢幕邊緣
 * @param chinHeight 下巴高度（像素），某些手錶螢幕底部有非顯示區域
 * @param crownPosition 錶冠位置（0.0-1.0），影響可觸控區域的設計
 */
data class CircularScreenConfig(
    // 預設 360px 直徑（常見手錶螢幕尺寸）
    val screenDiameter: Int = 360,
    // 預設 20px 安全邊距
    val safeMargin: Int = 20,
    // 預設 30px 下巴高度
    val chinHeight: Int = 30,
    // 預設錶冠在 30% 位置
    val crownPosition: Float = 0.3f,
) {
    /**
     * 可用半徑 = (螢幕直徑 / 2) - 安全邊距
     */
    val usableRadius: Int
        get() = (screenDiameter / 2) - safeMargin

    /**
     * 螢幕中心點座標
     */
    val centerX: Float
        get() = screenDiameter / 2f

    val centerY: Float
        get() = screenDiameter / 2f

    /**
     * 檢查指定點是否在圓形可用區域內
     *
     * @param x X 座標
     * @param y Y 座標
     * @return 如果點在圓形內則返回 true
     */
    fun isPointInCircle(
        x: Float,
        y: Float,
    ): Boolean {
        val dx = x - centerX
        val dy = y - centerY
        return sqrt(dx * dx + dy * dy) <= usableRadius
    }

    /**
     * 檢查矩形區域是否完全在圓形內
     *
     * @param left 矩形左邊界
     * @param top 矩形上邊界
     * @param right 矩形右邊界
     * @param bottom 矩形下邊界
     * @return 如果矩形完全在圓形內則返回 true
     */
    fun isRectangleInCircle(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): Boolean {
        // 檢查矩形四個角點是否都在圓形內
        return isPointInCircle(left, top) &&
            isPointInCircle(right, top) &&
            isPointInCircle(left, bottom) &&
            isPointInCircle(right, bottom)
    }

    /**
     * 計算距離螢幕中心的距離
     *
     * @param x X 座標
     * @param y Y 座標
     * @return 距離中心點的距離
     */
    fun distanceFromCenter(
        x: Float,
        y: Float,
    ): Float {
        val dx = x - centerX
        val dy = y - centerY
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * 檢查點是否在安全觸控區域內（考慮錶冠位置）
     *
     * @param x X 座標
     * @param y Y 座標
     * @return 如果在安全觸控區域內則返回 true
     */
    fun isInSafeTouchArea(
        x: Float,
        y: Float,
    ): Boolean {
        if (!isPointInCircle(x, y)) return false

        // 避開錶冠區域（右側一定範圍）
        val crownAreaStartY = centerY - (screenDiameter * 0.2f)
        val crownAreaEndY = centerY + (screenDiameter * 0.2f)
        val crownAreaX = centerX + (usableRadius * 0.8f)

        if (x > crownAreaX && y >= crownAreaStartY && y <= crownAreaEndY) {
            return false
        }

        return true
    }
}

/**
 * 常用的圓形螢幕配置預設值
 */
object CircularScreenPresets {
    /** 標準 360px 手錶螢幕 */
    val STANDARD_360 =
        CircularScreenConfig(
            screenDiameter = 360,
            safeMargin = 20,
            chinHeight = 30,
        )

    /** 較小的 320px 手錶螢幕 */
    val COMPACT_320 =
        CircularScreenConfig(
            screenDiameter = 320,
            safeMargin = 18,
            chinHeight = 25,
        )

    /** 較大的 480px 手錶螢幕 */
    val LARGE_480 =
        CircularScreenConfig(
            screenDiameter = 480,
            safeMargin = 25,
            chinHeight = 40,
        )
}
