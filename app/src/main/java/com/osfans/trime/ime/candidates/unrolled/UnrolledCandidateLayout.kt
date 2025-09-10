// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.unrolled

import android.annotation.SuppressLint
import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.core.add
import splitties.views.dsl.recyclerview.recyclerView

/**
 * 展開式候選字列表的佈局容器
 *
 * 提供展開式候選字視窗的基本佈局結構，包含背景樣式和內嵌的
 * RecyclerView。支援主題化的邊框和圓角設定。
 *
 * @param context Android 上下文
 * @param theme 主題配置物件，提供視覺樣式設定
 */
@SuppressLint("ViewConstructor")
class UnrolledCandidateLayout(
    context: Context,
    theme: Theme,
) : ConstraintLayout(context) {
    /** 候選字列表的 RecyclerView，停用垂直滾動条 */
    val recyclerView =
        recyclerView {
            isVerticalScrollBarEnabled = false
        }

    init {
        id = R.id.unrolled_candidate_view
        background =
            ColorManager.getDrawable(
                "candidate_background",
                "candidate_border_color",
                dp(theme.generalStyle.candidateBorder),
                dp(theme.generalStyle.candidateBorderRound),
            )

        add(
            recyclerView,
            lParams {
                centerInParent()
            },
        )
    }

    /**
     * 重設滾動位置
     *
     * 將 RecyclerView 的滾動位置重設為第一個項目。
     */
    fun resetPosition() {
        recyclerView.scrollToPosition(0)
    }
}
