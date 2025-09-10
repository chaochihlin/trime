/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.popup

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.osfans.trime.core.RimeProto
import com.osfans.trime.data.theme.Theme
import splitties.views.dsl.core.Ui
import splitties.views.dsl.recyclerview.recyclerView

/**
 * 分頁候選字使用者介面
 *
 * 提供一個可分頁的候選字列表介面，支援水平和垂直佈局模式。
 * 包含候選字項目和分頁控件，支援前後翻頁功能。
 *
 * @param ctx Android 上下文
 * @param theme 主題配置物件
 * @param onCandidateClick 候選字點擊事件回調
 * @param onPrevPage 上一頁按鈕事件回調
 * @param onNextPage 下一頁按鈕事件回調
 */
class PagedCandidatesUi(
    override val ctx: Context,
    val theme: Theme,
    private val onCandidateClick: (Int) -> Unit,
    private val onPrevPage: () -> Unit,
    private val onNextPage: () -> Unit,
) : Ui {
    private var menu = RimeProto.Context.Menu()

    private var isHorizontal = true

    /**
     * 封印類定義的視圖持有者
     *
     * 包含候選字和分頁控件兩種類型的視圖持有者。
     */
    sealed class UiHolder(
        open val ui: Ui,
    ) : RecyclerView.ViewHolder(ui.root) {
        /** 候選字項目的視圖持有者 */
        class Candidate(
            override val ui: LabeledCandidateItemUi,
        ) : UiHolder(ui)

        /** 分頁控件的視圖持有者 */
        class Pagination(
            override val ui: PaginationUi,
        ) : UiHolder(ui)
    }

    /** 候選字列表的適配器，處理候選字和分頁控件的顯示 */
    private val candidatesAdapter =
        object : BaseQuickAdapter<RimeProto.Candidate, UiHolder>() {
            override fun getItemCount(items: List<RimeProto.Candidate>) =
                items.size + (if (menu.pageNumber != 0 || !menu.isLastPage) 1 else 0)

            override fun getItemViewType(
                position: Int,
                list: List<RimeProto.Candidate>,
            ) = if (position < list.size) 0 else 1

            override fun onCreateViewHolder(
                context: Context,
                parent: ViewGroup,
                viewType: Int,
            ): UiHolder =
                when (viewType) {
                    0 -> UiHolder.Candidate(LabeledCandidateItemUi(ctx, theme))
                    else ->
                        UiHolder.Pagination(PaginationUi(ctx, theme)).apply {
                            val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
                            ui.root.layoutParams =
                                FlexboxLayoutManager.LayoutParams(wrap, wrap).apply {
                                    flexGrow = 1f
                                }
                        }
                }

            override fun onBindViewHolder(
                holder: UiHolder,
                position: Int,
                item: RimeProto.Candidate?,
            ) {
                when (holder) {
                    is UiHolder.Candidate -> {
                        val candidate = item ?: return
                        holder.ui.update(candidate, position == menu.highlightedCandidateIndex)
                        holder.ui.root.setOnClickListener {
                            onCandidateClick.invoke(position)
                        }
                    }
                    is UiHolder.Pagination -> {
                        holder.ui.update(menu)
                        holder.ui.root.updateLayoutParams<FlexboxLayoutManager.LayoutParams> {
                            width = if (isHorizontal) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT
                            alignSelf = if (isHorizontal) AlignItems.CENTER else AlignItems.STRETCH
                        }
                        holder.ui.prevIcon.setOnClickListener {
                            onPrevPage.invoke()
                        }
                        holder.ui.nextIcon.setOnClickListener {
                            onNextPage.invoke()
                        }
                    }
                }
            }
        }.apply {
            // We must do this to avoid ArrayIndexOutOfBoundsException
            // https://github.com/google/flexbox-layout/issues/363#issuecomment-382949953
            setHasStableIds(true)
        }

    /** Flexbox 佈局管理器，支援彈性的候選字排列 */
    private val candidatesLayoutManager =
        FlexboxLayoutManager(ctx).apply {
            flexWrap = FlexWrap.WRAP
        }

    override val root =
        recyclerView {
            itemAnimator = null
            isFocusable = false
            adapter = candidatesAdapter
            layoutManager = candidatesLayoutManager
            overScrollMode = View.OVER_SCROLL_NEVER
        }

    /**
     * 更新候選字列表內容和佈局模式
     *
     * 根據 RIME 選單資料和佈局模式更新列表內容和顯示樣式。
     *
     * @param menu RIME 選單資料，包含候選字和分頁資訊
     * @param isHorizontal 是否使用水平佈局模式
     */
    fun update(
        menu: RimeProto.Context.Menu,
        isHorizontal: Boolean,
    ) {
        this.menu = menu
        this.isHorizontal = isHorizontal
        candidatesLayoutManager.apply {
            if (isHorizontal) {
                flexDirection = FlexDirection.ROW
                alignItems = AlignItems.BASELINE
            } else {
                flexDirection = FlexDirection.COLUMN
                alignItems = AlignItems.STRETCH
            }
        }
        candidatesAdapter.submitList(menu.candidates.toList())
    }
}
