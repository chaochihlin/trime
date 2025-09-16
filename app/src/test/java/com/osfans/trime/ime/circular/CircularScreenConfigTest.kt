// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.circular

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

/**
 * CircularScreenConfig 的單元測試
 *
 * 測試圓形螢幕配置的各種幾何計算功能，確保座標計算、
 * 區域檢查等核心功能的正確性。
 */
class CircularScreenConfigTest {

    @Test
    fun `測試預設配置數值正確`() {
        val config = CircularScreenConfig()

        assertEquals(360, config.screenDiameter)
        assertEquals(20, config.safeMargin)
        assertEquals(30, config.chinHeight)
        assertEquals(0.3f, config.crownPosition, 0.01f)
        assertEquals(160, config.usableRadius) // (360/2) - 20 = 160
    }

    @Test
    fun `測試圓心座標計算正確`() {
        val config = CircularScreenConfig(screenDiameter = 400)

        assertEquals(200f, config.centerX, 0.01f)
        assertEquals(200f, config.centerY, 0.01f)
    }

    @Test
    fun `測試點在圓形內的判斷`() {
        val config = CircularScreenConfig(
            screenDiameter = 200,
            safeMargin = 10
        ) // usableRadius = 90

        // 測試圓心點
        assertTrue(config.isPointInCircle(100f, 100f))

        // 測試圓周附近的點
        assertTrue(config.isPointInCircle(190f, 100f)) // 距離 = 90，剛好在邊界
        assertFalse(config.isPointInCircle(191f, 100f)) // 距離 > 90，超出邊界

        // 測試對角線方向的點
        val diagonalDistance = 90f / sqrt(2f) // 約 63.64
        assertTrue(config.isPointInCircle(
            100f + diagonalDistance,
            100f + diagonalDistance
        ))
        assertFalse(config.isPointInCircle(
            100f + diagonalDistance + 1f,
            100f + diagonalDistance + 1f
        ))
    }

    @Test
    fun `測試矩形完全在圓形內的判斷`() {
        val config = CircularScreenConfig(
            screenDiameter = 200,
            safeMargin = 10
        ) // usableRadius = 90, center = (100, 100)

        // 測試小矩形在圓心附近
        assertTrue(config.isRectangleInCircle(95f, 95f, 105f, 105f))

        // 測試較大矩形，四個角點都在圓內
        val halfSide = 90f / sqrt(2f) - 5f // 確保在圓內
        assertTrue(config.isRectangleInCircle(
            100f - halfSide, 100f - halfSide,
            100f + halfSide, 100f + halfSide
        ))

        // 測試超出圓形的矩形
        assertFalse(config.isRectangleInCircle(50f, 50f, 150f, 150f))
    }

    @Test
    fun `測試距離中心點的計算`() {
        val config = CircularScreenConfig(screenDiameter = 200)

        // 測試圓心點
        assertEquals(0f, config.distanceFromCenter(100f, 100f), 0.01f)

        // 測試水平方向的點
        assertEquals(50f, config.distanceFromCenter(150f, 100f), 0.01f)

        // 測試對角線方向的點
        val expectedDistance = sqrt(50f * 50f + 50f * 50f)
        assertEquals(expectedDistance, config.distanceFromCenter(150f, 150f), 0.01f)
    }

    @Test
    fun `測試安全觸控區域判斷`() {
        val config = CircularScreenConfig(
            screenDiameter = 200,
            safeMargin = 10
        )

        // 測試圓心區域（應該是安全的）
        assertTrue(config.isInSafeTouchArea(100f, 100f))

        // 測試左側區域（遠離錶冠，應該是安全的）
        assertTrue(config.isInSafeTouchArea(50f, 100f))

        // 測試右側錶冠區域（可能不安全，取決於具體實作）
        val crownAreaX = 100f + (90f * 0.8f) // centerX + usableRadius * 0.8
        // 這個測試可能需要根據實際的錶冠避讓邏輯調整
    }

    @Test
    fun `測試預設配置的正確性`() {
        // 測試標準 360px 配置
        val standard360 = CircularScreenPresets.STANDARD_360
        assertEquals(360, standard360.screenDiameter)
        assertEquals(20, standard360.safeMargin)
        assertEquals(160, standard360.usableRadius)

        // 測試緊湊 320px 配置
        val compact320 = CircularScreenPresets.COMPACT_320
        assertEquals(320, compact320.screenDiameter)
        assertEquals(18, compact320.safeMargin)
        assertEquals(142, compact320.usableRadius) // (320/2) - 18 = 142

        // 測試大螢幕 480px 配置
        val large480 = CircularScreenPresets.LARGE_480
        assertEquals(480, large480.screenDiameter)
        assertEquals(25, large480.safeMargin)
        assertEquals(215, large480.usableRadius) // (480/2) - 25 = 215
    }

    @Test
    fun `測試邊界條件`() {
        val config = CircularScreenConfig(
            screenDiameter = 100,
            safeMargin = 0
        ) // usableRadius = 50

        // 測試零尺寸矩形
        assertTrue(config.isRectangleInCircle(50f, 50f, 50f, 50f))

        // 測試負座標
        assertTrue(config.isPointInCircle(0f, 50f)) // 距離 = 50，剛好在邊界
        assertFalse(config.isPointInCircle(-1f, 50f)) // 超出邊界

        // 測試極大座標
        assertFalse(config.isPointInCircle(1000f, 1000f))
    }

    @Test
    fun `測試配置變更的影響`() {
        var config = CircularScreenConfig(screenDiameter = 200, safeMargin = 10)

        // 初始狀態：點在圓內
        assertTrue(config.isPointInCircle(180f, 100f))

        // 增加安全邊距後：同一點可能超出範圍
        config = config.copy(safeMargin = 50)
        assertEquals(50, config.usableRadius) // (200/2) - 50 = 50
        assertFalse(config.isPointInCircle(180f, 100f)) // 距離 = 80 > 50
    }
}