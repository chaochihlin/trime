// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.core.CandidateItem
import splitties.dimensions.dp

/**
 * T9候選詞水平滾動列
 *
 * 專為T9輸入法設計的候選詞顯示組件，特性：
 * - 水平滾動佈局適應圓形螢幕
 * - 最多顯示7個可見候選詞
 * - 支援分頁瀏覽
 * - 圓形螢幕邊緣優化
 * - 與T9WhiteCandidateAdapter整合
 *
 * @param context Android上下文
 * @param attrs 屬性集
 */

class T9CandidateBar
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : RecyclerView(context, attrs) {
        companion object {
            const val CANDIDATE_HEIGHT_DP = 32 // 候選詞高度
            const val CANDIDATE_PADDING_DP = 8 // 候選詞內距
            const val MAX_VISIBLE_CANDIDATES = 7 // 最大可見候選數
        }

        interface OnCandidateClickListener {
            fun onCandidateClick(
                position: Int,
                candidate: CandidateItem,
            )
        }

        private var onCandidateClickListener: OnCandidateClickListener? = null

        // 使用T9WhiteCandidateAdapter
        private lateinit var candidateAdapter: T9WhiteCandidateAdapter

        private val linearLayoutManager =
            LinearLayoutManager(
                context,
                LinearLayoutManager.HORIZONTAL,
                false,
            )

        init {
            setupRecyclerView()
        }

        /**
         * 設置RecyclerView
         */
        private fun setupRecyclerView() {
            // 設置佈局管理器
            layoutManager = linearLayoutManager

            // 初始化T9WhiteCandidateAdapter
            candidateAdapter =
                T9WhiteCandidateAdapter().apply {
                    setOnItemClickListener { _, _, position ->
                        val item = items.getOrNull(position) ?: return@setOnItemClickListener
                        onCandidateClickListener?.onCandidateClick(position, item)
                    }
                }
            adapter = candidateAdapter

            // 圓形螢幕優化設置
            clipToPadding = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER

            // 設置內距以避免邊緣裁切
            setPadding(dp(4), dp(4), dp(52), dp(4))

            // 設置固定高度
            layoutParams?.height = dp(CANDIDATE_HEIGHT_DP)

            // 背景透明，融入容器底色
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        var onCandidatesUpdated: (() -> Unit)? = null

        /**
         * 更新候選詞列表
         */
        fun updateCandidates(candidates: List<CandidateItem>) {
            candidateAdapter.updateCandidates(candidates, 0, candidates.size >= MAX_VISIBLE_CANDIDATES)

            // 自動滾動到開始位置
            if (candidates.isNotEmpty()) {
                scrollToPosition(0)
            }
            post { onCandidatesUpdated?.invoke() }
        }

        /**
         * 設置候選詞點擊監聽器
         */
        fun setOnCandidateClickListener(listener: OnCandidateClickListener) {
            this.onCandidateClickListener = listener
        }

        /**
         * 清空候選詞
         */
        fun clearCandidates() {
            candidateAdapter.updateCandidates(emptyList(), 0, false)
            onCandidatesUpdated?.invoke()
        }

        /**
         * 滾動到指定位置
         */
        fun scrollToCandidate(position: Int) {
            if (position >= 0 && position < candidateAdapter.itemCount) {
                smoothScrollToPosition(position)
            }
        }

        /**
         * 取得當前可見的候選詞數量
         */
        fun getVisibleCandidateCount(): Int {
            val firstVisible = linearLayoutManager.findFirstVisibleItemPosition()
            val lastVisible = linearLayoutManager.findLastVisibleItemPosition()

            return if (firstVisible != RecyclerView.NO_POSITION && lastVisible != RecyclerView.NO_POSITION) {
                lastVisible - firstVisible + 1
            } else {
                0
            }
        }

        /**
         * 取得候選詞總數
         */
        fun getCandidateCount(): Int = candidateAdapter.itemCount

        /**
         * 檢查是否可以向左滾動
         */
        fun canScrollLeft(): Boolean = linearLayoutManager.findFirstVisibleItemPosition() > 0

        /**
         * 檢查是否可以向右滾動
         */
        fun canScrollRight(): Boolean {
            val lastVisible = linearLayoutManager.findLastVisibleItemPosition()
            return lastVisible < candidateAdapter.itemCount - 1
        }

        /**
         * 向左滾動一頁
         */
        fun scrollLeftPage() {
            val firstVisible = linearLayoutManager.findFirstVisibleItemPosition()
            val targetPosition = (firstVisible - MAX_VISIBLE_CANDIDATES).coerceAtLeast(0)
            smoothScrollToPosition(targetPosition)
        }

        /**
         * 向右滾動一頁
         */
        fun scrollRightPage() {
            val lastVisible = linearLayoutManager.findLastVisibleItemPosition()
            val targetPosition = (lastVisible + 1).coerceAtMost(candidateAdapter.itemCount - 1)
            smoothScrollToPosition(targetPosition)
        }

        /**
         * 設置候選詞列是否啟用
         */
        fun setCandidateBarEnabled(enabled: Boolean) {
            isEnabled = enabled
            candidateAdapter.setEnabled(enabled)
            alpha = if (enabled) 1.0f else 0.6f
        }

        /**
         * 隱藏候選詞列
         */
        fun hide() {
            visibility = View.GONE
        }

        /**
         * 顯示候選詞列
         */
        fun show() {
            visibility = View.VISIBLE
        }
    }
