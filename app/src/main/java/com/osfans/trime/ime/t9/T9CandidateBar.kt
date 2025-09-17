// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.core.CandidateItem
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.candidates.compact.CompactCandidateViewAdapter
import splitties.dimensions.dp
import timber.log.Timber

/**
 * T9候選詞水平滾動列
 *
 * 專為T9輸入法設計的候選詞顯示組件，特性：
 * - 水平滾動佈局適應圓形螢幕
 * - 最多顯示7個可見候選詞
 * - 支援分頁瀏覽
 * - 圓形螢幕邊緣優化
 * - 與CompactCandidateViewAdapter整合
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
        private var theme: Theme? = null

        // 使用現有的CompactCandidateViewAdapter
        private lateinit var candidateAdapter: CompactCandidateViewAdapter

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
            // adapter will be set in updateTheme when theme is available

            // 圓形螢幕優化設置
            clipToPadding = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER

            // 設置內距以避免邊緣裁切
            setPadding(dp(8), dp(4), dp(8), dp(4))

            // 設置固定高度
            layoutParams?.height = dp(CANDIDATE_HEIGHT_DP)
        }

        /**
         * 更新候選詞列表
         */
        fun updateCandidates(candidates: List<CandidateItem>) {
            if (::candidateAdapter.isInitialized) {
                candidateAdapter.updateCandidates(candidates, 0, candidates.size >= MAX_VISIBLE_CANDIDATES)
            }

            // 自動滾動到開始位置
            if (candidates.isNotEmpty()) {
                scrollToPosition(0)
            }
        }

        /**
         * 設置候選詞點擊監聽器
         */
        fun setOnCandidateClickListener(listener: OnCandidateClickListener) {
            this.onCandidateClickListener = listener
        }

        /**
         * 更新主題樣式
         */
        fun updateTheme(theme: Theme) {
            this.theme = theme

            // 初始化或更新適配器
            if (!::candidateAdapter.isInitialized) {
                candidateAdapter =
                    CompactCandidateViewAdapter(theme).apply {
                        setOnItemClickListener { _, view, position ->
                            onCandidateClickListener?.onCandidateClick(position, items[position])
                        }
                    }
                adapter = candidateAdapter
            } else {
                candidateAdapter.updateTheme(theme)
            }

            try {
                // 更新背景顏色 - 強制使用可見的藍色背景進行除錯
                val candidateBarBackgroundColor =
                    ColorManager.getColor("candidate_bar_background_color")
                        ?: Color.parseColor("#4444FF") // 強制使用藍色而非透明
                setBackgroundColor(candidateBarBackgroundColor)

                // 除錯日誌
                Timber.d("T9CandidateBar: 🎨 Background color set to: ${String.format("#%06X", candidateBarBackgroundColor and 0xFFFFFF)}")
            } catch (e: Exception) {
                // 使用可見的藍色而非透明
                setBackgroundColor(Color.parseColor("#4444FF"))
                Timber.w("T9CandidateBar: 🎨 Using fallback blue background color due to error: ${e.message}")
            }
        }

        /**
         * 清空候選詞
         */
        fun clearCandidates() {
            if (::candidateAdapter.isInitialized) {
                candidateAdapter.updateCandidates(emptyList(), 0, false)
            }
        }

        /**
         * 滾動到指定位置
         */
        fun scrollToCandidate(position: Int) {
            if (::candidateAdapter.isInitialized && position >= 0 && position < candidateAdapter.itemCount) {
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
        fun getCandidateCount(): Int = if (::candidateAdapter.isInitialized) candidateAdapter.itemCount else 0

        /**
         * 檢查是否可以向左滾動
         */
        fun canScrollLeft(): Boolean = linearLayoutManager.findFirstVisibleItemPosition() > 0

        /**
         * 檢查是否可以向右滾動
         */
        fun canScrollRight(): Boolean {
            val lastVisible = linearLayoutManager.findLastVisibleItemPosition()
            return if (::candidateAdapter.isInitialized) lastVisible < candidateAdapter.itemCount - 1 else false
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
            val targetPosition = if (::candidateAdapter.isInitialized) (lastVisible + 1).coerceAtMost(candidateAdapter.itemCount - 1) else 0
            smoothScrollToPosition(targetPosition)
        }

        /**
         * 設置候選詞列是否啟用
         */
        fun setCandidateBarEnabled(enabled: Boolean) {
            isEnabled = enabled
            if (::candidateAdapter.isInitialized) {
                candidateAdapter.setEnabled(enabled)
            }
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
