// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.graphics.Paint
import android.graphics.Typeface
import android.util.LruCache
import timber.log.Timber

/**
 * Paint 物件和渲染狀態快取，減少物件建立開銷
 */
class RenderStateCache {
    private val paintCache = LruCache<String, Paint>(20)
    private val colorCache = LruCache<String, Int>(30)
    private var cacheHits = 0
    private var cacheMisses = 0

    /**
     * 取得具有指定屬性的快取 Paint 物件
     */
    fun getCachedPaint(
        textSize: Float,
        color: Int,
        typeface: Typeface? = null,
    ): Paint {
        val key = "paint_${textSize}_${color}_${typeface?.hashCode() ?: 0}"

        val cachedPaint = paintCache.get(key)
        return if (cachedPaint != null) {
            cacheHits++
            cachedPaint
        } else {
            cacheMisses++
            createAndCachePaint(key, textSize, color, typeface)
        }
    }

    /**
     * 建立新的 Paint 物件並快取
     */
    private fun createAndCachePaint(
        key: String,
        textSize: Float,
        color: Int,
        typeface: Typeface?,
    ): Paint {
        val paint =
            Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                this.textSize = textSize
                this.color = color
                typeface?.let { this.typeface = it }
            }

        paintCache.put(key, paint)
        return paint
    }

    /**
     * 使用鍵值快取顏色值
     */
    fun cacheColor(
        key: String,
        color: Int,
    ) {
        colorCache.put(key, color)
    }

    /**
     * 取得快取的顏色值
     */
    fun getCachedColor(key: String): Int? {
        val color = colorCache.get(key)
        if (color != null) {
            cacheHits++
        } else {
            cacheMisses++
        }
        return color
    }

    /**
     * 取得快取命中率以進行效能監控
     */
    fun getCacheHitRate(): Double {
        val total = cacheHits + cacheMisses
        return if (total > 0) cacheHits.toDouble() / total else 0.0
    }

    /**
     * 清除所有快取
     */
    fun evictAll() {
        paintCache.evictAll()
        colorCache.evictAll()
        Timber.d("渲染狀態快取: 所有快取已清除")
    }

    /**
     * 記錄快取統計資訊
     */
    fun logCacheStats() {
        val hitRate = getCacheHitRate()
        val total = cacheHits + cacheMisses

        if (total > 100) { // 只有在充分使用後才記錄
            Timber.d("渲染狀態快取: 命中率: ${(hitRate * 100).toInt()}% ($cacheHits/$total)")

            if (hitRate < 0.5) {
                Timber.w("渲染狀態快取: 偵測到低快取命中率: ${(hitRate * 100).toInt()}%")
            }
        }
    }

    /**
     * 重設快取統計資訊
     */
    fun resetStats() {
        cacheHits = 0
        cacheMisses = 0
    }
}
