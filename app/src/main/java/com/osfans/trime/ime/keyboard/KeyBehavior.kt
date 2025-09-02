// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

/**
 * 按鍵行為枚舉類別，定義按鍵支援的各種輸入行為類型
 *
 * 此枚舉定義了按鍵可以響應的所有行為模式，包括基本的點擊、長按、滑動操作，
 * 以及根據輸入法狀態動態選擇的智慧行為。不同的行為會觸發不同的按鍵動作。
 *
 * 行為優先級和選擇邏輯：
 * - COMPOSING: 輸入法正在組字時的行為
 * - HAS_MENU: 存在候選詞選單時的行為
 * - PAGING: 候選詞翻頁時的行為
 * - COMBO: 組合按鍵行為（不在長按展開列表中顯示）
 * - ASCII: 英文模式下的行為
 * - CLICK: 基本點擊行為（預設行為）
 * - 其他：各種手勢和擴展行為
 *
 * @see Key
 * @see KeyAction
 */
enum class KeyBehavior {
    // 長按按鍵展開列表時，正上方為長按對應按鍵，排序如下，不展示 COMBO 及之前的按鍵，展示 EXTRA
    
    /** 輸入法正在組字時觸發的行為 */
    COMPOSING,
    
    /** 存在候選詞選單時觸發的行為 */
    HAS_MENU,
    
    /** 候選詞翻頁時觸發的行為 */
    PAGING,
    
    /** 組合按鍵行為（在長按展開列表中不顯示） */
    COMBO,
    
    /** 英文輸入模式下的行為 */
    ASCII,
    
    /** 基本點擊行為（預設行為） */
    CLICK,
    
    /** 向上滑動手勢行為 */
    SWIPE_UP,
    
    /** 長按行為 */
    LONG_CLICK,
    
    /** 向下滑動手勢行為 */
    SWIPE_DOWN,
    
    /** 向左滑動手勢行為 */
    SWIPE_LEFT,
    
    /** 向右滑動手勢行為 */
    SWIPE_RIGHT,
    
    /** 額外的擴展行為（在長按展開列表中顯示） */
    EXTRA,
}
