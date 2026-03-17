// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import splitties.dimensions.dp
import timber.log.Timber

/**
 * 注音選擇器視圖
 *
 * 可滾動的垂直列表，顯示當前數字鍵對應的所有注音符號。
 * 支援滑動選擇和點擊確認，使用 LinearSnapHelper 實現 Snap 對齊效果。
 */
class ZhuyinSelectorView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : RecyclerView(context, attrs) {
        companion object {
            private const val TAG = "ZhuyinSelectorView"
            private const val VERTICAL_PADDING_DP = 48
            private const val HORIZONTAL_PADDING_DP = 4
        }

        private var currentDigit: Int = -1
        private val zhuyinAdapter = ZhuyinSelectorAdapter()
        private val linearLayoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        private val snapHelper = LinearSnapHelper()
        private var selectionListener: ZhuyinSelectionListener? = null

        init {
            setupRecyclerView()
            setupClickListener()
            setupScrollListener()
        }

        /**
         * 設置 RecyclerView 基本配置
         */
        private fun setupRecyclerView() {
            layoutManager = linearLayoutManager
            adapter = zhuyinAdapter

            // 允許內容超出邊界時滾動
            clipToPadding = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS

            // 設置內距：上方小 padding 讓清單頂部貼近文字輸入框底部，下方大 padding 維持 Snap 居中效果
            val horizontalPadding = dp(HORIZONTAL_PADDING_DP)
            setPadding(horizontalPadding, dp(4), horizontalPadding, dp(VERTICAL_PADDING_DP))

            // 附加 Snap 助手實現對齊效果
            snapHelper.attachToRecyclerView(this)
        }

        /**
         * 設置點擊監聽器
         */
        private fun setupClickListener() {
            zhuyinAdapter.setOnItemClickListener { _, _, position ->
                val item = zhuyinAdapter.items.getOrNull(position) ?: return@setOnItemClickListener

                Timber.d("$TAG: 點擊選擇注音 - digit=$currentDigit, index=$position, zhuyin=${item.zhuyin}")

                // 觸覺回饋
                InputFeedbackManager.keyPressVibrate(this)

                // 更新選中狀態
                zhuyinAdapter.setSelectedPosition(position)

                // 通知監聽器
                selectionListener?.onZhuyinSelected(currentDigit, position, item.zhuyin)
            }
        }

        /**
         * 設置滾動監聽器
         *
         * 當滾動停止時，更新選中項並通知預覽變更
         */
        private fun setupScrollListener() {
            addOnScrollListener(
                object : OnScrollListener() {
                    override fun onScrollStateChanged(
                        recyclerView: RecyclerView,
                        newState: Int,
                    ) {
                        if (newState == SCROLL_STATE_IDLE) {
                            // 滾動停止時，找到 Snap 對齊的視圖
                            val snapView = snapHelper.findSnapView(linearLayoutManager)
                            if (snapView != null) {
                                val position = linearLayoutManager.getPosition(snapView)
                                if (position != zhuyinAdapter.getSelectedPosition()) {
                                    zhuyinAdapter.setSelectedPosition(position)

                                    val item = zhuyinAdapter.items.getOrNull(position)
                                    if (item != null) {
                                        Timber.d("$TAG: 滾動停止更新選中項 - position=$position, zhuyin=${item.zhuyin}")
                                        selectionListener?.onZhuyinPreviewChanged(currentDigit, position)
                                    }
                                }
                            }
                        }
                    }
                },
            )
        }

        /**
         * 顯示指定數字鍵對應的注音選項
         *
         * @param digit 數字鍵 (0-9)
         * @param preselectedIndex 預選的注音索引
         */
        fun showZhuyinForDigit(
            digit: Int,
            preselectedIndex: Int = 0,
        ) {
            currentDigit = digit

            val zhuyinList = T9ZhuyinMapper.getZhuyinForDigit(digit)
            if (zhuyinList.isEmpty()) {
                Timber.w("$TAG: 數字鍵 $digit 沒有對應的注音")
                return
            }

            Timber.d("$TAG: 顯示數字鍵 $digit 的注音選項: $zhuyinList, 預選索引: $preselectedIndex")
            zhuyinAdapter.setZhuyinList(zhuyinList, preselectedIndex)

            // 滾動到預選位置
            post {
                scrollToPosition(preselectedIndex)
            }
        }

        /**
         * 取得當前選中的注音符號
         */
        fun getSelectedZhuyin(): String = zhuyinAdapter.getSelectedZhuyin() ?: ""

        /**
         * 取得當前選中的索引
         */
        fun getSelectedIndex(): Int = zhuyinAdapter.getSelectedPosition()

        /**
         * 取得當前顯示的數字鍵
         */
        fun getCurrentDigit(): Int = currentDigit

        /**
         * 設置選擇監聽器
         */
        fun setSelectionListener(listener: ZhuyinSelectionListener) {
            selectionListener = listener
        }

        /**
         * 清空選擇器
         */
        fun clear() {
            currentDigit = -1
            zhuyinAdapter.submitList(emptyList())
        }

        /**
         * 顯示注音組合列表（新 API）
         *
         * 用於顯示從 RIME 候選詞提取的注音組合。
         *
         * @param combinations 注音組合列表，如 ["ㄏㄠ", "ㄏㄞ", "ㄒㄧ"]
         * @param preselectedIndex 預選索引，預設 0
         */
        fun showCombinations(
            combinations: List<String>,
            preselectedIndex: Int = 0,
        ) {
            // 組合模式下不追蹤單一數字鍵
            currentDigit = -1

            if (combinations.isEmpty()) {
                Timber.w("$TAG: 注音組合列表為空")
                return
            }

            Timber.d("$TAG: 顯示注音組合列表: $combinations, 預選索引: $preselectedIndex")
            zhuyinAdapter.setZhuyinList(combinations, preselectedIndex)

            // 滾動到預選位置
            post {
                scrollToPosition(preselectedIndex)
            }
        }
    }
