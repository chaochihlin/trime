// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.core

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Dialog
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.RectF
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.FrameLayout
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.BuildConfig
import com.osfans.trime.core.KeyModifiers
import com.osfans.trime.core.KeyValue
import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.RimeKeyMapping
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.core.RimeProto
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegate
import com.osfans.trime.data.prefs.PreferenceDelegateProvider
import com.osfans.trime.data.schema.SchemaManager
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.composition.CandidatesView
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import com.osfans.trime.ime.keyboard.KeyboardSwitcher
import com.osfans.trime.receiver.RimeIntentReceiver
import com.osfans.trime.util.any
import com.osfans.trime.util.findSectionFrom
import com.osfans.trime.util.forceShowSelf
import com.osfans.trime.util.isNightMode
import com.osfans.trime.util.monitorCursorAnchor
import com.osfans.trime.util.styledFloat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import splitties.bitflags.hasFlag
import splitties.systemservices.inputMethodManager
import timber.log.Timber

/** [輸入法][InputMethodService]主程序  */

open class TrimeInputMethodService : LifecycleInputMethodService() {
    private lateinit var rime: RimeSession
    private val jobs = Channel<Job>(capacity = Channel.UNLIMITED)

    private var normalTextEditor = false
    private val prefs: AppPrefs
        get() = AppPrefs.defaultInstance()
    private lateinit var decorView: View
    private lateinit var contentView: FrameLayout
    private var inputView: InputView? = null
    private var candidatesView: CandidatesView? = null
    private val navBarManager = NavigationBarManager()
    private val inputDeviceManager =
        InputDeviceManager onChange@{
            val w = window.window ?: return@onChange
            navBarManager.evaluate(w, useVirtualKeyboard = it)
        }
    private val rimeIntentReceiver = RimeIntentReceiver()

    var lastCommittedText: CharSequence = ""
        private set

    private val recreateInputViewPrefs: Array<PreferenceDelegate<*>> =
        arrayOf(prefs.keyboard.hideQuickBar)

    @Keep
    private val recreateInputViewListener =
        PreferenceDelegate.OnChangeListener<Any> { _, _ ->
            replaceInputView(ThemeManager.activeTheme)
        }

    @Keep
    private val recreateCandidatesViewListener =
        PreferenceDelegateProvider.OnChangeListener {
            replaceCandidateView(ThemeManager.activeTheme)
        }

    @Keep
    private val onThemeChangeListener =
        ThemeManager.OnThemeChangeListener {
            replaceInputViews(it)
        }

    @Keep
    private val onColorChangeListener =
        ColorManager.OnColorChangeListener {
            ContextCompat.getMainExecutor(this).execute {
                replaceInputViews(it)
            }
        }

    private fun postJob(
        scope: CoroutineScope,
        block: suspend () -> Unit,
    ): Job {
        val job = scope.launch(start = CoroutineStart.LAZY) { block() }
        jobs.trySend(job)
        return job
    }

    /**
     * 將 rime 操作發布到 [jobs] 中執行
     *
     * 與 `rime.runOnReady` 或 `rime.launchOnReady` 不同，在這些方法中
     * 如果先前的操作未完成（暫停），後續操作可以開始，
     * [postRimeJob] 確保操作按順序執行。
     */
    fun postRimeJob(block: suspend RimeApi.() -> Unit) = postJob(rime.lifecycleScope) { rime.runOnReady(block) }

