/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.preview

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.view.updateLayoutParams
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.dependency.InputScope
import com.osfans.trime.ime.keyboard.Key
import me.tatarka.inject.annotations.Inject
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import java.util.ArrayDeque

/**
 * 按鍵預覽顯示編排器
 *
 * 負責管理虛擬鍵盤按鍵預覽彈窗的顯示、位置計算和生命週期管理。
 * 當使用者按下按鍵時，會在按鍵上方顯示放大的預覽文字，提升打字準確性。
 * 
 * 主要功能：
 * - 管理預覽 UI 元件的物件池，避免頻繁創建銷毀
 * - 計算預覽彈窗的最佳顯示位置，避免超出螢幕範圍
 * - 處理預覽彈窗的顯示和隱藏動畫效果
 * - 支援左、中、右三種不同位置的預覽背景樣式
 *
 * @param context Android 應用程式上下文
 * @param theme 當前使用的主題配置，包含預覽樣式設定
 */
@InputScope
@Inject
class KeyPreviewChoreographer(
    private val context: Context,
    private val theme: Theme,
) {
    /** 可重複使用的預覽 UI 元件佇列，用於效能最佳化 */
    private val freeKeyPreviewUi = ArrayDeque<KeyPreviewUi>()
    
    /** 目前正在顯示預覽的按鍵與對應 UI 元件的映射表 */
    private val showingKeyPreviewUi = hashMapOf<Key, KeyPreviewUi>()

    /** 預覽彈窗的根容器，採用 FrameLayout 布局 */
    val root by lazy {
        context.frameLayout {
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            isClickable = false
            isFocusable = false
        }
    }

    /**
     * 取得或建立指定按鍵的預覽 UI 元件
     *
     * 優先從物件池中取得可重複使用的元件，若無可用元件則建立新的。
     * 這種設計可以避免頻繁的記憶體分配，提升效能表現。
     *
     * @param key 需要顯示預覽的按鍵
     * @return 對應的預覽 UI 元件
     */
    fun getKeyPreviewUi(key: Key): KeyPreviewUi =
        showingKeyPreviewUi.remove(key)
            ?: freeKeyPreviewUi.poll()
            ?: KeyPreviewUi(context, theme).also {
                root.add(it.root, MarginLayoutParams(0, 0))
            }

    /**
     * 檢查指定按鍵是否正在顯示預覽
     *
     * @param key 要檢查的按鍵
     * @return 如果按鍵正在顯示預覽則返回 true，否則返回 false
     */
    fun isShowingKeyPreview(key: Key): Boolean = showingKeyPreviewUi.containsKey(key)

    /**
     * 關閉指定按鍵的預覽顯示
     *
     * 將預覽 UI 元件設為不可見，並將其放回物件池以供重複使用。
     *
     * @param key 要關閉預覽的按鍵
     */
    fun dismissKeyPreview(key: Key) {
        val keyPreviewUi = showingKeyPreviewUi[key] ?: return
        showingKeyPreviewUi.remove(key)
        keyPreviewUi.root.visibility = View.INVISIBLE
        freeKeyPreviewUi.add(keyPreviewUi)
    }

    /**
     * 定位並顯示按鍵預覽
     *
     * 這是預覽系統的主要入口方法，負責計算預覽位置並顯示預覽內容。
     * 會自動選擇最適當的顯示位置，確保預覽不會超出螢幕範圍。
     *
     * @param key 要顯示預覽的按鍵
     * @param keyPreviewText 預覽中要顯示的文字內容
     * @param keyboardViewWidth 鍵盤視圖的寬度，用於邊界檢查
     * @param keyboardOrigin 鍵盤在螢幕中的起始座標 [x, y]
     */
    fun placeAndShowKeyPreview(
        key: Key,
        keyPreviewText: String,
        keyboardViewWidth: Int,
        keyboardOrigin: IntArray,
    ) {
        val keyPreviewUi = getKeyPreviewUi(key)
        placeKeyPreview(
            key,
            keyPreviewUi,
            keyPreviewText,
            keyboardViewWidth,
            keyboardOrigin,
        )
        showKeyPreview(key, keyPreviewUi)
    }

    /**
     * 計算並設定預覽元件的位置和大小
     *
     * 執行複雜的位置計算邏輯，確保預覽彈窗顯示在合適的位置：
     * - 水平方向：優先對齊按鍵中心，若超出邊界則調整位置並使用對應背景樣式
     * - 垂直方向：顯示在按鍵上方，根據主題設定調整偏移量
     * - 大小調整：根據主題配置設定預覽的寬度和高度
     *
     * @param key 要顯示預覽的按鍵
     * @param keyPreviewUi 預覽 UI 元件
     * @param keyPreviewText 要顯示的預覽文字
     * @param keyboardViewWidth 鍵盤視圖總寬度
     * @param originCoords 鍵盤在螢幕中的原點座標
     */
    private fun placeKeyPreview(
        key: Key,
        keyPreviewUi: KeyPreviewUi,
        keyPreviewText: String,
        keyboardViewWidth: Int,
        originCoords: IntArray,
    ) {
        keyPreviewUi.setPreviewText(keyPreviewText)
        keyPreviewUi.root.measure(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        val previewWidth = context.dp(38)
        val previewHeight = context.dp(theme.generalStyle.previewHeight)
        val keyDrawWidth = key.width
        
        // 水平位置計算：預覽彈窗與按鍵中心對齊，若超出螢幕範圍則內移並調整背景樣式
        val keyPreviewPosition: KeyPreviewUi.Position
        var previewX: Int = (
            key.x - (previewWidth - keyDrawWidth) / 2 +
                originCoords[0]
        )
        if (previewX < 0) {
            previewX = 0
            keyPreviewPosition = KeyPreviewUi.Position.LEFT
        } else if (previewX > keyboardViewWidth - previewWidth) {
            previewX = keyboardViewWidth - previewWidth
            keyPreviewPosition = KeyPreviewUi.Position.RIGHT
        } else {
            keyPreviewPosition = KeyPreviewUi.Position.MIDDLE
        }
        keyPreviewUi.setPreviewBackground(keyPreviewPosition)
        
        // 垂直位置計算：顯示在按鍵上方，加上主題設定的偏移量
        val previewY: Int = (
            key.y - previewHeight + theme.generalStyle.previewOffset +
                originCoords[1]
        )
        keyPreviewUi.root.updateLayoutParams<MarginLayoutParams> {
            width = previewWidth
            height = previewHeight
            setMargins(previewX, previewY, 0, 0)
        }
        keyPreviewUi.root.pivotX = previewWidth / 2.0f
        keyPreviewUi.root.pivotY = previewHeight.toFloat()
    }

    /**
     * 顯示按鍵預覽
     *
     * 將預覽 UI 元件設為可見狀態，並加入到顯示中的預覽映射表。
     * 這是預覽顯示流程的最後一步，確保使用者能看到預覽內容。
     *
     * @param key 要顯示預覽的按鍵
     * @param keyPreviewUi 對應的預覽 UI 元件
     */
    fun showKeyPreview(
        key: Key,
        keyPreviewUi: KeyPreviewUi,
    ) {
        keyPreviewUi.root.visibility = View.VISIBLE
        showingKeyPreviewUi[key] = keyPreviewUi
    }
}
