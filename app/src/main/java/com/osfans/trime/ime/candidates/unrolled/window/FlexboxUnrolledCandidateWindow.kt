// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.unrolled.window

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import androidx.transition.Slide
import androidx.transition.Transition
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.QuickBar
import com.osfans.trime.ime.candidates.CandidateViewHolder
import com.osfans.trime.ime.candidates.compact.CompactCandidateModule
import com.osfans.trime.ime.candidates.unrolled.PagingCandidateViewAdapter
import com.osfans.trime.ime.candidates.unrolled.UnrolledCandidateLayout
import com.osfans.trime.ime.candidates.unrolled.decoration.FlexboxHorizontalDecoration
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import splitties.dimensions.dp
import splitties.views.dsl.core.wrapContent
import splitties.views.setPaddingDp

/**
 * 使用 Flexbox 佈局的展開式候選字視窗
 *
 * 使用 FlexboxLayoutManager 來實現彈性的候選字佈局，
 * 支援自動換行和空間分配。提供向上滑出的動畫效果。
 *
 * @param context Android 應用程式上下文
 * @param service Trime 輸入法服務實例
 * @param rime RIME 輸入引擎會話物件
 * @param theme 主題配置物件
 * @param bar 快速工具列元件
 * @param windowManager 視窗管理器
 * @param compactCandidate 緊密式候選字模組
 */
class FlexboxUnrolledCandidateWindow(
    context: Context,
    service: TrimeInputMethodService,
    rime: RimeSession,
    theme: Theme,
    bar: QuickBar,
    windowManager: BoardWindowManager,
    compactCandidate: CompactCandidateModule,
) : BaseUnrolledCandidateWindow(context, service, rime, theme, bar, windowManager, compactCandidate) {
    /**
     * 定義視窗離開時的動畫效果
     *
     * 使用向上滑出的動畫效果。
     *
     * @param nextWindow 下一個視窗
     * @return 動畫轉場
     */
    override fun exitAnimation(nextWindow: BoardWindow): Transition =
        Slide().apply {
            slideEdge = Gravity.TOP
        }

    /** 使用 Flexbox 佈局的分頁候選字適配器 */
    override val adapter by lazy {
        object : PagingCandidateViewAdapter(theme) {
            override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int,
            ): CandidateViewHolder =
                super.onCreateViewHolder(parent, viewType).apply {
                    itemView.apply {
                        minimumWidth = dp(40)
                        val size = theme.generalStyle.candidatePadding
                        setPaddingDp(size, 0, size, 0)
                        layoutParams =
                            FlexboxLayoutManager
                                .LayoutParams(wrapContent, dp(theme.generalStyle.run { candidateViewHeight + commentHeight }))
                                .apply { flexGrow = 1f }
                    }
                }

            override fun onBindViewHolder(
                holder: CandidateViewHolder,
                position: Int,
            ) {
                super.onBindViewHolder(holder, position)
                bindCandidateUiViewHolder(holder)
            }
        }
    }

    /** Flexbox 佈局管理器，支援彈性排列和空間分配 */
    override val layoutManager by lazy {
        FlexboxLayoutManager(context).apply {
            justifyContent = JustifyContent.SPACE_AROUND
            alignItems = AlignItems.FLEX_START
        }
    }

    /**
     * 創建候選字佈局容器
     *
     * 創建並配置使用 Flexbox 佈局的候選字容器，
     * 添加水平分隔裝飾。
     *
     * @return 配置好的展開式候選字佈局容器
     */
    override fun onCreateCandidateLayout(): UnrolledCandidateLayout =
        UnrolledCandidateLayout(context, theme).apply {
            recyclerView.apply {
                adapter = this@FlexboxUnrolledCandidateWindow.adapter
                layoutManager = this@FlexboxUnrolledCandidateWindow.layoutManager
                addItemDecoration(FlexboxHorizontalDecoration(separatorDrawable))
            }
        }
}
