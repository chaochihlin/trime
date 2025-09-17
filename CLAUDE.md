# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 專案概覽

Trime 是一個基於 RIME 輸入法框架的 Android 中文輸入法編輯器 (IME)。使用 Kotlin 開發，並透過 JNI 整合原生 C++ 元件。此專案結合了 Android UI 框架與 librime 引擎，提供繁體中文和簡體中文的文字輸入功能。

## 建置指令

### 開發建置
```bash
# 除錯版本（推薦用於開發）
make debug
# 或在 Windows: .\gradlew assembleDebug

# 清理建置產物
make clean
# 或在 Windows: .\gradlew clean

# 完整建置包含程式碼風格檢查
make build
```

### 發布建置
```bash
# 需要 keystore.properties 檔案包含簽署資訊
make release
# 或在 Windows: .\gradlew assembleRelease
```

### 程式碼品質
```bash
# 執行所有風格檢查
make style-lint

# 套用程式碼格式化
make style-apply

# 個別格式化工具
make spotlessCheck    # Kotlin 格式檢查
make spotlessApply    # 套用 Kotlin 格式化
make clang-format-lint  # C++ 格式檢查
make clang-format     # 套用 C++ 格式化
```

### 測試
```bash
# 執行單元測試（目前測試覆蓋率有限）
./gradlew test

# 個別測試檔案位於：
# - app/src/test/java/com/osfans/trime/data/theme/GeneralStyleTest.kt  
# - app/src/test/java/com/osfans/trime/util/WeakHashSetTest.kt
```

### 翻譯
```bash
# 從繁體中文字串產生簡體中文
make translate
```

## 高階架構

### 核心元件

**RIME 引擎整合** (`app/src/main/java/com/osfans/trime/core/`)
- `Rime.kt`: librime JNI 呼叫和生命週期管理的主要包裝器
- `RimeApi.kt`: 定義 RIME 引擎操作的介面
- `RimeDaemon.kt` & `RimeSession.kt`: 背景服務管理
- `RimeDispatcher.kt`: RIME 操作的執行緒和訊息處理

**輸入法服務** (`app/src/main/java/com/osfans/trime/ime/`)
- `TrimeInputMethodService.kt`: 主要 Android IME 服務進入點
- `InputView.kt`: 核心輸入檢視管理
- `KeyboardView.kt` & `Keyboard.kt`: 虛擬鍵盤渲染和互動
- `CandidatesView.kt`: 候選文字選擇 UI

**資料層** (`app/src/main/java/com/osfans/trime/data/`)
- `DataManager.kt`: 檔案系統資料同步
- `ThemeManager.kt`: UI 主題和樣式管理  
- `SchemaManager.kt`: RIME 方案配置管理
- `Database.kt`: 使用者資料的本地 SQLite 資料庫
- `AppPrefs.kt`: 使用 SharedPreferences 的應用程式偏好設定

**UI 元件** (`app/src/main/java/com/osfans/trime/ui/`)
- `PrefMainActivity.kt`: 設定和配置畫面
- `SetupActivity.kt`: 初始應用程式設定流程
- 基於 Fragment 的設定組織

**原生層** (`app/src/main/jni/`)
- `librime_jni/`: Kotlin 和 C++ librime 之間的 JNI 橋接
- `librime/`: RIME 輸入法引擎（子模組）
- `OpenCC/`: 繁體/簡體中文轉換（子模組）

### 關鍵模式

**JNI 整合**: 透過 JNI 存取原生 RIME 引擎，在 `RimeDispatcher` 中進行謹慎的生命週期管理

**主題系統**: 使用 YAML 配置檔案的彈性主題系統，具備執行時切換功能

**資料同步**: 基於檔案的配置，在共享和使用者資料目錄之間進行背景同步

**生命週期管理**: 適當的 Android 服務生命週期處理，使用協程進行非同步操作

**模組化架構**: 輸入處理、UI 渲染、資料管理和原生引擎整合之間的清晰分離

## 開發注意事項

- 所有 Gradle 指令都應使用專案的內建包裝器 (`./gradlew`)
- 專案使用 Android SDK API level 35，最低 API 21
- 原生建置需要 Android NDK 進行 C++ 編譯
- 透過 Spotless (Kotlin) 和 clang-format (C++) 強制執行程式碼風格
- 主題檔案為 YAML 格式，從共享和使用者目錄載入
- 資料庫操作使用 Room 進行型別安全的 SQL 查詢

## 目標裝置與架構

- **主要目標**: 智慧手錶裝置（非標準 Wear OS）
- **架構**: armeabi-v7a (32位元 ARM 架構)
- **Android 版本**: Android 9（API level 28）
- **螢幕特性**: 固定圓形螢幕，無需兼容方形或其他形狀
- **建置命令**: 使用 `BUILD_ABI=armeabi-v7a ./gradlew assembleDebug` 針對特定架構建置
- **部署**: ✅ 已成功部署優化版本至手錶裝置
- **效能監控**: 使用 `./test_performance.sh` 和 `./test_general_trime.sh` 進行即時監控

### 手錶設備特性
- **非標準 Wear OS**: 不支援標準 WearOS API 檢測方法
- **圓形螢幕檢測**: 使用螢幕尺寸和固定配置方式而非系統 API
- **兼容性**: 專為此特定手錶型號優化，無需支援其他手錶形狀