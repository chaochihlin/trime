// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.annotation.TargetApi
import android.os.Build
import android.view.KeyEvent
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.enums.Keycode
import com.osfans.trime.util.virtualKeyCharacterMap

/**
 * 按鍵動作類別，定義按鍵的各種事件處理（單擊、長按、滑動等）
 *
 * 此類別負責解析和管理按鍵的動作配置，包括按鍵碼、修飾鍵、文本輸出、
 * 標籤顯示等屬性。支援從主題配置中載入預設按鍵或解析自定義按鍵定義。
 *
 * @param raw 原始按鍵定義字串，可能包含按鍵名稱、功能指令或複合定義
 *
 * @see Key
 * @see Keyboard
 * @see Keycode
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class KeyAction(
    raw: String,
) {
    /** 按鍵碼，對應 Android KeyEvent 的鍵碼值 */
    var code = 0
        private set

    /** 修飾鍵標誌位，如 Shift、Ctrl、Alt 等 */
    var modifier = 0
        private set

    /** 按鍵執行的指令名稱 */
    var command: String = ""
        private set

    /** 按鍵選項參數 */
    var option: String = ""
        private set

    /** 按鍵選取操作 */
    var select: String = ""
        private set

    /** 按鍵切換的選項名稱 */
    var toggle: String = ""
        private set

    /** 按鍵直接輸出的文本 */
    var commit: String = ""
        private set

    /** Shift 鎖定狀態下的行為 */
    var shiftLock: String = ""
        private set

    /** 是否為功能按鍵（非文字輸入） */
    var isFunctional = false
        private set

    /** 是否支援重複觸發 */
    var isRepeatable = false
        private set

    /** 是否為黏性按鍵（保持按下狀態） */
    var isSticky = false
        private set

    /** 按鍵實際輸出的文本內容 */
    private var text: String = ""

    /** 按鍵顯示的標籤文字 */
    private var label: String = ""

    /** Shift 狀態下顯示的標籤文字 */
    private var shiftLabel = ""

    /** 按鍵預覽顯示的文字 */
    private var preview: String = ""

    /** 按鍵狀態列表，用於切換顯示 */
    private var states: List<String> = listOf()

    /** 是否攔截 Shift+數字鍵的設定 */
    private val hookShiftNum by AppPrefs.defaultInstance().keyboard.hookShiftNum

    /** 是否攔截 Shift+符號鍵的設定 */
    private val hookShiftSymbol by AppPrefs.defaultInstance().keyboard.hookShiftSymbol

    /** RIME 輸入法引擎會話實例 */
    private val rime = RimeDaemon.getFirstSessionOrNull()!!

    /**
     * 根據鍵盤狀態調整文字大小寫
     *
     * @param str 待調整的字串
     * @param keyboard 當前鍵盤狀態
     * @return 調整後的字串
     */
    private fun adjustCase(
        str: String,
        keyboard: Keyboard,
    ): String {
        val status = rime.run { statusCached }
        return if (str.length == 1 && (keyboard.isShifted || (!status.isAsciiMode && keyboard.isLabelUppercase))) {
            str.uppercase()
        } else {
            str
        }
    }

    /**
     * 取得按鍵的顯示標籤
     *
     * 根據鍵盤狀態、RIME 引擎狀態和按鍵配置決定顯示的標籤文字。
     * 會考慮切換狀態、Shift 狀態和輸入法模式等因素。
     *
     * @param keyboard 當前鍵盤狀態
     * @return 按鍵應顯示的標籤文字
     */
    fun getLabel(keyboard: Keyboard): String {
        if (states.isNotEmpty() && toggle.isNotEmpty()) {
            return states[if (rime.run { getRuntimeOption(toggle) }) 1 else 0]
        }
        if (keyboard.isOnlyShiftOn) {
            val status = rime.run { statusCached }
            if (!hookShiftNum && !status.isComposing && code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                return adjustCase(shiftLabel, keyboard)
            }
            if (!hookShiftSymbol &&
                // TODO: 判断中英模式仅能正确处理已配置映射的符号，对于未配置映射的符号，即使在中文模式下也能上屏 Shift 切换的符号。
                status.isAsciiMode &&
                (
                    code in KeyEvent.KEYCODE_GRAVE..KeyEvent.KEYCODE_SLASH ||
                        code == KeyEvent.KEYCODE_COMMA ||
                        code == KeyEvent.KEYCODE_PERIOD
                )
            ) {
                return adjustCase(shiftLabel, keyboard)
            }
        }
        return adjustCase(label, keyboard)
    }

    /**
     * 取得按鍵輸出的文本內容
     *
     * @param keyboard 當前鍵盤狀態
     * @return 按鍵應輸出的文本內容
     */
    fun getText(keyboard: Keyboard): String =
        if (text.isNotEmpty()) {
            adjustCase(text, keyboard)
        } else if (keyboard.isShifted && code in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z && modifier == 0) {
            adjustCase(label, keyboard)
        } else {
            text
        }

    /**
     * 取得按鍵預覽顯示的文字
     *
     * @param keyboard 當前鍵盤狀態
     * @return 按鍵預覽顯示的文字，若無預覽文字則回傳標籤文字
     */
    fun getPreview(keyboard: Keyboard): String = preview.ifEmpty { getLabel(keyboard) }

    /**
     * 初始化按鍵動作
     *
     * 解析原始按鍵定義字串，從主題配置載入預設按鍵或解析自定義按鍵定義，
     * 設定按鍵的各項屬性包括按鍵碼、修飾鍵、標籤、文本等。
     */
    init {
        val unbraced = raw.removeSurrounding("{", "}")
        val presetKey = ThemeManager.activeTheme.presetKeys[unbraced]
        when {
            // 匹配預設按鍵：{ x: BackSpace } -> preset_keys/BackSpace: {..., send: BackSpace }
            presetKey != null -> {
                command = presetKey.command
                option = presetKey.option
                select = presetKey.select
                toggle = presetKey.toggle
                label = presetKey.label
                preview = presetKey.preview
                shiftLock = presetKey.shiftLock
                commit = presetKey.commit
                text = presetKey.text
                isSticky = presetKey.sticky
                isRepeatable = presetKey.repeatable
                isFunctional = presetKey.functional
                states = presetKey.states

                val send = presetKey.send
                if (send.isNotEmpty()) {
                    val (c, m) = Keycode.parseSend(send)
                    code = c
                    modifier = m
                } else if (command.isNotEmpty()) {
                    code = KeyEvent.KEYCODE_FUNCTION
                }

                if (label.isEmpty()) {
                    label =
                        when (code) {
                            KeyEvent.KEYCODE_SPACE -> rime.run { statusCached }.schemaName
                            KeyEvent.KEYCODE_UNKNOWN -> ""
                            else -> Keycode.getDisplayLabel(code, modifier)
                        }
                }
            }
            // 匹配組合鍵定義：{ x: "{Control+a}" }
            raw.matches(BRACED_STR) -> {
                val (c, m) = Keycode.parseSend(unbraced)
                if (c != KeyEvent.KEYCODE_UNKNOWN || m > 0) {
                    code = c
                    modifier = m
                }
                // 匹配複合定義：{ x: { commit: a, text: b, label: c } }
                decodeMapFromString(raw).takeIf { it.isNotEmpty() }?.let {
                    commit = it["commit"] ?: ""
                    text = it["text"] ?: ""
                    label = it["label"] ?: ""
                }
            }
            else -> {
                // 匹配簡單按鍵：{ x: 1 } 或 { x: q } ...
                code = Keycode.keyCodeOf(unbraced)
                // 匹配按鍵序列：{ x: "(){Left}" } (模擬按鍵序列)
                if (unbraced.isNotEmpty() && !Keycode.isStdKey(code)) {
                    text = raw
                    label = raw.replace(BRACED_STR, "")
                } else if (label.isEmpty()) {
                    label =
                        when (code) {
                            KeyEvent.KEYCODE_SPACE -> rime.run { statusCached }.schemaName
                            KeyEvent.KEYCODE_UNKNOWN -> ""
                            else -> Keycode.getDisplayLabel(code, modifier)
                        }
                }
            }
        }
        // 設定 Shift 標籤
        shiftLabel = label
        if (Keycode.isStdKey(code) && virtualKeyCharacterMap.isPrintingKey(code)) {
            virtualKeyCharacterMap.get(code, modifier or KeyEvent.META_SHIFT_ON).takeIf { it > 0 }?.let { charCode ->
                shiftLabel = charCode.toChar().toString()
            }
        }
    }

    companion object {
        /** 用於匹配大括號包圍字串的正則表達式 */
        private val BRACED_STR = Regex("""\{[^{}]+\}""")

        /**
         * 從字串解碼鍵值對映射
         *
         * @param str 包含鍵值對的字串，格式為 "{key1=value1, key2=value2}"
         * @return 解碼後的鍵值對映射
         */
        private fun decodeMapFromString(str: String): Map<String, String> =
            str
                .removeSurrounding("{", "}")
                .split(", ")
                .mapNotNull {
                    it.split("=").takeIf { it.size == 2 }?.let { (key, value) -> key to value }
                }.toMap()
    }
}
