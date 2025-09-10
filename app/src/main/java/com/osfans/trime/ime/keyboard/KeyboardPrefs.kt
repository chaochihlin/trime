// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.content.Context
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.util.isLandscape

/**
 * 鍵盤偏好設定管理器
 *
 * 提供鍵盤顯示模式相關的偏好設定功能，包括橫向模式的判定邏輯。
 * 此類別為單例物件，用於統一管理鍵盤的顯示行為設定。
 *
 * @since 1.0
 */
object KeyboardPrefs {
    /** 應用程式偏好設定實例 */
    private val prefs = AppPrefs.defaultInstance()

    /** 寬螢幕判定的最小寬度閾值（單位：dp） */
    private const val WIDE_SCREEN_WIDTH_DP = 600

    /**
     * 判定當前是否為橫向模式
     *
     * 根據使用者設定的橫向模式偏好，結合裝置實際狀態來判定是否應該以橫向模式顯示鍵盤。
     * 支援自動判定、強制橫向、總是橫向等多種模式。
     *
     * @return true 如果當前應該以橫向模式顯示，false 否則
     */
    fun Context.isLandscapeMode(): Boolean =
        when (prefs.keyboard.landscapeMode.getValue()) {
            AppPrefs.Keyboard.LandscapeMode.AUTO -> resources.configuration.isLandscape() || isWideScreen()
            AppPrefs.Keyboard.LandscapeMode.LANDSCAPE -> resources.configuration.isLandscape()
            AppPrefs.Keyboard.LandscapeMode.ALWAYS -> true
            else -> false
        }

    /**
     * 判定當前裝置是否為寬螢幕
     *
     * 根據螢幕寬度的密度無關像素（dp）值來判定是否為寬螢幕裝置。
     * 寬螢幕通常指平板電腦或大螢幕手機。
     *
     * @return true 如果螢幕寬度超過 {@link #WIDE_SCREEN_WIDTH_DP} dp，false 否則
     */
    private fun Context.isWideScreen(): Boolean {
        val metrics = resources.displayMetrics
        return metrics.widthPixels / metrics.density > WIDE_SCREEN_WIDTH_DP
    }
}
