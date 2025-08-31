// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

/**
 * 資料庫存取物件介面
 *
 * 使用 Room 框架定義的 DAO 介面，提供對剪貼簿歷史資料的 CRUD 操作。
 * 支援文本的儲存、更新、刪除和查詢功能，包括釘選狀態管理和時間戳處理。
 * 所有方法都使用協程以支援非同步資料庫操作。
 *
 * @see DatabaseBean
 * @see ClipboardHelper
 */
@Dao
interface DatabaseDao {
    /**
     * 插入新的資料記錄
     *
     * @param bean 要插入的資料庫實體
     * @return 插入記錄的行 ID
     */
    @Insert
    suspend fun insert(bean: DatabaseBean): Long

    /**
     * 更新現有的資料記錄
     *
     * @param bean 要更新的資料庫實體
     */
    @Update
    suspend fun update(bean: DatabaseBean)

    /**
     * 更新指定記錄的文本內容
     *
     * @param id 記錄的唯一識別碼
     * @param newText 新的文本內容
     */
    @Query("UPDATE ${DatabaseBean.TABLE_NAME} SET text=:newText WHERE id=:id")
    suspend fun updateText(
        id: Int,
        newText: String,
    )

    /**
     * 更新指定記錄的釘選狀態
     *
     * @param id 記錄的唯一識別碼
     * @param pinned 是否釘選
     */
    @Query("UPDATE ${DatabaseBean.TABLE_NAME} SET pinned=:pinned WHERE id=:id")
    suspend fun updatePinned(
        id: Int,
        pinned: Boolean,
    )

    /**
     * 更新指定記錄的時間戳
     *
     * @param id 記錄的唯一識別碼
     * @param timestamp 新的時間戳
     */
    @Query("UPDATE ${DatabaseBean.TABLE_NAME} SET time=:timestamp WHERE id=:id")
    suspend fun updateTime(
        id: Int,
        timestamp: Long,
    )

    /**
     * 刪除指定的資料記錄
     *
     * @param bean 要刪除的資料庫實體
     */
    @Delete
    suspend fun delete(bean: DatabaseBean)

    /**
     * 根據文本內容刪除記錄
     *
     * @param text 要刪除的文本內容
     */
    @Query("DELETE FROM ${DatabaseBean.TABLE_NAME} WHERE text=:text")
    suspend fun delete(text: String)

    /**
     * 根據 ID 刪除記錄
     *
     * @param id 要刪除記錄的唯一識別碼
     */
    @Query("DELETE FROM ${DatabaseBean.TABLE_NAME} WHERE id=:id")
    suspend fun delete(id: Int)

    /**
     * 批次刪除多筆資料記錄
     *
     * @param beans 要刪除的資料庫實體列表
     */
    @Delete
    suspend fun delete(beans: List<DatabaseBean>)

    /**
     * 刪除所有資料記錄
     */
    @Query("DELETE FROM ${DatabaseBean.TABLE_NAME}")
    suspend fun deleteAll()

    /**
     * 刪除所有未釘選的記錄
     */
    @Query("DELETE FROM ${DatabaseBean.TABLE_NAME} WHERE NOT pinned")
    suspend fun deleteAllUnpinned()

    /**
     * 刪除指定時間之前的未釘選記錄
     *
     * @param timestamp 時間戳界限，早於此時間的未釘選記錄將被刪除
     */
    @Query("DELETE FROM ${DatabaseBean.TABLE_NAME} WHERE time<:timestamp AND pinned=0")
    suspend fun deletedUnpinnedEarlierThan(timestamp: Long)

    /**
     * 取得所有資料記錄
     *
     * 按釘選狀態降序、時間降序排列，釘選的記錄會優先顯示
     *
     * @return 所有資料記錄的列表
     */
    @Query("SELECT * FROM ${DatabaseBean.TABLE_NAME} ORDER BY pinned DESC, time DESC")
    suspend fun getAll(): List<DatabaseBean>

    /**
     * 根據 ID 取得單筆記錄
     *
     * @param id 記錄的唯一識別碼
     * @return 符合條件的記錄，若不存在則返回 null
     */
    @Query("SELECT * FROM ${DatabaseBean.TABLE_NAME} WHERE id=:id LIMIT 1")
    suspend fun get(id: Int): DatabaseBean?

    /**
     * 根據行 ID 取得單筆記錄
     *
     * @param rowId 記錄的行 ID
     * @return 符合條件的記錄，若不存在則返回 null
     */
    @Query("SELECT * FROM ${DatabaseBean.TABLE_NAME} WHERE rowId=:rowId LIMIT 1")
    suspend fun get(rowId: Long): DatabaseBean?

    /**
     * 檢查是否有未釘選的記錄
     *
     * @return 如果存在未釘選的記錄則返回 true
     */
    @Query("SELECT EXISTS(SELECT 1 FROM ${DatabaseBean.TABLE_NAME} WHERE pinned=0)")
    suspend fun haveUnpinned(): Boolean

    /**
     * 取得所有未釘選的記錄
     *
     * @return 所有未釘選記錄的列表
     */
    @Query("SELECT * FROM ${DatabaseBean.TABLE_NAME} WHERE pinned=0")
    suspend fun getAllUnpinned(): List<DatabaseBean>

    /**
     * 根據文本內容查找記錄
     *
     * @param text 要查找的文本內容
     * @return 符合條件的記錄，若不存在則返回 null
     */
    @Query("SELECT * FROM ${DatabaseBean.TABLE_NAME} WHERE text=:text LIMIT 1")
    suspend fun find(text: String): DatabaseBean?

    /**
     * 統計資料表中的記錄數量
     *
     * @return 總記錄數
     */
    @Query("SELECT COUNT(*) FROM ${DatabaseBean.TABLE_NAME}")
    suspend fun itemCount(): Int
}
