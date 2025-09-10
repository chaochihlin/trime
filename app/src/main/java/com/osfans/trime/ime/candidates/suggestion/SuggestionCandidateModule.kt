/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.suggestion

import android.content.Context
import android.os.Build
import android.util.Size
import android.view.ViewGroup
import android.view.inputmethod.InlineSuggestion
import android.widget.inline.InlineContentView
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.QuickBar
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.core.TrimeInputMethodService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import splitties.dimensions.dp
import splitties.views.dsl.recyclerview.recyclerView
import splitties.views.recyclerview.horizontalLayoutManager
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 建議候選字模組
 *
 * 負責處理 Android R+ 的內嵌建議功能，將系統提供的內嵌建議
 * 轉換為可顯示的視圖元件。支援自動完成、應用建議等功能。
 *
 * @param context Android 應用程式上下文
 * @param service Trime 輸入法服務實例
 * @param rime RIME 輸入引擎會話物件
 * @param theme 主題配置物件
 * @param bar 快速工具列元件
 */
class SuggestionCandidateModule(
    val context: Context,
    val service: TrimeInputMethodService,
    val rime: RimeSession,
    val theme: Theme,
    val bar: QuickBar,
) : InputBroadcastReceiver {
    /** 建議項目的列表適配器 */
    private val adapter by lazy {
        SuggestionViewAdapter(theme)
    }

    /** 建議列表的水平滾動視圖 */
    val view by lazy {
        context.recyclerView(R.id.suggestion_view) {
            adapter = this@SuggestionCandidateModule.adapter
            layoutManager = horizontalLayoutManager()
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
        }
    }

    /** 建議項目的尺寸配置 */
    private val suggestionSize by lazy {
        Size(ViewGroup.LayoutParams.WRAP_CONTENT, context.dp(HEIGHT))
    }

    /** 直接執行器，用於同步執行任務 */
    private val directExecutor by lazy {
        Executor { it.run() }
    }

    /**
     * 處理內嵌建議事件
     *
     * 當系統提供內嵌建議時被調用，將建議轉換為視圖元件並更新列表。
     *
     * @param suggestions 內嵌建議列表
     */
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onInlineSuggestions(suggestions: List<InlineSuggestion>) {
        service.lifecycleScope.launch {
            val items =
                suggestions
                    .map { s ->
                        service.lifecycleScope.async {
                            SuggestionViewItem(inflateInlineContentView(s))
                        }
                    }.awaitAll()
            adapter.submitList(items)
        }
    }

    /**
     * 將內嵌建議轉換為視圖元件
     *
     * 使用協程方式將內嵌建議物件擴展為可顯示的視圖元件。
     *
     * @param suggestion 內嵌建議物件
     * @return 擴展後的視圖元件，可能為 null
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun inflateInlineContentView(suggestion: InlineSuggestion): InlineContentView? =
        suspendCoroutine { c ->
            // callback view might be null
            suggestion.inflate(context, suggestionSize, directExecutor) { v ->
                c.resume(v)
            }
        }

    companion object {
        /** 建議項目的預設高度，單位為 dp */
        const val HEIGHT = 40
    }
}
