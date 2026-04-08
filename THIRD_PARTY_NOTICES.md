<!--
SPDX-FileCopyrightText: 2026 Trime Watch Fork Contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 第三方授權與相依說明

本專案（Trime Watch Fork）以 **GPL-3.0-or-later** 授權釋出。本文件整理所有被直接使用或打包進成品的第三方元件、對應授權、與相容性說明。

---

## 1. 專案授權

- **授權條款**：GNU General Public License v3.0 or later（GPL-3.0-or-later）
- **授權檔案**：[`LICENSE`](./LICENSE)
- **說明**：衍生自上游 [osfans/trime](https://github.com/osfans/trime)（同為 GPL-3.0-or-later）

---

## 2. C++ 原生相依（子模組與內嵌）

| 元件 | 路徑 | 授權 | 與 GPL-3 相容 | 備註 |
|---|---|---|---|---|
| **librime** | `app/src/main/jni/librime/` | BSD 3-Clause | ✅ | RIME 輸入法引擎（子模組） |
| **librime-predict** | `app/src/main/jni/librime-predict/` | BSD 3-Clause | ✅ | RIME 預測外掛（子模組） |
| **OpenCC** | `app/src/main/jni/OpenCC/` | Apache-2.0 | ✅ | 繁簡轉換（子模組） |
| **snappy** | `app/src/main/jni/snappy/` | BSD 3-Clause | ✅ | Google 壓縮庫（子模組） |
| **Boost** | `app/src/main/jni/boost/` | Boost Software License 1.0 | ✅ | C++ 通用工具庫（已內嵌，非子模組） |

### librime 內部巢狀子模組（位於 `app/src/main/jni/librime/deps/`）

| 元件 | 授權 | 與 GPL-3 相容 | 備註 |
|---|---|---|---|
| **glog** | BSD 3-Clause | ✅ | Google logging 庫 |
| **leveldb** | BSD 3-Clause | ✅ | Key-value 儲存 |
| **marisa-trie** | BSD 2-Clause / LGPL-2.1 雙授權 | ✅ | 字典 trie 結構。本專案以 LGPL 條款下靜態連結於 GPL-3 下合規 |
| **yaml-cpp** | MIT | ✅ | YAML 解析 |
| **opencc**（巢狀副本） | Apache-2.0 | ✅ | 與頂層 OpenCC 同 |
| **googletest** | BSD 3-Clause | ✅ | 僅測試期使用，不入 APK |

所有子模組皆可在其對應目錄的 `LICENSE`、`COPYING`、`LICENSE.txt` 等檔案中找到完整授權文字。

---

## 3. Android / Kotlin 相依

由 Gradle 管理，集中於 `gradle/libs.versions.toml`。皆與 GPL-3 相容，無 AGPL。

| 函式庫 | 授權 | 用途 |
|---|---|---|
| AndroidX (`androidx.*`) | Apache-2.0 | Google 官方基礎庫 |
| Kotlin stdlib / coroutines / serialization | Apache-2.0 | Kotlin 官方 |
| [Splitties](https://github.com/LouisCAD/Splitties) | Apache-2.0 | Kotlin 擴充工具 |
| [AboutLibraries](https://github.com/mikepenz/AboutLibraries) | Apache-2.0 | App 內授權列表產生 |
| [kaml](https://github.com/charleskorn/kaml) | Apache-2.0 | YAML 解析（Kotlin） |
| [Timber](https://github.com/JakeWharton/timber) | Apache-2.0 | Logging |
| [xxPermissions](https://github.com/getActivity/XXPermissions) | Apache-2.0 | 權限請求 |
| [FlexboxLayout](https://github.com/google/flexbox-layout) | Apache-2.0 | Google flex 佈局 |
| [BaseRecyclerViewAdapterHelper (bravh)](https://github.com/CymChad/BaseRecyclerViewAdapterHelper) | MIT | RecyclerView adapter |
| [kotlin-inject](https://github.com/evant/kotlin-inject) | Apache-2.0 | 相依注入 |
| JUnit 5 / kotest | EPL-2.0 / Apache-2.0 | 測試（不入 APK） |

完整列表請執行 `./gradlew :app:exportLibraryDefinitions` 或查看建置後 app 的 About 頁面（由 AboutLibraries 自動產生）。

---

## 4. RIME 資料檔（字典、方案、主題）

`app/src/main/assets/shared/` 與 `app/src/main/assets/prelude/` 下的 YAML 資料檔出處：

| 檔案 | 來源 | 授權 |
|---|---|---|
| `bopomofo_t9.dict.yaml` | **本 fork 自製**（由 `scripts/generate_bopomofo_dict*.py` 產生） | GPL-3.0-or-later |
| `bopomofo_t9.schema.yaml` | 本 fork 自製 | GPL-3.0-or-later |
| `terra_pinyin.dict.yaml` / `terra_pinyin.schema.yaml` | [rime/rime-terra-pinyin](https://github.com/rime/rime-terra-pinyin) | BSD 3-Clause |
| `tongwenfeng.trime.yaml` | 上游 osfans/trime 預設主題 | GPL-3.0-or-later |
| `prelude/*`（子模組） | [rime/rime-prelude](https://github.com/rime/rime-prelude) | GPL-3.0-or-later |
| `app/src/main/assets/t9/t9_chars.json` | 本 fork 自製字表與權重 | GPL-3.0-or-later |

字典中的漢字音節資料部分衍生自教育部國語辭典等公開資料，本 fork 僅整理結構化索引，未重新散布原始辭書內容。

---

## 5. 建置工具鏈

建置過程中會下載但**不會打包進 APK** 的工具：

- Android Gradle Plugin（Apache-2.0）
- Kotlin Gradle Plugin（Apache-2.0）
- KSP — Kotlin Symbol Processing（Apache-2.0）
- CMake ≥ 3.18（BSD-3-Clause）
- Android NDK（Apache-2.0）

---

## 6. 取得完整授權文字

本專案所有第三方元件的完整授權文字可於以下位置找到：

- `LICENSE`（本專案）
- `app/src/main/jni/<submodule>/LICENSE`（C++ 子模組各自的授權檔）
- App 內「關於」頁面的第三方授權列表（由 AboutLibraries 在建置時自動彙整 Android 相依）

若需要整合進自家產品前的完整法務審查，建議：

1. 執行 `./gradlew :app:generateLicenseReport` 或開啟 app 內授權頁
2. 檢視 `app/src/main/jni/` 下各子模組的 LICENSE 檔
3. 確認你的散布方式（原始碼 / 二進位 / SaaS）對 GPL-3 的義務

---

## 7. 回饋

若發現授權資訊有誤或遺漏，請回報給專案維護者。
