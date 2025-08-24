# Watch OS 適配效能測試與驗證框架

## 框架概覽

本文檔定義了 Trime Watch OS 適配過程中的效能測試框架，確保每個優化任務都能被量化驗證和追蹤。

## 測試類別架構

### 1. 基準效能測試
**目的**: 建立優化前的效能基準線，用於比較優化效果

**檔案**: `app/src/androidTest/java/com/osfans/trime/performance/BaselinePerformanceTest.kt`

```kotlin
@RunWith(AndroidJUnit4::class)
class BaselinePerformanceTest {
    
    companion object {
        private const val PERFORMANCE_LOG_TAG = "TrimePerformance"
    }
    
    @Test
    fun measureInitialPerformanceMetrics() {
        val metrics = PerformanceCollector.collectBaseline()
        
        // 記錄基準數據到檔案
        PerformanceReporter.saveBaseline(metrics)
        
        // 輸出到 logcat 供 CI 系統讀取
        Log.i(PERFORMANCE_LOG_TAG, "Baseline metrics: $metrics")
        
        // 基準測試不應該失敗，只記錄數據
        assertTrue("Baseline collection should succeed", metrics.isValid())
    }
    
    @Test 
    fun measureMemoryUsageBaseline() {
        val beforeMemory = getMemoryUsage()
        
        // 執行標準操作流程
        performStandardOperations()
        
        val afterMemory = getMemoryUsage()
        val memoryIncrease = afterMemory - beforeMemory
        
        PerformanceReporter.recordMemoryBaseline(memoryIncrease)
        
        Log.i(PERFORMANCE_LOG_TAG, "Memory baseline: ${memoryIncrease}MB")
    }
    
    private fun performStandardOperations() {
        // 模擬標準使用流程
        // 1. 啟動輸入法
        // 2. 顯示鍵盤
        // 3. 輸入 100 個字符
        // 4. 切換鍵盤佈局 3 次
        // 5. 選擇候選詞 20 次
    }
}
```

### 2. KeyboardView 效能測試
**檔案**: `app/src/androidTest/java/com/osfans/trime/performance/KeyboardViewPerformanceTest.kt`

```kotlin
@RunWith(AndroidJUnit4::class) 
class KeyboardViewPerformanceTest {
    
    private lateinit var keyboardView: KeyboardView
    private val performanceCollector = PerformanceCollector()
    
    @Before
    fun setup() {
        keyboardView = createTestKeyboardView()
    }
    
    @Test
    fun measureRenderingPerformance() {
        val results = mutableListOf<Long>()
        
        // 執行多次測試取平均值
        repeat(50) {
            val startTime = System.nanoTime()
            
            // 觸發鍵盤重繪
            keyboardView.invalidate()
            keyboardView.draw(Canvas(createTestBitmap()))
            
            val endTime = System.nanoTime()
            results.add((endTime - startTime) / 1_000_000) // 轉換為毫秒
        }
        
        val averageTime = results.average()
        val maxTime = results.maxOrNull() ?: 0L
        
        PerformanceReporter.recordRenderingMetrics(averageTime, maxTime)
        
        // 驗證效能標準
        assertTrue("Average rendering time should be under 30ms", averageTime < 30)
        assertTrue("Max rendering time should be under 50ms", maxTime < 50)
    }
    
    @Test
    fun measureMemoryAllocation() {
        val beforeMemory = getMemoryUsage()
        
        // 執行記憶體密集操作
        repeat(100) {
            keyboardView.invalidate()
            keyboardView.draw(Canvas(createTestBitmap()))
        }
        
        // 強制 GC 以獲得準確測量
        System.gc()
        Thread.sleep(100)
        
        val afterMemory = getMemoryUsage()
        val memoryIncrease = afterMemory - beforeMemory
        
        PerformanceReporter.recordMemoryAllocation("KeyboardView", memoryIncrease)
        
        assertTrue("Memory increase should be under 20MB", memoryIncrease < 20)
    }
    
    @Test
    fun measureTouchResponseTime() {
        val responseTimes = mutableListOf<Long>()
        
        repeat(20) {
            val startTime = System.nanoTime()
            
            // 模擬觸控事件
            val motionEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
            keyboardView.onTouchEvent(motionEvent)
            
            val endTime = System.nanoTime()
            responseTimes.add((endTime - startTime) / 1_000_000)
            
            motionEvent.recycle()
        }
        
        val averageResponseTime = responseTimes.average()
        
        PerformanceReporter.recordTouchResponse(averageResponseTime)
        
        assertTrue("Touch response should be under 10ms", averageResponseTime < 10)
    }
}
```

### 3. UI 佈局效能測試
**檔案**: `app/src/androidTest/java/com/osfans/trime/performance/LayoutPerformanceTest.kt`

