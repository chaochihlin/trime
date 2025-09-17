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
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.QuickBar
import com.osfans.trime.ime.candidates.compact.CompactCandidateModule
import com.osfans.trime.ime.circular.CircularContainer
import com.osfans.trime.ime.circular.CircularScreenConfig
import com.osfans.trime.ime.composition.PreeditModule
import com.osfans.trime.ime.dependency.InputComponent
import com.osfans.trime.ime.dependency.create
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.t9.T9InputContainer
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
import timber.log.Timber

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
    private var t9InputContainer: T9InputContainer? = null

    /**
     * 檢測是否為圓形螢幕設備（如智慧手錶）
     *
     * 針對特定手錶設備優化：
     * - 非標準 Wear OS，Android 9
     * - 固定圓形螢幕，無需兼容其他形狀
     */
    private fun isRoundScreen(): Boolean =
        try {
            // 獲取螢幕尺寸信息
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val density = displayMetrics.density

            // 檢查是否為典型手錶螢幕尺寸（小正方形螢幕）
            val isSmallSquareScreen =
                (screenWidth in 300..500) &&
                    (screenHeight in 300..500) &&
                    kotlin.math.abs(screenWidth - screenHeight) < 50

            // 檢查設備特性（此設備固定為手錶）
            val hasWatchProperties =
                context.packageManager.hasSystemFeature("android.hardware.type.watch") ||
                    context.resources.configuration.smallestScreenWidthDp < 200

            // 由於目標設備固定為圓形手錶，直接返回 true
            // 這樣可以確保圓形適配始終啟用
            true
        } catch (e: Exception) {
            // 由於目標設備固定，即使檢測失敗也返回 true
            true
        }

    /**
     * 檢測是否應該使用T9輸入模式
     *
     * 基於圓形螢幕檢測和用戶偏好決定：
     * - 圓形螢幕自動啟用T9模式
     * - 未來可以添加用戶設定選項
     */
    private fun shouldUseT9Mode(): Boolean {
        return try {
            val useT9 = true // 暫時啟用 T9 模式來測試錯誤處理
            Timber.d("InputView: ★ shouldUseT9Mode() = $useT9 ★")
            Timber.d("InputView: Current timestamp: ${System.currentTimeMillis()}")
            useT9
        } catch (e: Exception) {
            Timber.e(e, "InputView: Error in shouldUseT9Mode()")
            false
        }
        // return isRoundScreen() // 目前基於圓形螢幕檢測
    }

    /**
     * 創建T9輸入容器
     */
    private fun createT9KeyboardView(): View =
        try {
            Timber.d("InputView: Starting T9InputContainer creation")

            val container = T9InputContainer(context)
            Timber.d("InputView: T9InputContainer instance created successfully")

            container.apply {
                Timber.d("InputView: Calling T9InputContainer.setup() with theme, rime, service")
                setup(theme, rime, service)
                Timber.d("InputView: T9InputContainer.setup() completed successfully")

                t9InputContainer = this
                Timber.d("InputView: T9InputContainer reference stored successfully")
            }
        } catch (e: Exception) {
            Timber.e(e, "InputView: Critical error creating T9InputContainer")
            throw e // Re-throw to allow proper error handling
        }

    init {
        addBroadcastReceivers()

        // 移除調試背景色

        windowManager.cacheResidentWindow(keyboardWindow, createView = true)
        // show KeyboardWindow by default
        windowManager.attachWindow(KeyboardWindow)

        keyboardBackground.imageDrawable = ColorManager.getDrawable("keyboard_background")

        // 決定使用哪種鍵盤模式
        keyboardView =
            try {
                if (shouldUseT9Mode()) {
                    Timber.d("InputView: Creating T9 mode keyboard view...")

                    // 使用T9輸入模式
                    val t9Container = createT9KeyboardView()
                    Timber.d("InputView: T9 container created, now wrapping in CircularContainer...")

                    // 包裝在圓形容器中
                    CircularContainer(context).apply {
                        Timber.d("InputView: CircularContainer instance created")

                        // 動態獲取實際螢幕尺寸並設定完全貼合的圓形配置
                        val displayMetrics = context.resources.displayMetrics
                        val screenSize = kotlin.math.min(displayMetrics.widthPixels, displayMetrics.heightPixels)
                        val perfectFitConfig =
                            CircularScreenConfig(
                                screenDiameter = screenSize,
                                safeMargin = 0, // 完全貼合螢幕邊緣，無安全邊距
                                chinHeight = 0, // 此設備無下巴
                                crownPosition = 0.3f,
                            )
                        Timber.d("InputView: Setting CircularScreenConfig with screenSize: $screenSize")
                        setScreenConfig(perfectFitConfig)

                        // 添加T9容器到圓形容器中
                        Timber.d("InputView: Adding T9 container to CircularContainer...")
                        add(
                            t9Container,
                            android.widget.FrameLayout.LayoutParams(
                                matchParent,
                                matchParent,
                            ),
                        )
                        Timber.d("InputView: T9 keyboard view creation completed successfully")

                        // 添加布局驗證日誌
                        post {
                            Timber.d("InputView: Verifying T9 layout - CircularContainer childCount: $childCount")
                            Timber.d("InputView: T9Container size: ${t9Container.width}x${t9Container.height}")
                            Timber.d("InputView: T9 keyboard visibility: ${t9Container.visibility}")
                        }
                    }
                } else {
                    Timber.d("InputView: Creating traditional keyboard view...")
                    // 使用傳統鍵盤模式
                    val originalKeyboardLayout =
                        constraintLayout {
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
                    if (isRoundScreen()) {
                        CircularContainer(context).apply {
                            val displayMetrics = context.resources.displayMetrics
                            val screenSize = kotlin.math.min(displayMetrics.widthPixels, displayMetrics.heightPixels)
                            val perfectFitConfig =
                                CircularScreenConfig(
                                    screenDiameter = screenSize,
                                    safeMargin = 0,
                                    chinHeight = 0,
                                    crownPosition = 0.3f,
                                )
                            setScreenConfig(perfectFitConfig)

                            add(
                                originalKeyboardLayout,
                                android.widget.FrameLayout.LayoutParams(
                                    matchParent,
                                    matchParent,
                                ),
                            )
                        }
                    } else {
                        originalKeyboardLayout
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "InputView: FATAL ERROR during keyboard view creation - falling back to traditional mode")

                // Fallback to traditional keyboard in case of error
                val originalKeyboardLayout =
                    constraintLayout {
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
                originalKeyboardLayout
            }

        updateWindowViewHeightJob =
            service.lifecycleScope.launch {
                keyboardWindow.currentKeyboardHeight.collect { height ->
                    try {
                        // 加強的安全檢查：確保view、layoutParams和height都有效
                        val view = windowManager.view
                        val layoutParams = view?.layoutParams

                        if (view != null && layoutParams != null && height > 0) {
                            Timber.d("InputView: Updating window view height to $height")
                            view.updateLayoutParams {
                                this.height = height
                            }
                        } else {
                            Timber.w(
                                "InputView: Skipping height update - view: ${view != null}, layoutParams: ${layoutParams != null}, height: $height",
                            )
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "InputView: Critical error updating window view height")
                        // 不重新拋出異常，避免崩潰整個應用
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
        try {
            // 檢查 bottomPaddingSpace.layoutParams 是否為 null
            if (bottomPaddingSpace.layoutParams != null) {
                bottomPaddingSpace.updateLayoutParams {
                    height = keyboardBottomPaddingPx
                }
            } else {
                Timber.w("InputView: bottomPaddingSpace.layoutParams is null in updateKeyboardSize")
            }
            val sidePadding = keyboardSidePaddingPx
            val unset = LayoutParams.UNSET

            // 檢查 windowManager.view.layoutParams 是否為 null，避免在 T9 模式下崩潰
            if (windowManager.view.layoutParams != null) {
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
                    if (leftPaddingSpace.layoutParams != null) {
                        leftPaddingSpace.updateLayoutParams {
                            width = sidePadding
                        }
                    }
                    if (rightPaddingSpace.layoutParams != null) {
                        rightPaddingSpace.updateLayoutParams {
                            width = sidePadding
                        }
                    }
                    windowManager.view.updateLayoutParams<LayoutParams> {
                        startToStart = unset
                        endToEnd = unset
                        startToEndOf(leftPaddingSpace)
                        endToStartOf(rightPaddingSpace)
                    }
                }
            } else {
                Timber.w("InputView: windowManager.view.layoutParams is null in updateKeyboardSize, skipping layout updates")
                // 仍然處理 padding space 的可見性，因為這些不依賴 windowManager
                if (sidePadding == 0) {
                    leftPaddingSpace.visibility = View.GONE
                    rightPaddingSpace.visibility = View.GONE
                } else {
                    leftPaddingSpace.visibility = View.VISIBLE
                    rightPaddingSpace.visibility = View.VISIBLE
                    if (leftPaddingSpace.layoutParams != null) {
                        leftPaddingSpace.updateLayoutParams {
                            width = sidePadding
                        }
                    }
                    if (rightPaddingSpace.layoutParams != null) {
                        rightPaddingSpace.updateLayoutParams {
                            width = sidePadding
                        }
                    }
                }
            }
            quickBar.view.setPadding(sidePadding, 0, sidePadding, 0)
        } catch (e: Exception) {
            Timber.e(e, "InputView: Error in updateKeyboardSize")
        }
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        try {
            // 檢查 layoutParams 是否為 null，避免在 T9 模式下崩潰
            if (bottomPaddingSpace.layoutParams != null) {
                bottomPaddingSpace.updateLayoutParams<LayoutParams> {
                    bottomMargin = getNavBarBottomInset(insets)
                }
            } else {
                Timber.w("InputView: bottomPaddingSpace.layoutParams is null in onApplyWindowInsets")
            }
        } catch (e: Exception) {
            Timber.e(e, "InputView: Error in onApplyWindowInsets")
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

    /**
     * 處理退格鍵（針對T9模式）
     */
    fun handleBackspace() {
        t9InputContainer?.handleBackspace()
    }

    /**
     * 檢查是否正在使用T9模式
     */
    fun isUsingT9Mode(): Boolean = t9InputContainer != null

    /**
     * 取得T9輸入容器（如果正在使用T9模式）
     */
    fun getT9InputContainer(): T9InputContainer? = t9InputContainer

    /**
     * 重置輸入狀態（針對T9模式）
     */
    fun resetInputState() {
        t9InputContainer?.reset()
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
