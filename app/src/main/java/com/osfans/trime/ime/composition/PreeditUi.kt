/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import android.content.Context
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.text.buildSpannedString
import com.osfans.trime.core.RimeProto
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.view

/**
 * 編輯區域 UI 類別，負責顯示 RIME 輸入法的編輯文字介面
 *
 * 此類別繼承 Splitties 的 UI DSL，用於建構可互動的編輯文字檢視。
 * 支持文字顯示、游標移動和觸控互動等功能。
 *
 * @param ctx Android 上下文物件
 * @param theme 主題設定，用於樣式配置
 * @param setupPreeditView 可選的文字檢視設定函式
 * @param onMoveCursor 可選的游標移動回調函式，參數為新的游標位置
 */
open class PreeditUi(
    final override val ctx: Context,
    private val theme: Theme,
    private val setupPreeditView: (TextView.() -> Unit)? = null,
    private val onMoveCursor: ((Int) -> Unit)? = null,
) : Ui {
    private val textColor = ColorManager.getColor("text_color")
    private val highlightTextColor = ColorManager.getColor("hilited_text_color")
    private val highlightBackColor = ColorManager.getColor("hilited_back_color")

    /**
     * 編輯文字檢視元件
     *
     * 自訂的 TextView 實作，支持觸控游標移動功能
     */
    val preedit =
        view(::PreeditTextView) {
            setTextColor(textColor)
            textSize = theme.generalStyle.textSize
            typeface = FontManager.getTypeface("text_font")
            setupPreeditView?.invoke(this)
            onMoveCursor = this@PreeditUi.onMoveCursor
        }

    /**
     * 根檢視容器，包含編輯文字檢視的線性佈局
     */
    override val root =
        object : LinearLayout(ctx) {
            override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean = false

            init {
                orientation = HORIZONTAL
                add(preedit, lParams())
            }
        }

    /**
     * 將 RIME 組合物件轉換為帶樣式的字串
     *
     * 根據選取範圍設定文字和背景顏色，實現高亮顯示效果
     *
     * @return 帶有顏色樣式的 SpannedString
     */
    private fun RimeProto.Context.Composition.toSpannedString() =
        buildSpannedString {
            if (!preedit.isNullOrEmpty()) {
                append(preedit)
                setSpan(ForegroundColorSpan(highlightTextColor), selStart, selEnd, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
                setSpan(BackgroundColorSpan(highlightBackColor), selStart, selEnd, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
            }
        }

    /**
     * 指示編輯區域是否可見
     *
     * 只有當存在編輯文字時才會顯示
     */
    var visible = false
        private set

    /**
     * 更新文字檢視的內容和可見性
     *
     * @param str 要顯示的文字內容
     * @param visible 是否顯示文字檢視
     */
    private fun updateTextView(
        str: CharSequence,
        visible: Boolean,
    ) = preedit.run {
        text = str
        visibility = if (visible) View.VISIBLE else View.GONE
    }

    /**
     * 更新編輯區域顯示
     *
     * 根據 RIME 組合狀態更新文字內容、游標位置和可見性。
     * 當沒有編輯文字時會隱藏整個編輯區域。
     *
     * @param inputComposition RIME 輸入組合物件，包含編輯文字和游標資訊
     */
    fun update(inputComposition: RimeProto.Context.Composition) {
        val string = inputComposition.toSpannedString()
        val cursorPos = inputComposition.cursorPos
        val hasPreedit = inputComposition.length > 0
        visible = hasPreedit
        if (!visible) {
            updateTextView("", false)
            return
        }
        val stringWithCursor =
            if (cursorPos == 0 || cursorPos == string.length) {
                string
            } else {
                buildSpannedString {
                    if (cursorPos > 0) append(string, 0, cursorPos)
                    append(string, cursorPos, string.length)
                }
            }
        updateTextView(stringWithCursor, hasPreedit)
    }
}
