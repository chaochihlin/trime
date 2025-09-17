// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.*
import splitties.views.dsl.core.add
import splitties.views.dsl.core.matchParent
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.daemon.RimeSession
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
class T9KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    companion object {
        // 組件尺寸配置常數 - 針對圓形螢幕優化
        const val GRID_SIZE = 3                    // 3x3網格
        // Stage 8A: 移除固定尺寸，改為動態計算
        // const val NUMBER_KEY_SIZE_DP = 42       // 數字鍵尺寸改為動態計算
        const val CONFIRM_BUTTON_SIZE_DP = 40      // 確認按鈕尺寸 (從50縮小到40)
        const val KEY_SPACING_DP = 2               // 按鍵間距 (從3縮小到2)
        const val FUNCTION_KEY_HEIGHT_DP = 56      // 功能鍵高度 (Stage 12: 從48增加到56)

        // T9注音對應表
        private val ZHUYIN_MAPPING = mapOf(
            1 to "ㄅㄆㄇㄈ",
            2 to "ㄉㄊㄋㄌ",
            3 to "ㄍㄎㄏ",
            4 to "ㄐㄑㄒ",
            5 to "ㄓㄔㄕㄖ",
            6 to "ㄗㄘㄙ",
            7 to "ㄧㄨㄩ",
            8 to "ㄚㄛㄜㄝ",
            9 to "ㄞㄟㄠㄡㄢㄣㄤㄥㄦ",
            0 to "ㄈㄌㄡㄖ"
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

    // Stage 8A: 動態按鍵尺寸計算
    private var dynamicKeySize: Int = 42  // 預設值，將在佈局時重新計算

    // 數字鍵網格（1-9）
    private val numberKeys = Array(9) { index ->
        T9NumberKey(context).apply {
            id = generateViewId()  // Stage 8A: 添加ID以支援ConstraintSet
            keyNumber = index + 1
            keyHints = getHintsForNumber(index + 1)
            setOnClickListener { actionListener?.onNumberKeyPress(index + 1) }
            setOnLongClickListener {
                actionListener?.onNumberKeyLongPress(index + 1) ?: false
            }
        }
    }

    // Stage 8A: confirmButton 已移至 T9InputContainer 管理

    // 功能鍵
    private val zeroKey = T9NumberKey(context).apply {
        id = generateViewId()  // Stage 8A: 添加ID以支援ConstraintSet
        keyNumber = 0
        keyHints = getHintsForNumber(0)
        setOnClickListener { actionListener?.onNumberKeyPress(0) }
        setOnLongClickListener {
            actionListener?.onNumberKeyLongPress(0) ?: false
        }
    }

    private val languageKey = T9FunctionKey(context).apply {
        id = generateViewId()  // Stage 8A: 添加ID以支援ConstraintSet
        text = "ZH(TW)"
        setOnClickListener { actionListener?.onLanguageSwitch() }
    }

    init {
        // Stage 8A: 延遲佈局設置，需要等待 onMeasure 確定尺寸
        // setupLayout() 將在 onSizeChanged 中調用

        // Stage 8A: 為主容器設置ID以支援ConstraintSet
        id = generateViewId()

        // 🔍 視覺除錯：在T9KeyboardView中添加额外的視覺標記
        // 黃色背景已在T9InputContainer中設置，這裡只記錄日誌
        Timber.d("T9KeyboardView: 🔥 T9KeyboardView init completed, layout will be set after size determination")
    }

    /**
     * 設置T9鍵盤配置
     */
    fun setup(theme: Theme, rimeSession: RimeSession, listener: T9KeyboardActionListener) {
        this.theme = theme
        this.rimeSession = rimeSession
        this.actionListener = listener

        // 更新所有按鍵的主題樣式
        updateThemeStyles()

        // Stage 8A: 如果尺寸已確定，設置佈局
        if (width > 0) {
            setupLayoutWithDynamicSizing()
        }
    }

    /**
     * 當View尺寸改變時重新計算佈局
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Timber.d("T9KeyboardView: 📐 Size changed to ${w}x${h}, setting up dynamic layout")
        if (w > 0) {
            setupLayoutWithDynamicSizing()
        }
    }

    /**
     * 設置鍵盤佈局（動態尺寸版本）
     */
    private fun setupLayoutWithDynamicSizing() {
        // Stage 8A: 計算動態按鍵尺寸
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
            Timber.w("T9KeyboardView: 📐 Width not available yet, using default size")
            dynamicKeySize = 42
            return
        }

        // 計算可用寬度：總寬度 - 間距
        // 3個按鍵 + 2個間距 = availableWidth
        val totalSpacing = dp(KEY_SPACING_DP * 2)  // 2個間距
        val availableForKeys = availableWidth - totalSpacing
        dynamicKeySize = availableForKeys / 3  // 3個按鍵平分

        val densityStr = dynamicKeySize / resources.displayMetrics.density
        Timber.d("T9KeyboardView: 📐 Calculated dynamic key size: ${dynamicKeySize}px (${densityStr}dp)")
    }