    private suspend fun updateRimeOption(api: RimeApi) {
        try {
            api.setRuntimeOption("soft_cursor", prefs.keyboard.softCursorEnabled.getValue()) // 軟光標
            api.setRuntimeOption("_horizontal", ThemeManager.activeTheme.generalStyle.horizontal) // 水平模式
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private fun registerReceiver() {
        val intentFilter =
            IntentFilter().apply {
                addAction(RimeIntentReceiver.ACTION_DEPLOY)
                addAction(RimeIntentReceiver.ACTION_SYNC_USER_DATA)
            }
        ContextCompat.registerReceiver(
            this,
            rimeIntentReceiver,
            intentFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onCreate() {
        Timber.w("🔍 [TrimeInputMethodService] onCreate: 開始創建輸入法服務")
        rime = RimeDaemon.createSession(javaClass.name)
        Timber.w("🔍 [TrimeInputMethodService] onCreate: RIME session 創建完成")
        lifecycleScope.launch {
            jobs.consumeEach { it.join() }
        }
        lifecycleScope.launch {
            rime.run { messageFlow }.collect {
                handleRimeMessage(it)
            }
        }
        recreateInputViewPrefs.forEach {
            it.registerOnChangeListener(recreateInputViewListener)
        }
        prefs.candidates.registerOnChangeListener(recreateCandidatesViewListener)
        DataManager.sync()

        // 確保 RIME 引擎在主題載入前啟動，以便 deployRimeConfigFile 能正常工作
        RimeDaemon.ensureRimeStarted()

        SchemaManager.init("bopomofo_t9")
        ThemeManager.init(resources.configuration)
        postRimeJob {
            setEnabledSchemata(arrayOf("bopomofo_t9"))
            selectSchema("bopomofo_t9")
        }

        // 強制觸發完整 RIME 部署以編譯所有 Schema
        postRimeJob {
            Timber.w("🔍 [TrimeInputMethodService] 觸發完整 RIME 部署...")
            RimeDaemon.restartRime(fullCheck = true)
        }
        ThemeManager.addOnChangedListener(onThemeChangeListener)
        ColorManager.addOnChangedListener(onColorChangeListener)
        super.onCreate()
        decorView = window.window!!.decorView
        contentView = decorView.findViewById(android.R.id.content)
        // 必須在 Service onCreate() 中用 try..catch 包裝所有程式碼以防止任何崩潰循環
        try {
            Timber.d("建立服務")
            InputFeedbackManager.init(this)
            registerReceiver()
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private fun handleRimeMessage(it: RimeMessage<*>) {
        when (it) {
            is RimeMessage.ResponseMessage ->
                it.data.let event@{
                    val (commit, ctx) = it
                    if (commit.text?.isNotEmpty() == true) {
                        commitText(commit.text)
                        InputFeedbackManager.textCommitSpeak(commit.text)
                    }
                    updateComposingText(ctx)
                    KeyboardSwitcher.currentKeyboardView?.invalidateAllKeys()
                }
            is RimeMessage.KeyMessage ->
                it.data.let event@{
                    val keyCode = it.value.keyCode
                    if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
                        when {
                            !it.modifiers.release && it.value.value > 0 -> {
                                runCatching {
                                    commitText("${Char(it.value.value)}")
                                }
                            }
                            else -> Timber.w("未處理的 Rime 按鍵事件: $it")
                        }
                        return
                    }

                    val eventTime = SystemClock.uptimeMillis()
                    if (it.modifiers.release) {
                        sendUpKeyEvent(eventTime, keyCode, it.modifiers.metaState)
                        return
                    }

                    // 待辦：尋找更好的解決方案
                    if (keyCode == KeyEvent.KEYCODE_ENTER) {
                        handleReturnKey()
                        return
                    }

                    if (keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_EQUALS) {
                        // 忽略 KP_X 鍵，這些由 `CommonKeyboardActionListener` 處理。
                        // 因為 Kotlin 要求，需要這個空主體
                        return
                    }

                    if (it.modifiers.shift) sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_SHIFT_LEFT)
                    sendDownKeyEvent(eventTime, keyCode, it.modifiers.metaState)
                    if (it.modifiers.shift) sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_SHIFT_LEFT)
                    if (it.modifiers.ctrl && keyCode == KeyEvent.KEYCODE_C) clearTextSelection()
                }
            else -> {}
        }
    }

    private fun replaceInputView(theme: Theme): InputView {
        val newInputView = InputView(this, rime, theme)
        setInputView(newInputView)
        inputDeviceManager.setInputView(newInputView)
        navBarManager.setupInputView(newInputView)
        inputView = newInputView
        return newInputView
    }

    private fun replaceCandidateView(theme: Theme): CandidatesView {
        val newCandidatesView = CandidatesView(this, rime, theme)
        contentView.removeView(candidatesView)
        contentView.addView(newCandidatesView)
        inputDeviceManager.setCandidatesView(newCandidatesView)
        navBarManager.setupInputView(newCandidatesView)
        candidatesView = newCandidatesView
        return newCandidatesView
    }

    private fun replaceInputViews(theme: Theme) {
        navBarManager.evaluate(window.window!!)
        replaceInputView(theme)
        replaceCandidateView(theme)
    }

    override fun onDestroy() {
        Timber.w("🔍 [TrimeInputMethodService] onDestroy: 開始銷毀輸入法服務")
        InputFeedbackManager.destroy()
        inputView = null
        recreateInputViewPrefs.forEach {
            it.unregisterOnChangeListener(recreateInputViewListener)
        }
        prefs.candidates.unregisterOnChangeListener(recreateCandidatesViewListener)
        ThemeManager.removeOnChangedListener(onThemeChangeListener)
        ColorManager.removeOnChangedListener(onColorChangeListener)
        super.onDestroy()
        unregisterReceiver(rimeIntentReceiver)
        Timber.w("🔍 [TrimeInputMethodService] onDestroy: 準備銷毀 RIME session")
        RimeDaemon.destroySession(javaClass.name)
        Timber.w("🔍 [TrimeInputMethodService] onDestroy: 輸入法服務銷毀完成")
    }

    private fun handleReturnKey() {
        currentInputEditorInfo.run {
            if (inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                return
            }
            if (imeOptions.hasFlag(EditorInfo.IME_FLAG_NO_ENTER_ACTION)) {
                val ic = currentInputConnection
                ic?.commitText("\n", 1)
                return
            }
            if (!actionLabel.isNullOrEmpty() && actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
                currentInputConnection.performEditorAction(actionId)
                return
            }
            when (val action = imeOptions and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_UNSPECIFIED, EditorInfo.IME_ACTION_NONE -> currentInputConnection.commitText("\n", 1)
                else -> currentInputConnection.performEditorAction(action)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        postRimeJob { clearComposition() }
        ColorManager.onSystemNightModeChange(newConfig.isNightMode())
    }

    override fun onWindowShown() {
        super.onWindowShown()
        // 每次視窗顯示時，導航列前景/背景顏色會重置
        navBarManager.update(window.window!!)
    }

    private val contentSize = floatArrayOf(0f, 0f)
    private val decorLocation = floatArrayOf(0f, 0f)
    private val decorLocationInt = intArrayOf(0, 0)
    private var decorLocationUpdated = false

    private fun updateDecorLocation() {
        contentSize[0] = contentView.width.toFloat()
        contentSize[1] =
            if (inputDeviceManager.isVirtualKeyboard) {
                inputViewLocation[1].toFloat()
            } else {
                contentView.height.toFloat()
            }
        decorView.getLocationOnScreen(decorLocationInt)
        decorLocation[0] = decorLocationInt[0].toFloat()
        decorLocation[1] = decorLocationInt[1].toFloat()
        // contentSize 和 decorLocation 可能完全錯誤，
        // 當在 IMS 生命週期第一次 onStartInputView() 後立即測量時
        if (contentSize[0] > 0 && contentSize[1] > 0) {
            decorLocationUpdated = true
        }
    }

    private val anchorPosition = RectF()

    private fun workaroundNullCursorAnchorInfo() {
        anchorPosition.set(0f, contentSize[1], 0f, contentSize[1])
        candidatesView?.updateCursorAnchor(anchorPosition, contentSize)
    }

    override fun onUpdateCursorAnchorInfo(info: CursorAnchorInfo) {
        val bounds = info.getCharacterBounds(0)
        // 更新錨點位置
        if (bounds == null) {
            // 目標應用程式或 trime 設定中禁用了編輯
            // 改用插入標記的位置
            anchorPosition.top = info.insertionMarkerTop
            anchorPosition.left = info.insertionMarkerHorizontal
            anchorPosition.bottom = info.insertionMarkerBottom
            anchorPosition.right = info.insertionMarkerHorizontal
        } else {
            // 對於不同的書寫系統（例如從右到左的語言），
            // 我們必須計算正確的 RectF
            val horizontal = if (candidatesView?.layoutDirection == View.LAYOUT_DIRECTION_RTL) bounds.right else bounds.left
            anchorPosition.top = bounds.top
            anchorPosition.left = horizontal
            anchorPosition.bottom = bounds.bottom
            anchorPosition.right = horizontal
        }
        if (!decorLocationUpdated) {
            updateDecorLocation()
        }
        if (anchorPosition.any(Float::isNaN)) {
            workaroundNullCursorAnchorInfo()
            return
        }
        info.matrix.mapRect(anchorPosition)
        val (dX, dY) = decorLocation
        anchorPosition.offset(-dX, -dY)
        candidatesView?.updateCursorAnchor(anchorPosition, contentSize)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd,
        )
        if (candidatesEnd != -1 && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            // 移動光標時，更新候選區
            if (newSelEnd in candidatesStart..<candidatesEnd) {
                val newPosition = newSelEnd - candidatesStart
                postRimeJob { moveCursorPos(newPosition) }
            }
        }
        if (candidatesStart == -1 && candidatesEnd == -1 && newSelStart == 0 && newSelEnd == 0) {
            // 上屏後，清除候選區
            postRimeJob { clearComposition() }
        }
        inputView?.updateSelection(newSelStart, newSelEnd)
    }

    private val inputViewLocation = intArrayOf(0, 0)

    override fun onComputeInsets(outInsets: Insets) {
        if (isRoundScreen() && inputDeviceManager.isVirtualKeyboard) {
            // 圓形螢幕特殊處理：強制全螢幕
            outInsets.apply {
                contentTopInsets = 0 // 佔用整個螢幕
                visibleTopInsets = 0 // 可見區域從頂部開始
                touchableInsets = Insets.TOUCHABLE_INSETS_FRAME // 整個框架可觸摸
            }
        } else if (inputDeviceManager.isVirtualKeyboard) {
            outInsets.apply {
                contentTopInsets = inputViewLocation[1]
                visibleTopInsets = inputViewLocation[1]
                touchableInsets = Insets.TOUCHABLE_INSETS_VISIBLE
            }
        } else {
            val n = decorView.findViewById<View>(android.R.id.navigationBarBackground)?.height ?: 0
            val h = decorView.height - n
            outInsets.apply {
                contentTopInsets = h
                visibleTopInsets = h
                touchableInsets = Insets.TOUCHABLE_INSETS_VISIBLE
            }
        }
    }

    private fun isRoundScreen(): Boolean {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        return (screenWidth in 300..500) &&
            (screenHeight in 300..500) &&
            kotlin.math.abs(screenWidth - screenHeight) < 50
    }

    private fun applyFullScreenHeight() {
        try {
            val window = window.window
            if (window != null && isRoundScreen()) {
                // 允許窗口超出系統限制
                window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

                val layoutParams = window.attributes
                layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
                layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT

                // 移除系統窗口裝飾
                layoutParams.flags = layoutParams.flags or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

                window.attributes = layoutParams
            }
        } catch (e: Exception) {
            Timber.w(e, "無法套用全螢幕高度")
        }
    }

    // 總是顯示 InputView，專為手錶裝置優化
    @SuppressLint("MissingSuperCall")
    override fun onEvaluateInputViewShown() = true

    fun superEvaluateInputViewShown() = super.onEvaluateInputViewShown()

    override fun onCreateInputView(): View? {
        Timber.d("建立輸入視窗")
        replaceInputViews(ThemeManager.activeTheme)
        inputView?.keyboardWindow?.initializeDrawingState()
        // 我們會自己呼叫 `setInputView`。這沒問題。
        return null
    }

    override fun setInputView(view: View) {
        super.setInputView(view)
        val inputArea = contentView.findViewById<FrameLayout>(android.R.id.inputArea)
        inputArea.updateLayoutParams<ViewGroup.LayoutParams> {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        view.updateLayoutParams<ViewGroup.LayoutParams> {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
    }

    override fun onConfigureWindow(
        win: Window,
        isFullscreen: Boolean,
        isCandidatesOnly: Boolean,
    ) {
        win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onStartInput(
        attribute: EditorInfo,
        restarting: Boolean,
    ) {
        Timber.d("開始輸入: 重新啟動=$restarting")
        postRimeJob {
            if (restarting) {
                // 當在同一編輯器中重新開始輸入時，清除先前的組合
                clearComposition()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        // Suggestion module removed for watch device optimization
        return null
    }

    @SuppressLint("NewApi")
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        if (!inputDeviceManager.isVirtualKeyboard) return false
        return inputView?.handleInlineSuggestions(response) == true
    }

    private val candidatesMode by AppPrefs.defaultInstance().candidates.mode

    override fun onStartInputView(
        attribute: EditorInfo,
        restarting: Boolean,
    ) {
        Timber.d("開始輸入視窗: 重新啟動=$restarting")

        // 圓形螢幕全螢幕配置
        applyFullScreenHeight()

        InputFeedbackManager.startInput()
        postRimeJob {
            updateRimeOption(this)
            ContextCompat.getMainExecutor(this@TrimeInputMethodService).execute {
                val useVirtualKeyboard =
                    inputDeviceManager.evaluateOnStartInputView(attribute, this@TrimeInputMethodService)
                if (useVirtualKeyboard) {
                    inputView?.startInput(attribute, restarting)
                }
                if (!useVirtualKeyboard) {
                    if (currentInputConnection?.monitorCursorAnchor() != true) {
                        if (!decorLocationUpdated) {
                            updateDecorLocation()
                        }
                        workaroundNullCursorAnchorInfo()
                    }
                }
            }
            when (attribute.inputType and InputType.TYPE_MASK_VARIATION) {
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                -> {
                    Timber.d(
                        "EditorInfo: private;" +
                            " packageName=" +
                            attribute.packageName +
                            "; fieldName=" +
                            attribute.fieldName +
                            "; actionLabel=" +
                            attribute.actionLabel +
                            "; inputType=" +
                            attribute.inputType +
                            "; VARIATION=" +
                            (attribute.inputType and InputType.TYPE_MASK_VARIATION) +
                            "; CLASS=" +
                            (attribute.inputType and InputType.TYPE_MASK_CLASS) +
                            "; ACTION=" +
                            (attribute.imeOptions and EditorInfo.IME_MASK_ACTION),
                    )
                    normalTextEditor = false
                }

                else -> {
                    Timber.d(
                        "EditorInfo: normal;" +
                            " packageName=" +
                            attribute.packageName +
                            "; fieldName=" +
                            attribute.fieldName +
                            "; actionLabel=" +
                            attribute.actionLabel +
                            "; inputType=" +
                            attribute.inputType +
                            "; VARIATION=" +
                            (attribute.inputType and InputType.TYPE_MASK_VARIATION) +
                            "; CLASS=" +
                            (attribute.inputType and InputType.TYPE_MASK_CLASS) +
                            "; ACTION=" +
                            (attribute.imeOptions and EditorInfo.IME_MASK_ACTION),
                    )
                    if (attribute.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                        == EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                    ) {
                        // 應用程式要求以隱身模式開啟鍵盤應用程式
                        normalTextEditor = false
                        Timber.d("編輯器資訊: 一般 -> 隱私模式, IME_FLAG_NO_PERSONALIZED_LEARNING")
                    } else if (attribute.packageName == BuildConfig.APPLICATION_ID) {
                        normalTextEditor = false
                        Timber.d("編輯器資訊: 一般 -> 排除, 套件名稱=%s", attribute.packageName)
                    } else {
                        normalTextEditor = true
                    }
                }
            }
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        Timber.d("結束輸入視窗: 完成輸入=$finishingInput")
        decorLocationUpdated = false
        inputDeviceManager.onFinishInputView()
        currentInputConnection?.apply {
            if (normalTextEditor) {
            }
            finishComposingText()
            monitorCursorAnchor(false)
        }
        postRimeJob {
            clearComposition()
        }
        InputFeedbackManager.finishInput()
    }

    // 直接 commit 不做任何處理
    fun commitText(
        text: CharSequence,
        clearMeatKeyState: Boolean = false,
    ) {
        val ic = currentInputConnection ?: return
        if (ic.commitText(text, 1)) {
            lastCommittedText = text
        }
        if (clearMeatKeyState) {
            ic.clearMetaKeyStates(KeyEvent.getModifierMetaStateMask())
        }
    }

    /**
     * 建構一個 meta state 整數標誌，可用於在向輸入連接傳送 KeyEvent 時設定 `metaState` 欄位。
     * 如果呼叫此方法時沒有將 meta 修飾鍵設為 true，則回傳預設值 `0`。
     *
     * @param ctrl 設為 true 以啟用 CTRL meta 修飾鍵。預設為 false。
     * @param alt 設為 true 以啟用 ALT meta 修飾鍵。預設為 false。
     * @param shift 設為 true 以啟用 SHIFT meta 修飾鍵。預設為 false。
     *
     * @return 包含所有傳遞的 meta 標誌並格式化以用於 [KeyEvent] 的整數。
     */
    fun meta(
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
        meta: Boolean = false,
        sym: Boolean = false,
    ): Int {
        var metaState = 0
        if (ctrl) {
            metaState = metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        }
        if (alt) {
            metaState = metaState or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        }
        if (shift) {
            metaState = metaState or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        }
        if (meta) {
            metaState = metaState or KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
        }
        if (sym) {
            metaState = metaState or KeyEvent.META_SYM_ON
        }

        return metaState
    }

    private fun sendDownKeyEvent(
        eventTime: Long,
        keyEventCode: Int,
        metaState: Int = 0,
    ): Boolean {
        val ic = currentInputConnection ?: return false
        return ic.sendKeyEvent(
            KeyEvent(
                eventTime,
                eventTime,
                KeyEvent.ACTION_DOWN,
                keyEventCode,
                0,
                metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
                InputDevice.SOURCE_KEYBOARD,
            ),
        )
    }

    private fun sendUpKeyEvent(
        eventTime: Long,
        keyEventCode: Int,
        metaState: Int = 0,
    ): Boolean {
        val ic = currentInputConnection ?: return false
        return ic.sendKeyEvent(
            KeyEvent(
                eventTime,
                SystemClock.uptimeMillis(),
                KeyEvent.ACTION_UP,
                keyEventCode,
                0,
                metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
                InputDevice.SOURCE_KEYBOARD,
            ),
        )
    }

    /**
     * 與 [InputMethodService.sendDownUpKeyEvents] 相同，但也允許設定 meta state。
     *
     * @param keyEventCode 要傳送的按鍵代碼，使用 Android [KeyEvent] 中定義的按鍵代碼。
     * @param metaState 指示目前按下哪些 meta 鍵的標誌。
     * @param count 在傳遞的 meta 鍵按下時按鍵被按下的次數。必須大於或等於
     *  `1`，否則此方法將立即回傳 false。
     *
     * @return 成功時回傳 true，如果發生錯誤或輸入連接無效則回傳 false。
     */
    fun sendDownUpKeyEvent(
        keyEventCode: Int,
        metaState: Int = meta(),
        count: Int = 1,
    ): Boolean {
        if (count < 1) return false
        val ic = currentInputConnection ?: return false
        ic.clearMetaKeyStates(
            KeyEvent.META_FUNCTION_ON
                or KeyEvent.META_SHIFT_MASK
                or KeyEvent.META_ALT_MASK
                or KeyEvent.META_CTRL_MASK
                or KeyEvent.META_META_MASK
                or KeyEvent.META_SYM_ON,
        )
        ic.beginBatchEdit()
        val eventTime = SystemClock.uptimeMillis()
        if (metaState and KeyEvent.META_CTRL_ON != 0) {
            sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_CTRL_LEFT)
        }
        if (metaState and KeyEvent.META_ALT_ON != 0) {
            sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_ALT_LEFT)
        }
        if (metaState and KeyEvent.META_SHIFT_ON != 0) {
            sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_SHIFT_LEFT)
        }
        if (metaState and KeyEvent.META_META_ON != 0) {
            sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_META_LEFT)
        }

        if (metaState and KeyEvent.META_SYM_ON != 0) {
            sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_SYM)
        }

        for (n in 0 until count) {
            sendDownKeyEvent(eventTime, keyEventCode, metaState)
            sendUpKeyEvent(eventTime, keyEventCode, metaState)
        }
        if (metaState and KeyEvent.META_SHIFT_ON != 0) {
            sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_SHIFT_LEFT)
        }
        if (metaState and KeyEvent.META_ALT_ON != 0) {
            sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_ALT_LEFT)
        }
        if (metaState and KeyEvent.META_CTRL_ON != 0) {
            sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_CTRL_LEFT)
        }

        if (metaState and KeyEvent.META_META_ON != 0) {
            sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_META_LEFT)
        }

        if (metaState and KeyEvent.META_SYM_ON != 0) {
            sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_SYM)
        }

