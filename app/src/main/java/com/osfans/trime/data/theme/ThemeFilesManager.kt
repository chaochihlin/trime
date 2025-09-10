/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.charleskorn.kaml.AnchorsAndAliases
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlNamingStrategy
import com.charleskorn.kaml.yamlMap
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.util.getString
import timber.log.Timber
import java.io.File

/**
 * 主題檔案管理器
 *
 * 負責掃描、解析和管理 Trime 輸入法的主題檔案。主題檔案以 YAML 格式儲存，
 * 檔案名稱必須以 "trime.yaml" 結尾。此管理器提供主題發現、名稱解析和錯誤處理功能。
 */
object ThemeFilesManager {
    /** YAML 檔案大小限制，設為 10 MB 以防止記憶體溢出 */
    private const val CODE_POINT_LIMIT = 10 * 1024 * 1024 // 10 MB

    /**
     * 預設的 YAML 解析器配置
     *
     * 設定彈性解析模式以支援各種主題檔案格式：
     * - 非嚴格模式：允許未知屬性
     * - 蛇形命名策略：支援 snake_case 屬性名稱
     * - 大小寫不敏感枚舉：提高相容性
     * - 允許錨點和別名：支援 YAML 引用功能
     */
    val yaml =
        Yaml(
            configuration =
                YamlConfiguration(
                    strictMode = false,
                    yamlNamingStrategy = YamlNamingStrategy.SnakeCase,
                    decodeEnumCaseInsensitive = true,
                    anchorsAndAliases = AnchorsAndAliases.Permitted(null),
                    codePointLimit = CODE_POINT_LIMIT, // 10 MB
                ),
        )

    /**
     * 列舉指定目錄中的所有可用主題
     *
     * 掃描指定目錄中所有以 "trime.yaml" 結尾的檔案，解析其內容並建立主題項目清單。
     * 主題按檔案修改時間降序排列，確保最新的主題出現在前面。
     *
     * @param dir 要掃描的目錄
     * @return 主題項目的可變清單，按修改時間降序排列。如果目錄不存在或無法讀取，返回空清單
     */
    fun listThemes(dir: File): MutableList<ThemeItem> {
        val files = dir.listFiles { _, name -> name.endsWith("trime.yaml") } ?: return mutableListOf()
        val deployedMap = hashMapOf<String, String>()
        // 檢查編譯結果目錄
        DataManager.buildDir.list()?.forEach {
            deployedMap[it] = it
        }
        // 檢查預建資源目錄
        DataManager.assetsDir.list()?.forEach {
            deployedMap[it] = it
        }
        return files
            .sortedByDescending { it.lastModified() }
            .mapNotNull decode@{
                val item =
                    runCatching {
                        val configId = it.nameWithoutExtension
                        val name =
                            if (deployedMap[it.name] != null) {
                                val file = File(DataManager.resolveDeployedResourcePath(configId))
                                val node = yaml.parseToYamlNode(file.readText()).yamlMap
                                node.getString("name")
                            } else {
                                configId.removeSuffix(".trime")
                            }
                        ThemeItem(configId, name)
                    }.getOrElse { e ->
                        Timber.w("Failed to decode theme file ${it.absolutePath}: ${e.message}")
                        return@decode null
                    }
                return@decode item
            }.toMutableList()
    }
}
