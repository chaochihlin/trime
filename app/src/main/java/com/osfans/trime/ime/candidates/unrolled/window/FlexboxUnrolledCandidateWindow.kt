// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.unrolled.window

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.Slide
import androidx.transition.Transition
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.QuickBar
import com.osfans.trime.ime.candidates.CandidateViewHolder
import com.osfans.trime.ime.candidates.compact.CompactCandidateModule
import com.osfans.trime.ime.candidates.unrolled.PagingCandidateViewAdapter
import com.osfans.trime.ime.candidates.unrolled.UnrolledCandidateLayout
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import splitties.dimensions.dp
import splitties.views.setPaddingDp

/**
 * 簡化的展開式候選字視窗（手錶裝置優化版）
 *
 * 使用簡單的 LinearLayoutManager 來實現候選字佈局，
 * 移除複雜的 Flexbox 功能以節省記憶體。保留基本的展開功能和動畫效果。
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

    /** 簡化的分頁候選字適配器（手錶裝置優化版） */
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
                        // 使用簡單的 LinearLayout.LayoutParams 替代 Flexbox
                        layoutParams =
                            ViewGroup.MarginLayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                dp(theme.generalStyle.run { candidateViewHeight + commentHeight }),
                            )
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

    /** 簡化的線性佈局管理器（手錶裝置優化版） */
    override val layoutManager by lazy {
        LinearLayoutManager(context).apply {
            orientation = LinearLayoutManager.VERTICAL
        }
    }

    /**
     * 創建候選字佈局容器（手錶裝置優化版）
     *
     * 創建並配置使用簡單線性佈局的候選字容器，
     * 移除裝飾元件以節省記憶體。
     *
     * @return 配置好的展開式候選字佈局容器
     */
    override fun onCreateCandidateLayout(): UnrolledCandidateLayout =
        UnrolledCandidateLayout(context, theme).apply {
            recyclerView.apply {
                adapter = this@FlexboxUnrolledCandidateWindow.adapter
                layoutManager = this@FlexboxUnrolledCandidateWindow.layoutManager
                // 移除裝飾元件以節省記憶體
            }
        }
}
