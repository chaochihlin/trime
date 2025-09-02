/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.popup

import android.content.Context
import android.content.res.ColorStateList
import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.osfans.trime.R
import com.osfans.trime.core.RimeProto
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.util.styledFloat
import splitties.dimensions.dp
import splitties.resources.drawable
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.imageDrawable

/**
 * 分頁控件使用者介面
 * 
 * 提供上一頁和下一頁的導航按鈕，根據分頁狀態動態調整按鈕的可用性。
 * 用於候選字彈出視窗中的分頁功能。
 * 
 * @param ctx Android 上下文
 * @param theme 主題配置物件
 */
class PaginationUi(
    override val ctx: Context,
    val theme: Theme,
) : Ui {
    /**
     * 創建導航按鈕圖示
     * 
     * 為上一頁和下一頁按鈕創建統一樣式的圖示。
     * 
     * @param icon 圖示資源 ID
     * @return 配置完成的 ImageView
     */
    private fun createIcon(
        @DrawableRes icon: Int,
    ) = imageView {
        imageTintList = ColorStateList.valueOf(ColorManager.getColor("key_text_color"))
        imageDrawable = drawable(icon)
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    /** 上一頁按鈕圖示 */
    val prevIcon = createIcon(R.drawable.ic_baseline_arrow_left_24)
    
    /** 下一頁按鈕圖示 */
    val nextIcon = createIcon(R.drawable.ic_baseline_arrow_right_24)

    private val disabledAlpha = ctx.styledFloat(android.R.attr.disabledAlpha)

    override val root =
        constraintLayout {
            val w = dp(10)
            val h = dp(20)
            add(
                nextIcon,
                lParams(w, h) {
                    centerVertically()
                    endOfParent()
                },
            )
            add(
                prevIcon,
                lParams(w, h) {
                    centerVertically()
                    before(nextIcon)
                },
            )
        }

    /**
     * 更新分頁控件狀態
     * 
     * 根據 RIME 選單的分頁資訊更新按鈕的可用狀態和視覺效果。
     * 
     * @param menu RIME 選單資料，包含分頁資訊
     */
    fun update(menu: RimeProto.Context.Menu) {
        prevIcon.alpha = if (menu.pageNumber != 0) 1f else disabledAlpha
        nextIcon.alpha = if (!menu.isLastPage) 1f else disabledAlpha
    }
}
