# Issue #001: Keyboard Display Delay on Wear OS Devices

**Status:** Open  
**Priority:** High  
**Created:** 2025-08-27  
**Platform:** Android Wear OS  
**Architecture:** armeabi-v7a (32位元 ARM)  

## 問題描述 (Issue Description)

在 Wear OS (手錶) 裝置上，Trime 輸入法鍵盤顯示出現 5-6 秒的嚴重延遲。當用戶點擊文字輸入框時，系統會停頓約 5-6 秒後鍵盤才會出現，嚴重影響使用體驗。

## 重現步驟 (Steps to Reproduce)

1. 在 Wear OS 裝置上安裝 Trime 輸入法
2. 在任何應用中點擊文字輸入框
3. 觀察鍵盤出現的時間延遲
4. **實際結果**: 延遲 5-6 秒後鍵盤才出現
5. **預期結果**: 鍵盤應在 1 秒內立即顯示

## 技術分析 (Technical Analysis)

### 確定的根本原因:

#### 1. 過度複雜的渲染初始化 (`KeyboardView.kt:409-439`)
- 每次顯示鍵盤時重新創建大量 GPU 資源
- `onDraw()` 方法包含記憶體壓力檢查、快取清理和多層渲染邏輯
- 非硬體加速模式下使用 bitmap buffer，需要額外的記憶體分配

#### 2. 記憶體監控開銷 (`MemoryMonitor.kt:20-45`)
- 每次渲染都執行記憶體壓力檢查 (`memoryMonitor.checkMemoryPressure()`)
- 在手錶有限記憶體下，80% 記憶體使用閾值經常被觸發
- 觸發後執行 `clearCaches()` 強制清空所有快取，下次又需重建

#### 3. 頻繁的快取重建 (`SmartBitmapCache.kt:22-63`)
- 每當記憶體壓力時，bitmap buffer 會被 `recycle()` 和重新分配
- 從 log 可看出 456x456 像素的大緩衝區頻繁分配/釋放 (約 830KB 每次)

#### 4. 協程和作業排程延遲 (`KeyboardView.kt:156-180`)
- 大量協程作業 (`longPressJob`, `repeatJob`, `removePreviewJob`) 同時排程
- `lifecycleScope.launch` 在記憶體壓力下可能延遲執行

### Log 證據:

```
15:22:20.415 gralloc I gralloc_register_buffer hnd=0x9a879740, share_fd=60, share_attr_fd=-1, magic=51647890, format=1, internal_format=1, byte_stride=256, flags=4, usage=0xb00, size=16896, width=62, height=66
15:22:28.158 gralloc I gralloc_register_buffer hnd=0x9a879980, share_fd=63, share_attr_fd=-1, magic=51647890, format=1, internal_format=1, byte_stride=1856, flags=4, usage=0xb00, size=846336, width=456, height=456
15:22:35.660 gralloc I gralloc_unregister_buffer hnd=0x9a879740
15:22:35.660 gralloc I gralloc_unregister_buffer hnd=0x9a8798c0
15:22:34.936 IInputConnectionWrapper W getExtractedText on inactive InputConnection
15:22:34.940 IInputConnectionWrapper W requestCursorAnchorInfo on inactive InputConnection
```

顯示頻繁的 GPU 記憶體分配/釋放操作和 InputConnection 警告。

## 建議修正方案 (Suggested Solutions)

### 短期優化:
1. **降低記憶體壓力檢查頻率** - 將 `memoryCheckInterval` 從 5 秒調整為 15-30 秒
2. **調整記憶體壓力閾值** - 將觸發清理的閾值從 80% 調整為 90%
3. **移除非關鍵監控** - 在 Wear OS 上停用效能日誌記錄

### 中期優化:
1. **預分配資源** - 在輸入法服務啟動時預分配 bitmap buffer
2. **簡化渲染流程** - 為 Wear OS 建立簡化的渲染模式
3. **RIME 引擎預熱** - 在背景預先初始化 RIME 引擎

### 長期優化:
1. **Wear OS 專用模式** - 建立專門針對手錶裝置的輕量化版本
2. **渲染快取策略** - 實作更積極的快取復用機制

## 相關文件 (Related Files)

- `app/src/main/java/com/osfans/trime/ime/keyboard/KeyboardView.kt` (Lines 409-439, 156-180)
- `app/src/main/java/com/osfans/trime/ime/keyboard/SmartBitmapCache.kt` (Lines 22-63)
- `app/src/main/java/com/osfans/trime/ime/keyboard/MemoryMonitor.kt` (Lines 20-45)
- `app/src/main/java/com/osfans/trime/ime/keyboard/RenderStateCache.kt`
- `app/src/main/java/com/osfans/trime/ime/core/TrimeInputMethodService.kt`

## 診斷詳細資料 (Diagnostic Details)

### 問題識別過程：
1. 分析 Android log 發現頻繁的 GPU 記憶體分配/釋放
2. 檢查 KeyboardView.kt 發現複雜的渲染流程
3. 分析快取管理類別發現積極的記憶體清理策略
4. 確認協程排程可能導致的延遲

### 效能影響：
- 使用者體驗：嚴重影響文字輸入的即時性
- 系統資源：過度消耗有限的手錶記憶體資源
- 電池續航：頻繁的 GPU 操作可能增加功耗

## 下一步行動 (Next Actions)

1. [ ] 實作記憶體監控閾值調整
2. [ ] 建立 Wear OS 檢測機制
3. [ ] 開發簡化渲染模式
4. [ ] 效能測試和驗證
5. [ ] 用戶測試確認修正效果

## 備註 (Notes)

此問題特別影響資源受限的 Wear OS 裝置，需要針對手錶平台進行專門優化。建議優先處理短期優化方案以快速改善使用者體驗。