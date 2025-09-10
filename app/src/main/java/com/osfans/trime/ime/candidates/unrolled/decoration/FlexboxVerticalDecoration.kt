// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.unrolled.decoration

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayoutManager
import splitties.dimensions.dp

/**
 * Flexbox 垂直裝飾器
 *
 * 為使用 FlexboxLayoutManager 的 RecyclerView 提供垂直方向的裝飾分隔線。
 * 支援 LTR 和 RTL 布局方向，在項目間繪製垂直分隔線。
 *
 * @param drawable 用於繪製分隔線的 Drawable 物件
 */
class FlexboxVerticalDecoration(
    val drawable: Drawable,
) : RecyclerView.ItemDecoration() {
    /**
     * 設定項目的偏移量
     *
     * 根據佈局方向（LTR 或 RTL）設定左或右側的偏移量。
     *
     * @param outRect 輸出的偏移量矩形
     * @param view 項目視圖
     * @param parent RecyclerView 父視圖
     * @param state RecyclerView 狀態
     */
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        when (parent.layoutDirection) {
            View.LAYOUT_DIRECTION_LTR -> {
                outRect.right = drawable.intrinsicWidth
            }
            View.LAYOUT_DIRECTION_RTL -> {
                outRect.left = drawable.intrinsicWidth
            }
            else -> {
                // should not reach here
                outRect.set(0, 0, 0, 0)
            }
        }
    }

    /**
     * 繪製分隔線
     *
     * 根據佈局方向在適當位置繪製垂直分隔線，並設定上下內縮距離。
     *
     * @param c 繪製用的 Canvas
     * @param parent RecyclerView 父視圖
     * @param state RecyclerView 狀態
     */
    override fun onDraw(
        c: Canvas,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val layoutManager = parent.layoutManager as FlexboxLayoutManager
        for (i in 0 until layoutManager.childCount) {
            val view = parent.getChildAt(i)
            val lp = view.layoutParams as FlexboxLayoutManager.LayoutParams
            val left: Int
            val right: Int
            when (parent.layoutDirection) {
                View.LAYOUT_DIRECTION_LTR -> {
                    left = view.right + lp.rightMargin
                    right = left + drawable.intrinsicWidth
                }
                View.LAYOUT_DIRECTION_RTL -> {
                    right = view.left + lp.leftMargin
                    left = right - drawable.intrinsicWidth
                }
                else -> {
                    // should not reach here
                    left = view.left
                    right = left + drawable.intrinsicWidth
                }
            }
            val top = view.top - lp.topMargin
            val bottom = view.bottom + lp.bottomMargin
            // make the divider shorter
            val vInset = parent.dp(8)
            drawable.setBounds(left, top + vInset, right, bottom - vInset)
            drawable.draw(c)
        }
    }
}