```kotlin
@RunWith(AndroidJUnit4::class)
class LayoutPerformanceTest {
    
    @Test
    fun measureInputViewLayoutTime() {
        val inputView = createTestInputView()
        val layoutTimes = mutableListList<Long>()
        
        repeat(30) {
            val startTime = System.nanoTime()
            
            inputView.measure(
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
            )
            inputView.layout(0, 0, 1000, 400)
            
            val endTime = System.nanoTime()
            layoutTimes.add((endTime - startTime) / 1_000_000)
        }
        
        val averageTime = layoutTimes.average()
        
        PerformanceReporter.recordLayoutMetrics(averageTime)
        
        assertTrue("Layout should complete under 25ms", averageTime < 25)
    }
    
    @Test
    fun measureViewHierarchyComplexity() {
        val inputView = createTestInputView()
        val depth = ViewHierarchyAnalyzer.calculateDepth(inputView)
        val childCount = ViewHierarchyAnalyzer.getTotalChildCount(inputView)
        
        PerformanceReporter.recordHierarchyMetrics(depth, childCount)
        
        assertTrue("View hierarchy should be under 4 levels", depth < 4)
        assertTrue("Total child views should be under 20", childCount < 20)
    }
}
```

### 4. 記憶體效能測試
**檔案**: `app/src/androidTest/java/com/osfans/trime/performance/MemoryPerformanceTest.kt`

```kotlin
@RunWith(AndroidJUnit4::class)
class MemoryPerformanceTest {
    
    @Test
    fun measureMemoryLeaks() {
        val initialObjects = countObjects()
        
        // 模擬完整生命週期
        repeat(10) {
            val keyboardView = createKeyboardView()
            performOperations(keyboardView)
            destroyKeyboardView(keyboardView)
        }
        
        // 強制 GC 清理
        repeat(3) {
            System.gc()
            Thread.sleep(100)
        }
        
        val finalObjects = countObjects()
        val leakedObjects = finalObjects - initialObjects
        
        PerformanceReporter.recordMemoryLeaks(leakedObjects)
        
        assertTrue("Should not leak objects", leakedObjects < 5)
    }
    
    @Test
    fun measureCacheEfficiency() {
        val colorManager = ColorManager.getInstance()
        
        // 預熱快取
        repeat(100) {
            colorManager.getColor("key_text_color")
            colorManager.getColor("key_background_color")
        }
        
        // 測量快取命中率
        val startTime = System.nanoTime()
        repeat(1000) {
            colorManager.getColor("key_text_color")
        }
        val cachedTime = System.nanoTime() - startTime
        
        // 清空快取後測量
        colorManager.clearCache()
        val startTime2 = System.nanoTime()
        repeat(1000) {
            colorManager.getColor("key_text_color")
        }
        val uncachedTime = System.nanoTime() - startTime2
        
        val efficiency = (uncachedTime - cachedTime).toDouble() / uncachedTime
        
        PerformanceReporter.recordCacheEfficiency(efficiency)
        
        assertTrue("Cache should provide >50% improvement", efficiency > 0.5)
    }
}
```

## 效能數據收集器

### PerformanceCollector 類別
**檔案**: `app/src/androidTest/java/com/osfans/trime/performance/PerformanceCollector.kt`

```kotlin
class PerformanceCollector {
    
    data class PerformanceMetrics(
        val timestamp: Long,
        val memoryUsageMB: Double,
        val renderingTimeMs: Double,
        val layoutTimeMs: Double,
        val touchResponseTimeMs: Double,
        val cacheHitRate: Double,
        val viewHierarchyDepth: Int
    ) {
        fun isValid(): Boolean = memoryUsageMB > 0 && renderingTimeMs > 0
    }
    
    companion object {
        fun collectBaseline(): PerformanceMetrics {
            return PerformanceMetrics(
                timestamp = System.currentTimeMillis(),
                memoryUsageMB = getCurrentMemoryUsage(),
                renderingTimeMs = measureRenderingTime(),
                layoutTimeMs = measureLayoutTime(),
                touchResponseTimeMs = measureTouchResponse(),
                cacheHitRate = measureCacheHitRate(),
                viewHierarchyDepth = measureViewDepth()
            )
        }
        
        private fun getCurrentMemoryUsage(): Double {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            return usedMemory / (1024.0 * 1024.0) // 轉換為 MB
        }
        
        // 其他測量方法...
    }
}
```

### PerformanceReporter 類別
**檔案**: `app/src/androidTest/java/com/osfans/trime/performance/PerformanceReporter.kt`

