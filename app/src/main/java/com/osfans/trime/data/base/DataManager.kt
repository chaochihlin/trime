// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.base

import android.content.res.AssetManager
import android.os.Build
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.util.FileUtils
import com.osfans.trime.util.ResourceUtils
import com.osfans.trime.util.appContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 資料管理器
 *
 * 負責 Trime 輸入法應用程式資料檔案的同步、部署和版本控制。管理各種資料目錄，
 * 包括共享資料、使用者資料和暫存資料，並提供檔案同步機制以確保資料一致性。
 *
 * 主要功能：
 * - 管理多個資料目錄（共享、使用者、預建、暫存）
 * - 透過檢查碼比較實現增量同步
 * - 提供執行緒安全的同步操作
 * - 支援資源檔案路徑解析
 * - 自動建立預設配置檔案
 */
object DataManager {
    /** 預設自訂配置檔案名稱 */
    private const val DEFAULT_CUSTOM_FILE_NAME = "default.custom.yaml"

    /** 資料檢查碼檔案名稱，用於追蹤檔案版本 */
    private const val DATA_CHECKSUMS_NAME = "checksums.json"

    /** 確保同步操作執行緒安全的重入鎖 */
    private val lock = ReentrantLock()

    /** JSON 序列化工具的延遲初始化 */
    private val json by lazy { Json }

    /**
     * 反序列化資料檢查碼
     *
     * @param raw JSON 格式的檢查碼字串
     * @return 反序列化的資料檢查碼物件
     */
    private fun deserializeDataChecksums(raw: String): DataChecksums = json.decodeFromString<DataChecksums>(raw)

    /**
     * 應用程式內部資料目錄
     *
     * Android N 以上版本使用裝置保護儲存空間而非憑證加密儲存空間，
     * 確保在使用者解鎖裝置前即可存取資料，提升輸入法的可用性。
     */
    private val dataDir: File =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Timber.d("Using device protected storage")
            appContext.createDeviceProtectedStorageContext().dataDir
        } else {
            File(appContext.applicationInfo.dataDir)
        }

    /**
     * 從 Assets 中讀取資料檢查碼
     *
     * @return 從 Assets 解析的資料檢查碼物件
     */
    private fun AssetManager.dataChecksums(): DataChecksums =
        open(DATA_CHECKSUMS_NAME)
            .bufferedReader()
            .use { it.readText() }
            .let { deserializeDataChecksums(it) }

    /** 應用程式偏好設定的延遲初始化 */
    private val prefs by lazy { AppPrefs.defaultInstance() }

    // === 簡化的目錄結構 ===

    /** 應用專用根目錄 */
    private val appDataRoot = appContext.getExternalFilesDir(null)!!

    /** 預建資源目錄，存放從 APK assets 解壓的檔案 */
    val assetsDir = File(appDataRoot, "assets").also { it.mkdirs() }

    /** 使用者配置目錄，存放自訂配置檔案 */
    val configDir = File(appDataRoot, "config").also { it.mkdirs() }

    /** 編譯結果目錄，存放 RIME 處理後的檔案 */
    val buildDir = File(appDataRoot, "build").also { it.mkdirs() }

    // === 向後相容性支援（將在後續版本移除）===

    /** @deprecated 使用 configDir 取代。為了支持使用者自訂路徑暫時保留 */
    val userDataDir
        get() =
            File(
                prefs.profile.userDataDir
                    .getValue()
                    .takeIf { it.isNotEmpty() } ?: configDir.absolutePath,
            ).also { it.mkdirs() }

    /** @deprecated 使用 assetsDir 取代 */
    @Deprecated("Use assetsDir instead", ReplaceWith("assetsDir"))
    val sharedDataDir = assetsDir

    /** @deprecated 使用 buildDir 取代 */
    @Deprecated("Use buildDir instead", ReplaceWith("buildDir"))
    val stagingDir get() = File(userDataDir, "build")

    /** @deprecated 使用 assetsDir 取代 */
    @Deprecated("Use assetsDir instead", ReplaceWith("assetsDir"))
    val prebuiltDataDir = assetsDir

    /**
     * 解析已部署資源的路徑
     *
     * 根據給定的資源 ID 返回已編譯配置檔案的絕對路徑。
     * 簡化版本：優先使用編譯結果，回退到預建資源。
     *
     * @param resourceId 通常等於配置檔案名稱（不含副檔名）
     * @return 已編譯配置檔案的絕對路徑
     */
    @JvmStatic
    fun resolveDeployedResourcePath(resourceId: String): String {
        // 優先查找編譯結果目錄
        val compiledPath = File(buildDir, "$resourceId.yaml")
        if (compiledPath.exists()) return compiledPath.absolutePath

        // 回退到預建資源目錄
        val prebuiltPath = File(assetsDir, "$resourceId.yaml")
        if (prebuiltPath.exists()) return prebuiltPath.absolutePath

        // 預設返回編譯路徑（即使不存在，讓 RIME 引擎處理）
        return compiledPath.absolutePath
    }

    /**
     * 同步資料檔案
     *
     * 比較舊版和新版的資料檢查碼，根據差異執行檔案的建立、更新或刪除操作。
     * 使用重入鎖確保同步操作的執行緒安全性，避免併發問題。
     *
     * 同步流程：
     * 1. 讀取舊版檢查碼檔案
     * 2. 從 Assets 中讀取新版檢查碼
     * 3. 計算差異並執行對應的檔案操作
     * 4. 更新檢查碼檔案
     * 5. 建立預設自訂配置檔案
     */
    fun sync() =
        lock.withLock {
            val oldChecksumsFile = File(dataDir, DATA_CHECKSUMS_NAME)
            val oldChecksums =
                oldChecksumsFile
                    .runCatching { deserializeDataChecksums(bufferedReader().use { it.readText() }) }
                    .getOrElse { DataChecksums("", emptyMap()) }

            val newChecksums = appContext.assets.dataChecksums()

            DataDiff.diff(oldChecksums, newChecksums).sortedByDescending { it.ordinal }.forEach {
                Timber.d("Diff: $it")
                when (it) {
                    is DataDiff.CreateFile,
                    is DataDiff.UpdateFile,
                    -> {
                        val destPath = sharedDataDir.resolveSibling(it.path).absolutePath
                        ResourceUtils.copyFile(it.path, destPath)
                    }
                    is DataDiff.DeleteDir,
                    is DataDiff.DeleteFile,
                    -> FileUtils.delete(sharedDataDir.resolve(it.path.substringAfterLast('/'))).getOrThrow()
                }
            }

            ResourceUtils.copyFile(DATA_CHECKSUMS_NAME, dataDir.resolve(DATA_CHECKSUMS_NAME).absolutePath)

            // 建立預設自訂配置檔案
            // TODO: 內置明月拼音等方案，提供最小開箱即用環境
            runCatching {
                val defaultCustom = File(configDir, DEFAULT_CUSTOM_FILE_NAME)
                if (defaultCustom.createNewFile()) {
                    defaultCustom.writeText(
                        """
                        patch:
                            schema_list: []
                        """.trimIndent(),
                    )
                }
            }.getOrElse { Timber.e(it, "Failed to create default.custom.yaml") }

            Timber.d("Synced!")
        }
}
