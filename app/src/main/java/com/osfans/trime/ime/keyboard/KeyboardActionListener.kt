/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

/**
 * 鍵盤動作事件監聽器接口
 *
 * 此接口定義了虛擬鍵盤上的按鍵事件回調方法。實現此接口的類別可以
 * 接收和處理使用者的按鍵互動，包括按下、釋放、點擊等各種動作。
 *
 * 事件處理流程：
 * 1. onPress() - 按鍵被按下時首先觸發
 * 2. onKey() 或 onAction() - 按鍵釋放時觸發具體動作
 * 3. onRelease() - 按鍵釋放後最後觸發
 *
 * @see Key
 * @see KeyAction
 * @see Keyboard
 */
interface KeyboardActionListener {
    /**
     * 當使用者按下按鍵時觸發
     *
     * 此方法在 onKey() 之前被調用，用於提供按鍵按下的立即反饋。
     * 對於可重複觸發的按鍵，此方法只在首次按下時觸發一次。
     *
     * @param keyEventCode 按下按鍵的代碼。如果觸摸不在有效按鍵上，值為 0
     */
    fun onPress(keyEventCode: Int)

    /**
     * 當使用者釋放按鍵時觸發
     *
     * 此方法在 onKey() 之後被調用，用於清理按鍵釋放後的狀態。
     * 對於可重複觸發的按鍵，此方法只在最後釋放時觸發一次。
     *
     * @param keyEventCode 釋放按鍵的代碼
     */
    fun onRelease(keyEventCode: Int)

    /**
     * 當按鍵觸發自定義動作時觸發
     *
     * 用於處理輸入法特有的功能動作，如切換輸入法、呼叫選單等。
     *
     * @param action 要執行的按鍵動作物件
     */
    fun onAction(action: KeyAction)

    /**
     * 傳送按鍵事件給監聽器
     *
     * 當使用者完成按鍵操作時觸發，用於處理標準的按鍵輸入。
     * 這個方法會接收按鍵代碼和修飾鍵狀態。
     *
     * @param keyEventCode 被按下的按鍵代碼
     * @param metaState 修飾鍵的狀態遐罩，用於指示 Shift、Ctrl、Alt 等修飾鍵的狀態
     */
    fun onKey(
        keyEventCode: Int,
        metaState: Int,
    )

    /**
     * 傳送文字序列給監聽器
     *
     * 用於直接輸出文字內容，而不是單個按鍵代碼。
     * 通常用於輸出多個字元或預定義的文字片段。
     *
     * @param text 要顯示的字元序列
     */
    fun onText(text: CharSequence)
}
