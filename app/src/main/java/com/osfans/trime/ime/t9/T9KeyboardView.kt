// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.theme.Theme
import timber.log.Timber

/**
 * T9注音九宮格鍵盤視圖
 *
 * 專為智慧手錶圓形螢幕設計的T9輸入法鍵盤組件。
 * 採用4x4網格佈局，包含：
 * - 3x3數字鍵網格（1-9）
 * - 圓形確認按鈕
 * - 底部功能鍵列（0鍵、語言切換）
 *
 * 功能特性：
 * - 圓形螢幕完美適配
 * - 注音符號提示顯示
 * - 觸覺回饋優化
 * - RIME引擎整合
 *
 * @param context Android上下文
 * @param attrs 屬性集
 * @param defStyleAttr 默認樣式屬性
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

            // T9注音對應表 - 與 YAML 配置保持一致
            private val ZHUYIN_MAPPING =
                mapOf(
                    1 to "ㄅㄉㄚ",
                    2 to "ㄍㄐㄞㄧ",
                    3 to "ㄓㄗㄢㄦ",
                    4 to "ㄆㄊㄛ",
                    5 to "ㄎㄑㄟㄨ",
                    6 to "ㄔㄘㄣ",
                    7 to "ㄇㄋㄜㄝ",
                    8 to "ㄏㄒㄠㄩ",
                    9 to "ㄕㄙㄤㄥ",
                    0 to "ㄈㄌㄡㄖ",
                )
        }

        // T9鍵盤事件監聽器
        interface T9KeyboardActionListener {
            /**
             * 處理數字鍵短按事件 - 輸入對應的注音符號
             * 透過 RIME 引擎處理，根據當前方案決定具體的注音符號
             *
             * @param number 按下的數字鍵 (0-9)
             */
            fun onNumberKeyPress(number: Int)

            /**
             * 處理數字鍵長按事件 - 直接輸入數字字符
             * 繞過 RIME 引擎，直接將數字提交到當前輸入目標
             *
             * @param number 按下的數字鍵 (0-9)
             * @return true 如果處理成功，false 否則
             */
            fun onNumberKeyLongPress(number: Int): Boolean

            fun onConfirmPress()

            fun onLanguageSwitch()
        }

        private var actionListener: T9KeyboardActionListener? = null
        private lateinit var theme: Theme
        private lateinit var rimeSession: RimeSession

        // 數字鍵網格（1-9）
        private val numberKeys =
            Array(9) { index ->
                T9NumberKey(context).apply {
                    id = generateViewId()
                    keyNumber = index + 1
                    keyHints = getHintsForNumber(index + 1)
                    setOnClickListener { actionListener?.onNumberKeyPress(index + 1) }
                    setOnLongClickListener {
                        actionListener?.onNumberKeyLongPress(index + 1) ?: false
                    }
                }
            }

        // 功能鍵
        private val zeroKey =
            T9NumberKey(context).apply {
                id = generateViewId()
                keyNumber = 0
                keyHints = getHintsForNumber(0)
                setOnClickListener { actionListener?.onNumberKeyPress(0) }
                setOnLongClickListener {
                    actionListener?.onNumberKeyLongPress(0) ?: false
                }
            }

        private val languageKey =
            T9FunctionKey(context).apply {
                id = generateViewId()
                text = "ZH(TW)"
                setOnClickListener { actionListener?.onLanguageSwitch() }
            }

        init {
            id = generateViewId()
        }

        /**
         * 設置T9鍵盤配置
         */
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
                post {
                    if (width > 0 && height > 0) {
                        setupLayoutWithDynamicSizing()
                    } else {
                        setupLayoutWithDynamicSizing()
                    }
                }
            }
        }

        /**
         * 當View尺寸改變時重新計算佈局
         */
        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w > 0) {
                setupLayoutWithDynamicSizing()
            }
        }

        /**
         * 設置鍵盤佈局（扁平化版本）
         */
        private fun setupLayoutWithDynamicSizing() {
            removeAllViews()
            setupDynamicLayout()
        }

        private fun setupDynamicLayout() {
            // 確保所有 view 都已加入，且都有 ID
            val allKeys = listOf(*numberKeys, zeroKey, languageKey)

            allKeys.forEach { key ->
                if (key.parent == null) {
                    if (key.id == View.NO_ID) {
                        key.id = View.generateViewId()
                    }
                    addView(key, LayoutParams(0, 0))
                }
            }

            val set = ConstraintSet()
            set.clone(this)

            // --- 1. 建立垂直輔助線 (用於劃分列) ---
            val vGuideline25 = View.generateViewId()
            val vGuideline50 = View.generateViewId()
            val vGuideline75 = View.generateViewId()
            set.create(vGuideline25, ConstraintSet.VERTICAL_GUIDELINE)
            set.create(vGuideline50, ConstraintSet.VERTICAL_GUIDELINE)
            set.create(vGuideline75, ConstraintSet.VERTICAL_GUIDELINE)
            set.setGuidelinePercent(vGuideline25, 0.33f) // 第一條線在 33% 位置
            set.setGuidelinePercent(vGuideline50, 0.66f) // 第二條線在 66% 位置
            // 因為只有三列，所以不需要75%的線，我們這裡簡化為兩條

            // --- 2. 建立水平輔助線 (用於劃分行) ---
            val hGuideline25 = View.generateViewId()
            val hGuideline50 = View.generateViewId()
            val hGuideline75 = View.generateViewId()
            set.create(hGuideline25, ConstraintSet.HORIZONTAL_GUIDELINE)
            set.create(hGuideline50, ConstraintSet.HORIZONTAL_GUIDELINE)
            set.create(hGuideline75, ConstraintSet.HORIZONTAL_GUIDELINE)
            set.setGuidelinePercent(hGuideline25, 0.25f) // 第一行結束
            set.setGuidelinePercent(hGuideline50, 0.50f) // 第二行結束
            set.setGuidelinePercent(hGuideline75, 0.75f) // 第三行結束

            // --- 3. 將按鍵約束到輔助線 ---

            // Row 1 (Keys 0, 1, 2)
            set.connect(numberKeys[0].id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            set.connect(numberKeys[0].id, ConstraintSet.BOTTOM, hGuideline25, ConstraintSet.TOP)
            set.connect(numberKeys[0].id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            set.connect(numberKeys[0].id, ConstraintSet.END, vGuideline25, ConstraintSet.START)

            set.connect(numberKeys[1].id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            set.connect(numberKeys[1].id, ConstraintSet.BOTTOM, hGuideline25, ConstraintSet.TOP)
            set.connect(numberKeys[1].id, ConstraintSet.START, vGuideline25, ConstraintSet.END)
            set.connect(numberKeys[1].id, ConstraintSet.END, vGuideline50, ConstraintSet.START)

            set.connect(numberKeys[2].id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            set.connect(numberKeys[2].id, ConstraintSet.BOTTOM, hGuideline25, ConstraintSet.TOP)
            set.connect(numberKeys[2].id, ConstraintSet.START, vGuideline50, ConstraintSet.END)
            set.connect(numberKeys[2].id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

            // Row 2 (Keys 3, 4, 5)
            set.connect(numberKeys[3].id, ConstraintSet.TOP, hGuideline25, ConstraintSet.BOTTOM)
            set.connect(numberKeys[3].id, ConstraintSet.BOTTOM, hGuideline50, ConstraintSet.TOP)
            set.connect(numberKeys[3].id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            set.connect(numberKeys[3].id, ConstraintSet.END, vGuideline25, ConstraintSet.START)

            set.connect(numberKeys[4].id, ConstraintSet.TOP, hGuideline25, ConstraintSet.BOTTOM)
            set.connect(numberKeys[4].id, ConstraintSet.BOTTOM, hGuideline50, ConstraintSet.TOP)
            set.connect(numberKeys[4].id, ConstraintSet.START, vGuideline25, ConstraintSet.END)
            set.connect(numberKeys[4].id, ConstraintSet.END, vGuideline50, ConstraintSet.START)

            set.connect(numberKeys[5].id, ConstraintSet.TOP, hGuideline25, ConstraintSet.BOTTOM)
            set.connect(numberKeys[5].id, ConstraintSet.BOTTOM, hGuideline50, ConstraintSet.TOP)
            set.connect(numberKeys[5].id, ConstraintSet.START, vGuideline50, ConstraintSet.END)
            set.connect(numberKeys[5].id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

            // Row 3 (Keys 6, 7, 8)
            set.connect(numberKeys[6].id, ConstraintSet.TOP, hGuideline50, ConstraintSet.BOTTOM)
            set.connect(numberKeys[6].id, ConstraintSet.BOTTOM, hGuideline75, ConstraintSet.TOP)
            set.connect(numberKeys[6].id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            set.connect(numberKeys[6].id, ConstraintSet.END, vGuideline25, ConstraintSet.START)

            set.connect(numberKeys[7].id, ConstraintSet.TOP, hGuideline50, ConstraintSet.BOTTOM)
            set.connect(numberKeys[7].id, ConstraintSet.BOTTOM, hGuideline75, ConstraintSet.TOP)
            set.connect(numberKeys[7].id, ConstraintSet.START, vGuideline25, ConstraintSet.END)
            set.connect(numberKeys[7].id, ConstraintSet.END, vGuideline50, ConstraintSet.START)

            set.connect(numberKeys[8].id, ConstraintSet.TOP, hGuideline50, ConstraintSet.BOTTOM)
            set.connect(numberKeys[8].id, ConstraintSet.BOTTOM, hGuideline75, ConstraintSet.TOP)
            set.connect(numberKeys[8].id, ConstraintSet.START, vGuideline50, ConstraintSet.END)
            set.connect(numberKeys[8].id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

            // Row 4 (0, Language)
            set.connect(zeroKey.id, ConstraintSet.TOP, hGuideline75, ConstraintSet.BOTTOM)
            set.connect(zeroKey.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            set.connect(zeroKey.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            set.connect(zeroKey.id, ConstraintSet.END, vGuideline50, ConstraintSet.START) // 這裡改為佔據兩列寬度

            set.connect(languageKey.id, ConstraintSet.TOP, hGuideline75, ConstraintSet.BOTTOM)
            set.connect(languageKey.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            set.connect(languageKey.id, ConstraintSet.START, vGuideline50, ConstraintSet.END)
            set.connect(languageKey.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

            // 最後，套用所有約束
            try {
                set.applyTo(this)
                requestLayout()
                invalidate()
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to apply ConstraintSet")
            }
        }

        /**
         * 更新主題樣式
         */
        private fun updateThemeStyles() {
            if (::theme.isInitialized) {
                try {
                    numberKeys.forEach { it.updateStyle() }
                    zeroKey.updateStyle()
                    languageKey.updateTheme(theme)
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: Failed to update theme styles")
                }
            }
        }

        /**
         * 取得指定數字對應的注音符號提示
         */
        private fun getHintsForNumber(number: Int): String = ZHUYIN_MAPPING[number] ?: ""

        /**
         * 設置鍵盤是否啟用
         */
        fun setKeyboardEnabled(enabled: Boolean) {
            numberKeys.forEach { it.isEnabled = enabled }
            zeroKey.isEnabled = enabled
            languageKey.isEnabled = enabled

            alpha = if (enabled) 1.0f else 0.6f
        }

        /**
         * 取得指定位置的按鍵
         */
        fun getNumberKey(number: Int): T9NumberKey? =
            when (number) {
                in 1..9 -> numberKeys[number - 1]
                0 -> zeroKey
                else -> null
            }

        /**
         * 取得語言切換按鈕
         */
        fun getLanguageKey(): T9FunctionKey = languageKey
    }