    /**
     * 設置動態佈局
     */
    private fun setupDynamicLayout() {
        Timber.d("T9KeyboardView: 🔧 Starting setupDynamicLayout() with calculated dimensions")
        Timber.d("T9KeyboardView: Dynamic key size: ${dynamicKeySize}px, Spacing: ${KEY_SPACING_DP}dp")

        // 添加3x3數字鍵網格
        for (i in 0 until 9) {
            val row = i / 3
            val col = i % 3
            val keyNumber = i + 1

            Timber.d("T9KeyboardView: 📍 Adding key $keyNumber at position row=$row, col=$col (index=$i)")

            add(numberKeys[i], lParams(dynamicKeySize, dynamicKeySize) {  // Stage 8A: 使用動態尺寸
                when (col) {
                    0 -> {
                        startOfParent(dp(4))   // Stage 6: 重置左邊距，因為整個T9鍵盤現在由容器居中
                        Timber.d("T9KeyboardView: 🔗 Key $keyNumber: startOfParent(4dp) - container-centered layout")
                    }
                    1 -> {
                        startToEndOf(numberKeys[i - 1], dp(KEY_SPACING_DP))
                        Timber.d("T9KeyboardView: 🔗 Key $keyNumber: right of key ${i} (${KEY_SPACING_DP}dp spacing)")
                    }
                    2 -> {
                        startToEndOf(numberKeys[i - 1], dp(KEY_SPACING_DP))
                        Timber.d("T9KeyboardView: 🔗 Key $keyNumber: right of key ${i} (${KEY_SPACING_DP}dp spacing)")
                    }
                }

                when (row) {
                    0 -> {
                        topOfParent(dp(16))  // Stage 14: 增加到16dp讓3x3按鈕與底部按鈕保持8dp間距
                        Timber.d("T9KeyboardView: 🔗 Key $keyNumber: topOfParent(16dp) - buttons moved down for 8dp margin")
                    }
                    1 -> {
                        topToBottomOf(numberKeys[i - 3], dp(KEY_SPACING_DP))
                        Timber.d("T9KeyboardView: 🔗 Key $keyNumber: below key ${i - 2} (${KEY_SPACING_DP}dp spacing)")
                    }
                    2 -> {
                        topToBottomOf(numberKeys[i - 3], dp(KEY_SPACING_DP))
                        Timber.d("T9KeyboardView: 🔗 Key $keyNumber: below key ${i - 2} (${KEY_SPACING_DP}dp spacing)")
                    }
                }
            })

            Timber.d("T9KeyboardView: ✅ Key $keyNumber added successfully")
        }

        // Stage 8A: confirmButton 已移至 T9InputContainer，此處不再添加

        // Stage 8A: 添加底部功能鍵列（水平置中，wrap-content）
        Timber.d("T9KeyboardView: 🔢 Adding zero key (0) at bottom center")
        add(zeroKey, lParams(0, dp(FUNCTION_KEY_HEIGHT_DP)) {  // Stage 8A: wrap-content寬度
            topToBottomOf(numberKeys[6], dp(KEY_SPACING_DP * 2))
            bottomOfParent(dp(4))  // Stage 8A: 置底
            constrainedWidth = true
        })
        Timber.d("T9KeyboardView: ✅ Zero key added successfully")

        Timber.d("T9KeyboardView: 🌐 Adding language key (ZH(TW)) at bottom center")
        add(languageKey, lParams(0, dp(FUNCTION_KEY_HEIGHT_DP)) {  // Stage 8A: wrap-content寬度
            // 初始約束，會在post{}中修改以創建chain
            topToBottomOf(numberKeys[7], dp(KEY_SPACING_DP * 2))
            bottomOfParent(dp(4))  // Stage 8A: 置底
            constrainedWidth = true
        })

        // Stage 8A: 使用直接約束避免ConstraintSet的ID問題
        // 簡單地讓兩個按鈕水平相鄰且置中
        post {
            // 修改zeroKey約束，讓它靠左置中
            val zeroParams = zeroKey.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            zeroParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            zeroParams.endToStart = languageKey.id
            zeroParams.horizontalChainStyle = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.CHAIN_PACKED
            zeroParams.marginStart = dp(16)  // Stage 14: 增加到16dp左邊距
            zeroParams.marginEnd = dp(8)     // Stage 14: 增加到8dp右邊距（與languageKey的間距）
            zeroKey.layoutParams = zeroParams

            // 修改languageKey約束，讓它靠右與zeroKey組成chain
            val langParams = languageKey.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            langParams.startToEnd = zeroKey.id
            langParams.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            langParams.marginStart = dp(8)   // Stage 14: 增加到8dp左邊距（與zeroKey的間距）
            langParams.marginEnd = dp(16)   // Stage 14: 增加到16dp右邊距
            languageKey.layoutParams = langParams

            Timber.d("T9KeyboardView: ✅ Function keys chain created with enhanced 16dp horizontal margins")
        }
        Timber.d("T9KeyboardView: ✅ Language key added successfully")

        Timber.d("T9KeyboardView: 🏁 Layout setup completed! Total components: 9 number keys + confirm button + zero key + language key")
    }

