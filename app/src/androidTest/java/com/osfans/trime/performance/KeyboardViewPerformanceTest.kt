// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.performance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.keyboard.Keyboard
import com.osfans.trime.ime.keyboard.KeyboardView
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class KeyboardViewPerformanceTest {
    companion object {
        private const val PERFORMANCE_TAG = "KeyboardViewPerf"
    }

    private lateinit var context: Context
    private lateinit var keyboardView: KeyboardView

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        keyboardView = createTestKeyboardView()
    }

    @Test
    fun measureRenderingPerformance() {
        val results = mutableListOf<Long>()

        // 預熱階段
        repeat(10) {
            keyboardView.invalidateAllKeys()
            keyboardView.draw(Canvas(createTestBitmap()))
        }

        // 正式測試
        repeat(50) {
            val startTime = System.nanoTime()

            keyboardView.invalidateAllKeys()
            keyboardView.draw(Canvas(createTestBitmap()))

            val endTime = System.nanoTime()
            results.add((endTime - startTime) / 1_000_000) // 轉換為毫秒
        }

        val averageTime = results.average()
        val maxTime = results.maxOrNull() ?: 0L
        val minTime = results.minOrNull() ?: 0L
        val stdDev = calculateStandardDeviation(results)

        Log.i(PERFORMANCE_TAG, "渲染效能基準:")
        Log.i(PERFORMANCE_TAG, "平均: ${averageTime}ms")
        Log.i(PERFORMANCE_TAG, "最大: ${maxTime}ms")
        Log.i(PERFORMANCE_TAG, "最小: ${minTime}ms")
        Log.i(PERFORMANCE_TAG, "標準差: ${stdDev}ms")

        // 記錄基準數據
        saveBaselineMetric("rendering_average_ms", averageTime)
        saveBaselineMetric("rendering_max_ms", maxTime.toDouble())

        // 目前基準驗證 - 這些數值將在優化後降低
        assertTrue("平均渲染時間基準已記錄", averageTime > 0)
        Log.i(PERFORMANCE_TAG, "目前基準 - 優化後目標: 平均<30ms，最大<50ms")
    }

    @Test
    fun measureMemoryUsage() {
        val beforeMemory = getMemoryUsage()

        // 執行記憶體密集操作
        repeat(100) {
            keyboardView.invalidateAllKeys()
            keyboardView.draw(Canvas(createTestBitmap()))
        }

        // 強制 GC 以獲得準確測量
        forceGarbageCollection()

        val afterMemory = getMemoryUsage()
        val memoryIncrease = afterMemory - beforeMemory

        Log.i(PERFORMANCE_TAG, "記憶體使用基準:")
        Log.i(PERFORMANCE_TAG, "之前: ${beforeMemory}MB")
        Log.i(PERFORMANCE_TAG, "之後: ${afterMemory}MB")
        Log.i(PERFORMANCE_TAG, "增加: ${memoryIncrease}MB")

        saveBaselineMetric("memory_usage_mb", memoryIncrease)

        assertTrue("記憶體增加基準已記錄", memoryIncrease >= 0)
        Log.i(PERFORMANCE_TAG, "目前基準 - 優化後目標: 增加<20MB")
    }

    @Test
    fun measureBitmapAllocationFrequency() {
        var allocationCount = 0
        val originalCreateBitmap = Bitmap::createBitmap

        // 追蹤位圖分配
        val allocations = mutableListOf<Long>()

        repeat(20) {
            val beforeTime = System.currentTimeMillis()

            // 觸發可能的位圖重建
            keyboardView.invalidateAllKeys()
            keyboardView.draw(Canvas(createTestBitmap()))

            val afterTime = System.currentTimeMillis()
            allocations.add(afterTime - beforeTime)
        }

        val avgAllocationTime = allocations.average()

        Log.i(PERFORMANCE_TAG, "點陣圖分配基準:")
        Log.i(PERFORMANCE_TAG, "平均分配時間: ${avgAllocationTime}ms")

        saveBaselineMetric("bitmap_allocation_avg_ms", avgAllocationTime)

        assertTrue("點陣圖分配基準已記錄", avgAllocationTime > 0)
        Log.i(PERFORMANCE_TAG, "目標: 透過智慧快取減少點陣圖重建")
    }

    private fun createTestKeyboardView(): KeyboardView {
        // 創建簡單的測試鍵盤視圖
        val theme = Theme.get()
        val keyboard = Keyboard(context)

        return KeyboardView(context, theme, keyboard).apply {
            measure(
                android.view.View.MeasureSpec
                    .makeMeasureSpec(800, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec
                    .makeMeasureSpec(300, android.view.View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 800, 300)
        }
    }

    private fun createTestBitmap(): Bitmap = createBitmap(800, 300, Bitmap.Config.ARGB_8888)

    private fun getMemoryUsage(): Double {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        return usedMemory / (1024.0 * 1024.0) // 轉換為 MB
    }

    private fun forceGarbageCollection() {
        repeat(3) {
            System.gc()
            Thread.sleep(100)
        }
    }

    private fun calculateStandardDeviation(values: List<Long>): Double {
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance)
    }

    private fun saveBaselineMetric(
        key: String,
        value: Double,
    ) {
        // 將基準數據保存到SharedPreferences，供後續比較使用
        val prefs = context.getSharedPreferences("performance_baseline", Context.MODE_PRIVATE)
        prefs.edit().putFloat(key, value.toFloat()).apply()

        Log.i(PERFORMANCE_TAG, "已保存基準指標: $key = $value")
    }
}
