# Trime Watch OS 適配計劃 - 詳細子任務分解

## 專案概覽
本計劃旨在將 Trime 輸入法移植到 Android Watch 平台，遵循 80/20 法則，重點優化影響最大但異動最小的核心元件。

## 效能基準線 (當前狀態)
- **記憶體使用量**: ~200MB (預估)
- **APK 大小**: ~50MB
- **啟動時間**: 需建立基準測試
- **輸入延遲**: 需建立基準測試
- **電池消耗**: 需建立基準測試

---

## 階段一：核心渲染系統優化 (80% 效益)

### 1.1 KeyboardView 位圖快取優化

**任務描述**: 優化 KeyboardView 的位圖快取機制，減少記憶體使用並提升渲染效能。

**技術背景**: 
- 當前 KeyboardView 使用 `drawingBuffer` 作為離屏位圖快取
- 位圖在每次鍵盤切換時重建，造成記憶體壓力
- 按鍵渲染時重複計算顏色和字體設定

**具體實作內容**:
1. 實作智能位圖快取策略：
   - 僅在鍵盤尺寸變更時重建 drawingBuffer
   - 實作按鍵狀態快取機制
   - 新增記憶體壓力監控

2. 優化按鍵渲染邏輯：
   - 預先計算常用的顏色和字體設定
   - 實作渲染狀態快取
   - 減少重複的 Canvas 操作

**完成標準**:
- [ ] drawingBuffer 記憶體使用減少 30%
- [ ] 按鍵渲染時間減少 20%
- [ ] 通過記憶體洩漏檢測
- [ ] 所有現有單元測試通過
- [ ] 新增效能測試案例

**驗證方法**:
```bash
# 效能測試
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=KeyboardViewPerformanceTest

# 記憶體檢測
make debug
adb shell am start -n com.osfans.trime/.ui.main.PrefMainActivity
# 使用 Android Studio Profiler 監控記憶體使用
```

**相關檔案**:
- `app/src/main/java/com/osfans/trime/ime/keyboard/KeyboardView.kt`
- `app/src/androidTest/java/com/osfans/trime/performance/KeyboardViewPerformanceTest.kt` (新建)

**預期 Commit 標題**: `perf: optimize KeyboardView bitmap cache mechanism`

---

### 1.2 觸控事件處理簡化

**任務描述**: 簡化 KeyboardView 的觸控事件處理邏輯，移除複雜的滑動手勢檢測，專注於基本點擊操作。

**技術背景**:
- 當前觸控處理邏輯超過 300 行，包含複雜的滑動手勢
- 多點觸控支援在手錶上不實用
- GestureDetector 增加不必要的複雜度

**具體實作內容**:
1. 移除複雜手勢支援：
   - 簡化 onTouchEvent 邏輯
   - 移除 GestureDetector 依賴
   - 專注於單點觸控操作

2. 優化觸控響應：
   - 減少觸控事件的狀態變數
   - 簡化按鍵按下/釋放邏輯
   - 移除不必要的多點觸控處理

**完成標準**:
- [ ] 觸控事件處理程式碼行數減少 50%
- [ ] 觸控響應延遲減少 10ms
- [ ] 移除所有多點觸控相關程式碼
- [ ] 基本觸控功能完全正常
- [ ] 通過觸控測試案例

**驗證方法**:
```bash
# 功能測試
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=TouchEventTest

# 手動測試
make debug
# 在實機上測試基本點擊、長按功能
```

**相關檔案**:
- `app/src/main/java/com/osfans/trime/ime/keyboard/KeyboardView.kt` (onTouchEvent 方法)
- `app/src/androidTest/java/com/osfans/trime/ime/TouchEventTest.kt` (新建)

**預期 Commit 標題**: `refactor: simplify touch event handling for watch compatibility`

---

### 1.3 協程使用優化

**任務描述**: 優化 KeyboardView 中協程的使用，避免過度使用協程處理簡單操作。

