// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.View
import com.osfans.trime.core.CandidateItem
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.AutoScaleTextView
import com.osfans.trime.util.pressHighlightDrawable
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.horizontalChain
import splitties.views.dsl.constraintlayout.packed
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.constraintlayout.verticalChain
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter

/**
 * 候選字項目的使用者介面實現
 *
 * 此類負責建立和管理單一候選字項目的視覺呈現，包含主要文字和註解文字的顯示。
 * 支援根據主題配置決定註解文字的位置（頂部或右側），並提供高亮顯示功能。
 *
 * @param ctx Android 上下文，用於建立視圖元件
 * @param theme 主題配置物件，包含文字大小、顏色、字型等樣式設定
 */
class CandidateItemUi(
    override val ctx: Context,
    theme: Theme,
) : Ui {
    private val firstTextSize = theme.generalStyle.candidateTextSize
    private val lastTextSize = theme.generalStyle.commentTextSize
    private val firstTextFont = FontManager.getTypeface("candidate_font")
    private val lastTextFont = FontManager.getTypeface("comment_font")
    private val firstTextColor = ColorManager.getColor("candidate_text_color")
    private val lastTextColor = ColorManager.getColor("comment_text_color")
    private val lastTextColorH = ColorManager.getColor("hilited_comment_text_color")
    private val firstTextColorH = ColorManager.getColor("hilited_candidate_text_color")
    private val firstBackColorH = ColorManager.getColor("hilited_candidate_back_color")

    private val firstText =
        view(::AutoScaleTextView) {
            textSize = firstTextSize
            typeface = firstTextFont
            isSingleLine = true
            gravity = gravityCenter
            scaleMode = AutoScaleTextView.Mode.Proportional
        }

    private val lastText =
        view(::AutoScaleTextView) {
            textSize = lastTextSize
            typeface = lastTextFont
            isSingleLine = true
            gravity = gravityCenter
            scaleMode = AutoScaleTextView.Mode.Proportional
        }

    override val root =
        constraintLayout {
            if (theme.generalStyle.commentOnTop) {
                verticalChain(
                    listOf(lastText, firstText),
                    style = packed,
                    defaultWidth = wrapContent,
                    initFirstViewParams = {
                        height = dp(theme.generalStyle.commentHeight)
                        topOfParent()
                    },
                    initLastViewParams = {
                        height = dp(theme.generalStyle.candidateViewHeight)
                        bottomOfParent()
                    },
                    initParams = { centerHorizontally() },
                )
            } else {
                horizontalChain(
                    listOf(firstText, lastText),
                    style = packed,
                    defaultWidth = wrapContent,
                    initParams = { centerVertically() },
                )
            }
        }

    /**
     * 更新候選字項目的顯示內容和狀態
     *
     * 根據候選字資料和當前狀態更新視圖的文字內容、顏色和背景。
     * 處理主要文字和註解文字的顯示邏輯，以及高亮狀態的視覺回饋。
     *
     * @param item 候選字項目資料，包含文字內容和註解
     * @param isHighlighted 是否處於高亮選中狀態
     * @param obtainLast 是否顯示註解文字
     */
    fun update(
        item: CandidateItem,
        isHighlighted: Boolean,
        obtainLast: Boolean,
    ) {
        val firstColor = if (isHighlighted) firstTextColorH else firstTextColor
        val lastColor = if (isHighlighted) lastTextColorH else lastTextColor
        firstText.text = item.text
        firstText.setTextColor(firstColor)
        lastText.run {
            if (obtainLast) {
                lastText.text = item.comment
                lastText.setTextColor(lastColor)
                if (visibility == View.GONE) visibility = View.VISIBLE
            } else if (visibility != View.GONE) {
                visibility = View.GONE
            }
        }
        root.background =
            if (isHighlighted) {
                ColorDrawable(firstBackColorH)
            } else {
                pressHighlightDrawable(firstBackColorH)
            }
    }
}
