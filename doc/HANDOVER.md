<!--
SPDX-FileCopyrightText: 2026 Trime Watch Fork Contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Trime Watch Fork — 接手指南 (HANDOVER)

本文件提供給**接手本專案的 Android 工程師**。假設你熟悉 Android / Kotlin 開發，但**沒有接觸過 RIME 或 Trime**。閱讀本文件後，你應該能：

1. 理解本專案是什麼、跟上游有何差異
2. 從零把專案 build 起來並部署到目標手錶
3. 知道程式碼的主要目錄與資料流
4. 知道 T9 模組的結構與字典流程
5. 知道常用的 debug 手法

---

## 1. 專案定位

### 1.1 這是什麼

本專案是 [osfans/trime](https://github.com/osfans/trime) 的 **fork**，目標是把 Trime 改造成**圓形螢幕智慧手錶專用的 T9 注音輸入法**。

上游 Trime 是一個通用的 Android RIME 前端（支援全尺寸手機鍵盤、多種輸入方案、主題切換等）；本 fork 聚焦於單一使用情境：

| 項目 | 值 |
|---|---|
| 目標硬體 | 智慧手錶（非標準 Wear OS） |
| 螢幕 | 456 × 456 圓形，182 dpi |
| 架構 | armeabi-v7a（32-bit ARM） |
| Android 版本 | Android 9（API 28） |
| 輸入方式 | T9 九宮格注音（非標準 QWERTY） |
| 輸入方案 | 注音 → 漢字（繁體中文） |

### 1.2 與上游的差異概覽

- **新增 T9 模組** — `app/src/main/java/com/osfans/trime/ime/t9/`（21 個 Kotlin 檔案）：全自製的 T9 九宮格鍵盤、注音映射、聲調選擇器、候選字顯示
- **新增 T9 字典** — `app/src/main/assets/shared/bopomofo_t9.dict.yaml`、`app/src/main/assets/t9/t9_chars.json`
- **圓形螢幕優化** — 針對 456×456 圓形螢幕的 layout、無需支援方形或其他尺寸
- **記憶體優化** — `MemoryMonitor.kt` 針對低資源手錶做了多項優化
- **移除部分上游功能** — 例如 popup 模組（見分支名 `remove-popup-module`），降低 APK 大小與記憶體佔用
- **armeabi-v7a 單架構建置** — 使用 `BUILD_ABI=armeabi-v7a` 參數減少 APK 大小

---

## 2. Build 與部署

### 2.1 前置需求

| 工具 | 版本 |
|---|---|
| JDK | 17 或以上 |
| Android SDK | API 35（compileSdk）、Build Tools 35.0.0 |
| Android NDK | r25c 或 r26b（CMake 需求 ≥ 3.18） |
| Gradle | **不需手動安裝**，專案附 wrapper 8.14.1 |

網路需求（出站）：
- `dl.google.com`（Android SDK、AGP）
- `repo.maven.apache.org`（Maven Central）
- `jitpack.io`（`settings.gradle.kts` 有使用，部分相依從此取得）
- `services.gradle.org`（Gradle distribution）

### 2.2 Debug build（日常開發用）

```bash
# 指定手錶架構（armeabi-v7a），產生 debug APK
BUILD_ABI=armeabi-v7a ./gradlew assembleDebug

# 或使用 Makefile 的 wrapper（不指定 ABI 時會 build 所有架構，比較慢）
make debug
```

產出位置：`app/build/outputs/apk/debug/app-debug.apk`

### 2.3 Release build（交付用）

Release build 需要簽章設定：

1. 複製 `keystore.properties.sample` → `keystore.properties`
2. 填入 keystore 路徑與密碼（此檔案已在 `.gitignore`，不會被追蹤）
3. 執行 `make release` 或 `BUILD_ABI=armeabi-v7a ./gradlew assembleRelease`

若 `keystore.properties` 不存在，release build 會以 unsigned 形式產出（無法安裝）。

### 2.4 部署到手錶

目標手錶特性：
- 螢幕：456 × 456 圓形
- 不支援標準 Wear OS API，部分程式碼用「螢幕尺寸 + 固定配置」取代系統檢測

部署步驟：
```bash
# 1. 連線手錶（透過 USB 或 Wi-Fi ADB）
adb devices

# 2. 若有多個裝置，指定 serial
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk

# 3. 設定為預設輸入法（需手動在手錶設定中啟用 Trime）

# 4. 防止螢幕關閉（測試時很重要）
adb -s <serial> shell svc power stayon usb
adb -s <serial> shell settings put system screen_off_timeout 600000
```

---

## 3. 程式碼地圖

### 3.1 目錄結構速覽

```
app/src/main/
├── java/com/osfans/trime/
│   ├── core/              # RIME 引擎 JNI 包裝與生命週期
│   ├── data/              # 資料層（主題、方案、設定、資料庫）
│   ├── ime/               # 輸入法服務主體
│   │   ├── core/          # TrimeInputMethodService 主入口
│   │   ├── keyboard/      # 上游鍵盤系統
│   │   ├── t9/            # ★ 本 fork 新增的 T9 模組
│   │   ├── window/        # 鍵盤視窗管理
│   │   ├── bar/           # 候選詞欄
│   │   └── ...
│   └── ui/                # 設定 Activity、Fragment、Preference 畫面
├── jni/                   # 原生層（C++）
│   ├── CMakeLists.txt
│   ├── librime/           # git submodule — RIME 引擎
│   ├── librime-predict/   # git submodule — 輸入預測
│   ├── librime_jni/       # JNI 橋接層
│   ├── OpenCC/            # git submodule — 繁簡轉換
│   ├── snappy/            # git submodule — 壓縮
│   └── boost/             # Boost C++ libraries（已內嵌，非 submodule）
├── assets/
│   ├── shared/            # RIME 共用資料：schema、dict
│   │   ├── bopomofo_t9.dict.yaml   # ★ 本 fork 自製 T9 字典
│   │   ├── terra_pinyin.dict.yaml  # 上游 rime-terra-pinyin
│   │   └── ...
│   ├── prelude/           # git submodule — rime-prelude 基本資源
│   └── t9/
│       └── t9_chars.json  # ★ T9 單字表與權重
└── jniLibs/               # 預編譯 native libs（若有）
```

### 3.2 RIME 基本概念（必讀）

**RIME** 是一個跨平台的開源輸入法引擎（C++），Trime 等於 RIME 的 Android 前端。RIME 的核心概念有四層：

```
[使用者按鍵]
    ↓
schema   ← 輸入方案定義（例：bopomofo_t9.schema.yaml）
          - 按鍵如何對應到音節
          - 算法（table / script / predict）
    ↓
dict     ← 音節到漢字的映射表（例：bopomofo_t9.dict.yaml）
          - 格式：<漢字> <tab> <讀音> <tab> <權重>
    ↓
candidates ← RIME 產出的候選字列表
    ↓
[UI 顯示]
```

輔助概念：

- **theme** — UI 外觀設定（顏色、字型、按鍵佈局）。本 fork 使用 `tongwenfeng.trime.yaml`
- **OpenCC** — 繁簡轉換，由 schema 決定是否啟用
- **predict** — 可選的詞彙預測外掛（librime-predict）

所有 RIME 資料檔都是 **YAML 格式**，放在 `app/src/main/assets/shared/`（內建）與執行期的使用者資料目錄（可覆蓋）。

### 3.3 一次按鍵的完整資料流

以 T9 注音輸入為例：

```
使用者按數字鍵 "2"（ㄅㄆㄇㄈ）
    ↓
T9KeyboardView  捕捉觸控事件
    ↓
T9InputEventHandler  處理按鍵邏輯、維護輸入狀態（T9InputState）
    ↓
T9ZhuyinMapper  計算 "2" 可能對應的注音組合
    ↓
T9RimeHelper  呼叫 RimeSession 把注音送入 RIME 引擎
    ↓
RimeSession  (com.osfans.trime.daemon)
    ↓
RimeDispatcher  將呼叫 marshal 到背景 thread（避免 IME thread 阻塞）
    ↓
Rime.kt  JNI 呼叫 librime
    ↓
librime (C++)  查 dict、計算候選字、回傳
    ↓
RimeProto.Context  （回傳的結構化資料）
    ↓
T9CandidateBar  顯示候選字
    ↓
使用者點候選字
    ↓
TrimeInputMethodService.currentInputConnection.commitText()  上屏
```

**重要檔案**：
- `ime/core/TrimeInputMethodService.kt` — IME 服務進入點（1152 行）
- `ime/t9/T9InputContainer.kt` — T9 整體容器
- `ime/t9/T9InputEventHandler.kt` — T9 事件處理核心
- `ime/t9/T9ZhuyinMapper.kt` — 數字鍵 → 注音映射邏輯
- `ime/t9/T9RimeHelper.kt` — 與 RIME 橋接
- `core/Rime.kt` — JNI 包裝（588 行）
- `core/RimeDispatcher.kt` — 執行緒派發

---

## 4. T9 模組深入

### 4.1 檔案分工

| 檔案 | 職責 |
|---|---|
| `T9InputContainer.kt` | 整體 T9 輸入容器，包含鍵盤 + 候選欄 + 聲調鍵 + 確認鍵 |
| `T9KeyboardView.kt` | 九宮格鍵盤 View（基於 ConstraintLayout） |
| `T9NumberKey.kt` | 單一數字鍵元件 |
| `T9FunctionKey.kt` | 功能鍵（模式切換、符號等） |
| `T9ConfirmButton.kt` | 確認鍵（✓） |
| `T9DeleteButton.kt` | 退格鍵 |
| `T9InputEventHandler.kt` | 事件處理核心（800+ 行）— 維護輸入狀態、過濾候選字、處理退格 |
| `T9InputState.kt` | 輸入狀態資料類別 |
| `T9ZhuyinMapper.kt` | **核心**：數字鍵 → 注音符號映射、組合合法性檢查、音韻規則過濾 |
| `T9CharDataLoader.kt` | 從 `t9_chars.json` 載入字表與權重 |
| `T9RimeHelper.kt` | 封裝 RIME 查詢邏輯 |
| `T9CandidateBar.kt` | 候選字橫向捲動欄 |
| `T9WhiteCandidateAdapter.kt` | RecyclerView adapter |
| `T9PreeditView.kt` | 顯示當前注音組合的預覽列 |
| `T9TextInputView.kt` | 文字輸入框（測試用） |
| `ContextDisplayArea.kt` | 顯示已輸入的注音組合 |
| `SimpleT9InputLogic.kt` | 簡化版 T9 邏輯（測試或 fallback） |
| `ZhuyinSelectorView.kt` | 注音符號直接選擇器（長按或特殊模式） |
| `ZhuyinSelectorAdapter.kt` | 上述的 adapter |
| `ZhuyinSelectorItemUi.kt` | 選擇器單格 UI |
| `ZhuyinSelectorModels.kt` | 選擇器資料模型 |

### 4.2 T9 按鍵 → 注音映射

實作在 `T9ZhuyinMapper.kt`。基本映射遵循**台灣手機傳統 T9 注音排列**：

```
鍵 2: ㄅ ㄆ ㄇ ㄈ
鍵 3: ㄉ ㄊ ㄋ ㄌ
鍵 4: ㄍ ㄎ ㄏ
鍵 5: ㄐ ㄑ ㄒ
鍵 6: ㄓ ㄔ ㄕ ㄖ
鍵 7: ㄗ ㄘ ㄙ
鍵 8: ㄧ ㄨ ㄩ
鍵 9: ㄚ ㄛ ㄜ ㄝ
鍵 0: ㄞ ㄟ ㄠ ㄡ
鍵 *: ㄢ ㄣ ㄤ ㄥ ㄦ
```

（實際映射請以 `T9ZhuyinMapper.T9_MAPPING` 為準）

合法性檢查由 `isValidZhuyinCombination()` 處理，套用漢語音韻學規則（聲母-介音相容、ㄐㄑㄒ 必接介音等），避免產生不可能的注音組合。

### 4.3 字典與權重

T9 用的字典由兩個檔案組成：

1. **`app/src/main/assets/shared/bopomofo_t9.dict.yaml`**  
   RIME 格式字典，提供「注音 → 漢字」的基礎映射。由 `scripts/generate_bopomofo_dict*.py` 產生。

2. **`app/src/main/assets/t9/t9_chars.json`**  
   T9 自有的字表，含常用度權重。用於排序與快速查詢。由 `T9CharDataLoader` 載入。

**更新字典流程**（若要調整權重或加字）：

```bash
# 1. 修改 scripts/generate_bopomofo_dict_v2.py 的來源資料
# 2. 重新產生 yaml
python3 scripts/generate_bopomofo_dict_v2.py
# 3. 重新 build 與安裝
BUILD_ABI=armeabi-v7a ./gradlew assembleDebug
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
# 4. 清除 RIME 資料快取以載入新字典（第一次開啟會自動 deploy）
adb -s <serial> shell pm clear com.osfans.trime.debug
```

**已知限制**：`t9_chars.json` 的多音字索引目前不完整，暫時以 RIME 的 `page_size=200` 設定涵蓋這個漏洞。若要徹底修復需重建索引結構。

### 4.4 相關腳本（在 `scripts/` 目錄）

| 腳本 | 用途 |
|---|---|
| `generate_bopomofo_dict.py` | 第一版字典產生器 |
| `generate_bopomofo_dict_v2.py` | 第二版字典產生器（較新，建議使用） |
| `t9_tone_filter_test.sh` | 聲調過濾功能測試 |
| `terra_pinyin_reference.dict.yaml` | 上游 rime-terra-pinyin 參考檔 |

---

## 5. Debug 速查

### 5.1 Logcat tag

本專案使用 [Timber](https://github.com/JakeWharton/timber) 做 log。常用過濾：

```bash
# 只看 Trime 相關 log
adb -s <serial> logcat -s Trime:* TimberTagged:V

# 看 T9 模組（大多 tag 以類別名為前綴）
adb -s <serial> logcat | grep -E "T9|Rime|Trime"

# 看 RIME JNI 層錯誤
adb -s <serial> logcat | grep -E "librime|rime_jni"

# 清 log 後重新觀察
adb -s <serial> logcat -c && adb -s <serial> logcat -s Trime:*
```

### 5.2 常見問題檢查

| 症狀 | 第一步檢查 |
|---|---|
| 輸入法不出現 | 檢查是否在系統設定啟用、預設輸入法是否切到 Trime |
| 候選字空白 | 檢查 RIME deploy 是否成功：`logcat \| grep deploy` |
| 字典更新後未生效 | `pm clear com.osfans.trime.debug` 重置資料目錄 |
| JNI crash | 檢查 NDK 版本與 armeabi-v7a 是否匹配 |
| 按鍵無反應 | 檢查 `TrimeInputMethodService` 是否啟動、onStartInput 是否呼叫 |

### 5.3 RIME 資料目錄

RIME 執行時使用兩個資料目錄：

- **共用目錄** — 打包進 APK，位於 `/data/data/com.osfans.trime.debug/files/rime/` 或 `...build/`
- **使用者目錄** — 存個人設定，位於使用者資料區

若要檢視實際部署的 RIME 檔案：

```bash
adb -s <serial> shell run-as com.osfans.trime.debug ls -la files/rime/
```

---

## 6. 程式碼風格

- **Kotlin 格式化**：Spotless（ktlint 規則）。執行 `./gradlew spotlessCheck` 檢查、`./gradlew spotlessApply` 自動修復
- **C++ 格式化**：clang-format。執行 `make clang-format-lint` / `make clang-format`
- **註解語言**：本 fork 新增的 Kotlin 程式碼註解以**繁體中文**為主；程式碼中偶見**簡體中文註解**皆為上游 osfans/trime 遺留，未修改，對台灣工程師應仍可讀
- **commit message**：參考 `CONTRIBUTING.md`（上游通用版）

---

## 7. 授權

- **本專案**：GPL-3.0-or-later（見 `LICENSE`）
- **子模組授權**：詳見 `THIRD_PARTY_NOTICES.md`

---

## 8. 其他文件索引

| 文件 | 說明 |
|---|---|
| `README.md` / `README_tc.md` | 上游 osfans/trime 原始 README（**未描述本 fork 的手錶 / T9 改動**） |
| `CHANGELOG.md` | 上游歷史 changelog（不含本 fork 變更） |
| `CONTRIBUTING.md` | 上游通用貢獻指引 |
| `PRIVACY.md` | 隱私政策 |
| `CODE_OF_CONDUCT.md` | 行為準則 |
| `doc/Keyboard.md` | 上游通用鍵盤配置教學（未反映本 fork T9 鍵盤） |
| `doc/trime-schema.json` | 上游主題 schema 定義 |
| `THIRD_PARTY_NOTICES.md` | 第三方相依與字典授權整理 |
| `keystore.properties.sample` | Release build 簽章設定範本 |

