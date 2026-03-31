// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

import android.content.Context
import com.osfans.trime.core.CandidateItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * T9 字詞資料載入器
 *
 * 從 assets/t9/t9_chars.json 載入 T9 數字鍵常用字映射表，
 * 將資料與邏輯分離，提高可維護性。
 */
object T9CharDataLoader {
    private const val TAG = "T9CharDataLoader"
    private const val DATA_FILE = "t9/t9_chars.json"
    private const val DICT_FILE = "shared/bopomofo_t9.dict.yaml"

    private val json by lazy {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    // 快取載入的資料
    private var cachedData: T9CharData? = null

    // 快取已轉換的 CandidateItem 列表（避免每次呼叫都重新配置）
    private var cachedDigitChars: Map<Int, List<CandidateItem>> = emptyMap()

    // 從 RIME 字典解析的靜態聲調映射（字 → 所有聲調集合）
    // RIME translator 每字只回傳一個讀音，此表補充完整的多音字聲調資訊
    private var dictToneMap: Map<String, Set<String?>> = emptyMap()

    /**
     * 初始化並載入資料
     *
     * @param context Android Context，用於存取 assets
     */
    fun init(context: Context) {
        if (cachedData != null) {
            Timber.d("$TAG: 資料已載入，跳過初始化")
            return
        }

        try {
            val jsonString =
                context.assets
                    .open(DATA_FILE)
                    .bufferedReader()
                    .use { it.readText() }
            cachedData = json.decodeFromString<T9CharData>(jsonString)
            // 預先建立 CandidateItem 快取
            cachedDigitChars =
                cachedData?.digitChars?.entries?.associate { (key, items) ->
                    key.toInt() to items.map { CandidateItem(it.text, it.comment) }
                } ?: emptyMap()
            Timber.d("$TAG: 成功載入 T9 字詞資料，版本: ${cachedData?.version}")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 載入 T9 字詞資料失敗")
            cachedData = T9CharData.EMPTY
        }

        // 從 RIME 字典建立靜態聲調映射
        try {
            dictToneMap = loadDictToneMap(context)
            Timber.d("$TAG: 靜態聲調映射載入完成，共 ${dictToneMap.size} 字")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 載入靜態聲調映射失敗")
        }
    }

    // 從 T9ZhuyinMapper.TONE_MARKS 衍生，避免每行都 toString() 產生新 String
    private val TONE_STRING_CACHE = T9ZhuyinMapper.TONE_MARKS.associateWith { it.toString() }

    /**
     * 從 bopomofo_t9.dict.yaml 解析所有字的聲調映射
     * 格式：字\t注音(含聲調)\t權重
     */
    private fun loadDictToneMap(context: Context): Map<String, Set<String?>> {
        val map = mutableMapOf<String, MutableSet<String?>>()
        context.assets.open(DICT_FILE).bufferedReader().use { reader ->
            var inEntries = false
            for (line in reader.lineSequence()) {
                if (!inEntries) {
                    if (line == "...") inEntries = true
                    continue
                }
                if (line.isBlank() || line.startsWith("#")) continue
                val parts = line.split('\t')
                if (parts.size < 2) continue
                val char = parts[0]
                if (char.length != 1) continue
                val zhuyin = parts[1]
                val tone = zhuyin.lastOrNull()?.let { TONE_STRING_CACHE[it] }
                map.getOrPut(char) { mutableSetOf() }.add(tone)
            }
        }
        return map
    }

    /**
     * 取得靜態聲調映射表（從字典解析，含完整多音字資訊）
     */
    fun getDictToneMap(): Map<String, Set<String?>> = dictToneMap

    /**
     * 取得指定數字鍵的候選字列表
     *
     * @param digit 數字鍵 (0-9)
     * @return 候選字列表
     */
    fun getDigitChars(digit: Int): List<CandidateItem> = cachedDigitChars[digit] ?: emptyList()

    /**
     * 取得指定注音的單韻母/單介音字列表
     *
     * @param zhuyin 注音符號，如 "ㄚ"
     * @return 候選字列表
     */
    fun getSingleVowelChars(zhuyin: String): List<CandidateItem> {
        val data = cachedData ?: return emptyList()
        return data.singleVowels[zhuyin]?.map {
            CandidateItem(it.text, it.comment)
        } ?: emptyList()
    }

    /**
     * 檢查指定注音是否有對應的單韻母/單介音字
     *
     * @param zhuyin 注音符號
     * @return 是否有對應的補充字
     */
    fun hasSingleVowelChars(zhuyin: String): Boolean {
        val data = cachedData ?: return false
        return data.singleVowels.containsKey(zhuyin)
    }

    /**
     * 檢查資料是否已載入
     */
    fun isLoaded(): Boolean = cachedData != null && cachedData != T9CharData.EMPTY
}

/**
 * T9 字詞資料結構
 */
@Serializable
data class T9CharData(
    val version: String = "1.0",
    val description: String = "",
    val digitChars: Map<String, List<T9CharItem>> = emptyMap(),
    val singleVowels: Map<String, List<T9CharItem>> = emptyMap(),
) {
    companion object {
        val EMPTY = T9CharData()
    }
}

/**
 * 單個字詞項目
 */
@Serializable
data class T9CharItem(
    val text: String,
    val comment: String = "",
)
