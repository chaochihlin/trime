// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import timber.log.Timber

/**
 * 鍵盤檢視優化的記憶體壓力監控
 */
class MemoryMonitor {
    private val maxBufferSize = 10 * 1024 * 1024 // 10MB
    private var lastMemoryCheck = 0L
    private val memoryCheckInterval = 5000L // 5 秒

    // 記憶體壓力閾值
    private val defaultMemoryThreshold = 0.8 // 一般裝置 80%
    private val wearOSMemoryThreshold = 0.9 // Wear OS 裝置 90%

    // 快取 Wear OS 檢測結果
    private val isWearOSDevice: Boolean = true

    /**
     * 檢查系統是否處於記憶體壓力狀態
     */
    fun checkMemoryPressure(): Boolean {
        val currentTime = System.currentTimeMillis()

        // 避免頻繁的記憶體檢查以提升效能
        if (currentTime - lastMemoryCheck < memoryCheckInterval) {
            return false
        }

        lastMemoryCheck = currentTime

        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsageRatio = usedMemory.toDouble() / maxMemory

        val threshold = if (isFennecWatch()) wearOSMemoryThreshold else defaultMemoryThreshold
        val isUnderPressure = memoryUsageRatio > threshold

        if (isUnderPressure) {
            val deviceType = if (isFennecWatch()) "Wear OS" else "一般"
            Timber.w(
                "記憶體監控器: ${deviceType}裝置檢測到記憶體壓力 - ${(memoryUsageRatio * 100).toInt()}% 已使用 (閾值: ${(threshold * 100).toInt()}%)",
            )
            logMemoryStats(runtime)
        } else {
            Timber.d("記憶體監控器: 記憶體使用率: ${(memoryUsageRatio * 100).toInt()}%")
        }

        return isUnderPressure
    }

    /**
     * 檢查緩衝區大小是否在合理範圍內
     */
    fun isBufferSizeReasonable(bufferMemoryUsage: Long): Boolean {
        val isReasonable = bufferMemoryUsage < maxBufferSize

        if (!isReasonable) {
            Timber.w("記憶體監控器: 緩衝區大小過大: ${bufferMemoryUsage / 1024 / 1024}MB")
        }

        return isReasonable
    }

    /**
     * 取得目前記憶體使用量（單位：MB）
     */
    fun getCurrentMemoryUsageMB(): Double {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        return usedMemory / (1024.0 * 1024.0)
    }

    /**
     * 當記憶體使用率高時建議執行垃圾回收
     */
    fun suggestGC(): Boolean {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()

        val shouldSuggestGC = usedMemory.toDouble() / maxMemory > 0.7

        if (shouldSuggestGC) {
            Timber.d("記憶體監控器: 建議執行 GC - 記憶體使用率偏高")
            System.gc() // 僅為建議，不保證執行
        }

        return shouldSuggestGC
    }

    /**
     * 記錄詳細的記憶體統計資訊
     */
    private fun logMemoryStats(runtime: Runtime) {
        val totalMemory = runtime.totalMemory() / (1024 * 1024)
        val freeMemory = runtime.freeMemory() / (1024 * 1024)
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val usedMemory = totalMemory - freeMemory

        Timber.i("記憶體監控器統計資訊:")
        Timber.i("  已使用: ${usedMemory}MB")
        Timber.i("  可用: ${freeMemory}MB")
        Timber.i("  總計: ${totalMemory}MB")
        Timber.i("  最大: ${maxMemory}MB")
    }

    /**
     * 檢查是否為 Wear OS 裝置
     */
    private fun isFennecWatch(): Boolean {
//        if (isWearOSDevice != null) {
//            return isWearOSDevice!!
//        }
//        isWearOSDevice = context?.packageManager?.hasSystemFeature(PackageManager.FEATURE_WATCH) ?: false
//        if (isWearOSDevice == true) {
//            Timber.i("記憶體監控器: 檢測到 Wear OS 裝置，使用較高的記憶體閾值")
//        }
        return isWearOSDevice
    }

    /**
     * 取得記憶體壓力等級 (0-3)
     * 0: 正常, 1: 中度, 2: 高, 3: 危險
     */
    fun getMemoryPressureLevel(): Int {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val ratio = usedMemory.toDouble() / maxMemory

        return when {
            ratio < 0.6 -> 0 // 正常
            ratio < 0.75 -> 1 // 中度
            ratio < 0.9 -> 2 // 高
            else -> 3 // 危險
        }
    }
}
