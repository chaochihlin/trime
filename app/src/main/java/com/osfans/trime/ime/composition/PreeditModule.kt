/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import android.content.Context
import android.graphics.Outline
import android.graphics.Rect
import android.view.View
import android.view.ViewOutlineProvider
import com.osfans.trime.core.RimeProto
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.candidates.popup.PopupCandidatesMode
import com.osfans.trime.ime.core.TouchEventReceiverWindow
import com.osfans.trime.ime.dependency.InputScope
import me.tatarka.inject.annotations.Inject
import splitties.dimensions.dp
import splitties.views.backgroundColor

/**
 * 編輯區域模組，管理 RIME 輸入法的編輯文字顯示功能
 *
 * 此模組負責整合編輯文字 UI 與 RIME 引擎，處理輸入上下文更新事件，
 * 並根據候選詞模式決定是否顯示編輯區域。同時管理觸控事件接收視窗。
 *
 * @param context Android 上下文物件
 * @param theme 主題設定物件
 * @param rime RIME 會話實例
 */
@InputScope
@Inject
class PreeditModule(
    context: Context,
    theme: Theme,
    rime: RimeSession,
) : InputBroadcastReceiver {
    /**
     * 左上角圓角外框提供者
     *
     * 自訂的 ViewOutlineProvider，為編輯區域提供左上角圓角效果
     */
    private val topLeftCornerRadiusOutlineProvider =
        object : ViewOutlineProvider() {
            override fun getOutline(
                view: View,
                outline: Outline,
            ) {
                val radius = context.dp(theme.generalStyle.layout.roundCorner)
                val width = view.width
                val height = view.height
                val rect = Rect(-radius.toInt(), 0, width, (height + radius).toInt())
                outline.setRoundRect(rect, radius)
            }
        }

    /**
     * 編輯區域 UI 實例
     *
     * 整合了主題樣式和游標移動功能的編輯文字介面
     */
    val ui =
        PreeditUi(
            context,
            theme,
            setupPreeditView = {
                backgroundColor = ColorManager.getColor("text_back_color")
            },
            onMoveCursor = { pos -> rime.launchOnReady { it.moveCursorPos(pos) } },
        ).apply {
            root.alpha = theme.generalStyle.layout.alpha / 255f
            root.outlineProvider = topLeftCornerRadiusOutlineProvider
            root.clipToOutline = true
            root.visibility = View.VISIBLE // 除錯：強制顯示來測試
        }

    /** 觸控事件接收視窗，用於處理編輯區域外的觸控事件 */
    private val touchEventReceiverWindow = TouchEventReceiverWindow(ui.root)

    /** 候選詞模式設定 */
    private val candidatesMode by AppPrefs.defaultInstance().candidates.mode

    /**
     * 處理輸入上下文更新事件
     *
     * 當 RIME 引擎的輸入狀態發生變化時，此方法會被調用。
     * 根據候選詞顯示模式和編輯文字狀態來決定是否顯示編輯區域，
     * 並相應地管理觸控事件接收視窗。
     *
     * @param ctx RIME 輸入上下文，包含當前的組合和候選詞資訊
     */
    override fun onInputContextUpdate(ctx: RimeProto.Context) {
        // TODO: 临时修复状态栏与悬浮窗同时显示，后续需优化：考虑分离数据或寻找更好的实现方式
        timber.log.Timber.d(
            "【除錯】PreeditModule.onInputContextUpdate: composition.preedit='${ctx.composition.preedit}', length=${ctx.composition.length}",
        )

        if (candidatesMode == PopupCandidatesMode.ALWAYS_SHOW) return

        ui.update(ctx.composition)
        timber.log.Timber.d("【除錯】PreeditModule: ui.visible=${ui.visible}, 設定visibility=${if (ui.visible) "VISIBLE" else "INVISIBLE"}")
        ui.root.visibility = if (ui.visible) View.VISIBLE else View.INVISIBLE
        if (ctx.composition.length > 0) {
            touchEventReceiverWindow.show()
        } else {
            touchEventReceiverWindow.dismiss()
        }
    }
}
