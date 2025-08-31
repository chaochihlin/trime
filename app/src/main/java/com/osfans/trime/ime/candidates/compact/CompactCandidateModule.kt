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
import com.google.android.flexbox.FlexboxLayoutManager
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
import com.osfans.trime.ime.candidates.unrolled.decoration.FlexboxVerticalDecoration
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
 * 此模組提供緊湊的候選字顯示界面，使用 RecyclerView 和 FlexboxLayoutManager
 * 實現彈性的候選字佈局。支援候選字的點擊選擇、長按顯示操作選單，
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

    val layoutManager by lazy {
        object : FlexboxLayoutManager(context) {
            override fun canScrollHorizontally(): Boolean = false

            override fun canScrollVertically(): Boolean = false

            override fun onLayoutCompleted(state: RecyclerView.State?) {
                super.onLayoutCompleted(state)
                refreshUnrolled()
            }
        }
    }

    private val separatorDrawable by lazy {
        ShapeDrawable(RectShape()).apply {
            val spacing = theme.generalStyle.candidateSpacing
            val intrinsicSize = max(spacing, context.dp(spacing)).toInt()
            intrinsicWidth = intrinsicSize
            intrinsicHeight = intrinsicSize
            paint.color = ColorManager.getColor("candidate_separator_color")
        }
    }

    val view by lazy {
        context.recyclerView(R.id.candidate_view) {
            adapter = this@CompactCandidateModule.adapter
            layoutManager = this@CompactCandidateModule.layoutManager
            addItemDecoration(FlexboxVerticalDecoration(separatorDrawable))
        }
    }

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
