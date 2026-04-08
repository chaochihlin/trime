// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import splitties.dimensions.dp
import timber.log.Timber

/**
 * T9注音鍵盤視圖（12鍵版）
 *
 * 專為智慧手錶圓形螢幕設計，佈局：
 * - 4×3 注音鍵網格（12鍵，按傳統注音順序分組）
 * - 獨立數字鍵列（0-9，直接輸入數字）
 * - 底部語言切換鍵
 */
class T9KeyboardView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : ConstraintLayout(context, attrs, defStyleAttr) {
        companion object {
            private const val TAG = "T9KeyboardView"

            // 文字間隔等寬設計：3-symbol 欄較寬，2-symbol 欄較窄
            private const val COL_WIDE = 0.28f
            private const val COL_NARROW = 0.22f

            // 每行的按鍵定義：(keyNum, half) — half: 0=完整, 1=前半, 2=後半
            private val ROW_DEFS =
                arrayOf(
                    arrayOf(1 to 0, 2 to 0, 3 to 0),
                    arrayOf(4 to 0, 5 to 0, 6 to 1, 6 to 2),
                    arrayOf(7 to 0, 8 to 0, 9 to 1, 9 to 2),
                    arrayOf(0 to 0, 10 to 0, 11 to 1, 11 to 2),
                )
        }

        interface T9KeyboardActionListener {
            fun onNumberKeyPress(
                number: Int,
                half: Int = 0,
            )

            fun onNumberKeyLongPress(number: Int): Boolean

            fun onDigitInput(digit: Int)

            fun onConfirmPress()

            fun onLanguageSwitch()
        }

        private var actionListener: T9KeyboardActionListener? = null
        private lateinit var theme: Theme
        private lateinit var rimeSession: RimeSession

        // 注音鍵（15個：Row1×3 + Row2-4×4，4-symbol 鍵拆成兩個視覺按鍵）
        // 二維陣列 [row][col]，與 ROW_DEFS 對應
        private val zhuyinKeys: Array<Array<T9NumberKey>> =
            Array(ROW_DEFS.size) { row ->
                Array(ROW_DEFS[row].size) { col ->
                    val (keyNum, half) = ROW_DEFS[row][col]
                    T9NumberKey(context).apply {
                        id = generateViewId()
                        keyNumber = -1 // 不顯示數字
                        keyHints = getHintsForNumber(keyNum, half)
                        tag = keyNum // 用 tag 記錄實際 key number

                        setOnClickListener { actionListener?.onNumberKeyPress(keyNum, half) }
                        setOnLongClickListener {
                            Timber.d("$TAG: 長按注音鍵 $keyNum")
                            updateKeyDisplay(isPressed = true, mode = T9NumberKey.KeyDisplayMode.DIGIT)
                            val result = actionListener?.onNumberKeyLongPress(keyNum) ?: false
                            if (result) markLongPressHandled()
                            result
                        }
                    }
                }
            }

        // 所有注音鍵的平面列表（用於遍歷）
        private val allZhuyinKeys: List<T9NumberKey> = zhuyinKeys.flatMap { it.toList() }