    /**
     * 更新主題樣式
     */
    private fun updateThemeStyles() {
        if (::theme.isInitialized) {
            // 更新所有按鍵的主題樣式
            numberKeys.forEach { it.updateTheme(theme) }
            // Stage 8A: confirmButton 已移至 T9InputContainer 管理
            zeroKey.updateTheme(theme)
            languageKey.updateTheme(theme)
        }
    }

    /**
     * 取得指定數字對應的注音符號提示
     */
    private fun getHintsForNumber(number: Int): String {
        return ZHUYIN_MAPPING[number] ?: ""
    }

    /**
     * 設置鍵盤是否啟用
     */
    fun setKeyboardEnabled(enabled: Boolean) {
        numberKeys.forEach { it.isEnabled = enabled }
        // Stage 8A: confirmButton 已移至 T9InputContainer 管理
        zeroKey.isEnabled = enabled
        languageKey.isEnabled = enabled

        alpha = if (enabled) 1.0f else 0.6f
    }

    /**
     * 取得指定位置的按鍵
     */
    fun getNumberKey(number: Int): T9NumberKey? {
        return when (number) {
            in 1..9 -> numberKeys[number - 1]
            0 -> zeroKey
            else -> null
        }
    }

    // Stage 8A: getConfirmButton 已移至 T9InputContainer 提供

    /**
     * 取得語言切換按鈕
     */
    fun getLanguageKey(): T9FunctionKey = languageKey
}