// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.circular

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * SafeAreaCalculator 的單元測試
 *
 * 測試安全區域計算器的各種功能，包括可用區域計算、
 * 最佳位置計算、網格佈局等核心功能。
 */
class SafeAreaCalculatorTest {
    @Test
    fun `測試可用矩形區域計算`() {
        val config =
            CircularScreenConfig(
                screenDiameter = 200,
                safeMargin = 0,
            ) // usableRadius = 100, center = (100, 100)

        val usableArea = SafeAreaCalculator.calculateUsableArea(config)

        // 內接正方形的邊長 = radius * sqrt(2)，半邊長 = radius / sqrt(2)
        val expectedHalfSide = 100f / sqrt(2f) // 約 70.71
        val expectedLeft = 100f - expectedHalfSide
        val expectedTop = 100f - expectedHalfSide
        val expectedRight = 100f + expectedHalfSide
        val expectedBottom = 100f + expectedHalfSide

        assertEquals(expectedLeft, usableArea.left, 0.01f)
        assertEquals(expectedTop, usableArea.top, 0.01f)
        assertEquals(expectedRight, usableArea.right, 0.01f)
        assertEquals(expectedBottom, usableArea.bottom, 0.01f)

        // 驗證計算出的矩形確實在圓內
        assertTrue(
            config.isRectangleInCircle(
                usableArea.left,
                usableArea.top,
                usableArea.right,
                usableArea.bottom,
            ),
        )
    }

    @Test
    fun `測試指定長寬比的可用區域計算`() {
        val config =
            CircularScreenConfig(
                screenDiameter = 200,
                safeMargin = 0,
            ) // usableRadius = 100

        // 測試正方形（長寬比 1:1）
        val squareArea = SafeAreaCalculator.calculateUsableAreaWithAspectRatio(config, 1.0f)
        assertEquals(squareArea.width(), squareArea.height(), 0.01f)

        // 測試寬矩形（長寬比 2:1）
        val wideArea = SafeAreaCalculator.calculateUsableAreaWithAspectRatio(config, 2.0f)
        assertEquals(2.0f, wideArea.width() / wideArea.height(), 0.01f)

        // 測試高矩形（長寬比 1:2）
        val tallArea = SafeAreaCalculator.calculateUsableAreaWithAspectRatio(config, 0.5f)
        assertEquals(0.5f, tallArea.width() / tallArea.height(), 0.01f)

        // 驗證計算出的矩形都在圓內
        assertTrue(
            config.isRectangleInCircle(
                squareArea.left,
                squareArea.top,
                squareArea.right,
                squareArea.bottom,
            ),
        )
        assertTrue(
            config.isRectangleInCircle(
                wideArea.left,
                wideArea.top,
                wideArea.right,
                wideArea.bottom,
            ),
        )
        assertTrue(
            config.isRectangleInCircle(
                tallArea.left,
                tallArea.top,
                tallArea.right,
                tallArea.bottom,
            ),
        )
    }

    @Test
    fun `測試矩形可見性檢查`() {
        val config =
            CircularScreenConfig(
                screenDiameter = 200,
                safeMargin = 10,
            ) // usableRadius = 90

        // 測試完全在圓內的小矩形
        val smallRect = RectF(95f, 95f, 105f, 105f)
        assertTrue(SafeAreaCalculator.isRectFullyVisible(smallRect, config))

        // 測試部分超出圓形的矩形
        val largeRect = RectF(50f, 50f, 150f, 150f)
        assertFalse(SafeAreaCalculator.isRectFullyVisible(largeRect, config))

        // 測試剛好在邊界的矩形
        val boundaryRect = RectF(99f, 99f, 101f, 101f)
        assertTrue(SafeAreaCalculator.isRectFullyVisible(boundaryRect, config))
    }

    @Test
    fun `測試最佳位置計算`() {
        val config =
            CircularScreenConfig(
                screenDiameter = 200,
                safeMargin = 10,
            ) // usableRadius = 90, center = (100, 100)

        // 測試期望位置已經是最佳的情況
        val (optimalX1, optimalY1) =
            SafeAreaCalculator.calculateOptimalPosition(
                viewWidth = 20f,
                viewHeight = 20f,
                preferredX = 100f,
                preferredY = 100f,
                config = config,
            )
        assertEquals(100f, optimalX1, 0.01f)
        assertEquals(100f, optimalY1, 0.01f)

        // 測試需要調整位置的情況（期望位置會導致視圖超出圓形）
        val (optimalX2, optimalY2) =
            SafeAreaCalculator.calculateOptimalPosition(
                viewWidth = 40f,
                viewHeight = 40f,
                preferredX = 180f, // 太靠右
                preferredY = 100f,
                config = config,
            )

        // 最佳位置應該比期望位置更靠近圓心
        assertTrue(optimalX2 < 180f)
        assertEquals(100f, optimalY2, 0.01f)

        // 驗證計算出的位置確實能讓視圖完全在圓內
        val resultRect =
            RectF(
                optimalX2 - 20f,
                optimalY2 - 20f,
                optimalX2 + 20f,
                optimalY2 + 20f,
            )
        assertTrue(SafeAreaCalculator.isRectFullyVisible(resultRect, config))
    }

