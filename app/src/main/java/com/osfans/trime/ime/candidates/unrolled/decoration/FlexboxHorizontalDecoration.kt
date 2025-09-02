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

/**
 * Flexbox 水平裝飾器
 * 
 * 為使用 FlexboxLayoutManager 的 RecyclerView 提供水平方向的裝飾分隔線。
 * 在每個項目的底部繪製分隔線，適用於水平排列的候選字列表。
 * 
 * @param drawable 用於繪製分隔線的 Drawable 物件
 */
class FlexboxHorizontalDecoration(
    val drawable: Drawable,
) : RecyclerView.ItemDecoration() {
    /**
     * 設定項目的偏移量
     * 
     * 為每個項目的底部保留分隔線的空間。
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
        outRect.bottom = drawable.intrinsicHeight
    }

    /**
     * 繪製分隔線
     * 
     * 在每個子視圖的底部繪製水平分隔線。
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
            val left = view.left
            val right = view.right
            val top = view.bottom
            val bottom = view.bottom + drawable.intrinsicHeight
            drawable.setBounds(left, top, right, bottom)
            drawable.draw(c)
        }
    }
}
