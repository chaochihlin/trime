// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.core.view.updateLayoutParams
import com.chad.library.adapter4.BaseQuickAdapter
import com.osfans.trime.core.CandidateItem
import com.osfans.trime.ime.core.AutoScaleTextView
import splitties.dimensions.dp
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.setPaddingDp

class T9WhiteCandidateAdapter : BaseQuickAdapter<CandidateItem, T9WhiteCandidateViewHolder>() {
    var isLastPage: Boolean = false
        private set
    var previous: Int = 0
        private set

    fun updateCandidates(
        list: List<CandidateItem>,
        previous: Int,
        isLastPage: Boolean,
    ) {
        this.isLastPage = isLastPage
        this.previous = previous
        super.submitList(list)
    }

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): T9WhiteCandidateViewHolder {
        val textView =
            AutoScaleTextView(context).apply {
                textSize = 20f // 增大字體以改善點擊
                setTextColor(Color.WHITE)
                isSingleLine = true
                gravity = gravityCenter
                scaleMode = AutoScaleTextView.Mode.Proportional
                minimumWidth = dp(44) // 增大最小寬度
                setPaddingDp(8, 0, 8, 0)
                layoutParams =
                    ViewGroup.MarginLayoutParams(wrapContent, matchParent).apply {
                        marginStart = dp(2)
                        marginEnd = dp(2)
                    }
                // 添加半透明底色以顯示點擊範圍
                setBackgroundColor("#40FFFFFF".toColorInt())
            }
        return T9WhiteCandidateViewHolder(textView)
    }

    override fun onBindViewHolder(
        holder: T9WhiteCandidateViewHolder,
        position: Int,
        item: CandidateItem?,
    ) {
        item ?: return
        holder.textView.text = item.text
        holder.textView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        }
    }

    fun setEnabled(enabled: Boolean) {
        notifyDataSetChanged()
    }
}

class T9WhiteCandidateViewHolder(
    val textView: AutoScaleTextView,
) : androidx.recyclerview.widget.RecyclerView.ViewHolder(textView)
