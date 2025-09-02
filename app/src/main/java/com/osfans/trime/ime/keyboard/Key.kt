// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.graphics.drawable.Drawable
import android.view.KeyEvent
import androidx.annotation.ColorInt
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.data.theme.model.TextKeyboard
import splitties.bitflags.hasFlag

/**
 * 鍵盤中的按鍵類別，代表虛擬鍵盤上的每個按鍵元件
 *
 * 此類別封裝了按鍵的所有屬性和行為，包括外觀配置、事件處理、位置資訊等。
 * 每個按鍵可以支援多種輸入行為（點擊、長按、滑動等），並根據鍵盤狀態動態調整顯示效果。
 *
 * 主要功能：
 * - 管理按鍵的位置、尺寸和外觀
 * - 處理點擊、長按、滑動等各種輸入行為
 * - 根據輸入法狀態動態切換顯示內容
 * - 支援修飾鍵（Shift、Ctrl、Alt等）的狀態管理
 * - 提供按鍵顏色和背景的主題化支援
 *
 * @param parent 所屬的鍵盤實例
 * @param selfConfig 按鍵的自定義配置，若為 null 則使用預設配置
 *
 * @see Keyboard
 * @see KeyAction
 * @see KeyBehavior
 */
class Key(
    private val parent: Keyboard,
    private val selfConfig: TextKeyboard.TextKey? = null,
) {
    private val rime = RimeDaemon.getFirstSessionOrNull()!!

    /** 按鍵支援的所有行為動作映射表 */
    val keyActions: Map<KeyBehavior, KeyAction> =
        buildMap {
            selfConfig?.behaviors?.forEach {
                put(it.key, KeyActionManager.getAction(it.value))
            }
        }
    /** 按鍵的邊緣標記，用於標示按鍵是否位於鍵盤的邊緣 */
    var edgeFlags = 0
    private val sendBindings: Boolean

    /** 按鍵是否目前處於按下狀態 */
    var isPressed = false
        private set

    /** 按鍵是否處於開啟狀態（用於黏性按鍵） */
    var isOn = false
        private set

    /** 按鍵的 x 座標位置 */
    var x = 0

    /** 按鍵的 y 座標位置 */
    var y = 0

    /** 按鍵寬度 */
    var width = 0

    /** 按鍵高度 */
    var height = 0

    /** 按鍵間距 */
    var gap = 0

    /** 按鍵所在行號 */
    var row = 0

    /** 按鍵所在列號 */
    var column = 0

    private val label = selfConfig?.label ?: ""
    private val labelSymbol = selfConfig?.labelSymbol ?: ""
    /** 按鍵的提示文字 */
    val hint: String = selfConfig?.hint ?: ""

    /** 按鍵主要文字的字體大小 */
    val keyTextSize: Float = selfConfig?.keyTextSize ?: 0f

    /** 按鍵符號文字的字體大小 */
    val symbolTextSize: Float = selfConfig?.symbolTextSize ?: 0f

    /** 按鍵的圓角半徑 */
    val roundCorner: Float = selfConfig?.roundCorner ?: 0f
    var keyTextOffsetX = 0f
        get() = field + keyOffsetX
    var keyTextOffsetY = 0f
        get() = field + keyOffsetY
    var keySymbolOffsetX = 0f
        get() = field + keyOffsetX
    var keySymbolOffsetY = 0f
        get() = field + keyOffsetY
    var keyHintOffsetX = 0f
        get() = field + keyOffsetX
    var keyHintOffsetY = 0f
        get() = field + keyOffsetY
    var keyPressOffsetX = 0
    var keyPressOffsetY = 0

    // get color from key customization or just fallback to specified color
    private fun getColor(
        src: TextKeyboard.TextKey.() -> String,
        fallback: String,
    ): Int =
        selfConfig?.let {
            runCatching { ColorManager.getColor(src(it)) }.getOrNull()
        } ?: ColorManager.getColor(fallback)

    // get color from common color schemes or just fallback to default color
    private fun getColor(
        key: String,
        @ColorInt default: Int,
    ): Int = runCatching { ColorManager.getColor(key) }.getOrDefault(default)

    private fun getDrawable(
        src: TextKeyboard.TextKey.() -> String,
        fallback: String,
    ) = selfConfig?.let {
        if (src(it).isEmpty()) null
        ColorManager.getDrawable(src(it))
    } ?: ColorManager.getDrawable(fallback)

    private val keyBackground by lazy { getDrawable({ keyBackColor }, "key_back_color") }
    private val offKeyBackground by lazy { ColorManager.getDrawable("off_key_back_color") }
    private val onKeyBackground by lazy { ColorManager.getDrawable("on_key_back_color") }

    private val keyTextColor by lazy { getColor({ keyTextColor }, "key_text_color") }
    private val offKeyTextColor by lazy { getColor("off_key_text_color", keyTextColor) }
    private val onKeyTextColor by lazy { getColor("on_key_text_color", keyTextColor) }
    private val keySymbolColor by lazy { getColor({ keySymbolColor }, "key_symbol_color") }
    private val offKeySymbolColor by lazy { getColor("off_key_symbol_color", keySymbolColor) }
    private val onKeySymbolColor by lazy { getColor("on_key_symbol_color", keySymbolColor) }
    private val hlKeyBackground by lazy { getDrawable({ hlKeyBackColor }, "hilited_key_back_color") }
    private val hlOffKeyBackground by lazy { ColorManager.getDrawable("hilited_off_key_back_color") }
    private val hlOnKeyBackground by lazy { ColorManager.getDrawable("hilited_on_key_back_color") }
    private val hlKeyTextColor by lazy { getColor({ hlKeyTextColor }, "hilited_key_text_color") }
    private val hlOffKeyTextColor by lazy { getColor("hilited_off_key_text_color", hlKeyTextColor) }
    private val hlOnKeyTextColor by lazy { getColor("hilited_on_key_text_color", hlKeyTextColor) }
    private val hlKeySymbolColor by lazy { getColor({ hlKeySymbolColor }, "hilited_key_symbol_color") }
    private val hlOffKeySymbolColor by lazy { getColor("hilited_off_key_symbol_color", hlKeySymbolColor) }
    private val hlOnKeySymbolColor by lazy { getColor("hilited_on_key_symbol_color", hlKeySymbolColor) }

    init {
        if (selfConfig != null) {
            val hasComposingKey = selfConfig.behaviors.keys.any { it < KeyBehavior.COMBO }
            if (hasComposingKey) parent.composingKeys.add(this)
            sendBindings = selfConfig.sendBindings || hasComposingKey
        } else {
            sendBindings = true
        }
        parent.setModifierKey(this.code, this)
    }

    fun setOn(on: Boolean): Boolean {
        isOn = if (on && isOn) false else on
        return isOn
    }

    private val keyOffsetX: Int
        get() = if (isPressed) keyPressOffsetX else 0
    private val keyOffsetY: Int
        get() = if (isPressed) keyPressOffsetY else 0

    /**
     * Informs the key that it has been pressed, in case it needs to change its appearance or state.
     *
     * @see .onReleased
     */
    fun onPressed() {
        isPressed = true
    }

    /**
     * Changes the pressed state of the key. If it is a sticky key, it will also change the toggled
     * state of the key if the finger was release inside.
     *
     * @see .onPressed
     */
    fun onReleased() {
        isPressed = false
        if (click!!.isSticky) isOn = !isOn
    }

    /**
     * Detects if a point falls inside this key.
     *
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return whether or not the point falls inside the key. If the key is attached to an edge, it
     * will assume that all points between the key and the edge are considered to be inside the
     * key.
     */
    fun isInside(
        x: Int,
        y: Int,
    ): Boolean {
        val leftEdge = edgeFlags and Keyboard.EDGE_LEFT > 0
        val rightEdge = edgeFlags and Keyboard.EDGE_RIGHT > 0
        val topEdge = edgeFlags and Keyboard.EDGE_TOP > 0
        val bottomEdge = edgeFlags and Keyboard.EDGE_BOTTOM > 0
        return (
            (x >= this.x || leftEdge && x <= this.x + width) &&
                (x < this.x + width || rightEdge && x >= this.x) &&
                (y >= this.y || topEdge && y <= this.y + height) &&
                (y < this.y + height || bottomEdge && y >= this.y)
        )
    }

    /**
     * Returns the square of the distance between the center of the key and the given point.
     *
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return the square of the distance of the point from the center of the key
     */
    fun squaredDistanceFrom(
        x: Int,
        y: Int,
    ): Int {
        val xDist = this.x + width / 2 - x
        val yDist = this.y + height / 2 - y
        return xDist * xDist + yDist * yDist
    }

    val isModifierKey: Boolean
        // Trime把function键消费掉了，因此键盘只处理function键以外的修饰键
        get() = KeyEvent.isModifierKey(this.code) && this.code != KeyEvent.KEYCODE_FUNCTION

    /** 取得此修飾鍵的按下狀態遐罩 */
    val modifierKeyOnMask: Int
        get() = getModifierKeyOnMask(this.code)

    /**
     * 根據按鍵代碼取得對應的修飾鍵狀態遐罩
     *
     * @param keycode 按鍵代碼
     * @return 對應的修飾鍵狀態遐罩
     */
    private fun getModifierKeyOnMask(keycode: Int): Int =
        when (keycode) {
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> KeyEvent.META_SHIFT_ON
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> KeyEvent.META_CTRL_ON
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> KeyEvent.META_META_ON
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> KeyEvent.META_ALT_ON
            KeyEvent.KEYCODE_SYM -> KeyEvent.META_SYM_ON
            else -> 0
        }

    /** 檢查此按鍵是否為 Shift 鍵 */
    val isShift: Boolean
        get() = this.code == KeyEvent.KEYCODE_SHIFT_LEFT || this.code == KeyEvent.KEYCODE_SHIFT_RIGHT

    /** 檢查 Shift 鍵在點擊時是否觸發鎖定狀態 */
    val isShiftLock: Boolean
        // Shift、Ctrl、Alt、Meta 等修飾鍵在點擊時是否觸發鎖定
        get() =
            when (click?.shiftLock) {
                "long" -> false // 长按锁定
                "click" -> true // 点击锁定
                "ascii_long" -> !rime.run { statusCached }.isAsciiMode // 英文长按锁定，中文点击锁定
                else -> false
            }

    /**
     * 檢查指定行為是否需要傳送按鍵綁定事件
     *
     * @param behavior 按鍵行為類型（點擊/長按/滑動等）
     * @return 如果需要傳送綁定事件則返回 true
     */
    fun sendBindings(behavior: KeyBehavior): Boolean =
        keyActions[behavior]?.takeIf { behavior != KeyBehavior.CLICK } != null || checkKeyAction(sendBindings) != null

    private val keyAction: KeyAction?
        get() = checkKeyAction() ?: click

    /** 取得按鍵的點擊動作 */
    val click: KeyAction?
        get() = keyActions[KeyBehavior.CLICK]

    /** 取得按鍵的長按動作 */
    val longClick: KeyAction?
        get() = keyActions[KeyBehavior.LONG_CLICK]

    /**
     * 檢查按鍵是否定義了指定的行為動作
     *
     * @param behavior 要檢查的按鍵行為類型
     * @return 如果按鍵定義了該行為則返回 true
     */
    fun hasAction(behavior: KeyBehavior): Boolean = keyActions[behavior] != null

    /**
     * 取得指定行為對應的按鍵動作
     *
     * 會根據當前輸入法狀態選擇合適的動作，若無特定動作則回傳預設的點擊動作。
     *
     * @param behavior 要取得的按鍵行為類型
     * @return 對應的按鍵動作，若無定義則返回 null
     */
    fun getAction(behavior: KeyBehavior): KeyAction? =
        keyActions[behavior]?.takeIf { behavior != KeyBehavior.CLICK } ?: checkKeyAction(sendBindings) ?: click

    private fun checkKeyAction(): KeyAction? {
        val status = rime.run { statusCached }
        val menu = rime.run { menuCached }
        return keyActions[KeyBehavior.ASCII].takeIf { status.isAsciiMode }
            ?: keyActions[KeyBehavior.PAGING]?.takeIf { menu.pageNumber != 0 }
            ?: keyActions[KeyBehavior.HAS_MENU]?.takeIf { menu.candidates.isNotEmpty() }
            ?: keyActions[KeyBehavior.COMPOSING]?.takeIf { status.isComposing }
    }

    private fun checkKeyAction(sendBindings: Boolean): KeyAction? = checkKeyAction().takeIf { sendBindings }

    /** 取得按鍵的代碼（預設為點擊動作的代碼） */
    val code: Int
        get() = click?.code ?: KeyEvent.KEYCODE_UNKNOWN

    /**
     * 取得指定行為對應的按鍵代碼
     *
     * @param behavior 按鍵行為類型
     * @return 對應的按鍵代碼
     */
    fun getCode(behavior: KeyBehavior): Int = getAction(behavior)!!.code

    /**
     * 取得按鍵應顯示的標籤文字
     *
     * 根據當前輸入法狀態和按鍵配置決定顯示內容，
     * 在中文狀態下可能顯示自定義標籤，在英文狀態下顯示按鍵動作標籤。
     *
     * @return 按鍵應顯示的標籤文字
     */
    fun getLabel(): String =
        when {
            label.isNotEmpty() &&
                keyAction == click &&
                !keyActions.containsKey(KeyBehavior.ASCII) &&
                !rime.run { statusCached }.let { it.isAsciiMode || it.isAsciiPunch } -> label
            else -> keyAction!!.getLabel(parent) // 中文狀態顯示標籤
        }

    /**
     * 取得指定行為的預覽文字
     *
     * 當使用者長按按鍵顯示預覽時使用，不同行為可能顯示不同的預覽內容。
     *
     * @param behavior 按鍵行為類型
     * @return 對應行為的預覽文字
     */
    fun getPreviewText(behavior: KeyBehavior): String =
        when (behavior) {
            KeyBehavior.CLICK -> keyAction!!.getPreview(parent)
            else -> getAction(behavior)!!.getPreview(parent)
        }

    /** 取得按鍵的符號標籤文字 */
    val symbolLabel: String
        get() = labelSymbol.ifEmpty { longClick?.getLabel(parent) ?: "" }

    private val appearanceType: Int
        get() {
            return when {
                isModifierKey && parent.modifier.hasFlag(modifierKeyOnMask) || isOn -> 2
                click?.isSticky == true || click?.isFunctional == true -> 1
                else -> 0
            }
        }

    /**
     * 取得按鍵的背景繪製物件
     *
     * 根據按鍵的外觀類型（一般按鍵、功能按鍵、修飾鍵）和狀態（按下、未按下）
     * 返回對應的背景繪製物件。
     *
     * @return 按鍵背景的 Drawable 物件，若無自定義背景則可能返回 null
     */
    fun getBackgroundDrawable(): Drawable? =
        when (appearanceType) {
            2 -> if (isPressed) hlOnKeyBackground else onKeyBackground
            1 -> {
                if (isPressed) {
                    hlOffKeyBackground ?: hlKeyBackground
                } else {
                    selfConfig?.keyBackColor.takeIf { !it.isNullOrEmpty() }?.let { keyBackground }
                        ?: (offKeyBackground ?: keyBackground)
                }
            }
            else -> if (isPressed) hlKeyBackground else keyBackground
        }

    /**
     * 取得按鍵文字的顏色值
     *
     * 根據按鍵類型和當前狀態返回對應的文字顏色。
     * 不同類型的按鍵（一般、功能、修飾鍵）可能有不同的顏色配置。
     *
     * @return 按鍵文字的顏色值（ARGB 格式）
     */
    fun getTextColor(): Int =
        when (appearanceType) {
            2 -> if (isPressed) hlOnKeyTextColor else onKeyTextColor
            1 -> if (isPressed) hlOffKeyTextColor else getColor(selfConfig?.keyTextColor ?: "", offKeyTextColor)
            else -> if (isPressed) hlKeyTextColor else keyTextColor
        }

    /**
     * 取得按鍵符號文字的顏色值
     *
     * 用於顯示按鍵上的輔助符號或提示文字的顏色。
     * 通常與主要文字顏色略有不同，用於區分層次。
     *
     * @return 按鍵符號文字的顏色值（ARGB 格式）
     */
    fun getSymbolColor(): Int =
        when (appearanceType) {
            2 -> if (isPressed) hlOnKeySymbolColor else onKeySymbolColor
            1 -> if (isPressed) hlOffKeySymbolColor else offKeySymbolColor
            else -> if (isPressed) hlKeySymbolColor else keySymbolColor
        }
}
