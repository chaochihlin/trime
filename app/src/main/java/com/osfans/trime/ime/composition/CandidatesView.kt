/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import android.annotation.SuppressLint
import android.graphics.RectF
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.ViewTreeObserver.OnPreDrawListener
import android.view.WindowInsets
import androidx.annotation.Size
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.graphics.component3
import androidx.core.graphics.component4
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.core.RimeProto
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.candidates.popup.PagedCandidatesUi
import com.osfans.trime.ime.core.BaseInputView
import com.osfans.trime.ime.core.TouchEventReceiverWindow
import com.osfans.trime.ime.core.TrimeInputMethodService
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.withTheme
import splitties.views.dsl.core.wrapContent
import splitties.views.horizontalPadding
import splitties.views.setPaddingDp
import splitties.views.verticalPadding
import kotlin.math.roundToInt

/**
 * 候選詞視圖，負責顯示輸入法候選詞和預編輯文本
 *
 * 此視圖繼承自 [BaseInputView]，提供浮動視窗功能來顯示 RIME 輸入法的候選詞列表和預編輯區域。
 * 支援多種顯示位置和動態定位，能夠根據游標位置或預設配置來調整顯示位置。
 *
 * @param service Trime 輸入法服務實例
 * @param rime RIME 會話實例，用於與輸入法引擎交互
 * @param theme 主題配置實例，用於UI樣式設定
 *
 * @see BaseInputView
 * @see TrimeInputMethodService
 * @see RimeSession
 */
