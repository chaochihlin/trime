// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.bar

import android.content.Context
import android.os.Build
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ViewAnimator
import androidx.annotation.RequiresApi
import com.osfans.trime.R
import com.osfans.trime.core.RimeProto
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.ui.CandidateUi
import com.osfans.trime.ime.bar.ui.TabUi
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.candidates.CandidateModule
import com.osfans.trime.ime.candidates.popup.PopupCandidatesMode
import com.osfans.trime.ime.candidates.unrolled.window.FlexboxUnrolledCandidateWindow
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dependency.InputScope
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import me.tatarka.inject.annotations.Inject
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent

/**
 * 快速操作欄管理器
 *
 * 負責管理輸入法底部的快速操作欄，提供候選詞顯示、建議顯示等功能。
 * 透過狀態機控制不同 UI 介面的切換，支援展開/收合候選詞視窗、
 * 切換輸入選項等操作。
 *
 * 主要包含三種 UI 狀態：
 * - [CandidateUi]: 候選詞顯示介面，含展開/收合按鈕
 * - [TabUi]: 標籤式擴展介面
 *
 * @param context Android 上下文
 * @param service 輸入法服務實例
 * @param rime RIME 會話管理器
 * @param theme 主題配置
 * @param windowManager 視窗管理器
 * @param lazyCandidate 延遲載入的候選詞模組
 */