**技術背景**:
- 當前長按和重複按鍵功能使用多個並行 Job
- 簡單的延遲操作也包裝成協程
- 協程生命週期管理可能造成記憶體洩漏

**具體實作內容**:
1. 重構長按處理機制：
   - 使用單一協程處理長按事件
   - 簡化重複按鍵邏輯
   - 改善協程生命週期管理

2. 減少不必要的協程使用：
   - 將簡單延遲操作改為 Handler
   - 移除過度包裝的協程操作

**完成標準**:
- [ ] 併發 Job 數量減少 70%
- [ ] 長按功能響應時間保持一致
- [ ] 無協程相關的記憶體洩漏
- [ ] 所有功能測試通過

**驗證方法**:
```bash
# 長按功能測試
./gradlew test -Dtest.single=LongPressTest
```

**相關檔案**:
- `app/src/main/java/com/osfans/trime/ime/keyboard/KeyboardView.kt` (協程相關邏輯)

**預期 Commit 標題**: `perf: optimize coroutine usage in KeyboardView`

---

## 階段二：UI 元件架構簡化 (15% 效益)

### 2.1 InputView 佈局層次簡化

**任務描述**: 簡化 InputView 的佈局結構，減少 View 層次並優化約束佈局使用。

**技術背景**:
- 當前 InputView 使用多層 ConstraintLayout 嵌套
- 複雜的約束關係影響佈局效能
- 深層 View 層次增加渲染開銷

**具體實作內容**:
1. 扁平化佈局結構：
   - 減少 ViewGroup 嵌套層次
   - 使用更高效的佈局方式
   - 簡化約束關係

2. 優化動態佈局計算：
   - 快取佈局參數
   - 減少不必要的 measure/layout 操作

**完成標準**:
- [ ] View 層次減少 30%
- [ ] 佈局時間減少 25%
- [ ] UI 響應性保持一致
- [ ] 通過 UI 測試

**驗證方法**:
```bash
# UI 測試
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=InputViewLayoutTest
```

**相關檔案**:
- `app/src/main/java/com/osfans/trime/ime/core/InputView.kt`
- `app/src/main/java/com/osfans/trime/ime/core/BaseInputView.kt`

**預期 Commit 標題**: `refactor: simplify InputView layout hierarchy`

---

### 2.2 候選詞顯示機制優化

**任務描述**: 簡化候選詞顯示機制，專注於基本的水平候選詞列表。

**具體實作內容**:
1. 移除複雜的候選詞佈局選項
2. 簡化候選詞選擇邏輯
3. 優化候選詞渲染效能

**完成標準**:
- [ ] 候選詞顯示延遲減少 15%
- [ ] 候選詞選擇功能正常
- [ ] 通過候選詞功能測試

**相關檔案**:
- `app/src/main/java/com/osfans/trime/ime/core/InputView.kt`

**預期 Commit 標題**: `refactor: simplify candidate view mechanism`

---

## 階段三：主題系統輕量化 (3% 效益)

### 3.1 主題快取系統實作

**任務描述**: 實作高效的主題快取機制，減少 YAML 解析開銷。

**技術背景**:
- 當前每次主題切換都重新解析完整配置
- ColorManager 僅使用 LruCache(10)，快取容量不足
- 缺乏主題資源預載機制

**具體實作內容**:
1. 擴大主題資源快取：
   - 增加 ColorManager 快取大小至 50
   - 實作主題資源預載機制
   - 新增快取命中率監控

2. 優化 YAML 解析：
   - 實作增量主題更新
   - 快取解析結果
   - 減少重複解析

**完成標準**:
- [ ] 主題切換時間減少 60%
- [ ] 快取命中率達到 80% 以上
- [ ] 記憶體使用增加不超過 5MB
- [ ] 所有主題功能正常

**驗證方法**:
```bash
# 主題切換效能測試
./gradlew test -Dtest.single=ThemePerformanceTest
```

