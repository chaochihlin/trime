// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.circular

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.widget.FrameLayout
import kotlin.math.min

/**
 * 圓形螢幕適配容器
 *
 * 自動將子視圖裁切為圓形，適用於智慧手錶等圓形螢幕設備。
 * 基於 FrameLayout 實作，支援標準的佈局參數和子視圖管理。
 *
 * 主要功能：
 * - 自動圓形裁切：根據容器尺寸計算最佳圓形區域
 * - 安全區域管理：支援自定義安全邊距避免內容過於靠近邊緣
 * - 效能優化：使用 Path 裁切，支援硬體加速
 * - 響應式適配：自動適應不同尺寸的圓形螢幕
 *
 * 使用範例：
 * ```xml
 * <com.osfans.trime.ime.circular.CircularContainer
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent"
 *     app:safeMargin="20dp">
 *
 *     <!-- 子視圖會被自動裁切為圓形 -->
 *     <LinearLayout ... />
 *
 * </com.osfans.trime.ime.circular.CircularContainer>
 * ```
 */
class CircularContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** 圓形裁切路徑 */
    private val clipPath = Path()

    /** 當前的圓形螢幕配置 */
    private var screenConfig = CircularScreenPresets.STANDARD_360

    /** 計算得出的圓形半徑 */
    private var calculatedRadius = 0f

    /** 圓形中心點 */
    private var centerX = 0f
    private var centerY = 0f

    /** 是否啟用圓形裁切 */
    var isCircularClippingEnabled = true
        set(value) {
            field = value
            invalidate()
        }

    init {
        // 初始化配置
        updateScreenConfig()

        // 關閉子視圖裁切以確保我們的自定義裁切生效
        clipChildren = false
        clipToPadding = false
    }

    /**
     * 設定圓形螢幕配置
     *
     * @param config 新的圓形螢幕配置
     */
    fun setScreenConfig(config: CircularScreenConfig) {
        screenConfig = config
        updateScreenConfig()
    }

    /**
     * 根據當前尺寸更新圓形配置
     */
    private fun updateScreenConfig() {
        if (width > 0 && height > 0) {
            // 優先使用配置的螢幕直徑，若未設定則使用容器實際尺寸
            val effectiveDiameter = if (screenConfig.screenDiameter > 0) {
                screenConfig.screenDiameter
            } else {
                min(width, height)
            }
            calculatedRadius = (effectiveDiameter / 2f) - screenConfig.safeMargin
            centerX = width / 2f
            centerY = height / 2f


            updateClipPath()
        }
    }

    /**
     * 更新裁切路徑
     */
    private fun updateClipPath() {
        clipPath.reset()
        if (isCircularClippingEnabled && calculatedRadius > 0) {
            clipPath.addCircle(centerX, centerY, calculatedRadius, Path.Direction.CW)
        } else {
            // 如果不啟用圓形裁切，使用整個容器區域
            clipPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateScreenConfig()
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (isCircularClippingEnabled && !clipPath.isEmpty) {
            // 使用路徑裁切繪製
            val saveCount = canvas.save()
            canvas.clipPath(clipPath)
            super.dispatchDraw(canvas)
            canvas.restoreToCount(saveCount)
        } else {
            // 不裁切，正常繪製
            super.dispatchDraw(canvas)
        }
    }

    /**
     * 檢查指定點是否在可用圓形區域內
     *
     * @param x X 座標（相對於此容器）
     * @param y Y 座標（相對於此容器）
     * @return 如果點在圓形內則返回 true
     */
    fun isPointInUsableArea(x: Float, y: Float): Boolean {
        if (!isCircularClippingEnabled) return true

        val dx = x - centerX
        val dy = y - centerY
        return (dx * dx + dy * dy) <= (calculatedRadius * calculatedRadius)
    }

    /**
     * 檢查子視圖是否完全在可用圓形區域內
     *
     * @param child 要檢查的子視圖
     * @return 如果子視圖完全在圓形內則返回 true
     */
    fun isChildViewFullyVisible(child: android.view.View): Boolean {
        if (!isCircularClippingEnabled) return true

        val childLeft = child.left.toFloat()
        val childTop = child.top.toFloat()
        val childRight = child.right.toFloat()
        val childBottom = child.bottom.toFloat()

        // 檢查子視圖的四個角點是否都在圓形內
        return isPointInUsableArea(childLeft, childTop) &&
                isPointInUsableArea(childRight, childTop) &&
                isPointInUsableArea(childLeft, childBottom) &&
                isPointInUsableArea(childRight, childBottom)
    }

    /**
     * 取得可用的圓形區域資訊
     *
     * @return 包含圓形區域資訊的 RectF
     */
    fun getUsableCircularBounds(): RectF {
        return RectF(
            centerX - calculatedRadius,
            centerY - calculatedRadius,
            centerX + calculatedRadius,
            centerY + calculatedRadius
        )
    }

    /**
     * 取得目前的圓形半徑
     */
    fun getCurrentRadius(): Float = calculatedRadius

    /**
     * 取得圓形中心點座標
     */
    fun getCenterPoint(): Pair<Float, Float> = Pair(centerX, centerY)

    /**
     * 調試用：繪製圓形邊界（僅在調試模式下使用）
     */
    fun drawDebugCircle(canvas: Canvas, paint: android.graphics.Paint) {
        if (calculatedRadius > 0) {
            canvas.drawCircle(centerX, centerY, calculatedRadius, paint)
        }
    }
}