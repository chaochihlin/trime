// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import timber.log.Timber

/**
 * KeyboardView 的智慧點陣圖快取，最小化記憶體使用並提升效能
 */
class SmartBitmapCache {
    private var currentBuffer: Bitmap? = null
    private var bufferSize: Pair<Int, Int>? = null
    private var bufferDirty = true

    /**
     * 取得快取的點陣圖緩衝區，只有在必要時才建立或重新建立
     */
    fun getBuffer(
        width: Int,
        height: Int,
    ): Bitmap {
        if (needsRecreation(width, height)) {
            recreateBuffer(width, height)
        }
        return currentBuffer!!
    }

    /**
     * 根據大小或回收狀態檢查緩衝區是否需要重新建立
     */
    private fun needsRecreation(
        width: Int,
        height: Int,
    ): Boolean =
        currentBuffer == null ||
            bufferSize != Pair(width, height) ||
            currentBuffer!!.isRecycled

    /**
     * 使用新尺寸重新建立緩衝區
     */
    private fun recreateBuffer(
        width: Int,
        height: Int,
    ) {
        Timber.d("智慧點陣圖快取: 重新建立緩衝區 ${width}x$height")

        // 回收舊緩衝區
        currentBuffer?.let {
            if (!it.isRecycled) {
                it.recycle()
            }
        }

        // 建立新緩衝區
        currentBuffer = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bufferSize = Pair(width, height)
        bufferDirty = true
    }

    /**
     * 標記緩衝區為髒污，需要重新繪製
     */
    fun markDirty() {
        bufferDirty = true
    }

    /**
     * 繪製後標記緩衝區為乾淨
     */
    fun markClean() {
        bufferDirty = false
    }

    /**
     * 檢查緩衝區是否為髒污並需要重新繪製
     */
    fun isDirty(): Boolean = bufferDirty

    /**
     * 在記憶體壓力下如有必要則清除緩衝區
     */
    fun clearIfNecessary() {
        currentBuffer?.let {
            if (!it.isRecycled) {
                Timber.d("智慧點陣圖快取: 因記憶體壓力而清除緩衝區")
                it.recycle()
            }
        }
        currentBuffer = null
        bufferSize = null
        bufferDirty = true
    }

    /**
     * 取得目前緩衝區的記憶體使用量（位元組）
     */
    fun getMemoryUsage(): Long =
        currentBuffer?.let {
            if (!it.isRecycled) it.byteCount.toLong() else 0L
        } ?: 0L
}
