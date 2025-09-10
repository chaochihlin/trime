// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.speech.tts.TextToSpeech
import android.util.SparseIntArray
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import androidx.core.util.containsValue
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.soundeffect.SoundEffectManager
import splitties.systemservices.audioManager
import splitties.systemservices.vibrator
import timber.log.Timber

/**
 * 輸入回饋管理器
 *
 * 管理按鍵回饋效果，包括振動、音效、語音播放等功能。
 * 為使用者提供觸覺和聽覺的輸入回饋，提升打字體驗。
 */
object InputFeedbackManager {
    private val keyboardPrefs = AppPrefs.defaultInstance().keyboard

    /** 文字轉語音服務 */
    private var tts: TextToSpeech? = null

    /** 音效播放器 */
    private var soundPool: SoundPool? = null

    /** 音效播放進度，用於旋律模式 */
    private var effectPlayProgress = 0

    /** 快取音效 ID 的快取 */
    private val cachedSoundIds = SparseIntArray(30)

    /**
     * 初始化輸入回饋管理器
     *
     * 初始化文字轉語音服務和音效播放器。
     *
     * @param context Android 應用程式上下文
     */
    fun init(context: Context) {
        try {
            tts = TextToSpeech(context, null)
            soundPool =
                SoundPool
                    .Builder()
                    .setMaxStreams(3)
                    .setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    ).build()
        } catch (e: Exception) {
            Timber.e(e, "Error on initializing InputFeedbackManager")
        }
    }

    /**
     * 快取音效 ID
     *
     * 預加載所有音效文件並快取其 ID，提高播放效率。
     */
    private fun cacheSoundId() {
        cachedSoundIds.clear()
        SoundEffectManager.activeAudioPaths.forEachIndexed { i, path ->
            val id = soundPool?.load(path, 1) ?: 0
            if (id != 0 && !cachedSoundIds.containsValue(id)) {
                cachedSoundIds.put(i, id)
            }
        }
    }

    /**
     * 開始輸入時的初始化
     *
     * 在輸入法服務開始輸入時調用，重新加載音效。
     */
    fun startInput() {
        cacheSoundId()
    }

    private val hasAmplitudeControl =
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) &&
            vibrator.hasAmplitudeControl()

    private val vibrateOnKeyPress by keyboardPrefs.vibrateOnKeyPress
    private val vibrationDuration by keyboardPrefs.vibrationDuration
    private val vibrationAmplitude by keyboardPrefs.vibrationAmplitude

    /**
     * 按鍵振動回饋
     *
     * 如果使用者在設定中啟用了振動功能，則產生按鍵振動。
     * 支援自定義振動時間和強度，並區分短按和長按。
     *
     * @param view 觸發振動的視圖元件
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
     * 查詢音效索引
     *
     * 根據按鍵代碼和音效配置查找對應的音效索引。
     * 支援旋律模式和按鍵對應模式。
     *
     * @param keyCode 按鍵代碼
     * @return 音效索引
     */
    private fun querySoundIndex(keyCode: Int): Int {
        val effect = SoundEffectManager.activeSoundEffect ?: return 0
        val sounds = effect.sound
        if (sounds.isEmpty()) return 0
        val melody = effect.melody
        return if (melody.isNotEmpty()) {
            val index = sounds.indexOf(melody[effectPlayProgress])
            effectPlayProgress = (effectPlayProgress + 1) % melody.size
            index
        } else {
            var index = 0
            for (key in effect.keyset) {
                val i = key.querySoundIndex(keyCode)
                if (i >= 0) {
                    index = i
                    break
                }
            }
            Timber.d("without melody: index: $index, sounds.size=${sounds.size}")
            index
        }
    }

    private val soundOnKeyPress by keyboardPrefs.soundOnKeyPress
    private val soundEffectEnabled by keyboardPrefs.soundEffectEnabled
    private val soundVolume by keyboardPrefs.soundVolume

    /**
     * 按鍵音效回饋
     *
     * 如果使用者在設定中啟用了音效功能，則播放按鍵音效。
     * 支援自定義音效和系統預設音效兩種模式。
     *
     * @param keyCode 按鍵代碼，預設為 0
     */
    fun keyPressSound(keyCode: Int = 0) {
        if (!soundOnKeyPress) return
        if (soundEffectEnabled) {
            if (soundVolume <= 0) return
            val volume = soundVolume / 100f
            val index = querySoundIndex(keyCode)
            val soundId = cachedSoundIds[index]
            soundPool?.play(soundId, volume, volume, 0, 0, 1f)
        } else {
            val effect =
                when (keyCode) {
                    KeyEvent.KEYCODE_SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                    KeyEvent.KEYCODE_DEL -> AudioManager.FX_KEYPRESS_DELETE
                    KeyEvent.KEYCODE_ENTER -> AudioManager.FX_KEYPRESS_RETURN
                    else -> AudioManager.FX_KEYPRESS_STANDARD
                }
            val volume =
                if (soundVolume == 0) {
                    -1f
                } else {
                    soundVolume / 100f
                }
            audioManager.playSoundEffect(
                effect,
                volume,
            )
        }
    }

    private val speakOnKeyPress by keyboardPrefs.speakOnKeyPress
    private val speakOnCommit by keyboardPrefs.speakOnCommit

    /**
     * 按鍵語音播放
     *
     * 如果啟用了按鍵語音功能，則播放按鍵對應的語音。
     *
     * @param keyCode 按鍵代碼
     */
    fun keyPressSpeak(keyCode: Int) {
        if (!speakOnKeyPress) return
        contentSpeakInternal(keyCode)
    }

    /**
     * 文本提交語音播放
     *
     * 如果啟用了文本提交語音功能，則播放指定文本的語音。
     *
     * @param text 要播放的文本內容
     */
    fun textCommitSpeak(text: String) {
        if (!speakOnCommit) return
        contentSpeakInternal(text)
    }

    /**
     * 內部語音播放實現
     *
     * 處理按鍵代碼或文本內容的語音播放邏輯。
     *
     * @param T 內容類型，可為 Int 或 String
     * @param content 要播放的內容
     */
    private inline fun <reified T> contentSpeakInternal(content: T) {
        val text =
            when {
                0 is T -> {
                    KeyEvent
                        .keyCodeToString(content as Int)
                        .replace("KEYCODE_", "")
                        .replace("_", " ")
                        .lowercase()
                }
                "" is T -> content as String
                else -> return
            }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TrimeTTS")
    }

    /**
     * 結束輸入時的清理
     *
     * 在輸入法服務結束輸入時調用，重設相關狀態。
     */
    fun finishInput() {
        effectPlayProgress = 0
    }

    /**
     * 销毀資源
     *
     * 釋放所有占用的資源，包括文字轉語音服務和音效播放器。
     */
    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        soundPool?.release()
        soundPool = null
    }
}
