// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.circular

import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 安全區域計算器
 *
 * 為圓形螢幕設備提供安全區域計算和視圖可見性檢查功能。
 * 用於確保 UI 元件在圓形螢幕內正確顯示，避免內容被裁切或位於不可見區域。
 *
 * 主要功能：
 * - 計算可用顯示區域
 * - 檢查視圖和座標點的可見性
 * - 提供安全佈局建議
 * - 支援多種圓形螢幕配置
 */
object SafeAreaCalculator {

    /**
     * 計算圓形螢幕的可用矩形區域
     *
     * 根據圓形螢幕配置計算出最大的可用矩形區域，
     * 確保矩形完全位於圓形內且符合安全邊距要求。
     *
     * @param config 圓形螢幕配置
     * @return 可用的矩形區域
     */
    fun calculateUsableArea(config: CircularScreenConfig): RectF {
        val center = config.centerX
        val radius = config.usableRadius.toFloat()

        // 計算內接正方形的邊長 = radius * sqrt(2)
        val squareHalfSide = radius / sqrt(2f)

        return RectF(
            center - squareHalfSide,    // left
            center - squareHalfSide,    // top
            center + squareHalfSide,    // right
            center + squareHalfSide     // bottom
        )
    }

    /**
     * 計算圓形螢幕的可用矩形區域（考慮長寬比）
     *
     * 根據指定的長寬比計算可用矩形區域，適用於需要特定比例的 UI 佈局。
     *
     * @param config 圓形螢幕配置
     * @param aspectRatio 期望的長寬比（寬度/高度）
     * @return 指定長寬比的可用矩形區域
     */
    fun calculateUsableAreaWithAspectRatio(
        config: CircularScreenConfig,
        aspectRatio: Float
    ): RectF {
        val center = config.centerX
        val radius = config.usableRadius.toFloat()

        // 根據長寬比和圓形半徑計算矩形尺寸
        val width: Float
        val height: Float

        if (aspectRatio >= 1.0f) {
            // 寬度較大的情況
            height = radius * 2f / sqrt(1 + aspectRatio * aspectRatio)
            width = height * aspectRatio
        } else {
            // 高度較大的情況
            width = radius * 2f / sqrt(1 + 1f / (aspectRatio * aspectRatio))
            height = width / aspectRatio
        }

        val halfWidth = width / 2f
        val halfHeight = height / 2f

        return RectF(
            center - halfWidth,
            center - halfHeight,
            center + halfWidth,
            center + halfHeight
        )
    }

    /**
     * 檢查視圖是否完全在圓形可見區域內
     *
     * @param view 要檢查的視圖
     * @param config 圓形螢幕配置
     * @return 如果視圖完全可見則返回 true
     */
    fun isViewFullyVisible(view: View, config: CircularScreenConfig): Boolean {
        val bounds = Rect()
        view.getGlobalVisibleRect(bounds)

        // 檢查視圖的四個角點是否都在圓形內
        return config.isPointInCircle(bounds.left.toFloat(), bounds.top.toFloat()) &&
                config.isPointInCircle(bounds.right.toFloat(), bounds.top.toFloat()) &&
                config.isPointInCircle(bounds.left.toFloat(), bounds.bottom.toFloat()) &&
                config.isPointInCircle(bounds.right.toFloat(), bounds.bottom.toFloat())
    }

    /**
     * 檢查矩形區域是否完全在圓形可見區域內
     *
     * @param rect 要檢查的矩形區域
     * @param config 圓形螢幕配置
     * @return 如果矩形完全可見則返回 true
     */
    fun isRectFullyVisible(rect: RectF, config: CircularScreenConfig): Boolean {
        return config.isRectangleInCircle(rect.left, rect.top, rect.right, rect.bottom)
    }

    /**
     * 計算視圖在圓形螢幕內的最佳位置
     *
     * 根據視圖尺寸和期望位置，計算出在圓形螢幕內的最佳顯示位置，
     * 確保視圖完全可見且儘可能接近期望位置。
     *
     * @param viewWidth 視圖寬度
     * @param viewHeight 視圖高度
     * @param preferredX 期望的 X 位置
     * @param preferredY 期望的 Y 位置
     * @param config 圓形螢幕配置
     * @return 調整後的最佳位置座標 Pair(x, y)
     */
    fun calculateOptimalPosition(
        viewWidth: Float,
        viewHeight: Float,
        preferredX: Float,
        preferredY: Float,
        config: CircularScreenConfig
    ): Pair<Float, Float> {
        val halfWidth = viewWidth / 2f
        val halfHeight = viewHeight / 2f

        // 檢查期望位置是否可行
        val preferredRect = RectF(
            preferredX - halfWidth,
            preferredY - halfHeight,
            preferredX + halfWidth,
            preferredY + halfHeight
        )

        if (isRectFullyVisible(preferredRect, config)) {
            return Pair(preferredX, preferredY)
        }

        // 如果期望位置不可行，計算最近的可行位置
        val centerX = config.centerX
        val centerY = config.centerY
        val radius = config.usableRadius.toFloat()

        // 計算從圓心到期望位置的方向
        val dx = preferredX - centerX
        val dy = preferredY - centerY
        val distance = sqrt(dx * dx + dy * dy)

        if (distance == 0f) {
            // 期望位置就是圓心，直接返回
            return Pair(centerX, centerY)
        }

        // 計算方向單位向量
        val unitX = dx / distance
        val unitY = dy / distance

        // 計算視圖矩形在此方向上能放置的最遠距離
        val maxDistance = calculateMaxDistanceForRect(
            halfWidth, halfHeight, unitX, unitY, radius
        )

        val optimalX = centerX + unitX * maxDistance
        val optimalY = centerY + unitY * maxDistance

        return Pair(optimalX, optimalY)
    }

