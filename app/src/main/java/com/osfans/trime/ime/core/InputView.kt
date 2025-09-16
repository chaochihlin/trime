// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.core

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import android.view.View.OnClickListener
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.QuickBar
import com.osfans.trime.ime.candidates.compact.CompactCandidateModule
import com.osfans.trime.ime.composition.PreeditModule
import com.osfans.trime.ime.dependency.InputComponent
import com.osfans.trime.ime.dependency.create
import com.osfans.trime.ime.keyboard.KeyboardWindow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.withTheme
import splitties.views.dsl.core.wrapContent
import splitties.views.imageDrawable
import com.osfans.trime.ime.circular.CircularContainer
import com.osfans.trime.ime.circular.CircularScreenPresets
import com.osfans.trime.ime.circular.CircularScreenConfig
import android.content.res.Configuration

/**
 * Successor of the old InputRoot
 */
@SuppressLint("ViewConstructor")
class InputView(
    service: TrimeInputMethodService,
    rime: RimeSession,
    theme: Theme,
) : BaseInputView(service, rime, theme) {
    private val keyboardBackground =
        imageView {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
    private val placeholderListener = OnClickListener { }

    private val leftPaddingSpace =
        view(::View) {
            setOnClickListener(placeholderListener)
        }

    private val rightPaddingSpace =
        view(::View) {
            setOnClickListener(placeholderListener)
        }

    private val bottomPaddingSpace =
        view(::View) {
            setOnClickListener(placeholderListener)
        }

    private val updateWindowViewHeightJob: Job

    private val themedContext = context.withTheme(android.R.style.Theme_DeviceDefault_Settings)
    private val inputComponent = InputComponent::class.create(this, themedContext, theme, service, rime)
    private val broadcaster = inputComponent.broadcaster
    private val enterKeyLabel = inputComponent.enterKeyLabel
    private val windowManager = inputComponent.windowManager
    private val quickBar: QuickBar = inputComponent.quickBar
    private val preedit: PreeditModule = inputComponent.preedit
    public val keyboardWindow: KeyboardWindow = inputComponent.keyboardWindow
    private val compactCandidate: CompactCandidateModule = inputComponent.candidate.compactCandidateModule

    private fun addBroadcastReceivers() {
        broadcaster.addReceiver(quickBar)
        broadcaster.addReceiver(preedit)
        broadcaster.addReceiver(keyboardWindow)
        broadcaster.addReceiver(compactCandidate)
    }

    private val keyboardSidePadding = theme.generalStyle.keyboardPadding
    private val keyboardBottomPadding = theme.generalStyle.keyboardPaddingBottom

    private val keyboardSidePaddingPx: Int
        get() {
            return dp(keyboardSidePadding)
        }

    private val keyboardBottomPaddingPx: Int
        get() {
            return dp(keyboardBottomPadding)
        }

    val keyboardView: View

    /**
     * 檢測是否為圓形螢幕設備（如智慧手錶）
     *
     * 針對特定手錶設備優化：
     * - 非標準 Wear OS，Android 9
     * - 固定圓形螢幕，無需兼容其他形狀
     */
    private fun isRoundScreen(): Boolean {
        return try {
            // 獲取螢幕尺寸信息
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val density = displayMetrics.density

            // 檢查是否為典型手錶螢幕尺寸（小正方形螢幕）
            val isSmallSquareScreen = (screenWidth in 300..500) &&
                                     (screenHeight in 300..500) &&
                                     kotlin.math.abs(screenWidth - screenHeight) < 50

            // 檢查設備特性（此設備固定為手錶）
            val hasWatchProperties = context.packageManager.hasSystemFeature("android.hardware.type.watch") ||
                                   context.resources.configuration.smallestScreenWidthDp < 200


            // 由於目標設備固定為圓形手錶，直接返回 true
            // 這樣可以確保圓形適配始終啟用
            true
        } catch (e: Exception) {
            // 由於目標設備固定，即使檢測失敗也返回 true
            true
        }
    }

    init {
        addBroadcastReceivers()

        // 移除調試背景色

        windowManager.cacheResidentWindow(keyboardWindow, createView = true)
        // show KeyboardWindow by default
        windowManager.attachWindow(KeyboardWindow)

        keyboardBackground.imageDrawable = ColorManager.getDrawable("keyboard_background")

        // 創建原本的 ConstraintLayout 鍵盤
        val originalKeyboardLayout = constraintLayout {
            isMotionEventSplittingEnabled = true
            add(
                keyboardBackground,
                lParams {
                    centerInParent()
                },
            )
            add(
                quickBar.view,
                lParams(matchParent, dp(quickBar.themedHeight)) {
                    topOfParent()
                    centerHorizontally()
                },
            )
            add(
                leftPaddingSpace,
                lParams {
                    below(quickBar.view)
                    startOfParent()
                    bottomOfParent()
                },
            )
            add(
                rightPaddingSpace,
                lParams {
                    below(quickBar.view)
                    endOfParent()
                    bottomOfParent()
                },
            )
            add(
                windowManager.view,
                lParams {
                    below(quickBar.view)
                    above(bottomPaddingSpace)
                },
            )
            add(
                bottomPaddingSpace,
                lParams {
                    startToEndOf(leftPaddingSpace)
                    endToStartOf(rightPaddingSpace)
                    bottomOfParent()
                },
            )
        }

        // 根據螢幕類型決定是否使用圓形容器
        keyboardView = if (isRoundScreen()) {
            CircularContainer(context).apply {
                // 動態獲取實際螢幕尺寸並設定完全貼合的圓形配置
                val displayMetrics = context.resources.displayMetrics
                val screenSize = kotlin.math.min(displayMetrics.widthPixels, displayMetrics.heightPixels)
                val perfectFitConfig = CircularScreenConfig(
                    screenDiameter = screenSize,
                    safeMargin = 0,  // 完全貼合螢幕邊緣，無安全邊距
                    chinHeight = 0,  // 此設備無下巴
                    crownPosition = 0.3f
                )
                setScreenConfig(perfectFitConfig)


                // 添加原本的鍵盤佈局到圓形容器中
                add(originalKeyboardLayout, android.widget.FrameLayout.LayoutParams(
                    matchParent, matchParent
                ))
            }
        } else {
            // 非圓形螢幕，直接使用原本的佈局
            originalKeyboardLayout
        }

        updateWindowViewHeightJob =
            service.lifecycleScope.launch {
                keyboardWindow.currentKeyboardHeight.collect {
                    windowManager.view.updateLayoutParams {
                        height = it
                    }
                }
            }

        updateKeyboardSize()

        add(
            preedit.ui.root,
            lParams(matchParent, wrapContent) {
                above(keyboardView)
                centerHorizontally()
            },
        )

        add(
            keyboardView,
            lParams(matchParent, matchParent) {
                centerHorizontally()
                // 移除 bottomOfParent() 以允許完全填滿父容器
            },
        )

    }

    private fun updateKeyboardSize() {
        bottomPaddingSpace.updateLayoutParams {
            height = keyboardBottomPaddingPx
        }
        val sidePadding = keyboardSidePaddingPx
        val unset = LayoutParams.UNSET
        if (sidePadding == 0) {
            // hide side padding space views when unnecessary
            leftPaddingSpace.visibility = View.GONE
            rightPaddingSpace.visibility = View.GONE
            windowManager.view.updateLayoutParams<LayoutParams> {
                startToEnd = unset
                endToStart = unset
                startOfParent()
                endOfParent()
            }
        } else {
            leftPaddingSpace.visibility = View.VISIBLE
            rightPaddingSpace.visibility = View.VISIBLE
            leftPaddingSpace.updateLayoutParams {
                width = sidePadding
            }
            rightPaddingSpace.updateLayoutParams {
                width = sidePadding
            }
            windowManager.view.updateLayoutParams<LayoutParams> {
                startToStart = unset
                endToEnd = unset
                startToEndOf(leftPaddingSpace)
                endToStartOf(rightPaddingSpace)
            }
        }
        quickBar.view.setPadding(sidePadding, 0, sidePadding, 0)
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        bottomPaddingSpace.updateLayoutParams<LayoutParams> {
            bottomMargin = getNavBarBottomInset(insets)
        }
        return insets
    }

    fun startInput(
        info: EditorInfo,
        restarting: Boolean = false,
    ) {
        broadcaster.onStartInput(info)
        enterKeyLabel.updateLabelOnEditorInfo(info)
        if (!restarting) {
            windowManager.attachWindow(KeyboardWindow)
        }
    }

    override fun handleRimeMessage(it: RimeMessage<*>) {
        when (it) {
            is RimeMessage.SchemaMessage -> {
                broadcaster.onRimeSchemaUpdated(it.data)

                windowManager.attachWindow(KeyboardWindow)
            }

            is RimeMessage.OptionMessage -> {
                broadcaster.onRimeOptionUpdated(it.data)
            }

            is RimeMessage.ResponseMessage ->
                it.data.let event@{
                    broadcaster.onInputContextUpdate(it.context)
                }

            else -> {}
        }
    }

    fun updateSelection(
        start: Int,
        end: Int,
    ) {
        broadcaster.onSelectionUpdate(start, end)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun handleInlineSuggestions(response: InlineSuggestionsResponse): Boolean {
        val suggestions = response.inlineSuggestions
        broadcaster.onInlineSuggestions(suggestions)
        quickBar.handleInlineSuggestions(suggestions.isEmpty())
        return true
    }

    override fun onDetachedFromWindow() {
        ViewCompat.setOnApplyWindowInsetsListener(this, null)
        // cancel the notification job and clear all broadcast receivers,
        // implies that InputView should not be attached again after detached.
        updateWindowViewHeightJob.cancel()
        broadcaster.clear()
        super.onDetachedFromWindow()
    }
}