```kotlin
class PerformanceReporter {
    
    companion object {
        private const val REPORT_FILE = "/sdcard/trime_performance.json"
        
        fun saveBaseline(metrics: PerformanceCollector.PerformanceMetrics) {
            val json = Gson().toJson(mapOf("baseline" to metrics))
            writeToFile(json)
        }
        
        fun recordOptimizationResult(
            taskName: String, 
            before: PerformanceCollector.PerformanceMetrics,
            after: PerformanceCollector.PerformanceMetrics
        ) {
            val improvement = calculateImprovement(before, after)
            val result = OptimizationResult(taskName, before, after, improvement)
            
            appendToFile(Gson().toJson(result))
            
            // 輸出到 logcat 供 CI 讀取
            Log.i("TrimeOptimization", "Task: $taskName, Improvement: $improvement")
        }
        
        private fun calculateImprovement(
            before: PerformanceCollector.PerformanceMetrics,
            after: PerformanceCollector.PerformanceMetrics
        ): ImprovementMetrics {
            return ImprovementMetrics(
                memoryReduction = (before.memoryUsageMB - after.memoryUsageMB) / before.memoryUsageMB,
                renderingSpeedup = (before.renderingTimeMs - after.renderingTimeMs) / before.renderingTimeMs,
                layoutSpeedup = (before.layoutTimeMs - after.layoutTimeMs) / before.layoutTimeMs,
                touchImprovement = (before.touchResponseTimeMs - after.touchResponseTimeMs) / before.touchResponseTimeMs
            )
        }
    }
    
    data class ImprovementMetrics(
        val memoryReduction: Double,      // 記憶體減少比例
        val renderingSpeedup: Double,     // 渲染速度提升比例
        val layoutSpeedup: Double,        // 佈局速度提升比例  
        val touchImprovement: Double      // 觸控響應改善比例
    )
}
```

## 持續整合測試

### GitHub Actions 工作流程
**檔案**: `.github/workflows/performance-monitoring.yml`

```yaml
name: Performance Monitoring

on:
  pull_request:
    paths:
      - 'app/src/main/java/com/osfans/trime/ime/**'
      - 'app/src/main/java/com/osfans/trime/data/theme/**'
  
jobs:
  performance-test:
    runs-on: ubuntu-latest
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v3
        
      - name: Setup Android SDK
        uses: android-actions/setup-android@v2
        
      - name: Setup Java
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
          
      - name: Cache Gradle dependencies
        uses: actions/cache@v3
        with:
          path: ~/.gradle/caches
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*') }}
          
      - name: Run performance tests
        run: |
          ./gradlew assembleDebugAndroidTest
          ./gradlew connectedDebugAndroidTest \
            -Pandroid.testInstrumentationRunnerArguments.class=com.osfans.trime.performance.BaselinePerformanceTest
            
      - name: Extract performance data
        run: |
          adb pull /sdcard/trime_performance.json ./performance_results.json
          
      - name: Compare with baseline
        run: |
          python scripts/compare_performance.py \
            --current ./performance_results.json \
            --baseline ./baseline_performance.json \
            --threshold 0.05
            
      - name: Comment performance results
        uses: actions/github-script@v6
        with:
          script: |
            const fs = require('fs');
            const results = JSON.parse(fs.readFileSync('./performance_comparison.json'));
            
            const comment = `
            ## 🔍 效能測試結果
            
            | 指標 | 變化 | 狀態 |
            |------|------|------|
            | 記憶體使用 | ${results.memory}% | ${results.memory < 5 ? '✅' : '⚠️'} |
            | 渲染時間 | ${results.rendering}% | ${results.rendering < 5 ? '✅' : '⚠️'} |
            | 佈局時間 | ${results.layout}% | ${results.layout < 5 ? '✅' : '⚠️'} |
            | 觸控響應 | ${results.touch}% | ${results.touch < 5 ? '✅' : '⚠️'} |
            `;
            
            github.rest.issues.createComment({
              issue_number: context.issue.number,
              owner: context.repo.owner,
              repo: context.repo.repo,
              body: comment
            });
```

## 效能基準設定

### 目標指標
| 指標 | 當前基準 | Watch 目標 | 改善要求 |
|------|----------|------------|----------|
| 記憶體使用 | ~200MB | <50MB | 75% ↓ |
| 啟動時間 | ~3s | <2s | 33% ↓ |
| 鍵盤渲染 | ~50ms | <30ms | 40% ↓ |
| UI 佈局 | ~40ms | <25ms | 37% ↓ |
| 觸控響應 | ~20ms | <10ms | 50% ↓ |

### 測試環境規格
- **模擬器**: Wear OS API 30, RAM 1GB
- **實機**: 支援的 Android Watch 裝置
- **測試數據量**: 標準中文詞庫 (精簡版)

## 使用指南

### 執行基準測試
```bash
# 建立初始基準
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=BaselinePerformanceTest

# 執行特定效能測試
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=KeyboardViewPerformanceTest
```

### 比較優化結果
```bash
# 在優化前執行基準測試
./gradlew performanceBaseline

# 在優化後執行比較測試  
./gradlew performanceComparison

# 生成效能報告
./gradlew generatePerformanceReport
```

### 監控持續效能
```bash
# 設定效能回歸警報
./gradlew setupPerformanceMonitoring

# 每日效能檢查
./gradlew dailyPerformanceCheck
```

這個測試框架確保每個優化任務都能被量化驗證，並追蹤長期的效能趨勢。