    /**
     * 計算矩形在指定方向上的最大可放置距離
     */
    private fun calculateMaxDistanceForRect(
        halfWidth: Float,
        halfHeight: Float,
        unitX: Float,
        unitY: Float,
        radius: Float
    ): Float {
        // 計算矩形四個角點相對於中心的位置
        val corners = arrayOf(
            Pair(-halfWidth, -halfHeight),
            Pair(halfWidth, -halfHeight),
            Pair(-halfWidth, halfHeight),
            Pair(halfWidth, halfHeight)
        )

        var minDistance = Float.MAX_VALUE

        for ((cornerX, cornerY) in corners) {
            // 角點在移動後的絕對位置 = (centerX + unitX * d + cornerX, centerY + unitY * d + cornerY)
            // 要求角點在圓內: (unitX * d + cornerX)² + (unitY * d + cornerY)² ≤ radius²

            // 展開: d²(unitX² + unitY²) + 2d(unitX*cornerX + unitY*cornerY) + (cornerX² + cornerY²) ≤ radius²
            // 由於 unitX² + unitY² = 1，簡化為: d² + 2d(unitX*cornerX + unitY*cornerY) + (cornerX² + cornerY²) ≤ radius²

            val a = 1f // unitX² + unitY² = 1
            val b = 2f * (unitX * cornerX + unitY * cornerY)
            val c = cornerX * cornerX + cornerY * cornerY - radius * radius

            // 解二次方程 ad² + bd + c ≤ 0，求 d 的最大值
            val discriminant = b * b - 4 * a * c

            if (discriminant >= 0) {
                val sqrtDiscriminant = sqrt(discriminant)
                val d1 = (-b - sqrtDiscriminant) / (2 * a)
                val d2 = (-b + sqrtDiscriminant) / (2 * a)

                // 取正值中的較小者（最嚴格的限制）
                val maxD = if (d1 > 0 && d2 > 0) minOf(d1, d2) else maxOf(d1, d2)
                if (maxD > 0) {
                    minDistance = minOf(minDistance, maxD)
                }
            }
        }

        return if (minDistance == Float.MAX_VALUE) 0f else maxOf(0f, minDistance)
    }

    /**
     * 計算圓形區域內指定角度位置的座標
     *
     * @param config 圓形螢幕配置
     * @param angleInDegrees 角度（0度為正右方，90度為正上方）
     * @param distanceRatio 距離比例（0.0為圓心，1.0為圓邊）
     * @return 計算得出的座標 Pair(x, y)
     */
    fun getPositionAtAngle(
        config: CircularScreenConfig,
        angleInDegrees: Float,
        distanceRatio: Float
    ): Pair<Float, Float> {
        val angleInRadians = Math.toRadians(angleInDegrees.toDouble())
        val distance = config.usableRadius * distanceRatio

        val x = config.centerX + distance * cos(angleInRadians).toFloat()
        val y = config.centerY - distance * sin(angleInRadians).toFloat() // Y軸向下為正

        return Pair(x, y)
    }

    /**
     * 計算適合圓形佈局的網格位置
     *
     * 為圓形螢幕計算網格佈局的元素位置，確保所有元素都在可見區域內。
     *
     * @param config 圓形螢幕配置
     * @param gridSize 網格尺寸（如 3x3 網格）
     * @param elementSize 單個元素的尺寸
     * @return 網格位置列表，每個位置為 Pair(x, y)
     */
    fun calculateGridPositions(
        config: CircularScreenConfig,
        gridSize: Int,
        elementSize: Float
    ): List<Pair<Float, Float>> {
        val positions = mutableListOf<Pair<Float, Float>>()
        val usableArea = calculateUsableArea(config)

        // 計算網格間距
        val availableWidth = usableArea.width() - elementSize
        val availableHeight = usableArea.height() - elementSize
        val spacingX = if (gridSize > 1) availableWidth / (gridSize - 1) else 0f
        val spacingY = if (gridSize > 1) availableHeight / (gridSize - 1) else 0f

        // 計算起始位置
        val startX = usableArea.left + elementSize / 2f
        val startY = usableArea.top + elementSize / 2f

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val x = startX + col * spacingX
                val y = startY + row * spacingY

                // 確保位置在圓形內
                if (config.isPointInCircle(x, y)) {
                    positions.add(Pair(x, y))
                }
            }
        }

        return positions
    }
}