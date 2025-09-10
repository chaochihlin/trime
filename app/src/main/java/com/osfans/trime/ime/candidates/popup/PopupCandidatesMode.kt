/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.popup

import com.osfans.trime.R
import com.osfans.trime.data.prefs.PreferenceDelegateEnum

/**
 * 候選字顯示模式列舉（手錶裝置優化版）
 *
 * 為了保持架構穩定性而保留的配置枚舉，但實際上所有模式都將
 * 使用緊湊型候選詞顯示，不再有真正的彈出視窗功能。
 * 專為記憶體有限的手錶裝置進行優化。
 */
enum class PopupCandidatesMode(
    override val stringRes: Int,
) : PreferenceDelegateEnum {
    /** 使用系統預設行為（實際使用緊湊顯示） */
    SYSTEM_DEFAULT(R.string.system_default),

    /** 根據輸入裝置類型決定（實際使用緊湊顯示） */
    INPUT_DEVICE(R.string.depends_on_input_device),

    /** 總是顯示候選字（實際使用緊湊顯示） */
    ALWAYS_SHOW(R.string.always_show),

    /** 停用彈出候選字視窗（實際使用緊湊顯示） */
    DISABLED(R.string.disable),
}
