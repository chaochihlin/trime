// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.view.KeyEvent
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.ime.keyboard.KeyboardPrefs.isLandscapeMode
import com.osfans.trime.util.appContext
import com.osfans.trime.util.sp
import splitties.bitflags.hasFlag
import splitties.dimensions.dp
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.pow

/**
 * 虛擬鍵盤類別，從 YAML 配置檔案中載入鍵盤布局和按鍵定義
 *
 * 此類別是輸入法鍵盤系統的核心組件，負責管理整個鍵盤的布局、按鍵配置和狀態。
 * 它包含多個 [Key] 實例，每個都代表一個可互動的按鍵元件。
 *
 * 主要功能：
 * - 從主題配置載入鍵盤师局和外觀設定
 * - 動態計算按鍵的位置、尺寸和間距
 * - 管理修飾鍵（Shift、Ctrl、Alt等）的狀態
 * - 支援橫/直屏和分割鍵盤模式
 * - 提供按鍵的距離計算和最近鄰居檢索
 * - 支援自動高度調整和響應式布局
 *
 * @param theme 主題配置物件，包含整體外觀設定
 * @param selfConfig 鍵盤的自定義配置，若為 null 則使用預設配置
 *
 * @see Key
 * @see Theme
 * @see TextKeyboard
 */
