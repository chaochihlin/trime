// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import timber.log.Timber

/**
 * 鍵盤顯示時間測量器
 * 測量從系統要求顯示鍵盤到鍵盤完整渲染完成的時間
 */
object KeyboardDisplayTimer {
    // 測量狀態
    private var startTime: Long = 0L
    private var isTimerActive: Boolean = false
    private var currentSessionId: String = ""

    // 統計資料
    private var measurementCount = 0
    private var totalTime = 0L
    private var maxTime = 0L
    private var minTime = Long.MAX_VALUE

    /**
     * 開始測量鍵盤顯示時間
     * @param sessionId 會話標識符，用於避免重複測量
     * @param isFirstTime 是否為首次顯示（影響性能基準）
     */
    fun startMeasurement(
        sessionId: String,
        isFirstTime: Boolean = false,
    ) {
        if (isTimerActive && currentSessionId == sessionId) {
            // 避免重複測量同一會話
            return
        }

        startTime = System.nanoTime()
        isTimerActive = true
        currentSessionId = sessionId

        val displayType = if (isFirstTime) "首次" else "切換"
        Timber.d("⏱️ 鍵盤顯示計時器: 開始測量${displayType}顯示 (會話: $sessionId)")
    }

    /**
     * 結束測量並記錄結果
     * @param sessionId 會話標識符，如果為空則使用當前活躍的會話
     * @param renderInfo 額外的渲染資訊
     */
    fun endMeasurement(
        sessionId: String = "",
        renderInfo: String = "",
    ) {
        if (!isTimerActive) {
            // 非活躍狀態，忽略
            return
        }

        if (sessionId.isNotEmpty() && currentSessionId != sessionId) {
            // 會話不符，忽略
            return
        }

        val endTime = System.nanoTime()
        val durationNanos = endTime - startTime
        val durationMs = durationNanos / 1_000_000.0

        // 更新統計
        measurementCount++
        totalTime += durationNanos
        maxTime = maxOf(maxTime, durationNanos)
        minTime = minOf(minTime, durationNanos)

        // 重設狀態
        isTimerActive = false
        currentSessionId = ""

        // 記錄結果
        logMeasurementResult(durationMs, renderInfo)

        // 每5次測量輸出統計摘要
        if (measurementCount % 5 == 0) {
            logStatisticsSummary()
        }
    }

    /**
     * 取消當前測量（例如：鍵盤顯示被中斷）
     */
    fun cancelMeasurement() {
        if (isTimerActive) {
            Timber.d("⏱️ 鍵盤顯示計時器: 測量被取消 (會話: $currentSessionId)")
            isTimerActive = false
            currentSessionId = ""
        }
    }

    /**
     * 記錄測量結果
     */
    private fun logMeasurementResult(
        durationMs: Double,
        renderInfo: String,
    ) {
        val formatDuration = String.format("%.2f", durationMs)
        val performanceLevel =
            when {
                durationMs < 100.0 -> "🟢 優秀"
                durationMs < 500.0 -> "🟡 良好"
                durationMs < 1000.0 -> "🟠 普通"
                durationMs < 3000.0 -> "🔴 緩慢"
                else -> "🚨 嚴重延遲"
            }

        Timber.i("⏱️ 鍵盤顯示測量 #$measurementCount: ${formatDuration}ms $performanceLevel")

        if (renderInfo.isNotEmpty()) {
            Timber.d("   渲染資訊: $renderInfo")
        }

        // 記錄異常延遲情況
        if (durationMs > 1000.0) {
            Timber.w("⚠️ 檢測到鍵盤顯示延遲超過1秒: ${formatDuration}ms")

            // 記錄記憶體狀況以供分析
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val memoryRatio = (usedMemory.toDouble() / maxMemory * 100).toInt()

            Timber.w("   當時記憶體使用率: $memoryRatio% (${usedMemory / 1024 / 1024}MB / ${maxMemory / 1024 / 1024}MB)")
        }
    }

    /**
     * 記錄統計摘要
     */
    private fun logStatisticsSummary() {
        val avgTime = totalTime / measurementCount / 1_000_000.0
        val maxTimeMs = maxTime / 1_000_000.0
        val minTimeMs = minTime / 1_000_000.0

        Timber.i("📊 鍵盤顯示統計摘要 (共${measurementCount}次測量):")
        Timber.i("   平均: ${String.format("%.2f", avgTime)}ms")
        Timber.i("   最快: ${String.format("%.2f", minTimeMs)}ms")
        Timber.i("   最慢: ${String.format("%.2f", maxTimeMs)}ms")

        // 效能評估
        when {
            avgTime < 200.0 -> Timber.i("   📈 整體效能: 優秀")
            avgTime < 500.0 -> Timber.i("   📈 整體效能: 良好")
            avgTime < 1000.0 -> Timber.i("   📈 整體效能: 需要改善")
            else -> Timber.w("   📈 整體效能: 嚴重問題，需要優化")
        }
    }

    /**
     * 重設所有統計資料
     */
    fun resetStatistics() {
        measurementCount = 0
        totalTime = 0L
        maxTime = 0L
        minTime = Long.MAX_VALUE

        Timber.i("🔄 鍵盤顯示統計已重設")
    }

    /**
     * 取得當前統計資料
     */
    fun getStatistics(): Map<String, Any> =
        if (measurementCount > 0) {
            mapOf(
                "count" to measurementCount,
                "avgMs" to (totalTime / measurementCount / 1_000_000.0),
                "maxMs" to (maxTime / 1_000_000.0),
                "minMs" to (minTime / 1_000_000.0),
            )
        } else {
            emptyMap()
        }
}
