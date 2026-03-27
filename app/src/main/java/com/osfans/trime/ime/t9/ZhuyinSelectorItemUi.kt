// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.gravityCenter

/**
 * 注音選項項目的 UI 定義
 *
 * 遵循專案的 UI 模式（參考 CandidateItemUi），使用 Splitties DSL 建立視圖。
 * 負責單一注音選項的視覺呈現，包含文字顯示和選中狀態的視覺回饋。
 */
class ZhuyinSelectorItemUi(
    override val ctx: Context,
) : Ui {
    companion object {
        // UI 規格常數
        const val SELECTED_BG_COLOR = 0x33FFFFFF // 半透明白色背景 (20% 不透明度)
        const val SELECTED_TEXT_COLOR = 0xFFFFFFFF.toInt() // 白色文字
        const val UNSELECTED_TEXT_COLOR = 0xFF888888.toInt() // 灰色文字
        const val ITEM_HEIGHT_DP = 36
        const val TEXT_SIZE_SP = 18f
        const val CORNER_RADIUS_DP = 8
    }

    private val zhuyinText =
        textView {
            textSize = TEXT_SIZE_SP
            setTextColor(UNSELECTED_TEXT_COLOR)
            gravity = gravityCenter
            isSingleLine = true
        }

    private val backgroundDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = ctx.dp(CORNER_RADIUS_DP).toFloat()
            setColor(Color.TRANSPARENT)
        }

    override val root =
        frameLayout {
            background = backgroundDrawable
            addView(
                zhuyinText,
                lParams(matchParent, matchParent) {
                    gravity = gravityCenter
                },
            )
        }

    /**
     * 更新注音項目的顯示狀態
     *
     * @param item 注音項目資料
     */
    fun update(item: ZhuyinItem) {
        zhuyinText.text = item.zhuyin
        if (item.isSelected) {
            zhuyinText.setTextColor(SELECTED_TEXT_COLOR)
            backgroundDrawable.setColor(SELECTED_BG_COLOR)
        } else {
            zhuyinText.setTextColor(UNSELECTED_TEXT_COLOR)
            backgroundDrawable.setColor(Color.TRANSPARENT)
        }
    }
}
