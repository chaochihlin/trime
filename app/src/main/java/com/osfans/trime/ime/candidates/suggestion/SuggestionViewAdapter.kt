// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.suggestion

import android.content.Context
import android.os.Build
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.osfans.trime.data.theme.Theme
import splitties.dimensions.dp
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent
import splitties.views.setPaddingDp

/**
 * 建議視圖適配器
 *
 * 負責管理內嵌建議的顯示，將建議資料綁定到視圖元件上。
 *
 * @param theme 主題配置物件，提供樣式設定
 */
class SuggestionViewAdapter(
    private val theme: Theme,
) : BaseQuickAdapter<SuggestionViewItem, SuggestionViewAdapter.ViewHolder>() {
    /**
     * 建議項目的視圖持有者
     *
     * 包裝 SuggestionItemUi 作為 RecyclerView 的 ViewHolder。
     *
     * @param ui 建議項目的使用者介面容器
     */
    inner class ViewHolder(
        val ui: SuggestionItemUi,
    ) : RecyclerView.ViewHolder(ui.root)

    /**
     * 創建視圖持有者
     *
     * 為每個建議項目創建對應的視圖持有者，設定適當的尺寸和內邊距。
     *
     * @param context Android 上下文
     * @param parent 父視圖組
     * @param viewType 視圖類型
     * @return 建議視圖持有者實例
     */
    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val ui = SuggestionItemUi(context)
        ui.root.apply {
            minimumWidth = dp(40)
            val size = theme.generalStyle.candidatePadding
            setPaddingDp(size, 0, size, 0)
            layoutParams = RecyclerView.LayoutParams(wrapContent, matchParent)
        }
        return ViewHolder(ui)
    }

    /**
     * 綁定視圖持有者
     *
     * 將建議資料中的視圖內容添加到持有者的使用者介面中。
     *
     * @param holder 視圖持有者
     * @param position 項目在列表中的位置
     * @param item 建議視圖項目資料
     */
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        item: SuggestionViewItem?,
    ) {
        item?.view ?: return
        holder.ui.addView(item.view)
    }
}
