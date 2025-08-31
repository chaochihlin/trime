// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates

import androidx.recyclerview.widget.RecyclerView

/**
 * 候選字列表項目的視圖持有者
 *
 * 此類為 RecyclerView 的 ViewHolder，負責管理單一候選字項目的視圖和相關資料。
 * 提供候選字項目在列表中的索引、文字內容和註解內容的快取。
 *
 * @param ui 候選字項目的使用者介面實現，包含視圖結構和樣式
 */
class CandidateViewHolder(
    val ui: CandidateItemUi,
) : RecyclerView.ViewHolder(ui.root) {
    /** 候選字在列表中的索引位置，預設為 -1 表示未設定 */
    var idx = -1

    /** 候選字的主要文字內容 */
    var text = ""

    /** 候選字的註解文字內容，通常為拼音或其他輔助資訊 */
    var comment = ""
}