@Suppress("ktlint:standard:property-naming")
class Keyboard(
    private val theme: Theme,
    selfConfig: TextKeyboard? = null,
) {
    /** 按鍵預設水平間距 */
    private val horizontalGap: Int =
        (
            intArrayOf(
                selfConfig?.horizontalGap ?: 0,
                theme.generalStyle.horizontalGap,
            ).firstOrNull { it > 0 } ?: 0
        ).also { appContext.dp(it) }

    /** 按鍵預設寬度 */
    private val keyWidth: Int = (allowedWidth * theme.generalStyle.keyWidth / 100).toInt()

    /** 按鍵預設高度 */
    private val keyHeight: Int =
        (
            intArrayOf(
                selfConfig?.height?.toInt() ?: 0,
                theme.generalStyle.keyHeight,
            ).firstOrNull { it > 0 } ?: 0
        ).also { appContext.dp(it) }

    /** 按鍵預設垂直間距 */
    private val verticalGap: Int =
        (
            intArrayOf(
                selfConfig?.verticalGap ?: 0,
                theme.generalStyle.verticalGap,
            ).firstOrNull { it > 0 } ?: 0
        ).also { appContext.dp(it) }

    /** 按鍵預設圓角半徑 */
    val roundCorner: Float =
        floatArrayOf(
            selfConfig?.roundCorner ?: 0f,
            theme.generalStyle.roundCorner,
        ).firstOrNull { it > 0 } ?: 0f

    /** 鍵盤上的 Shift 修飾鍵 */
    var mShiftKey: Key? = null
    var mCtrlKey: Key? = null
    var mAltKey: Key? = null
    var mMetaKey: Key? = null
    var mSymKey: Key? = null

    /** 鍵盤的總高度（包括所有按鍵和間距） */
    var height = 0
        private set

    /** 鍵盤的最小寬度（包括左側間距和按鍵，不包括右側間距） */
    var minWidth = 0
        private set

    /** 鍵盤中的所有按鍵列表 */
    private val mKeys = mutableListOf<Key>()
    /** 可用於組字的按鍵列表 */
    val composingKeys = mutableListOf<Key>()
    /** 當前按下的修飾鍵狀態遐罩 */
    var modifier = 0
        private set

    /** 螢幕可用於放置鍵盤的寬度 */
    private val allowedWidth: Int
        get() {
            val keyboardSidePadding = theme.generalStyle.keyboardPadding
            val keyboardSidePaddingLandscape = theme.generalStyle.keyboardPaddingLand
            val sidePaddingPx = if (appContext.isLandscapeMode()) keyboardSidePaddingLandscape else keyboardSidePadding
            return appContext.resources.displayMetrics.widthPixels - 2 * appContext.dp(sidePaddingPx)
        }

    /** 鍵盤預設 ASCII 模式 */
    val asciiMode = selfConfig?.asciiMode ?: false
    val resetAsciiMode = selfConfig?.resetAsciiMode ?: true

    private val preferredSplitPercent by AppPrefs.defaultInstance().keyboard.splitSpacePercent
    private val landscapePercent =
        intArrayOf(
            selfConfig?.landscapeSplitPercent ?: 0,
            preferredSplitPercent,
        ).firstOrNull { it > 0 } ?: 0

    // 預計算最近按鍵的變數
    private val labelTransform = selfConfig?.labelTransform ?: TextKeyboard.LabelTransform.NONE
    private var mCellWidth = 0
    private var mCellHeight = 0
    private var gridNeighbors: Array<IntArray?>? = null

    private val proximityThreshold: Int =
        (keyWidth * SEARCH_DISTANCE).pow(2).toInt() // Square it for comparison
    val isLock = selfConfig?.lock ?: false // 切換程序時記憶鍵盤
    val asciiKeyboard: String? = selfConfig?.asciiKeyboard // 英文鍵盤

    // 待辦：將按下按鍵彈出的內容改為單獨設計的檢視，而不是鍵盤
    val keyboardHeight: Int =
        intArrayOf(
            selfConfig?.let { getKeyboardHeightFromKeyboardConfig(it) } ?: 0,
            getKeyboardHeightFromTheme(theme),
        ).firstOrNull { it > 0 } ?: 0

    init {
        if (selfConfig != null) {
            val columns = selfConfig.columns
            // 按鍵高度取值順序：keys > keyboard/height > style/key_height
            // 考慮到 key 設定 height_land 需要對皮膚做大量修改，而當部分 key 設定 height 而部分沒有設定時會造成按鍵高度異常，故取消普通按鍵的 height_land 參數
            var rowHeight = keyHeight
            // 定義新的鍵盤尺寸計算方式，避免尺寸計算不恰當，導致切換鍵盤時鍵盤高度發生變化，UI 閃爍的問題。同時可以快速調整整個鍵盤的尺寸
            // 1. default 鍵盤的高度 = 其他鍵盤的高度
            // 2. 當鍵盤高度（不含 padding）與 keyboard_height 不一致時，每行按鍵等比例縮放按鍵高度，行之間的間距向上取整數、padding 不縮放
            // 3. 由於高度只能取整數，縮放後仍然存在餘數的，由 auto_height_index 指定的行吸收（遵循四捨五入）
            //    特別的，當值為負數時，為倒序序號（-1 即倒數第一個）；當值大於按鍵行數時，為最後一行
            val autoHeightIndex = selfConfig.autoHeightIndex
            val keys = selfConfig.keys
            val keyboardKeyWidth = selfConfig.width
            val maxColumns = if (columns == -1) Int.MAX_VALUE else columns
            val isSplit = appContext.isLandscapeMode() && landscapePercent > 0
            val (rowWidthTotalWeight, oneWeightWidthPx, multiplier, scaledHeight, scaledVerticalGap) =
                KeyboardSizeCalculator(
                    isSplit,
                    landscapePercent,
                    maxColumns,
                    allowedWidth,
                    keyboardHeight,
                    keyboardKeyWidth,
                    keyHeight,
                    horizontalGap,
                    verticalGap,
                    autoHeightIndex,
                ).calc(keys)

            var x = this.horizontalGap / 2
            var y = scaledVerticalGap
            var row = 0
            var column = 0
            minWidth = 0

            try {
                var rowWidthWeight = 0f
                for (textKey in keys) {
                    val gap = this.horizontalGap
                    val keyWidth =
                        if (textKey.width == 0f && textKey.click.isNotEmpty()) {
                            keyboardKeyWidth
                        } else {
                            textKey.width
                        }
                    var widthPx = (keyWidth * oneWeightWidthPx).toInt()
                    widthPx -= gap
                    if (column >= maxColumns || x + widthPx > allowedWidth) {
                        // 新行
                        rowWidthWeight = 0f
                        x = gap / 2
                        y += scaledVerticalGap + rowHeight
                        column = 0
                        row++
                        if (mKeys.isNotEmpty()) {
                            mKeys[mKeys.size - 1].edgeFlags =
                                mKeys[mKeys.size - 1].edgeFlags or EDGE_RIGHT
                        }
                    }
                    rowWidthWeight += keyWidth
                    val totalWeightOfThisRow = rowWidthTotalWeight[row] ?: 0f
                    if (isSplit && rowWidthWeight >= totalWeightOfThisRow / 2 + 1) {
                        rowWidthWeight = Int.MIN_VALUE.toFloat()
                        val weight = (totalWeightOfThisRow * multiplier * oneWeightWidthPx).toInt()
                        if (keyWidth > 20) {
                            // 如果此按鍵是長按鍵則放大按鍵
                            widthPx += weight
                        } else {
                            x += weight // (10 * (defaultWidth))
                        }
                    }
                    if (column == 0) {
                        rowHeight =
                            if (keyboardHeight > 0) {
                                scaledHeight[row]
                            } else {
                                if (textKey.height > 0) {
                                    appContext.sp(textKey.height).toInt()
                                } else {
                                    keyHeight
                                }
                            }
                    }
                    if (textKey.click.isEmpty()) { // 無按鍵事件
                        x += widthPx + gap
                        continue // 縮排
                    }
                    val key = Key(this, textKey)
                    key.keyTextOffsetX = textKey.keyTextOffsetX
                    key.keyTextOffsetY = textKey.keyTextOffsetY
                    key.keySymbolOffsetX = textKey.keySymbolOffsetX
                    key.keySymbolOffsetY = textKey.keySymbolOffsetY
                    key.keyHintOffsetX = textKey.keyHintOffsetX
                    key.keyHintOffsetY = textKey.keyHintOffsetY
                    key.keyPressOffsetX = textKey.keyPressOffsetX
                    key.keyPressOffsetY = textKey.keyPressOffsetY
                    key.x = x
                    key.y = y
                    val rightGap = abs(allowedWidth - x - widthPx - gap / 2)
                    // 右側不留白
                    key.width =
                        if (rightGap <= allowedWidth / 100) allowedWidth - x - gap / 2 else widthPx
                    key.height = rowHeight
                    key.gap = gap
                    key.row = row
                    key.column = column
                    column++
                    x += key.width + key.gap
                    mKeys.add(key)
                    if (x > minWidth) {
                        minWidth = x
                    }
                }
                if (mKeys.isNotEmpty()) {
                    mKeys[mKeys.size - 1].edgeFlags =
                        mKeys[mKeys.size - 1].edgeFlags or EDGE_RIGHT
                }
                this.height = y + rowHeight + scaledVerticalGap
                for (key in mKeys) {
                    if (key.column == 0) key.edgeFlags = key.edgeFlags or EDGE_LEFT
                    if (key.row == 0) key.edgeFlags = key.edgeFlags or EDGE_TOP
                    if (key.row == row) key.edgeFlags = key.edgeFlags or EDGE_BOTTOM
                }
            } catch (e: Exception) {
                Timber.e(e, "建立鍵盤失敗")
            }
        }
    }

    /**
     * 從主題獲取鍵盤高度
     *
     * @param theme 主題物件
     * @return 鍵盤高度（像素）
     */
    private fun getKeyboardHeightFromTheme(theme: Theme): Int {
        var keyboardHeight = theme.generalStyle.keyboardHeight
        if (appContext.isLandscapeMode()) {
            val keyboardHeightLand = theme.generalStyle.keyboardHeightLand
            if (keyboardHeightLand > 0) keyboardHeight = keyboardHeightLand
        }
        return appContext.dp(keyboardHeight)
    }

    /**
     * 從鍵盤配置獲取鍵盤高度
     *
     * @param textKeyboard 文字鍵盤配置
     * @return 鍵盤高度（像素）
     */
    private fun getKeyboardHeightFromKeyboardConfig(textKeyboard: TextKeyboard): Int {
        var keyboardHeight = textKeyboard.keyboardHeight
        if (appContext.isLandscapeMode()) {
            val keyboardHeightLand = textKeyboard.keyboardHeightLand
            if (keyboardHeightLand > 0) keyboardHeight = keyboardHeightLand
        }
        return appContext.dp(keyboardHeight)
    }

    /**
     * 設定修飾鍵
     *
     * @param c 按鍵代碼
     * @param key 要設定的按鍵物件
     */
    fun setModifierKey(
        c: Int,
        key: Key?,
    ) {
        when (c) {
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> {
                mShiftKey = key
            }
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> {
                mCtrlKey = key
            }
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> {
                mMetaKey = key
            }
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> {
                mAltKey = key
            }
            KeyEvent.KEYCODE_SYM -> {
                mSymKey = key
            }
        }
    }

    /**
     * 獲取鍵盤中所有按鍵的列表
     */
    val keys: List<Key>
        get() = mKeys

    /**
     * 設定修飾鍵狀態
     *
     * @param mask 修飾鍵掩碼
     * @param value 要設定的狀態值
     * @return 修飾鍵狀態是否發生變化
     */
    private fun setModifier(
        mask: Int,
        value: Boolean,
    ): Boolean {
        if (modifier.hasFlag(mask) == value) return false
        modifier = if (value) modifier or mask else modifier and mask.inv()
        return true
    }

    /**
     * 檢查 Shift 鍵是否被按下或鎖定
     */
    val isShifted: Boolean
        get() = modifier.hasFlag(KeyEvent.META_SHIFT_ON) || mShiftKey?.isOn == true

    /**
     * 檢查是否只有 Shift 鍵被按下，沒有其他修飾鍵
     */
    val isOnlyShiftOn: Boolean
        get() =
            isShifted &&
                !modifier.hasFlag(KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON or KeyEvent.META_SYM_ON or KeyEvent.META_META_ON)

    /**
     * 設置 Shift 鍵狀態（用於自動大寫）
     *
     * @param on 是否鎖定 Shift 鍵
     * @param shifted 是否按下 Shift 鍵
     * @return Shift 鍵狀態是否改變
     */
    fun setShifted(
        on: Boolean,
        shifted: Boolean,
    ): Boolean {
        mShiftKey?.setOn(on)
        return setModifier(KeyEvent.META_SHIFT_ON, shifted)
    }

    /**
     * 設置修飾鍵的狀態
     *
     * @param on 是否鎖定修飾鍵
     * @param keycode 修飾鍵的 KeyEvent 掩碼
     * @return 修飾鍵狀態是否改變
     */
    fun clickModifierKey(
        on: Boolean,
        keycode: Int,
    ): Boolean {
        val keyDown = !modifier.hasFlag(keycode)
        val modifierKey =
            when (keycode) {
                KeyEvent.META_SHIFT_ON -> mShiftKey
                KeyEvent.META_ALT_ON -> mAltKey
                KeyEvent.META_CTRL_ON -> mCtrlKey
                KeyEvent.META_META_ON -> mMetaKey
                KeyEvent.KEYCODE_SYM -> mSymKey
                else -> null
            }
        val keepOn = modifierKey?.setOn(on) ?: on
        return if (on) setModifier(keycode, keepOn) else setModifier(keycode, keyDown)
    }

    /**
     * 刷新修飾鍵狀態，重置所有未鎖定的修飾鍵
     *
     * @return 修飾鍵狀態是否發生變化
     */
    fun refreshModifier(): Boolean {
        // 此處改為一次性重置全部修飾鍵狀態並返回 TRUE 刷新 UI，可能有問題
        var result = false
        if (mShiftKey != null && !mShiftKey!!.isOn) result = result || setModifier(KeyEvent.META_SHIFT_ON, false)
        if (mAltKey != null && !mAltKey!!.isOn) result = result || setModifier(KeyEvent.META_ALT_ON, false)
        if (mCtrlKey != null && !mCtrlKey!!.isOn) result = result || setModifier(KeyEvent.META_CTRL_ON, false)
        if (mMetaKey != null && !mMetaKey!!.isOn) result = result || setModifier(KeyEvent.META_META_ON, false)
        if (mSymKey != null && !mSymKey!!.isOn) result = result || setModifier(KeyEvent.KEYCODE_SYM, false)
        return result
    }

    /**
     * 計算最近鄰按鍵的網格
     */
    private fun computeNearestNeighbors() {
        // 向上取整以免有任何像素落在網格外
        mCellWidth = (minWidth + GRID_WIDTH - 1) / GRID_WIDTH
        mCellHeight = (height + GRID_HEIGHT - 1) / GRID_HEIGHT
        gridNeighbors = arrayOfNulls(GRID_SIZE)
        val indices = IntArray(mKeys.size)
        val gridWidth = GRID_WIDTH * mCellWidth
        val gridHeight = GRID_HEIGHT * mCellHeight
        var x = 0
        while (x < gridWidth) {
            var y = 0
            while (y < gridHeight) {
                var count = 0
                for (i in mKeys.indices) {
                    val key = mKeys[i]
                    if (key.squaredDistanceFrom(x, y) < proximityThreshold ||
                        key.squaredDistanceFrom(x + mCellWidth - 1, y) < proximityThreshold ||
                        (
                            key.squaredDistanceFrom(x + mCellWidth - 1, y + mCellHeight - 1)
                                < proximityThreshold
                        ) ||
                        key.squaredDistanceFrom(x, y + mCellHeight - 1) < proximityThreshold ||
                        key.isInside(x, y) ||
                        key.isInside(x + mCellWidth - 1, y) ||
                        key.isInside(x + mCellWidth - 1, y + mCellHeight - 1) ||
                        key.isInside(x, y + mCellHeight - 1)
                    ) {
                        indices[count++] = i
                    }
                }
                val cell = IntArray(count)
                System.arraycopy(indices, 0, cell, 0, count)
                gridNeighbors?.set(y / mCellHeight * GRID_WIDTH + x / mCellWidth, cell)
                y += mCellHeight
            }
            x += mCellWidth
        }
    }

    /**
     * 取得距離指定座標點最近的按鍵索引陣列
     *
     * 使用預計算的網格系統快速找出指定座標附近的所有按鍵。
     * 這個方法主要用於觸摸事件的按鍵匹配。
     *
     * @param x 目標點的 x 座標
     * @param y 目標點的 y 座標
     * @return 最近按鍵的索引陣列，若座標超出範圍則返回空陣列
     */
    fun getNearestKeys(
        x: Int,
        y: Int,
    ): IntArray? {
        if (gridNeighbors == null) computeNearestNeighbors()
        if (x in 0 until minWidth && y in 0 until height) {
            val index = y / mCellHeight * GRID_WIDTH + x / mCellWidth
            if (index < GRID_SIZE) {
                return gridNeighbors!![index]
            }
        }
        return IntArray(0)
    }

    /** 檢查按鍵標籤是否應該強制顯示為大寫 */
    val isLabelUppercase: Boolean
        get() = labelTransform == TextKeyboard.LabelTransform.UPPERCASE

    companion object {
        /** 按鍵位於鍵盤左邊緣的標記 */
        const val EDGE_LEFT = 0x01
        
        /** 按鍵位於鍵盤右邊緣的標記 */
        const val EDGE_RIGHT = 0x02
        
        /** 按鍵位於鍵盤上邊緣的標記 */
        const val EDGE_TOP = 0x04
        
        /** 按鍵位於鍵盤下邊緣的標記 */
        const val EDGE_BOTTOM = 0x08
        
        /** 網格系統的水平網格數量 */
        private const val GRID_WIDTH = 10
        
        /** 網格系統的垂直網格數量 */
        private const val GRID_HEIGHT = 5
        
        /** 網格系統的總網格數量 */
        private const val GRID_SIZE = GRID_WIDTH * GRID_HEIGHT

        /** 從當前觸摸點搜尋最近按鍵的搜尋半徑（以按鍵寬度為單位） */
        const val SEARCH_DISTANCE = 1.4f
    }
}
