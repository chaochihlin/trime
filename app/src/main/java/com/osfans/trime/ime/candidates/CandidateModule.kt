/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates

import android.content.Context
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.QuickBar
import com.osfans.trime.ime.candidates.compact.CompactCandidateModule
import com.osfans.trime.ime.candidates.suggestion.SuggestionCandidateModule
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dependency.InputScope
import me.tatarka.inject.annotations.Inject

/**
 * 候選字模組的核心管理器
 *
 * 此類作為候選字功能的中央協調器，負責管理和初始化各種候選字相關的子模組。
 * 整合了簡潔型候選字模組和建議候選字模組，提供統一的候選字服務入口。
 *
 * @param context Android 應用程式上下文
 * @param service Trime 輸入法服務實例
 * @param rime RIME 輸入引擎會話物件
 * @param theme 主題配置物件，包含視覺樣式設定
 * @param bar 快速工具列元件
 */
@InputScope
@Inject
class CandidateModule(
    val context: Context,
    val service: TrimeInputMethodService,
    val rime: RimeSession,
    val theme: Theme,
    val bar: QuickBar,
) {
    /** 簡潔型候選字模組，提供緊湊的候選字顯示介面 */
    val compactCandidateModule = CompactCandidateModule(context, service, rime, theme, bar)

    /** 建議候選字模組，提供內嵌建議和自動完成功能 */
    val suggestionCandidateModule = SuggestionCandidateModule(context, service, rime, theme, bar)
}
