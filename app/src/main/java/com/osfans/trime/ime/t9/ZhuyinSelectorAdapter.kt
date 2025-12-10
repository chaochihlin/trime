// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import splitties.dimensions.dp

/**
 * 注音選擇器的 RecyclerView Adapter
 *
 * 遵循專案的 Adapter 模式（參考 T9WhiteCandidateAdapter），使用 BRAVH 4.1.7。
 * 負責管理注音選項列表的顯示和選中狀態。
 */
class ZhuyinSelectorAdapter : BaseQuickAdapter<ZhuyinItem, ZhuyinSelectorViewHolder>() {
    private var selectedPosition: Int = 0

    /**
     * 設置注音列表
     *
     * @param zhuyinList 注音符號列表（如 ["ㄏ", "ㄒ", "ㄠ", "ㄩ"]）
     * @param preselectedIndex 預選的注音索引
     */
    fun setZhuyinList(
        zhuyinList: List<String>,
        preselectedIndex: Int = 0,
    ) {
        selectedPosition = preselectedIndex.coerceIn(0, (zhuyinList.size - 1).coerceAtLeast(0))

        val items =
            zhuyinList.mapIndexed { index, zhuyin ->
                ZhuyinItem(
                    zhuyin = zhuyin,
                    index = index,
                    isSelected = index == selectedPosition,
                )
            }
        submitList(items)
    }

    /**
     * 設置選中位置
     *
     * @param position 要選中的位置
     */
    fun setSelectedPosition(position: Int) {
        if (position == selectedPosition || position < 0 || position >= itemCount) return

        val oldPosition = selectedPosition
        selectedPosition = position

        // 更新選中狀態
        items.getOrNull(oldPosition)?.isSelected = false
        items.getOrNull(position)?.isSelected = true

        // 通知更新
        notifyItemChanged(oldPosition)
        notifyItemChanged(position)
    }

    /**
     * 取得當前選中位置
     */
    fun getSelectedPosition(): Int = selectedPosition

    /**
     * 取得當前選中的注音符號
     */
    fun getSelectedZhuyin(): String? = items.getOrNull(selectedPosition)?.zhuyin

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): ZhuyinSelectorViewHolder {
        val ui = ZhuyinSelectorItemUi(context)
        ui.root.layoutParams =
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                context.dp(ZhuyinSelectorItemUi.ITEM_HEIGHT_DP),
            )
        return ZhuyinSelectorViewHolder(ui)
    }

    override fun onBindViewHolder(
        holder: ZhuyinSelectorViewHolder,
        position: Int,
        item: ZhuyinItem?,
    ) {
        item ?: return
        holder.ui.update(item)
    }
}

/**
 * 注音選擇器的 ViewHolder
 */
class ZhuyinSelectorViewHolder(
    val ui: ZhuyinSelectorItemUi,
) : RecyclerView.ViewHolder(ui.root)
