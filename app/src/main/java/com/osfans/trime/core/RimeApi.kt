// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.core

import kotlinx.coroutines.flow.SharedFlow

/**
 * RIME 輸入法引擎 API 介面
 *
 * 定義與 RIME 輸入法引擎交互的核心 API，提供輸入處理、候選詞管理、
 * 方案配置、狀態查詢等功能。所有方法都使用協程以支援非同步操作。
 *
 * 此介面透過 Flow 提供狀態和訊息的響應式更新，確保 UI 層能夠
 * 即時回應輸入法引擎的狀態變化。
 *
 * @see RimeSession
 * @see RimeProto
 * @see RimeLifecycle
 */
interface RimeApi {
    /** RIME 訊息的響應式資料流，用於接收引擎狀態更新 */
    val messageFlow: SharedFlow<RimeMessage<*>>

    /** RIME 生命週期狀態的響應式資料流 */
    val stateFlow: SharedFlow<RimeLifecycle.State>

    /** 輸入法引擎是否就緒可用 */
    val isReady: Boolean

    /** 快取的輸入法狀態資訊 */
    val statusCached: RimeProto.Status

    /** 快取的組合輸入資訊 */
    val compositionCached: RimeProto.Context.Composition

    /** 快取的候選詞選單資訊 */
    val menuCached: RimeProto.Context.Menu

    /** 快取的原始輸入字串 */
    val rawInputCached: String

    /**
     * 檢查輸入法引擎是否為空閒狀態
     *
     * @return 如果沒有正在進行的輸入或組合則返回 true
     */
    suspend fun isEmpty(): Boolean

    /**
     * 同步使用者資料
     *
     * @return 同步操作是否成功
     */
    suspend fun syncUserData(): Boolean

    /**
     * 處理按鍵輸入（使用原始按鍵值）
     *
     * @param value 按鍵值
     * @param modifiers 修飾鍵標誌位，預設為 0
     * @return 按鍵是否被成功處理
     */
    suspend fun processKey(
        value: Int,
        modifiers: UInt = 0u,
    ): Boolean

    /**
     * 處理按鍵輸入（使用封裝的按鍵類型）
     *
     * @param value 按鍵值封裝
     * @param modifiers 修飾鍵封裝
     * @return 按鍵是否被成功處理
     */
    suspend fun processKey(
        value: KeyValue,
        modifiers: KeyModifiers,
    ): Boolean

    /**
     * 選擇候選詞
     *
     * @param idx 候選詞在當前頁面的索引
     * @return 選擇操作是否成功
     */
    suspend fun selectCandidate(idx: Int): Boolean

    /**
     * 從使用者詞典中移除候選詞
     *
     * @param idx 要移除的候選詞索引
     * @return 移除操作是否成功
     */
    suspend fun forgetCandidate(idx: Int): Boolean

    /**
     * 選擇分頁候選詞
     *
     * @param idx 候選詞在分頁中的索引
     * @return 選擇操作是否成功
     */
    suspend fun selectPagedCandidate(idx: Int): Boolean

    /**
     * 刪除分頁候選詞
     *
     * @param idx 要刪除的候選詞索引
     * @return 刪除操作是否成功
     */
    suspend fun deletedPagedCandidate(idx: Int): Boolean

    /**
     * 切換候選詞頁面
     *
     * @param backward 是否向前翻頁，false 為向後翻頁
     * @return 翻頁操作是否成功
     */
    suspend fun changeCandidatePage(backward: Boolean): Boolean

    /**
     * 移動游標位置
     *
     * @param position 新的游標位置
     */
    suspend fun moveCursorPos(position: Int)

    /**
     * 取得所有可用的輸入方案
     *
     * @return 可用的輸入方案陣列
     */
    suspend fun availableSchemata(): Array<SchemaItem>

    /**
     * 取得已啟用的輸入方案
     *
     * @return 已啟用的輸入方案陣列
     */
    suspend fun enabledSchemata(): Array<SchemaItem>

    /**
     * 設定啟用的輸入方案
     *
     * @param schemaIds 要啟用的方案識別碼陣列
     * @return 設定操作是否成功
     */
    suspend fun setEnabledSchemata(schemaIds: Array<String>): Boolean

    /**
     * 取得已選擇的輸入方案
     *
     * @return 已選擇的輸入方案陣列
     */
    suspend fun selectedSchemata(): Array<SchemaItem>

    /**
     * 取得當前選擇的輸入方案識別碼
     *
     * @return 當前輸入方案的識別碼
     */
    suspend fun selectedSchemaId(): String

    /**
     * 選擇輸入方案
     *
     * @param schemaId 要選擇的方案識別碼
     * @return 選擇操作是否成功
     */
    suspend fun selectSchema(schemaId: String): Boolean

    /**
     * 提交當前的組合輸入
     *
     * @return 提交操作是否成功
     */
    suspend fun commitComposition(): Boolean

    /**
     * 清除當前的組合輸入
     */
    suspend fun clearComposition()

    /**
     * 設定執行時選項
     *
     * @param option 選項名稱
     * @param value 選項值
     */
    suspend fun setRuntimeOption(
        option: String,
        value: Boolean,
    )

    /**
     * 取得執行時選項的值
     *
     * @param option 選項名稱
     * @return 選項的布林值
     */
    suspend fun getRuntimeOption(option: String): Boolean

    /**
     * 取得指定範圍的候選詞
     *
     * @param startIndex 開始索引
     * @param limit 候選詞數量限制
     * @return 候選詞項目陣列
     */
    suspend fun getCandidates(
        startIndex: Int,
        limit: Int,
    ): Array<CandidateItem>
}
