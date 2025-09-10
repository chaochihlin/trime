// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.compact

import android.content.Context
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import com.chad.library.adapter4.BaseQuickAdapter
import com.osfans.trime.core.CandidateItem
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.candidates.CandidateItemUi
import com.osfans.trime.ime.candidates.CandidateViewHolder
import splitties.dimensions.dp
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent
import splitties.views.setPaddingDp

/**
 * 簡潔型候選字視圖適配器
 *
 * 此適配器負責將候選字資料綁定到視圖元件，管理候選字列表的顯示狀態，
 * 包括分頁資訊、高亮索引和前置偏移量。使用簡化的 LinearLayoutManager（手錶裝置優化版）
 * 提供彈性的佈局效果。
 *
 * @param theme 主題配置物件，控制候選字的視覺樣式
 */
open class CompactCandidateViewAdapter(
    val theme: Theme,
) : BaseQuickAdapter<CandidateItem, CandidateViewHolder>() {
    /** 是否為最後一頁候選字 */
    var isLastPage: Boolean = false
        private set

    /** 前置候選字的數量，用於計算絕對索引 */
    var previous: Int = 0
        private set

    /** 當前高亮顯示的候選字索引 */
    var highlightedIdx: Int = -1
        private set

    /**
     * 更新候選字列表資料
     *
     * 同時更新候選字列表內容和相關狀態資訊，包括分頁狀態、
     * 前置偏移量和高亮索引。
     *
     * @param list 新的候選字列表
     * @param isLastPage 是否為最後一頁
     * @param previous 前置候選字數量
     * @param highlightedIdx 高亮候選字索引
     */
    fun updateCandidates(
        list: List<CandidateItem>,
        isLastPage: Boolean,
        previous: Int,
        highlightedIdx: Int,
    ) {
        this.isLastPage = isLastPage
        this.previous = previous
        this.highlightedIdx = highlightedIdx
        super.submitList(list)
    }

    /**
     * 創建候選字視圖持有者
     *
     * 為每個候選字項目創建對應的視圖持有者，設定適當的佈局參數和內邊距。
     *
     * @param context Android 上下文
     * @param parent 父視圖組
     * @param viewType 視圖類型
     * @return 候選字視圖持有者實例
     */
    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): CandidateViewHolder {
        val ui = CandidateItemUi(context, theme)
        ui.root.apply {
            minimumWidth = dp(40)
            val size = theme.generalStyle.candidatePadding
            setPaddingDp(size, 0, size, 0)
            layoutParams = ViewGroup.MarginLayoutParams(wrapContent, matchParent)
        }
        return CandidateViewHolder(ui)
    }

    /**
     * 綁定候選字視圖持有者
     *
     * 將候選字資料綁定到視圖上，設定高亮狀態、註解顯示和佈局參數。
     *
     * @param holder 視圖持有者
     * @param position 項目在列表中的位置
     * @param item 候選字項目資料
     */
    override fun onBindViewHolder(
        holder: CandidateViewHolder,
        position: Int,
        item: CandidateItem?,
    ) {
        item ?: return
        val isHighlighted = theme.generalStyle.candidateUseCursor && position == highlightedIdx
        val obtainComment = items.any { it.comment.isNotEmpty() }
        holder.ui.update(item, isHighlighted, obtainComment)
        holder.text = item.text
        holder.comment = item.comment
        holder.idx = previous + position // unused
        holder.ui.root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            // 簡化版本不需要 flexGrow 和 minWidth 設定
        }
    }
}
