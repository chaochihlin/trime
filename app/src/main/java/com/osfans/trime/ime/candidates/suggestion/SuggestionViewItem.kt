/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.suggestion

import android.widget.inline.InlineContentView

/**
 * 建議視圖項目資料類別
 * 
 * 封裝內嵌建議的視圖內容，用於在建議列表中顯示。
 * 
 * @param view 內嵌內容視圖，可能為 null 如果載入失敗
 */
data class SuggestionViewItem(
    val view: InlineContentView?,
)
