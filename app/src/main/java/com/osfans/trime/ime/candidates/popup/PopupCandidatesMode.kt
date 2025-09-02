/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.popup

import com.osfans.trime.R
import com.osfans.trime.data.prefs.PreferenceDelegateEnum

/**
 * 彈出候選字視窗的顯示模式列舉
 * 
 * 定義候選字彈出視窗的各種顯示行為模式，包括系統預設、
 * 依輸入裝置決定、總是顯示或停用等選項。
 */
enum class PopupCandidatesMode(
    override val stringRes: Int,
) : PreferenceDelegateEnum {
    /** 使用系統預設行為 */
    SYSTEM_DEFAULT(R.string.system_default),
    
    /** 根據輸入裝置類型決定是否顯示 */
    INPUT_DEVICE(R.string.depends_on_input_device),
    
    /** 總是顯示彈出候選字視窗 */
    ALWAYS_SHOW(R.string.always_show),
    
    /** 停用彈出候選字視窗 */
    DISABLED(R.string.disable),
}
