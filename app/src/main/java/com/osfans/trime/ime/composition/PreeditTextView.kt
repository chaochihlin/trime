/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.TextView

/**
 * 編輯文字專用檢視，支持觸控游標移動
 *
 * 這是一個自訂的 TextView，專為 RIME 輸入法的編輯文字區域設計。
 * 主要功能包括觸控事件處理和游標位置計算，允許使用者透過點擊來移動編輯游標。
 */
@SuppressLint("AppCompatCustomView")
class PreeditTextView
    @JvmOverloads
    constructor(
        context: Context,
        attributeSet: AttributeSet? = null,
    ) : TextView(context, attributeSet) {
        /**
         * 游標移動回調函式
         *
         * 當使用者觸控文字檢視時，會調用此函式來通知游標位置變更
         */
        var onMoveCursor: ((Int) -> Unit)? = null

        /** 觸控 X 座標位置 */
        private var touchX: Int = 0

        /** 新的選取內容 */
        private var newSel: CharSequence = ""

        /**
         * 處理觸控事件
         *
         * 覆寫父類方法以實現自訂的游標移動邏輯。
         * 在觸控按下時計算點擊位置對應的字符位置，
         * 在觸控放開時觸發游標移動回調。
         *
         * @param event 觸控事件物件
         * @return 是否處理了該事件
         */
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val x = event.x - paddingLeft
                    val y = event.y - paddingTop
                    touchX = getOffsetForPosition(x, y)
                    newSel = text.subSequence(0, touchX).dropWhile { it.isWhitespace() }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    onMoveCursor?.invoke(newSel.length)
                    touchX = 0
                    newSel = ""
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    touchX = 0
                    newSel = ""
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }
