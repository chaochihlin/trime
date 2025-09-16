// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.os.Build
import android.os.VibrationEffect
import android.view.HapticFeedbackConstants
import android.view.View
import com.osfans.trime.data.prefs.AppPrefs
import splitties.systemservices.vibrator
import timber.log.Timber

/**
 * 輸入回饋管理器（手錶裝置簡化版）
 *
 * 專為手錶裝置優化的輕量級回饋管理器，僅保留震動回饋功能。
 * 移除了音效和語音功能以節省記憶體和提升效能。
 */
object InputFeedbackManager {
    private val keyboardPrefs = AppPrefs.defaultInstance().keyboard

    /**
     * 初始化輸入回饋管理器
     * 簡化版本無需特殊初始化
     */
    fun init(context: android.content.Context) {
        Timber.d("InputFeedbackManager 初始化 (手錶簡化版)")
    }

    /**
     * 開始輸入時的初始化
     * 簡化版本無需特殊處理
     */
    fun startInput() {
        // 簡化版本無需處理
    }

    private val hasAmplitudeControl =
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) &&
            vibrator.hasAmplitudeControl()

    private val vibrateOnKeyPress by keyboardPrefs.vibrateOnKeyPress
    private val vibrationDuration by keyboardPrefs.vibrationDuration
    private val vibrationAmplitude by keyboardPrefs.vibrationAmplitude

    /**
     * 按鍵震動回饋
     *
     * 提供基本的震動回饋功能，支援自定義震動時間和強度。
     *
     * @param view 觸發震動的視圖元件
     * @param longPress 是否為長按操作
     */
    fun keyPressVibrate(
        view: View,
        longPress: Boolean = false,
    ) {
        if (!vibrateOnKeyPress) return
        
        val duration: Long = vibrationDuration.toLong()
        val hfc =
            if (longPress) {
                HapticFeedbackConstants.LONG_PRESS
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            }

        if (duration != 0L) { // use vibrator
            if (hasAmplitudeControl && vibrationAmplitude != 0) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, vibrationAmplitude))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ve = VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(ve)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        } else {
            @Suppress("DEPRECATION")
            val flags =
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            view.performHapticFeedback(hfc, flags)
        }
    }

    /**
     * 按鍵音效回饋（已移除）
     * 手錶裝置不支援音效回饋以節省資源
     */
    fun keyPressSound(keyCode: Int = 0) {
        // 手錶裝置簡化版本：不提供音效回饋
    }

    /**
     * 按鍵語音播放（已移除）
     * 手錶裝置不支援語音回饋以節省資源
     */
    fun keyPressSpeak(keyCode: Int) {
        // 手錶裝置簡化版本：不提供語音回饋
    }

    /**
     * 文本提交語音播放（已移除）
     * 手錶裝置不支援語音回饋以節省資源
     */
    fun textCommitSpeak(text: String) {
        // 手錶裝置簡化版本：不提供語音回饋
    }

    /**
     * 結束輸入時的清理
     * 簡化版本無需特殊處理
     */
    fun finishInput() {
        // 簡化版本無需處理
    }

    /**
     * 銷毀資源
     * 簡化版本無需特殊清理
     */
    fun destroy() {
        Timber.d("InputFeedbackManager 銷毀 (手錶簡化版)")
    }
}