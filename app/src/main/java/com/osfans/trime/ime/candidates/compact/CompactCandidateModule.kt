/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.compact

import android.content.Context
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.view.View
import android.widget.PopupMenu
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.osfans.trime.R
import com.osfans.trime.core.CandidateItem
import com.osfans.trime.core.RimeProto
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.QuickBar
import com.osfans.trime.ime.bar.UnrollButtonStateMachine
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import splitties.dimensions.dp
import splitties.views.dsl.recyclerview.recyclerView
import kotlin.math.max

/**
 * 簡潔型候選字模組
 *
 * 此模組提供緊湊的候選字顯示界面，使用 RecyclerView 和簡化的 LinearLayoutManager（手錶裝置優化版）
 * 實現簡潔的候選字佈局。支援候選字的點擊選擇、長按顯示操作選單，
 * 以及與展開狀態的候選字列表進行整合。
 *
 * @param context Android 應用程式上下文
 * @param service Trime 輸入法服務實例
 * @param rime RIME 輸入引擎會話物件
 * @param theme 主題配置物件，控制視覺樣式和佈局參數
 * @param bar 快速工具列元件，用於狀態管理
 */
class CompactCandidateModule(
    val context: Context,
    val service: TrimeInputMethodService,
    val rime: RimeSession,
    val theme: Theme,
    val bar: QuickBar,
) : InputBroadcastReceiver {
    private val _unrolledCandidateOffset =
        MutableSharedFlow<Int>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val unrolledCandidateOffset = _unrolledCandidateOffset.asSharedFlow()

    /**
     * 重新整理展開候選字列表的狀態
     *
     * 更新展開候選字的偏移量，並通知工具列狀態機更新展開按鈕的狀態。
     * 當候選字列表內容變更時會被調用。
     */
    fun refreshUnrolled() {
        runBlocking {
            _unrolledCandidateOffset.emit(adapter.previous + view.childCount)
        }
        bar.unrollButtonStateMachine.push(
            UnrollButtonStateMachine.TransitionEvent.UnrolledCandidatesUpdated,
            UnrollButtonStateMachine.BooleanKey.UnrolledCandidatesEmpty to
                (adapter.run { isLastPage && itemCount == layoutManager.childCount }),
        )
    }

    /** 候選字列表適配器，處理候選字項目的顯示和互動邏輯 */
    val adapter by lazy {
        CompactCandidateViewAdapter(theme).apply {
            setOnItemClickListener { _, view, position ->
                rime.launchOnReady {
                    InputFeedbackManager.keyPressVibrate(view)
                    it.selectCandidate(previous + position)
                }
            }
            setOnItemLongClickListener { _, view, position ->
                showCandidateAction(previous + position, items[position].text, view)
                true
            }
        }
    }

    /** 簡化的線性佈局管理器（手錶裝置優化版） */
    val layoutManager by lazy {
        object : LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false) {
            override fun canScrollHorizontally(): Boolean = false

            override fun canScrollVertically(): Boolean = false

            override fun onLayoutCompleted(state: RecyclerView.State?) {
                super.onLayoutCompleted(state)
                refreshUnrolled()
            }
        }
    }

    /** 候選字項目間的分隔線繪製物件 */
    private val separatorDrawable by lazy {
        ShapeDrawable(RectShape()).apply {
            val spacing = theme.generalStyle.candidateSpacing
            val intrinsicSize = max(spacing, context.dp(spacing)).toInt()
            intrinsicWidth = intrinsicSize
            intrinsicHeight = intrinsicSize
            paint.color = ColorManager.getColor("candidate_separator_color")
        }
    }

    /** 候選字列表視圖，使用 RecyclerView 實現 */
    val view by lazy {
        context.recyclerView(R.id.candidate_view) {
            adapter = this@CompactCandidateModule.adapter
            layoutManager = this@CompactCandidateModule.layoutManager
            // 移除裝飾元件以節省記憶體（手錶裝置優化）
        }
    }

    /**
     * 處理輸入上下文更新事件
     *
     * 當 RIME 引擎的候選字資料更新時被調用，負責更新候選字列表的顯示內容。
     *
     * @param ctx RIME 輸入上下文，包含候選字資料和狀態資訊
     */
    override fun onInputContextUpdate(ctx: RimeProto.Context) {
        val candidates = ctx.menu.candidates.map { CandidateItem(it.text, it.comment ?: "") }
        val isLastPage = ctx.menu.isLastPage
        val previous = ctx.menu.run { pageSize * pageNumber }
        val highlightedIdx = ctx.menu.highlightedCandidateIndex
        adapter.updateCandidates(candidates, isLastPage, previous, highlightedIdx)
        if (candidates.isEmpty()) {
            refreshUnrolled()
        }
    }

    private var candidateActionMenu: PopupMenu? = null

    /**
     * 顯示候選字操作選單
     *
     * 長按候選字時顯示的彈出選單，提供忘記該詞等操作選項。
     *
     * @param idx 候選字在列表中的索引
     * @param text 候選字的文字內容
     * @param view 觸發操作的視圖元件
     */
    fun showCandidateAction(
        idx: Int,
        text: String,
        view: View,
    ) {
        candidateActionMenu?.dismiss()
        candidateActionMenu = null
        service.lifecycleScope.launch {
            InputFeedbackManager.keyPressVibrate(view, longPress = true)
            candidateActionMenu =
                PopupMenu(context, view).apply {
                    menu
                        .add(
                            buildSpannedString {
                                bold {
                                    color(ColorManager.getColor("hilited_candidate_text_color")) { append(text) }
                                }
                            },
                        ).apply {
                            isEnabled = false
                        }
                    menu.add(R.string.forget_this_word).setOnMenuItemClickListener {
                        rime.runIfReady { forgetCandidate(idx) }
                        true
                    }
                    setOnDismissListener {
                        candidateActionMenu = null
                    }
                    show()
                }
        }
    }
}