**相關檔案**:
- `app/src/main/java/com/osfans/trime/data/theme/ColorManager.kt`
- `app/src/main/java/com/osfans/trime/data/theme/ThemeManager.kt`

**預期 Commit 標題**: `perf: implement efficient theme caching system`

---

### 3.2 Watch 專用主題實作

**任務描述**: 創建專為 Watch OS 優化的簡化主題。

**具體實作內容**:
1. 設計簡化的 Watch 主題
2. 移除不必要的視覺效果
3. 優化小螢幕顯示

**完成標準**:
- [ ] Watch 主題 APK 大小減少 20%
- [ ] 渲染效能提升 15%
- [ ] 小螢幕可用性良好

**相關檔案**:
- `app/src/main/assets/themes/watch_optimized.yaml` (新建)

**預期 Commit 標題**: `feat: add watch-optimized theme`

---

## 階段四：RIME 引擎整合優化 (2% 效益)

### 4.1 JNI 調用優化

**任務描述**: 減少不必要的 JNI 調用，實作 RIME 狀態快取機制。

**具體實作內容**:
1. 實作 RIME 狀態快取
2. 批量處理 JNI 操作
3. 減少狀態查詢頻率

**完成標準**:
- [ ] JNI 調用次數減少 40%
- [ ] 輸入響應時間保持一致
- [ ] RIME 引擎功能完全正常

**相關檔案**:
- `app/src/main/java/com/osfans/trime/core/RimeApi.kt`

**預期 Commit 標題**: `perf: optimize JNI calls with state caching`

---

## 效能驗證框架

### 基準測試實作

**檔案**: `app/src/androidTest/java/com/osfans/trime/performance/PerformanceBenchmark.kt`

```kotlin
@RunWith(AndroidJUnit4::class)
class PerformanceBenchmark {
    
    @Test
    fun measureKeyboardViewRenderingTime() {
        // 測量鍵盤渲染時間
    }
    
    @Test
    fun measureMemoryUsage() {
        // 測量記憶體使用量
    }
    
    @Test
    fun measureInputLatency() {
        // 測量輸入延遲
    }
}
```

### 持續整合檢查

**檔案**: `.github/workflows/performance-check.yml`

```yaml
name: Performance Check
on: [pull_request]
jobs:
  performance-test:
    runs-on: ubuntu-latest
    steps:
      - name: Run performance tests
        run: ./gradlew connectedDebugAndroidTest
```

---

## 實施時程安排

| 階段 | 預估工時 | 完成標準 |
|------|----------|----------|
| 階段一 | 3-4 天 | 80% 效能提升目標 |
| 階段二 | 2-3 天 | 15% 效能提升目標 |
| 階段三 | 1-2 天 | 3% 效能提升目標 |
| 階段四 | 1-2 天 | 2% 效能提升目標 |
| **總計** | **7-11 天** | **100% 完整適配** |

## 成功指標

### 量化指標
- **記憶體使用**: 降低 60-70%（目標：<50MB）
- **APK 大小**: 減少 40-50%（目標：<25MB）
- **啟動時間**: 縮短 70-80%（目標：<2秒）
- **輸入延遲**: 改善 30-40%（目標：<100ms）
- **電池續航**: 延長 40-50%

### 功能指標
- [ ] 所有核心輸入功能正常
- [ ] Watch OS 約束下運行穩定
- [ ] 通過所有自動化測試
- [ ] 用戶體驗滿足手錶使用場景

## 風險管控

### 技術風險
1. **向後兼容性**: 所有優化保持 API 兼容
2. **功能完整性**: 核心功能不受影響
3. **穩定性**: 充分的測試覆蓋

### 實施策略
1. **漸進式重構**: 小步快跑，每個 commit 可獨立驗證
2. **特性分支**: 使用 `feature/watch-adaptation` 分支開發
3. **回滾計劃**: 每個階段都可獨立回滾