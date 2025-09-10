// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.unrolled.window

import android.content.Context
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.view.View
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.QuickBar
import com.osfans.trime.ime.bar.UnrollButtonStateMachine
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.candidates.CandidateViewHolder
import com.osfans.trime.ime.candidates.compact.CompactCandidateModule
import com.osfans.trime.ime.candidates.unrolled.CandidatesPagingSource
import com.osfans.trime.ime.candidates.unrolled.PagingCandidateViewAdapter
import com.osfans.trime.ime.candidates.unrolled.UnrolledCandidateLayout
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import splitties.dimensions.dp
import kotlin.math.max

/**
 * 展開式候選字視窗的抽象基礎類
 *
 * 提供展開式候選字視窗的核心功能，包括分頁加載、点擊互动、
 * 狀態管理等。子類需要實現具體的佈局管理器和適配器。
 *
 * @param context Android 應用程式上下文
 * @param service Trime 輸入法服務實例
 * @param rime RIME 輸入引擎會話物件
 * @param theme 主題配置物件
 * @param bar 快速工具列元件
 * @param windowManager 視窗管理器
 * @param compactCandidate 緊密式候選字模組
 */
abstract class BaseUnrolledCandidateWindow(
    protected val context: Context,
    protected val service: TrimeInputMethodService,
    protected val rime: RimeSession,
    protected val theme: Theme,
    private val bar: QuickBar,
    private val windowManager: BoardWindowManager,
    private val compactCandidate: CompactCandidateModule,
) : BoardWindow.NoBarBoardWindow(),
    InputBroadcastReceiver {
    private lateinit var lifecycleCoroutineScope: LifecycleCoroutineScope
    private lateinit var candidateLayout: UnrolledCandidateLayout

    /** 候選字項目間的分隔線繪製物件 */
    protected val separatorDrawable by lazy {
        ShapeDrawable(RectShape()).apply {
            val spacing = theme.generalStyle.candidateSpacing
            val intrinsicSize = max(spacing, context.dp(spacing)).toInt()
            intrinsicWidth = intrinsicSize
            intrinsicHeight = intrinsicSize
            paint.color = ColorManager.getColor("candidate_separator_color")
        }
    }

    /**
     * 創建候選字佈局容器
     *
     * 子類必須實現此方法來提供具體的佈局實現。
     *
     * @return 展開式候選字佈局容器
     */
    abstract fun onCreateCandidateLayout(): UnrolledCandidateLayout

    /**
     * 創建視窗視圖
     *
     * 創建并配置候選字佈局容器，停用項目動畫以提高性能。
     *
     * @return 配置好的視圖
     */
    final override fun onCreateView(): View {
        candidateLayout =
            onCreateCandidateLayout().apply {
                recyclerView.apply {
                    // disable item cross-fade animation
                    itemAnimator = null
                }
            }
        return candidateLayout
    }

    /** 分頁候選字視圖適配器，由子類實現 */
    abstract val adapter: PagingCandidateViewAdapter

    /** RecyclerView 的佈局管理器，由子類實現 */
    abstract val layoutManager: RecyclerView.LayoutManager

    private var offsetJob: Job? = null

    /** 候選字分頁加載器，使用 Paging3 庫 */
    private val candidatesPager by lazy {
        Pager(PagingConfig(pageSize = 48)) {
            CandidatesPagingSource(
                rime,
                offset = adapter.offset,
            )
        }
    }

    private var candidatesSubmitJob: Job? = null

    /**
     * 視窗附加時的初始化操作
     *
     * 設置生命週期範圍、狀態更新、偏移量監聽和分頁資料的提交。
     */
    override fun onAttached() {
        lifecycleCoroutineScope = candidateLayout.findViewTreeLifecycleOwner()!!.lifecycleScope
        bar.unrollButtonStateMachine.push(UnrollButtonStateMachine.TransitionEvent.UnrolledCandidatesAttached)
        offsetJob =
            lifecycleCoroutineScope.launch {
                compactCandidate.unrolledCandidateOffset.collect {
                    updateCandidatesWithOffset(it)
                }
            }
        candidatesSubmitJob =
            lifecycleCoroutineScope.launch {
                candidatesPager.flow.collect {
                    adapter.submitData(it)
                }
            }
    }

    /**
     * 綁定候選字視圖持有者的互動事件
     *
     * 設定点擊和長按事件監聽器，處理候選字的選擇和操作選單。
     *
     * @param holder 候選字視圖持有者
     */
    fun bindCandidateUiViewHolder(holder: CandidateViewHolder) {
        holder.itemView.run {
            setOnClickListener { view ->
                rime.launchOnReady {
                    InputFeedbackManager.keyPressVibrate(view)
                    it.selectCandidate(holder.idx)
                }
            }
            setOnLongClickListener { view ->
                compactCandidate.showCandidateAction(holder.idx, holder.text, view)
                true
            }
        }
    }

    /**
     * 根據偏移量更新候選字列表
     *
     * 根據新的偏移量重新整理適配器資料，或在無候選字時關閉視窗。
     *
     * @param offset 新的偏移量
     */
    private fun updateCandidatesWithOffset(offset: Int) {
        val candidates = compactCandidate.adapter.items
        if (candidates.isEmpty()) {
            windowManager.attachWindow(KeyboardWindow)
        } else {
            adapter.refreshWithOffset(offset)
            lifecycleCoroutineScope.launch(Dispatchers.Main) {
                candidateLayout.resetPosition()
            }
        }
    }

    /**
     * 視窗分離時的清理操作
     *
     * 更新狀態機狀態，取消协程作業以釋放資源。
     */
    override fun onDetached() {
        bar.unrollButtonStateMachine.push(
            UnrollButtonStateMachine.TransitionEvent.UnrolledCandidatesDetached,
            UnrollButtonStateMachine.BooleanKey.UnrolledCandidatesEmpty to
                (compactCandidate.adapter.run { isLastPage && (previous + itemCount) == adapter.offset }),
        )
        offsetJob?.cancel()
        candidatesSubmitJob?.cancel()
    }
}