@InputScope
@Inject
class QuickBar(
    private val context: Context,
    private val service: TrimeInputMethodService,
    private val rime: RimeSession,
    private val theme: Theme,
    private val windowManager: BoardWindowManager,
    lazyCandidate: Lazy<CandidateModule>,
) : InputBroadcastReceiver {
    /** 候選詞模組實例 */
    private val candidate by lazyCandidate

    /** 應用程式偏好設定 */
    private val prefs = AppPrefs.defaultInstance()

    /** 是否隱藏快速操作欄的設定 */
    private val hideQuickBar by prefs.keyboard.hideQuickBar

    /** 快速操作欄的主題化高度，包含候選詞高度和註解高度 */
    val themedHeight =
        theme.generalStyle.candidateViewHeight + theme.generalStyle.commentHeight

    /**
     * 候選詞顯示介面
     *
     * 顯示輸入過程中產生的候選詞，並提供展開/收合功能。
     */
    private val candidateUi by lazy {
        CandidateUi(context, candidate.compactCandidateModule.view)
    }

    /**
     * 標籤式介面
     *
     * 提供標籤式的擴展功能介面，可以載入外部視窗內容。
     */
    private val tabUi by lazy {
        TabUi(context, theme)
    }

    /**
     * 快速操作欄狀態機
     *
     * 管理不同 UI 介面之間的狀態切換，確保正確的顯示邏輯。
     */
    private val barStateMachine =
        QuickBarStateMachine.new {
            switchUiByState(it)
        }

    /**
     * 展開按鈕狀態機
     *
     * 管理候選詞展開/收合按鈕的狀態和行為：
     * - [UnrollButtonStateMachine.State.ClickToAttachWindow]: 點擊展開候選詞視窗
     * - [UnrollButtonStateMachine.State.ClickToDetachWindow]: 點擊收合候選詞視窗
     * - [UnrollButtonStateMachine.State.Hidden]: 隱藏展開按鈕
     */
    val unrollButtonStateMachine =
        UnrollButtonStateMachine.new {
            when (it) {
                UnrollButtonStateMachine.State.ClickToAttachWindow -> {
                    setUnrollButtonToAttach()
                    setUnrollButtonEnabled(true)
                }
                UnrollButtonStateMachine.State.ClickToDetachWindow -> {
                    setUnrollButtonToDetach()
                    setUnrollButtonEnabled(true)
                }
                UnrollButtonStateMachine.State.Hidden -> {
                    setUnrollButtonEnabled(false)
                }
            }
        }

    /**
     * 設定展開按鈕為附加模式
     *
     * 配置按鈕點擊時展開候選詞視窗，並設定對應圖示。
     */
    private fun setUnrollButtonToAttach() {
        candidateUi.unrollButton.setOnClickListener { view ->
            InputFeedbackManager.keyPressVibrate(view)
            windowManager.attachWindow(
                FlexboxUnrolledCandidateWindow(context, service, rime, theme, this, windowManager, candidate.compactCandidateModule),
            )
        }
        candidateUi.unrollButton.setIcon(R.drawable.ic_baseline_expand_more_24)
    }

    /**
     * 設定展開按鈕為分離模式
     *
     * 配置按鈕點擊時收合候選詞視窗回到鍵盤介面，並設定對應圖示。
     */
    private fun setUnrollButtonToDetach() {
        candidateUi.unrollButton.setOnClickListener { view ->
            InputFeedbackManager.keyPressVibrate(view)
            windowManager.attachWindow(KeyboardWindow)
        }
        candidateUi.unrollButton.setIcon(R.drawable.ic_baseline_expand_less_24)
    }

    /**
     * 設定展開按鈕的啟用狀態
     *
     * @param enabled 是否啟用按鈕，控制按鈕的可見性
     */
    private fun setUnrollButtonEnabled(enabled: Boolean) {
        candidateUi.unrollButton.visibility = if (enabled) View.VISIBLE else View.INVISIBLE
    }

    /** 候選詞顯示模式設定 */
    private val candidatesMode by AppPrefs.defaultInstance().candidates.mode

    /**
     * 當輸入內容更新時的回調
     *
     * 根據候選詞的變化更新快速操作欄的狀態。
     * 當候選詞模式設為 ALWAYS_SHOW 時，暫時跳過狀態更新以避免
     * 狀態欄與懸浮窗同時顯示的問題。
     *
     * @param ctx RIME 輸入內容
     */
    override fun onInputContextUpdate(ctx: RimeProto.Context) {
        // TODO: 临时修复状态栏与悬浮窗同时显示，后续需优化：考虑分离数据或寻找更好的实现方式
        if (candidatesMode == PopupCandidatesMode.ALWAYS_SHOW) return

        barStateMachine.push(
            QuickBarStateMachine.TransitionEvent.CandidatesUpdated,
            QuickBarStateMachine.BooleanKey.CandidateEmpty to ctx.menu.candidates.isEmpty(),
        )
    }

    /**
     * 根據狀態切換 UI 介面
     *
     * 根據狀態機的當前狀態顯示對應的 UI 介面。
     * 當切換到非標籤介面時，會清除標籤介面的外部內容。
     *
     * @param state 目標狀態
     */
    private fun switchUiByState(state: QuickBarStateMachine.State) {
        val index = state.ordinal
        if (view.displayedChild == index) return
        val new = view.getChildAt(index)
        if (new != tabUi.root) {
            tabUi.setBackButtonOnClickListener { }
            tabUi.removeExternal()
        }
        view.displayedChild = index
    }

    /**
     * 快速操作欄的主視圖
     *
     * 使用 ViewAnimator 管理多個子介面的切換，包含：
     * 1. 候選詞介面 (candidateUi)
     * 2. 標籤介面 (tabUi)
     * 3. 建議詞介面 (suggestionUi)
     *
     * 視圖的可見性由使用者設定控制，背景樣式由主題配置決定。
     */
    val view by lazy {
        ViewAnimator(context).apply {
            visibility =
                if (hideQuickBar) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
            background =
                ColorManager.getDrawable(
                    "candidate_background",
                    "candidate_border_color",
                    dp(theme.generalStyle.candidateBorder),
                    dp(theme.generalStyle.candidateBorderRound),
                )
            add(candidateUi.root, lParams(matchParent, matchParent))
            add(tabUi.root, lParams(matchParent, matchParent))
        }
    }

    /**
     * 開始輸入時的回調
     * @param info 編輯器資訊
     */
    override fun onStartInput(info: EditorInfo) {
    }

    /**
     * 視窗附加時的回調
     *
     * 當有外部視窗附加到快速操作欄時，將其內容載入到標籤介面中，
     * 並配置返回按鈕的行為。
     *
     * @param window 被附加的視窗
     */
    override fun onWindowAttached(window: BoardWindow) {
        if (window is BoardWindow.BarBoardWindow) {
            window.onCreateBarView()?.let { tabUi.addExternal(it, window.showTitle) }
            tabUi.setBackButtonOnClickListener {
                windowManager.attachWindow(KeyboardWindow)
            }
            barStateMachine.push(QuickBarStateMachine.TransitionEvent.BarBoardWindowAttached)
        }
    }

    /**
     * 視窗分離時的回調
     *
     * 當外部視窗從快速操作欄分離時，觸發相應的狀態轉換。
     *
     * @param window 被分離的視窗
     */
    override fun onWindowDetached(window: BoardWindow) {
        barStateMachine.push(QuickBarStateMachine.TransitionEvent.WindowDetached)
    }

    /**
     * 處理內嵌建議功能 (Android R+ 限定)
     *
     * 根據建議內容的變化更新快速操作欄的狀態。
     * 此功能僅在 Android 11 (API 30) 及以上版本可用。
     *
     * @param isEmpty 建議內容是否為空
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun handleInlineSuggestions(isEmpty: Boolean) {
        barStateMachine.push(
            QuickBarStateMachine.TransitionEvent.SuggestionUpdated,
            QuickBarStateMachine.BooleanKey.SuggestionEmpty to isEmpty,
        )
    }
}
