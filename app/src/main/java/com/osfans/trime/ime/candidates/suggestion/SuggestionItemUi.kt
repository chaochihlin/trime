// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.suggestion

import android.content.Context
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.util.pressHighlightDrawable
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.core.Ui

/**
 * 建議項目的使用者介面容器
 * 
 * 為內嵌建議提供一個簡單的容器包裝器，支援加入外部提供的視圖內容。
 * 
 * @param ctx Android 上下文
 */
class SuggestionItemUi(
    override val ctx: Context,
) : Ui {
    override val root =
        constraintLayout {
            background = pressHighlightDrawable(ColorManager.getColor("hilited_candidate_back_color"))
        }

    /**
     * 添加內嵌視圖內容
     * 
     * 清除現有內容並添加新的視圖元件。
     * 
     * @param view 要添加的視圖元件
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun addView(view: View) {
        root.removeAllViews()
        root.addView(view)
    }
}
