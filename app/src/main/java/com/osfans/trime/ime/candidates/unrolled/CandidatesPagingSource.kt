// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.unrolled

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.osfans.trime.core.CandidateItem
import com.osfans.trime.daemon.RimeSession
import timber.log.Timber

/**
 * 候選字分頁資料來源
 * 
 * 實現 Paging3 的 PagingSource，用於懶加載候選字資料。
 * 支援分頁載入大量候選字，提高性能和使用者體驗。
 * 
 * @param rime RIME 輸入引擎會話物件
 * @param offset 起始偏移量，用於從指定位置開始載入
 */
class CandidatesPagingSource(
    val rime: RimeSession,
    val offset: Int,
) : PagingSource<Int, CandidateItem>() {
    /**
     * 載入候選字資料
     * 
     * 根據載入參數從 RIME 引擎取得指定範圍的候選字資料，
     * 並計算前後頁的索引鍵值。
     * 
     * @param params 加載參數，包含鍵值和頁面大小
     * @return 加載結果，包含資料和前後頁鍵值
     */
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CandidateItem> {
        // use candidate index for key, null means load from beginning (including offset)
        val startIndex = params.key ?: offset
        val pageSize = params.loadSize
        Timber.d("getCandidates(offset=$startIndex, limit=$pageSize)")
        val candidates =
            rime.runOnReady {
                getCandidates(startIndex, pageSize)
            }
        val prevKey = if (startIndex >= pageSize) startIndex - pageSize else null
        val nextKey = if (candidates.size < pageSize) null else startIndex + pageSize
        return LoadResult.Page(candidates.toList(), prevKey, nextKey)
    }

    /**
     * 取得重新整理的鍵值
     * 
     * 總是從開始位置重新載入，因此返回 null。
     * 
     * @param state 分頁狀態
     * @return 總是返回 null，表示從頭開始載入
     */
    override fun getRefreshKey(state: PagingState<Int, CandidateItem>) = null
}