    @Test
    fun `測試角度位置計算`() {
        val config =
            CircularScreenConfig(
                screenDiameter = 200,
                safeMargin = 0,
            ) // usableRadius = 100, center = (100, 100)

        // 測試 0 度（正右方）
        val (x0, y0) = SafeAreaCalculator.getPositionAtAngle(config, 0f, 1.0f)
        assertEquals(200f, x0, 0.01f) // center + radius
        assertEquals(100f, y0, 0.01f) // center

        // 測試 90 度（正上方）
        val (x90, y90) = SafeAreaCalculator.getPositionAtAngle(config, 90f, 1.0f)
        assertEquals(100f, x90, 0.01f) // center
        assertEquals(0f, y90, 0.01f) // center - radius

        // 測試 180 度（正左方）
        val (x180, y180) = SafeAreaCalculator.getPositionAtAngle(config, 180f, 1.0f)
        assertEquals(0f, x180, 0.01f) // center - radius
        assertEquals(100f, y180, 0.01f) // center

        // 測試 270 度（正下方）
        val (x270, y270) = SafeAreaCalculator.getPositionAtAngle(config, 270f, 1.0f)
        assertEquals(100f, x270, 0.01f) // center
        assertEquals(200f, y270, 0.01f) // center + radius

        // 測試距離比例
        val (x50, y50) = SafeAreaCalculator.getPositionAtAngle(config, 0f, 0.5f)
        assertEquals(150f, x50, 0.01f) // center + radius * 0.5
        assertEquals(100f, y50, 0.01f) // center
    }

    @Test
    fun `測試網格位置計算`() {
        val config =
            CircularScreenConfig(
                screenDiameter = 200,
                safeMargin = 10,
            ) // usableRadius = 90

        // 測試 3x3 網格
        val gridPositions =
            SafeAreaCalculator.calculateGridPositions(
                config = config,
                gridSize = 3,
                elementSize = 20f,
            )

        // 應該生成 9 個位置（某些位置可能因為超出圓形而被排除）
        assertTrue(gridPositions.size <= 9)
        assertTrue(gridPositions.isNotEmpty())

        // 驗證所有位置都在圓內
        gridPositions.forEach { (x, y) ->
            assertTrue("Position ($x, $y) should be within circle", config.isPointInCircle(x, y))
        }

        // 測試 1x1 網格（應該只有圓心位置）
        val singlePosition =
            SafeAreaCalculator.calculateGridPositions(
                config = config,
                gridSize = 1,
                elementSize = 20f,
            )
        assertEquals(1, singlePosition.size)
        val (centerX, centerY) = singlePosition.first()
        assertEquals(config.centerX, centerX, 5f) // 允許一些誤差
        assertEquals(config.centerY, centerY, 5f)
    }

    @Test
    fun `測試邊界條件和異常處理`() {
        val config =
            CircularScreenConfig(
                screenDiameter = 100,
                safeMargin = 40,
            ) // usableRadius = 10，非常小的可用區域

        // 測試極小可用區域的情況
        val tinyUsableArea = SafeAreaCalculator.calculateUsableArea(config)
        assertTrue(tinyUsableArea.width() > 0)
        assertTrue(tinyUsableArea.height() > 0)

        // 測試大視圖在小圓形中的位置計算
        val (optimalX, optimalY) =
            SafeAreaCalculator.calculateOptimalPosition(
                viewWidth = 100f, // 比可用區域大很多
                viewHeight = 100f,
                preferredX = 50f,
                preferredY = 50f,
                config = config,
            )

        // 應該返回圓心位置（最佳的妥協方案）
        assertEquals(config.centerX, optimalX, 5f)
        assertEquals(config.centerY, optimalY, 5f)
    }

    @Test
    fun `測試零尺寸和負值處理`() {
        val config = CircularScreenConfig(screenDiameter = 200, safeMargin = 0)

        // 測試零尺寸視圖
        val (x, y) =
            SafeAreaCalculator.calculateOptimalPosition(
                viewWidth = 0f,
                viewHeight = 0f,
                preferredX = 100f,
                preferredY = 100f,
                config = config,
            )
        assertEquals(100f, x, 0.01f)
        assertEquals(100f, y, 0.01f)

        // 測試零長寬比
        val zeroAspectArea = SafeAreaCalculator.calculateUsableAreaWithAspectRatio(config, 0f)
        assertTrue(zeroAspectArea.width() >= 0)
        assertTrue(zeroAspectArea.height() >= 0)
    }
}
