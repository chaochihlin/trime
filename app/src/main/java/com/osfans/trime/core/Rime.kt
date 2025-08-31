// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.core

import com.osfans.trime.BuildConfig
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.schema.SchemaManager
import com.osfans.trime.util.appContext
import com.osfans.trime.util.isAsciiPrintable
import com.osfans.trime.util.isStorageAvailable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Rime JNI and instance methods
 *
 * @see [librime](https://github.com/rime/librime)
 */
class Rime :
    RimeApi,
    RimeLifecycleOwner {
    private val lifecycleImpl = RimeLifecycleImpl()
    override val lifecycle get() = lifecycleImpl

    override val messageFlow = messageFlow_.asSharedFlow()

    override val stateFlow get() = lifecycle.currentStateFlow

    override val isReady: Boolean
        get() = lifecycle.currentStateFlow.value == RimeLifecycle.State.READY

    override var statusCached = RimeProto.Status()
        private set

    override var compositionCached = RimeProto.Context.Composition()
        private set

    override var menuCached = RimeProto.Context.Menu()
        private set

    override var rawInputCached = ""
        private set

    private val dispatcher =
        RimeDispatcher(
            object : RimeDispatcher.RimeLooper {
                override fun nativeStartup(fullCheck: Boolean) {
                    DataManager.sync()

                    val sharedDataDir = DataManager.sharedDataDir.absolutePath
                    val userDataDir = DataManager.userDataDir.absolutePath
                    Timber.d(
                        """
                        Starting rime with:
                        sharedDataDir: $sharedDataDir
                        userDataDir: $userDataDir
                        fullCheck: $fullCheck
                        """.trimIndent(),
                    )
                    startupRime(sharedDataDir, userDataDir, BuildConfig.BUILD_VERSION_NAME, fullCheck)

                    lifecycleImpl.emitState(RimeLifecycle.State.READY)

                    requireResponse()

                    // 檢查初始化後的方案狀態
                    val currentSchema = getCurrentRimeSchema()
                    val availableSchemas = getAvailableRimeSchemaList()
                    val selectedSchemas = getSelectedRimeSchemaList()

                    Timber.i("Rime startup complete:")
                    Timber.i("  Current schema: $currentSchema")
                    Timber.i("  Available schemas: ${availableSchemas.map { "${it.id}:${it.name}" }}")
                    Timber.i("  Selected schemas: ${selectedSchemas.map { "${it.id}:${it.name}" }}")

                    SchemaManager.init(currentSchema)
                }

                override fun nativeFinalize() {
                    exitRime()
                }
            },
        )

    private suspend inline fun <T> withRimeContext(crossinline block: suspend () -> T): T =
        withContext(dispatcher) {
            block()
        }

    override suspend fun isEmpty(): Boolean =
        withRimeContext {
            val currentSchema = getCurrentRimeSchema()
            val selectedSchemas = getRimeSchemaList()
            val availableSchemas = getAvailableRimeSchemaList()
            val enabledSchemas = getSelectedRimeSchemaList()

            Timber.d("Rime.isEmpty: Current schema: $currentSchema")
            Timber.d("Rime.isEmpty: Selected schemas count: ${selectedSchemas.size}")
            Timber.d("Rime.isEmpty: Available schemas count: ${availableSchemas.size}")
            Timber.d("Rime.isEmpty: Enabled schemas count: ${enabledSchemas.size}")

            selectedSchemas.forEach { schema ->
                Timber.d("Rime.isEmpty: Selected schema - ID: ${schema.id}, Name: ${schema.name}")
            }

            availableSchemas.forEach { schema ->
                Timber.d("Rime.isEmpty: Available schema - ID: ${schema.id}, Name: ${schema.name}")
            }

            enabledSchemas.forEach { schema ->
                Timber.d("Rime.isEmpty: Enabled schema - ID: ${schema.id}, Name: ${schema.name}")
            }

            val isEmpty = currentSchema == ".default" || selectedSchemas.isEmpty()
            Timber.d("Rime.isEmpty: Result: $isEmpty")
            isEmpty
        }

    override suspend fun syncUserData(): Boolean =
        withRimeContext {
            syncRimeUserData()
        }

    override suspend fun processKey(
        value: Int,
        modifiers: UInt,
    ): Boolean =
        withRimeContext {
            Timber.d(
                "【除錯】processKey: value=0x${value.toString(
                    16,
                )} ('${if (value in 32..126) Char(value) else "非ASCII"}'), modifiers=$modifiers",
            )

            // 詳細診斷 RIME 引擎狀態
            val preStatus = getRimeStatus()
            val preContext = getRimeContext()
            Timber.d("【除錯】processKey前: composing=${preStatus?.isComposing}, input='${preContext?.input}'")

            processRimeKey(value, modifiers.toInt()).also {
                Timber.d("【除錯】processRimeKey結果: $it")
                if (it) {
                    Timber.d("【除錯】呼叫 requireResponse()")
                    requireResponse()
                } else {
                    val status = getRimeStatus()
                    Timber.d(
                        "【除錯】按鍵被拒絕時的RIME狀態: schema='${status?.schemaId}', disabled=${status?.isDisabled}, ascii_mode=${status?.isAsciiMode}",
                    )
                    Timber.d("【除錯】呼叫 requireKeyMessage()")
                    requireKeyMessage(value, modifiers.toInt())
                }
            }
        }

    override suspend fun processKey(
        value: KeyValue,
        modifiers: KeyModifiers,
    ): Boolean =
        withRimeContext {
            Timber.d("【除錯】processKey(KeyValue): value=${value.value} (0x${value.value.toString(16)}), modifiers=$modifiers")
            processRimeKey(value.value, modifiers.toInt()).also {
                Timber.d("【除錯】processRimeKey結果: $it")
                if (it) {
                    Timber.d("【除錯】呼叫 requireResponse()")
                    requireResponse()
                } else {
                    val status = getRimeStatus()
                    Timber.d(
                        "【除錯】按鍵被拒絕時的RIME狀態: schema='${status?.schemaId}', disabled=${status?.isDisabled}, ascii_mode=${status?.isAsciiMode}",
                    )
                    Timber.d("【除錯】呼叫 requireKeyMessage()")
                    requireKeyMessage(value.value, modifiers.toInt())
                }
            }
        }

    override suspend fun selectCandidate(idx: Int): Boolean =
        withRimeContext {
            selectRimeCandidate(idx).also { if (it) requireResponse() }
        }

    override suspend fun forgetCandidate(idx: Int): Boolean =
        withRimeContext {
            forgetRimeCandidate(idx).also { if (it) requireResponse() }
        }

    override suspend fun selectPagedCandidate(idx: Int): Boolean =
        withRimeContext {
            selectRimeCandidateOnCurrentPage(idx).also { if (it) requireResponse() }
        }

    override suspend fun deletedPagedCandidate(idx: Int): Boolean =
        withRimeContext {
            deleteRimeCandidateOnCurrentPage(idx).also { if (it) requireResponse() }
        }

    override suspend fun changeCandidatePage(backward: Boolean): Boolean =
        withRimeContext {
            changeRimeCandidatePage(backward).also { if (it) requireResponse() }
        }

    override suspend fun moveCursorPos(position: Int) =
        withRimeContext {
            setRimeCaretPos(position)
            requireResponse()
        }

    override suspend fun availableSchemata(): Array<SchemaItem> = withRimeContext { getAvailableRimeSchemaList() }

    override suspend fun enabledSchemata(): Array<SchemaItem> = withRimeContext { getSelectedRimeSchemaList() }

    override suspend fun setEnabledSchemata(schemaIds: Array<String>) = withRimeContext { selectRimeSchemas(schemaIds) }

    override suspend fun selectedSchemata(): Array<SchemaItem> = withRimeContext { getRimeSchemaList() }

    override suspend fun selectedSchemaId(): String = withRimeContext { getCurrentRimeSchema() }

    override suspend fun selectSchema(schemaId: String) =
        withRimeContext {
            val result = selectRimeSchema(schemaId)
            if (result) {
                requireResponse()
                SchemaManager.init(schemaId)
            }
            result
        }

    override suspend fun commitComposition(): Boolean = withRimeContext { commitRimeComposition().also { if (it) requireResponse() } }

    override suspend fun clearComposition() =
        withRimeContext {
            clearRimeComposition()
            requireResponse()
        }

    override suspend fun setRuntimeOption(
        option: String,
        value: Boolean,
    ): Unit =
        withRimeContext {
            setRimeOption(option, value)
        }

    override suspend fun getRuntimeOption(option: String): Boolean =
        withRimeContext {
            getRimeOption(option)
        }

    override suspend fun getCandidates(
        startIndex: Int,
        limit: Int,
    ): Array<CandidateItem> =
        withRimeContext {
            getRimeCandidates(startIndex, limit)
        }

    private fun handleRimeMessage(it: RimeMessage<*>) {
        when (it) {
            is RimeMessage.SchemaMessage -> {
                getRimeStatus()?.let { statusCached = it }
                SchemaManager.init(it.data.id)
            }
            is RimeMessage.OptionMessage -> {
                getRimeStatus()?.let { statusCached = it }
                SchemaManager.updateSwitchOptions()
            }
            is RimeMessage.ResponseMessage ->
                it.data.let event@{ data ->
                    statusCached = data.status
                    compositionCached = data.context.composition
                    menuCached = data.context.menu
                    rawInputCached = data.context.input
                }
            else -> {}
        }
    }

    fun startup(fullCheck: Boolean) {
        if (lifecycle.currentStateFlow.value != RimeLifecycle.State.STOPPED) {
            Timber.w("Skip starting rime: not at stopped state!")
            return
        }
        if (appContext.isStorageAvailable()) {
            registerRimeMessageHandler(::handleRimeMessage)
            lifecycleImpl.emitState(RimeLifecycle.State.STARTING)
            dispatcher.start(fullCheck)
        }
    }

    fun finalize() {
        if (lifecycle.currentStateFlow.value != RimeLifecycle.State.READY) {
            Timber.w("Skip stopping rime: not at ready state!")
            return
        }
        lifecycleImpl.emitState(RimeLifecycle.State.STOPPING)
        Timber.i("Rime finalize()")
        dispatcher.stop().let {
            if (it.isNotEmpty()) {
                Timber.w("${it.size} job(s) didn't get a chance to run!")
            }
        }
        lifecycleImpl.emitState(RimeLifecycle.State.STOPPED)
        unregisterRimeMessageHandler(::handleRimeMessage)
    }

    companion object {
        private val messageFlow_ =
            MutableSharedFlow<RimeMessage<*>>(
                extraBufferCapacity = 15,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )

        private val rimeMessageHandlers = ArrayList<(RimeMessage<*>) -> Unit>()

        init {
            System.loadLibrary("rime_jni")
        }

        @JvmStatic
        fun simulateKeySequence(sequence: CharSequence): Boolean {
            if (!sequence.first().isAsciiPrintable()) return false
            Timber.d("【除錯】simulateKeySequence: '$sequence'")

            // 詳細診斷 RIME 引擎狀態
            val preStatus = getRimeStatus()
            val preContext = getRimeContext()
            Timber.d(
                "【除錯】simulateKeySequence前: schema='${preStatus?.schemaId}', disabled=${preStatus?.isDisabled}, ascii_mode=${preStatus?.isAsciiMode}",
            )
            Timber.d("【除錯】simulateKeySequence前: composing=${preStatus?.isComposing}, input='${preContext?.input}'")

            // 簡單的 RIME 健康檢查: 測試單個ASCII字符
            if (sequence.length == 1 && sequence.first().isLetter()) {
                Timber.d("【除錯】執行RIME健康檢查: 測試字符'a'")
                val healthTestResult = simulateRimeKeySequence("a")
                val healthContext = getRimeContext()
                clearRimeComposition() // 清除測試輸入
                Timber.d(
                    "【除錯】RIME健康檢查結果: $healthTestResult, input='${healthContext?.input}', preedit='${healthContext?.composition?.preedit}'",
                )

                if (!healthTestResult && healthContext?.input.isNullOrEmpty()) {
                    Timber.e("【除錯】RIME引擎健康檢查失敗! 引擎可能無法接受任何輸入")
                }
            }

            val simulateResult =
                simulateRimeKeySequence(
                    sequence.toString().replace("{}", "{braceleft}{braceright}"),
                )
            Timber.d("【除錯】simulateRimeKeySequence結果: $simulateResult")

            val commit = getRimeCommit()
            val ctx = getRimeContext()

            Timber.d(
                "【除錯】simulateKeySequence後: commit='${commit?.text}', ctx.input='${ctx?.input}', ctx.preedit='${ctx?.composition?.preedit}'",
            )

            // 詳細失敗分析
            val hasCommitText = !commit?.text.isNullOrEmpty()
            val hasContextInput = !ctx?.input.isNullOrEmpty()
            val hasCompositionPreedit = !ctx?.composition?.preedit.isNullOrEmpty()

            Timber.d("【除錯】simulateKeySequence詳細分析:")
            Timber.d("  simulateResult (JNI返回): $simulateResult")
            Timber.d("  hasCommitText: $hasCommitText ('${commit?.text}')")
            Timber.d("  hasContextInput: $hasContextInput ('${ctx?.input}')")
            Timber.d("  hasCompositionPreedit: $hasCompositionPreedit ('${ctx?.composition?.preedit}')")

            if (!simulateResult) {
                Timber.w("【除錯】JNI層simulateRimeKeySequence返回失敗!")

                // 檢查可能的失敗原因
                if (preStatus?.isDisabled == true) {
                    Timber.w("【除錯】可能原因: RIME引擎已禁用")
                }
                if (preStatus?.isAsciiMode == true) {
                    Timber.w("【除錯】可能原因: RIME處於ASCII模式")
                }
                if (preStatus?.schemaId.isNullOrEmpty()) {
                    Timber.w("【除錯】可能原因: 沒有選中的輸入方案")
                }

                return false
            }

            return (simulateResult && (hasCommitText || hasContextInput)).also {
                Timber.d("【除錯】simulateKeySequence最終結果: ${if (it) "success" else "failed"}")
                if (it) {
                    handleRimeMessage(
                        4, // RimeMessage.MessageType.Response
                        arrayOf(
                            commit ?: RimeProto.Commit(null),
                            ctx ?: return false,
                            getRimeStatus() ?: return false,
                        ),
                    )
                }
            }
        }

        // init
        @JvmStatic
        external fun startupRime(
            sharedDir: String,
            userDir: String,
            versionName: String,
            fullCheck: Boolean,
        )

        @JvmStatic
        external fun exitRime()

        @JvmStatic
        external fun deployRimeSchemaFile(schemaFile: String): Boolean

        @JvmStatic
        external fun deployRimeConfigFile(
            fileName: String,
            versionKey: String,
        ): Boolean

        @JvmStatic
        external fun syncRimeUserData(): Boolean

        // input
        @JvmStatic
        external fun processRimeKey(
            keycode: Int,
            mask: Int,
        ): Boolean

        @JvmStatic
        external fun commitRimeComposition(): Boolean

        @JvmStatic
        external fun clearRimeComposition()

        // output
        @JvmStatic
        external fun getRimeCommit(): RimeProto.Commit?

        @JvmStatic
        external fun getRimeContext(): RimeProto.Context?

        @JvmStatic
        external fun getRimeStatus(): RimeProto.Status?

        // runtime options
        @JvmStatic
        external fun setRimeOption(
            option: String,
            value: Boolean,
        )

        @JvmStatic
        external fun getRimeOption(option: String): Boolean

        @JvmStatic
        external fun getRimeSchemaList(): Array<SchemaItem>

        @JvmStatic
        external fun getCurrentRimeSchema(): String

        @JvmStatic
        external fun selectRimeSchema(schemaId: String): Boolean

        // testing
        @JvmStatic
        external fun simulateRimeKeySequence(keySequence: String): Boolean

        @JvmStatic
        external fun getRimeRawInput(): String?

        @JvmStatic
        external fun getRimeCaretPos(): Int

        @JvmStatic
        external fun setRimeCaretPos(caretPos: Int)

        @JvmStatic
        external fun selectRimeCandidateOnCurrentPage(index: Int): Boolean

        @JvmStatic
        external fun deleteRimeCandidateOnCurrentPage(index: Int): Boolean

        @JvmStatic
        external fun selectRimeCandidate(index: Int): Boolean

        @JvmStatic
        external fun forgetRimeCandidate(index: Int): Boolean

        @JvmStatic
        external fun changeRimeCandidatePage(backward: Boolean): Boolean

        @JvmStatic
        external fun getAvailableRimeSchemaList(): Array<SchemaItem>

        @JvmStatic
        external fun getSelectedRimeSchemaList(): Array<SchemaItem>

        @JvmStatic
        external fun selectRimeSchemas(schemaIds: Array<String>): Boolean

        @JvmStatic
        external fun getRimeCandidates(
            startIndex: Int,
            limit: Int,
        ): Array<CandidateItem>

        @JvmStatic
        fun handleRimeMessage(
            type: Int,
            params: Array<Any>,
        ) {
            val message = RimeMessage.nativeCreate(type, params)
            Timber.d("Handling $message")
            rimeMessageHandlers.forEach { it.invoke(message) }
            messageFlow_.tryEmit(message)
        }

        private fun requireResponse() {
            val commit = getRimeCommit() ?: RimeProto.Commit(null)
            val context = getRimeContext() ?: return
            val status = getRimeStatus() ?: return

            Timber.d(
                "【除錯】requireResponse: commit='${commit.text}', context.preedit='${context.composition.preedit}', context.input='${context.input}'",
            )
            Timber.d("【除錯】RIME狀態: schema='${status.schemaId}', disabled=${status.isDisabled}, composing=${status.isComposing}")

            handleRimeMessage(
                4, // RimeMessage.MessageType.Response
                arrayOf(commit, context, status),
            )
        }

        private fun requireKeyMessage(
            value: Int,
            modifiers: Int,
        ) {
            handleRimeMessage(
                5, // RimeMessage.MessageType.Key,
                arrayOf(value, modifiers),
            )
        }

        private fun registerRimeMessageHandler(handler: (RimeMessage<*>) -> Unit) {
            if (rimeMessageHandlers.contains(handler)) return
            rimeMessageHandlers.add(handler)
        }

        private fun unregisterRimeMessageHandler(handler: (RimeMessage<*>) -> Unit) {
            rimeMessageHandlers.remove(handler)
        }
    }
}