        ic.endBatchEdit()
        return true
    }

    private fun forwardKeyEvent(event: KeyEvent): Boolean {
        val modifiers = KeyModifiers.fromKeyEvent(event)
        val charCode = event.unicodeChar
        if (charCode > 0 && charCode != '\t'.code && charCode != '\n'.code) {
            postRimeJob {
                processKey(charCode, modifiers.modifiers)
            }
            return true
        }
        val keyVal = KeyValue.fromKeyEvent(event)
        if (keyVal.value != RimeKeyMapping.RimeKey_VoidSymbol) {
            postRimeJob {
                processKey(keyVal, modifiers)
            }
            return true
        }
        Timber.d("跳過按鍵事件: $event")
        return false
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        // 圓形螢幕手錶：實體 BACK 鍵（此手錶的 Return 鍵硬體映射為 BACK）觸發 T9 確認
        if (keyCode == KeyEvent.KEYCODE_BACK && isInputViewShown && isRoundScreen()) {
            inputView?.getT9InputContainer()?.getEventHandler()?.onConfirmPress()
            return true
        }
        if (inputDeviceManager.evaluateOnKeyDown(event, this)) {
            decorLocationUpdated = false
            forceShowSelf()
        }
        return forwardKeyEvent(event) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean = forwardKeyEvent(event) || super.onKeyUp(keyCode, event)

    // 在 API level 14 中新增，在 29 中棄用
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onViewClicked(focusChanged: Boolean) {
        super.onViewClicked(focusChanged)
        if (Build.VERSION.SDK_INT < 34) {
            decorLocationUpdated = false
            inputDeviceManager.evaluateOnViewClicked(this)
        }
    }

    @TargetApi(34)
    override fun onUpdateEditorToolType(toolType: Int) {
        super.onUpdateEditorToolType(toolType)
        decorLocationUpdated = false
        inputDeviceManager.evaluateOnUpdateEditorToolType(toolType, this)
    }

    fun switchToPrevIme() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                switchToPreviousInputMethod()
            } else {
                @Suppress("DEPRECATION")
                inputMethodManager.switchToLastInputMethod(window.window!!.attributes.token)
            }
        } catch (e: Exception) {
            Timber.e(e, "無法切換到上一個輸入法。")
            inputMethodManager.showInputMethodPicker()
        }
    }

    fun switchToNextIme() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                switchToNextInputMethod(false)
            } else {
                @Suppress("DEPRECATION")
                inputMethodManager.switchToNextInputMethod(window.window!!.attributes.token, false)
            }
        } catch (e: Exception) {
            Timber.e(e, "無法切換到下一個輸入法。")
            inputMethodManager.showInputMethodPicker()
        }
    }

    fun shareText(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val ic = currentInputConnection ?: return false
            val cs = ic.getSelectedText(0)
            if (cs == null) ic.performContextMenuAction(android.R.id.selectAll)
            return ic.performContextMenuAction(android.R.id.shareText)
        }
        return false
    }

    /** 編輯操作 */
    fun hookKeyboard(
        code: Int,
        mask: Int,
    ): Boolean {
        val ic = currentInputConnection ?: return false
        // 沒按下 Ctrl 鍵
        if (mask != KeyEvent.META_CTRL_ON) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (prefs.keyboard.hookCtrlZY.getValue()) {
                when (code) {
                    KeyEvent.KEYCODE_Y -> return ic.performContextMenuAction(android.R.id.redo)
                    KeyEvent.KEYCODE_Z -> return ic.performContextMenuAction(android.R.id.undo)
                }
            }
        }

        when (code) {
            KeyEvent.KEYCODE_A -> {
                // 全選
                return if (prefs.keyboard.hookCtrlA.getValue()) {
                    ic.performContextMenuAction(android.R.id.selectAll)
                } else {
                    false
                }
            }

            KeyEvent.KEYCODE_X -> {
                // 剪下
                if (prefs.keyboard.hookCtrlCV.getValue()) {
                    val etr = ExtractedTextRequest()
                    etr.token = 0
                    val et = ic.getExtractedText(etr, 0)
                    if (et != null) {
                        if (et.selectionStart != et.selectionEnd) return ic.performContextMenuAction(android.R.id.cut)
                    }
                }
                Timber.w("鍵盤鉤子剪下失敗")
                return false
            }

            KeyEvent.KEYCODE_C -> {
                // 複製
                if (prefs.keyboard.hookCtrlCV.getValue()) {
                    val etr = ExtractedTextRequest()
                    etr.token = 0
                    val et = ic.getExtractedText(etr, 0)
                    if (et != null) {
                        if (et.selectionStart != et.selectionEnd) {
                            ic.performContextMenuAction(android.R.id.copy).also { result ->
                                if (result) {
                                    clearTextSelection()
                                }
                                return result
                            }
                        }
                    }
                }
                Timber.w("鍵盤鉤子複製失敗")
                return false
            }

            KeyEvent.KEYCODE_V -> {
                // 貼上
                if (prefs.keyboard.hookCtrlCV.getValue()) {
                    val etr = ExtractedTextRequest()
                    etr.token = 0
                    val et = ic.getExtractedText(etr, 0)
                    if (et == null) {
                        Timber.d("鍵盤鉤子貼上, et == null, 嘗試 commitText")
                        return false
                    } else if (ic.performContextMenuAction(android.R.id.paste)) {
                        return true
                    }
                    Timber.w("鍵盤鉤子貼上失敗")
                }
                return false
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (prefs.keyboard.hookCtrlLR.getValue()) {
                    val etr = ExtractedTextRequest()
                    etr.token = 0
                    val et = ic.getExtractedText(etr, 0)
                    if (et != null) {
                        val moveTo = et.text.findSectionFrom(et.startOffset + et.selectionEnd)
                        ic.setSelection(moveTo, moveTo)
                        return true
                    }
                }
            }

            KeyEvent.KEYCODE_DPAD_LEFT ->
                if (prefs.keyboard.hookCtrlLR.getValue()) {
                    val etr = ExtractedTextRequest()
                    etr.token = 0
                    val et = ic.getExtractedText(etr, 0)
                    if (et != null) {
                        val moveTo = et.text.findSectionFrom(et.startOffset + et.selectionStart, true)
                        ic.setSelection(moveTo, moveTo)
                        return true
                    }
                }
        }
        return false
    }

    fun clearTextSelection() {
        val ic = currentInputConnection ?: return
        val etr = ExtractedTextRequest().apply { token = 0 }
        val et = currentInputConnection.getExtractedText(etr, 0)
        et?.let {
            if (it.selectionStart != it.selectionEnd) {
                ic.setSelection(it.selectionEnd, it.selectionEnd)
            }
        }
    }

    private val composingTextMode by prefs.general.composingTextMode

    private fun updateComposingText(ctx: RimeProto.Context) {
        val ic = currentInputConnection ?: return
        val text =
            when (composingTextMode) {
                ComposingTextMode.DISABLE -> ""
                ComposingTextMode.PREEDIT -> ctx.composition.preedit ?: ""
                ComposingTextMode.COMMIT_TEXT_PREVIEW -> ctx.composition.commitTextPreview ?: ""
                ComposingTextMode.RAW_INPUT -> ctx.input
            }
        if (ic.getSelectedText(0).isNullOrEmpty() || text.isNotEmpty()) {
            ic.setComposingText(text, 1)
        }
    }

    fun getActiveText(type: Int): String {
        if (type == 2) return rime.run { rawInputCached } // 當前編碼
        var text: CharSequence? = rime.run { compositionCached }.commitTextPreview // 當前候選
        if (text.isNullOrEmpty()) {
            val info = currentInputEditorInfo
            text = EditorInfoCompat.getInitialSelectedText(info, 0) // 選中字
        }
        if (text.isNullOrEmpty()) {
            if (type == 1) text = lastCommittedText // 剛上屏字
        }
        if (text.isNullOrEmpty()) {
            val step = if (type == 4) 1024 else 1
            text = getTextAroundCursor(step, before = true)
        }
        if (text.isNullOrEmpty()) {
            text = getTextAroundCursor(before = false)
        }
        return text.toString()
    }

    private fun getTextAroundCursor(
        initialStep: Int = 1024,
        before: Boolean,
    ): String? {
        val info = currentInputEditorInfo ?: return null
        var step = initialStep
        while (true) {
            val text =
                if (before) {
                    EditorInfoCompat.getInitialTextBeforeCursor(info, step, 0)
                } else {
                    EditorInfoCompat.getInitialTextAfterCursor(info, step, 0)
                } ?: return null
            if (text.length < step) {
                return text.toString()
            }
            step *= 2
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    private var showingDialog: Dialog? = null

    fun showDialog(dialog: Dialog) {
        showingDialog?.dismiss()
        dialog.window?.also {
            it.attributes.apply {
                token = decorView.windowToken
                type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
            }
            it.addFlags(
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            )
            it.setDimAmount(styledFloat(android.R.attr.backgroundDimAmount))
        }
        dialog.setOnDismissListener {
            showingDialog = null
        }
        dialog.show()
        showingDialog = dialog
    }

    companion object {
        /** 用於分割語言/地區標籤的分隔符號正規表示式。 */
        private val DELIMITER_SPLITTER = """[-_]""".toRegex()
    }
}
