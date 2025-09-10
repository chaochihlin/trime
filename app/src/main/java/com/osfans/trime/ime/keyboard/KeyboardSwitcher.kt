// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

/**
 * 鍵盤切換器（已棄用）
 *
 * 管理鍵盤實例和其狀態。此類別原本用於管理鍵盤的切換和狀態維護，
 * 但現在已被 {@link KeyboardWindow} 取代。目前僅保留以維持向後相容性。
 *
 * @deprecated 請使用 {@link KeyboardWindow} 以取代此類別
 * @see KeyboardWindow
 * @since 1.0
 */
@Deprecated("Migrate into KeyboardWindow")
object KeyboardSwitcher {
    /** 當前活躍的鍵盤實例 */
    lateinit var currentKeyboard: Keyboard

    /** 當前活躍的鍵盤視圖實例 */
    var currentKeyboardView: KeyboardView? = null
}
