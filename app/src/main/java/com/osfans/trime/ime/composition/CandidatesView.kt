/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import android.annotation.SuppressLint
import android.graphics.RectF
import android.view.View
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.BaseInputView
import com.osfans.trime.ime.core.TrimeInputMethodService

/**
 * 候選詞視圖（手錶裝置優化版）
 *
 * 這是一個輕量化的空實現，專為記憶體有限的手錶裝置設計。
 * 保持了 API 兼容性但移除了所有彈出視窗功能，大幅減少記憶體使用。
 * 實際的候選詞顯示完全由 CompactCandidateModule 處理。
 */
@SuppressLint("ViewConstructor")
class CandidatesView(
    service: TrimeInputMethodService,
    rime: RimeSession,
    theme: Theme,
) : BaseInputView(service, rime, theme) {
    // handleMessages 由父類管理，不需要重寫

    init {
        // 始終隱藏此視圖，因為我們不需要彈出候選詞功能
        visibility = View.GONE
    }

    /**
     * 更新游標錨點位置（空實現）
     * 為保持 API 兼容性而保留，實際不執行任何操作
     */
    fun updateCursorAnchor(
        anchorPosition: RectF,
        contentSize: FloatArray,
    ) {
        // 空實現 - 手錶裝置不需要彈出候選詞功能
    }

    /**
     * 顯示候選詞視圖（空實現）
     * 為保持 API 兼容性而保留，實際不執行任何操作
     */
    fun show() {
        // 空實現 - 候選詞由 CompactCandidateModule 處理
    }

    /**
     * 隱藏候選詞視圖（空實現）
     * 為保持 API 兼容性而保留，實際不執行任何操作
     */
    fun hide() {
        // 空實現 - 候選詞由 CompactCandidateModule 處理
    }

    /**
     * 處理 RIME 消息（空實現）
     * 為保持 API 兼容性而保留，實際不執行任何操作
     */
    override fun handleRimeMessage(message: RimeMessage<*>) {
        // 空實現 - 手錶裝置不需要處理 RIME 消息
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 確保視圖保持隱藏
        visibility = View.GONE
    }
}