        // 10 個數字按鈕（獨立行，直接輸入數字）
        private val digitButtons =
            Array(10) { index ->
                TextView(context).apply {
                    id = generateViewId()
                    text = index.toString()
                    textSize = 14f
                    setTextColor(Color.parseColor("#AAAAAA"))
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        InputFeedbackManager.keyPressVibrate(this)
                        InputFeedbackManager.keyPressSound()
                        actionListener?.onDigitInput(index)
                    }
                }
            }

        private val languageKey =
            T9FunctionKey(context).apply {
                id = generateViewId()
                text = "注"
                setOnClickListener { actionListener?.onLanguageSwitch() }
            }

        init {
            id = generateViewId()
        }

        fun setup(
            theme: Theme,
            rimeSession: RimeSession,
            listener: T9KeyboardActionListener,
        ) {
            this.theme = theme
            this.rimeSession = rimeSession
            this.actionListener = listener

            updateThemeStyles()

            if (width > 0) {
                setupLayoutWithDynamicSizing()
            } else {
                post { setupLayoutWithDynamicSizing() }
            }
        }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w > 0) setupLayoutWithDynamicSizing()
        }

        private fun setupLayoutWithDynamicSizing() {
            removeAllViews()
            // 加入所有 view
            val allViews = mutableListOf<View>()
            allViews.addAll(allZhuyinKeys)
            allViews.addAll(digitButtons)
            allViews.add(languageKey)

            allViews.forEach { view ->
                if (view.id == View.NO_ID) view.id = View.generateViewId()
                addView(view, LayoutParams(0, 0))
            }

            val set = ConstraintSet()
            set.clone(this)

            // --- 垂直輔助線（Row1 key3 跨 col3+col4） ---
            val vGuide1 = View.generateViewId()
            val vGuide2 = View.generateViewId()
            val vGuide3 = View.generateViewId()
            set.create(vGuide1, ConstraintSet.VERTICAL_GUIDELINE)
            set.create(vGuide2, ConstraintSet.VERTICAL_GUIDELINE)
            set.create(vGuide3, ConstraintSet.VERTICAL_GUIDELINE)
            set.setGuidelinePercent(vGuide1, COL_WIDE)
            set.setGuidelinePercent(vGuide2, COL_WIDE * 2)
            set.setGuidelinePercent(vGuide3, COL_WIDE * 2 + COL_NARROW)

            // --- 水平輔助線（6行：4行注音 + 1行數字 + 1行底部） ---
            // 比例：19% × 4 + 12% + 12% = 100%
            val hLines = Array(5) { View.generateViewId() }
            val hPercents = floatArrayOf(0.19f, 0.38f, 0.57f, 0.76f, 0.88f)
            for (i in hLines.indices) {
                set.create(hLines[i], ConstraintSet.HORIZONTAL_GUIDELINE)
                set.setGuidelinePercent(hLines[i], hPercents[i])
            }

            val colGuides = arrayOf(ConstraintSet.PARENT_ID, vGuide1, vGuide2, vGuide3, ConstraintSet.PARENT_ID)

            // --- Row 1-4: 注音鍵 ---
            for (row in 0..3) {
                val topAnchor = if (row == 0) ConstraintSet.PARENT_ID else hLines[row - 1]
                val bottomAnchor = hLines[row]
                val topSide = if (row == 0) ConstraintSet.TOP else ConstraintSet.BOTTOM
                val cols = zhuyinKeys[row].size

                for (col in 0 until cols) {
                    val keyId = zhuyinKeys[row][col].id
                    set.connect(keyId, ConstraintSet.TOP, topAnchor, topSide)
                    set.connect(keyId, ConstraintSet.BOTTOM, bottomAnchor, ConstraintSet.TOP)

                    // Row1 key3 跨 col3+col4，讓注音第三欄視覺上較寬
                    val endIdx = if (row == 0 && col == 2) colGuides.lastIndex else col + 1
                    val startAnchor = colGuides[col]
                    val endAnchor = colGuides[endIdx]
                    val startSide = if (col == 0) ConstraintSet.START else ConstraintSet.END
                    val endSide = if (endAnchor == ConstraintSet.PARENT_ID) ConstraintSet.END else ConstraintSet.START
                    set.connect(keyId, ConstraintSet.START, startAnchor, startSide)
                    set.connect(keyId, ConstraintSet.END, endAnchor, endSide)
                }
            }

            // --- Row 5: 數字鍵 1-7（左側大幅內縮適配圓形螢幕左下弧線） ---
            val row5Order = intArrayOf(1, 2, 3, 4, 5, 6, 7)
            val row5Ids = IntArray(7) { digitButtons[row5Order[it]].id }
            for (id in row5Ids) {
                set.connect(id, ConstraintSet.TOP, hLines[3], ConstraintSet.BOTTOM)
                set.connect(id, ConstraintSet.BOTTOM, hLines[4], ConstraintSet.TOP)
            }
            set.createHorizontalChain(
                ConstraintSet.PARENT_ID,
                ConstraintSet.LEFT,
                ConstraintSet.PARENT_ID,
                ConstraintSet.RIGHT,
                row5Ids,
                null,
                ConstraintSet.CHAIN_SPREAD,
            )
            set.setMargin(row5Ids.first(), ConstraintSet.START, dp(70))
            set.setMargin(row5Ids.last(), ConstraintSet.END, dp(5))

            // --- Row 6: 數字鍵 8,9,0 + 注（左側大幅內縮適配圓形螢幕底部弧線） ---
            val row6Order = intArrayOf(8, 9, 0)
            val row6Ids = IntArray(4)
            for (i in 0..2) row6Ids[i] = digitButtons[row6Order[i]].id
            row6Ids[3] = languageKey.id
            for (id in row6Ids) {
                set.connect(id, ConstraintSet.TOP, hLines[4], ConstraintSet.BOTTOM)
                set.connect(id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            }
            set.createHorizontalChain(
                ConstraintSet.PARENT_ID,
                ConstraintSet.LEFT,
                ConstraintSet.PARENT_ID,
                ConstraintSet.RIGHT,
                row6Ids,
                null,
                ConstraintSet.CHAIN_SPREAD,
            )
            set.setMargin(row6Ids.first(), ConstraintSet.START, dp(100))
            set.setMargin(row6Ids.last(), ConstraintSet.END, dp(10))

            try {
                set.applyTo(this)
                invalidate()
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to apply ConstraintSet")
            }
        }

        private fun updateThemeStyles() {
            if (::theme.isInitialized) {
                try {
                    allZhuyinKeys.forEach { it.updateStyle() }
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: Failed to update theme styles")
                }
            }
        }

        private fun getHintsForNumber(
            number: Int,
            half: Int = 0,
        ): String = T9ZhuyinMapper.getZhuyinForDigitHalf(number, half).joinToString("")

        fun setKeyboardEnabled(enabled: Boolean) {
            allZhuyinKeys.forEach { it.isEnabled = enabled }
            digitButtons.forEach { it.isEnabled = enabled }
            languageKey.isEnabled = enabled
            alpha = if (enabled) 1.0f else 0.6f
        }

        fun getNumberKey(number: Int): T9NumberKey? = allZhuyinKeys.firstOrNull { it.tag == number }

        fun getLanguageKey(): T9FunctionKey = languageKey
    }
