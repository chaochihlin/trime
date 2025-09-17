// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.theme.Theme
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.constraintlayout.topToBottomOf
import splitties.views.dsl.core.add

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
            // 組件尺寸配置常數 - 針對圓形螢幕優化
            const val GRID_SIZE = 3 // 3x3網格
            const val CONFIRM_BUTTON_SIZE_DP = 40 // 確認按鈕尺寸 (從50縮小到40)
            const val KEY_SPACING_DP = 2 // 按鍵間距 (從3縮小到2)
            const val FUNCTION_KEY_HEIGHT_DP = 56 // 功能鍵高度

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
            fun onNumberKeyPress(number: Int)

            fun onNumberKeyLongPress(number: Int): Boolean

            fun onConfirmPress()

            fun onLanguageSwitch()
        }

        private var actionListener: T9KeyboardActionListener? = null
        private lateinit var theme: Theme
        private lateinit var rimeSession: RimeSession

        // 動態按鍵尺寸計算
        private var dynamicKeySize: Int = 42 // 預設值，將在佈局時重新計算

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

            // 更新所有按鍵的主題樣式
            updateThemeStyles()

            // 如果尺寸已確定，設置佈局
            if (width > 0) {
                setupLayoutWithDynamicSizing()
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
         * 設置鍵盤佈局（動態尺寸版本）
         */
        private fun setupLayoutWithDynamicSizing() {
            // 計算動態按鍵尺寸
            calculateDynamicKeySize()

            // 清除現有佈局
            removeAllViews()

            // 重新添加所有按鍵
            setupDynamicLayout()
        }

        /**
         * 計算動態按鍵尺寸
         */
        private fun calculateDynamicKeySize() {
            val availableWidth = width
            if (availableWidth <= 0) {
                dynamicKeySize = 42
                return
            }

            // 計算可用寬度：總寬度 - 間距
            // 3個按鍵 + 2個間距 = availableWidth
            val totalSpacing = dp(KEY_SPACING_DP * 2) // 2個間距
            val availableForKeys = availableWidth - totalSpacing
            dynamicKeySize = availableForKeys / 3 // 3個按鍵平分

            val densityStr = dynamicKeySize / resources.displayMetrics.density
        }

        /**
         * 設置動態佈局
         */
        private fun setupDynamicLayout() {
            // 添加3x3數字鍵網格
            for (i in 0 until 9) {
                val row = i / 3
                val col = i % 3
                val keyNumber = i + 1

                add(
                    numberKeys[i],
                    lParams(dynamicKeySize, dynamicKeySize) {
                        // 使用動態尺寸
                        when (col) {
                            0 -> {
                                startOfParent(dp(4)) // 重置左邊距，因為整個T9鍵盤現在由容器居中
                            }
                            1 -> {
                                startToEndOf(numberKeys[i - 1], dp(KEY_SPACING_DP))
                            }
                            2 -> {
                                startToEndOf(numberKeys[i - 1], dp(KEY_SPACING_DP))
                            }
                        }

                        when (row) {
                            0 -> {
                                topOfParent(dp(16)) // 增加到16dp讓3x3按鈕與底部按鈕保持8dp間距
                            }
                            1 -> {
                                topToBottomOf(numberKeys[i - 3], dp(KEY_SPACING_DP))
                            }
                            2 -> {
                                topToBottomOf(numberKeys[i - 3], dp(KEY_SPACING_DP))
                            }
                        }
                    },
                )
            }

            // confirmButton 已移至 T9InputContainer，此處不再添加

            // 添加底部功能鍵列（水平置中，wrap-content）
            add(
                zeroKey,
                lParams(0, dp(FUNCTION_KEY_HEIGHT_DP)) {
                    // wrap-content寬度
                    topToBottomOf(numberKeys[6], dp(KEY_SPACING_DP * 2))
                    bottomOfParent(dp(4)) // 置底
                    constrainedWidth = true
                },
            )

            add(
                languageKey,
                lParams(0, dp(FUNCTION_KEY_HEIGHT_DP)) {
                    // wrap-content寬度
                    // 初始約束，會在post{}中修改以創建chain
                    topToBottomOf(numberKeys[7], dp(KEY_SPACING_DP * 2))
                    bottomOfParent(dp(4)) // 置底
                    constrainedWidth = true
                },
            )

            // 使用直接約束避免ConstraintSet的ID問題
            // 簡單地讓兩個按鈕水平相鄰且置中
            post {
                // 修改zeroKey約束，讓它靠左置中
                val zeroParams = zeroKey.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                zeroParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                zeroParams.endToStart = languageKey.id
                zeroParams.horizontalChainStyle = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.CHAIN_PACKED
                zeroParams.marginStart = dp(16) // 增加到16dp左邊距
                zeroParams.marginEnd = dp(8) // 增加到8dp右邊距（與languageKey的間距）
                zeroKey.layoutParams = zeroParams

                // 修改languageKey約束，讓它靠右與zeroKey組成chain
                val langParams = languageKey.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                langParams.startToEnd = zeroKey.id
                langParams.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                langParams.marginStart = dp(8) // 增加到8dp左邊距（與zeroKey的間距）
                langParams.marginEnd = dp(16) // 增加到16dp右邊距
                languageKey.layoutParams = langParams
            }
        }

        /**
         * 更新主題樣式
         */
        private fun updateThemeStyles() {
            if (::theme.isInitialized) {
                // 更新所有按鍵的主題樣式
                numberKeys.forEach { it.updateTheme(theme) }
                zeroKey.updateTheme(theme)
                languageKey.updateTheme(theme)
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

        // getConfirmButton 已移至 T9InputContainer 提供

        /**
         * 取得語言切換按鈕
         */
        fun getLanguageKey(): T9FunctionKey = languageKey
    }
