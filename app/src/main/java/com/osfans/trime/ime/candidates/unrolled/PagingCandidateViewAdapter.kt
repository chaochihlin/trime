/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.unrolled

import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.osfans.trime.core.CandidateItem
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.candidates.CandidateItemUi
import com.osfans.trime.ime.candidates.CandidateViewHolder

/**
 * 分頁候選字視圖適配器
 * 
 * 使用 Paging3 庫的 PagingDataAdapter 來處理候選字的分頁顯示。
 * 支援懶加載和效能優化，適用於大量候選字的展開顯示。
 * 
 * @param theme 主題配置物件，提供視覺樣式設定
 */
open class PagingCandidateViewAdapter(
    val theme: Theme,
) : PagingDataAdapter<CandidateItem, CandidateViewHolder>(diffCallback) {
    companion object {
        /** DiffUtil 回調，用於計算候選字項目之間的差異 */
        private val diffCallback =
            object : DiffUtil.ItemCallback<CandidateItem>() {
                override fun areItemsTheSame(
                    oldItem: CandidateItem,
                    newItem: CandidateItem,
                ): Boolean = oldItem === newItem

                override fun areContentsTheSame(
                    oldItem: CandidateItem,
                    newItem: CandidateItem,
                ): Boolean = oldItem == newItem
            }
    }

    /** 當前分頁的偏移量，用於計算候選字的絕對索引 */
    var offset: Int = 0
        private set

    /**
     * 使用新的偏移量重新整理資料
     * 
     * 更新偏移量並觸發資料重新整理，用於展開候選字視窗時同步狀態。
     * 
     * @param offset 新的偏移量
     */
    fun refreshWithOffset(offset: Int) {
        this.offset = offset
        refresh()
    }

    /**
     * 創建視圖持有者
     * 
     * 為每個候選字項目創建對應的視圖持有者。
     * 
     * @param parent 父視圖組
     * @param viewType 視圖類型
     * @return 候選字視圖持有者
     */
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): CandidateViewHolder = CandidateViewHolder(CandidateItemUi(parent.context, theme))

    /**
     * 綁定視圖持有者
     * 
     * 將候選字資料綁定到視圖持有者，設定文字內容和索引。
     * 
     * @param holder 視圖持有者
     * @param position 項目在列表中的位置
     */
    override fun onBindViewHolder(
        holder: CandidateViewHolder,
        position: Int,
    ) {
        val item = getItem(position) ?: return
        val obtainComment = snapshot().items.any { it.comment.isNotEmpty() }
        holder.ui.update(item, false, obtainComment)
        holder.text = item.text
        holder.comment = item.comment
        holder.idx = position + offset
    }
}