@SuppressLint("ViewConstructor")
class CandidatesView(
    service: TrimeInputMethodService,
    rime: RimeSession,
    theme: Theme,
) : BaseInputView(service, rime, theme) {
    /** 使用系統預設設定主題的上下文 */
    private val ctx = context.withTheme(android.R.style.Theme_DeviceDefault_Settings)

    /** 候選詞視圖的顯示位置偏好設定 */
    private val position by AppPrefs.defaultInstance().candidates.position

    /** RIME 輸入法引擎的選單資料 */
    private var menu = RimeProto.Context.Menu()

    /** RIME 輸入法引擎的組合輸入資料 */
    private var inputComposition = RimeProto.Context.Composition()

    /** 錨點位置的矩形區域，用於定位候選詞視圖 */
    private val anchorPosition = RectF()

    /** 父視圖的寬高尺寸陣列 [寬度, 高度] */
    private val parentSize = floatArrayOf(0f, 0f)

    /** 標記是否需要更新視圖位置 */
    private var shouldUpdatePosition = false

    /**
     * 佈局監聽器，監聽全域佈局變更
     *
     * 佈局更新可能會或不會導致 [CandidatesView] 的尺寸發生 [onSizeChanged]，
     * 無論哪種情況，我們都應該重新定位視圖
     */
    private val layoutListener =
        OnGlobalLayoutListener {
            shouldUpdatePosition = true
        }

    /**
     * 預繪製監聽器，在實際繪製前更新位置
     *
     * [CandidatesView] 的位置是根據其尺寸計算的，
     * 所以我們需要在佈局之後、實際繪製之前重新計算位置，
     * 以避免閃爍現象
     */
    private val preDrawListener =
        OnPreDrawListener {
            if (shouldUpdatePosition) {
                updatePosition()
            }
            true
        }

    /** 預編輯 UI 組件，負責顯示正在輸入的文本 */
    private val preeditUi =
        PreeditUi(
            ctx,
            theme,
            setupPreeditView = { setPaddingDp(3, 1, 3, 1) },
            onMoveCursor = { pos -> rime.launchOnReady { it.moveCursorPos(pos) } },
        )

    /** 分頁候選詞 UI 組件，負責顯示候選詞列表和分頁功能 */
    private val candidatesUi =
        PagedCandidatesUi(
            ctx,
            theme,
            onCandidateClick = { index -> rime.launchOnReady { it.selectPagedCandidate(index) } },
            onPrevPage = { rime.launchOnReady { it.changeCandidatePage(true) } },
            onNextPage = { rime.launchOnReady { it.changeCandidatePage(false) } },
        )

    /** 觸控事件接收視窗，處理候選詞視圖的觸控交互 */
    private val touchEventReceiverWindow = TouchEventReceiverWindow(this)

    /** 底部系統邊距值，用於適配系統導航欄 */
    private var bottomInsets = 0

    /**
     * 處理 RIME 引擎訊息
     *
     * @param it RIME 引擎發送的訊息，包含輸入狀態更新
     */
    override fun handleRimeMessage(it: RimeMessage<*>) {
        if (it is RimeMessage.ResponseMessage) {
            inputComposition = it.data.context.composition
            menu = it.data.context.menu
            updateUi()
        }
    }

    /**
     * 評估候選詞視圖是否應該顯示
     *
     * @return 當有預編輯文本或候選詞時返回 true
     */
    private fun evaluateVisibility(): Boolean =
        !inputComposition.preedit.isNullOrEmpty() ||
            menu.candidates.isNotEmpty()

    /**
     * 更新 UI 介面顯示
     *
     * 根據當前的輸入組合和選單資料更新預編輯區域和候選詞列表的顯示狀態
     */
    private fun updateUi() {
        preeditUi.update(inputComposition)
        preeditUi.root.visibility = if (preeditUi.visible) View.VISIBLE else View.INVISIBLE
        // 如果可以顯示候選詞視圖，RIME 引擎大部分時間都是就緒的，
        // 所以立即獲取選項應該是安全的
        val isHorizontalLayout = rime.run { getRuntimeOption("_horizontal") }
        candidatesUi.update(menu, isHorizontalLayout)
        if (evaluateVisibility()) {
            visibility = View.VISIBLE
        } else {
            // 當祖先視圖為 GONE 時，RecyclerView 不會更新其項目
            visibility = View.INVISIBLE
            touchEventReceiverWindow.dismiss()
        }
    }

    /**
     * 更新候選詞視圖的顯示位置
     *
     * 根據配置的位置偏好和當前錨點位置計算並設定視圖的顯示位置，
     * 同時更新觸控事件接收視窗的位置
     */
    private fun updatePosition() {
        if (visibility != View.VISIBLE) return
        val (parentWidth, parentHeight) = parentSize
        if (parentWidth <= 0 || parentHeight <= 0) {
            translationX = 0f
            translationY = 0f
            return
        }
        val (horizontal, top, _, bottom) = anchorPosition
        val w = width
        val h = height
        val selfWidth = w.toFloat()
        val selfHeight = h.toFloat()

        val x: Float
        val y: Float
        val minX = 0f
        val minY = 0f
        val maxX = parentWidth - selfWidth
        val maxY = (if (bottom + selfHeight > parentHeight) top else parentHeight) - selfHeight
        when (position) {
            PopupPosition.TOP_RIGHT -> {
                x = maxX
                y = minY
            }
            PopupPosition.TOP_LEFT -> {
                x = minX
                y = minY
            }
            PopupPosition.BOTTOM_RIGHT -> {
                x = maxX
                y = maxY
            }
            PopupPosition.BOTTOM_LEFT -> {
                x = minX
                y = maxY
            }
            PopupPosition.FOLLOW -> {
                x =
                    if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                        val rtlOffset = parentWidth - horizontal
                        if (rtlOffset + selfWidth > parentWidth) selfWidth - parentWidth else -rtlOffset
                    } else {
                        if (horizontal + selfWidth > parentWidth) parentWidth - selfWidth else horizontal
                    }
                val bottomLimit = parentHeight - bottomInsets
                y = if (bottom + selfHeight > bottomLimit) top - selfHeight else bottom
            }
        }
        translationX = x
        translationY = y
        // 在候選詞視圖位置更新後，更新觸控事件接收視窗的位置
        touchEventReceiverWindow.showAt(x.roundToInt(), y.roundToInt(), w, h)
        shouldUpdatePosition = false
    }

    /**
     * 更新游標錨點位置和父視圖尺寸
     *
     * @param anchorPosition 新的錨點位置矩形
     * @param parent 父視圖的尺寸陣列 [寬度, 高度]
     */
    fun updateCursorAnchor(
        anchorPosition: RectF,
        @Size(2) parent: FloatArray,
    ) {
        this.anchorPosition.set(anchorPosition)
        val (parentWidth, parentHeight) = parent
        parentSize[0] = parentWidth
        parentSize[1] = parentHeight
        updatePosition()
    }

    init {
        visibility = View.INVISIBLE

        minWidth = dp(theme.generalStyle.layout.minWidth)
        minHeight = dp(theme.generalStyle.layout.minHeight)
        verticalPadding = dp(theme.generalStyle.layout.marginX)
        horizontalPadding = dp(theme.generalStyle.layout.marginY)
        background =
            ColorManager.getDrawable(
                "text_back_color",
                "border_color",
                dp(theme.generalStyle.layout.border),
                dp(theme.generalStyle.layout.roundCorner),
                theme.generalStyle.layout.alpha,
            )
        add(
            preeditUi.root,
            lParams(wrapContent, wrapContent) {
                topOfParent()
                startOfParent()
            },
        )
        add(
            candidatesUi.root,
            lParams(matchConstraints, wrapContent) {
                matchConstraintMinWidth = wrapContent
                below(preeditUi.root)
                centerHorizontally()
                bottomOfParent()
            },
        )

        isFocusable = false
        layoutParams = ViewGroup.LayoutParams(wrapContent, wrapContent)
    }

    /**
     * 應用視窗邊距設定
     *
     * @param insets 系統視窗邊距資訊
     * @return 處理後的邊距資訊
     */
    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            bottomInsets = getNavBarBottomInset(insets)
        }
        return insets
    }

    /**
     * 當視圖附加到視窗時的回調
     *
     * 註冊佈局監聽器和預繪製監聽器
     */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        candidatesUi.root.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        viewTreeObserver.addOnPreDrawListener(preDrawListener)
    }

    /**
     * 當視圖從視窗分離時的回調
     *
     * 移除監聽器並關閉觸控事件接收視窗
     */
    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        candidatesUi.root.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        touchEventReceiverWindow.dismiss()
        super.onDetachedFromWindow()
    }
}
