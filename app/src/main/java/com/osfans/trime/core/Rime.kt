// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.core

import com.osfans.trime.BuildConfig
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.schema.SchemaManager
import com.osfans.trime.util.isAsciiPrintable
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

                    val assetsDataDir = DataManager.assetsDir.absolutePath
                    val userDataDir = DataManager.userDataDir.absolutePath
                    Timber.d(
                        """
                        Starting rime with:
                        assetsDataDir: $assetsDataDir
                        userDataDir: $userDataDir
                        fullCheck: $fullCheck
                        """.trimIndent(),
                    )
                    startupRime(assetsDataDir, userDataDir, BuildConfig.BUILD_VERSION_NAME, fullCheck)

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
            processRimeKey(value, modifiers.toInt()).also {
                if (it) {
                    requireResponse()
                } else {
                    requireKeyMessage(value, modifiers.toInt())
                }
            }
        }

    override suspend fun processKey(
        value: KeyValue,
        modifiers: KeyModifiers,
    ): Boolean =
        withRimeContext {
            processRimeKey(value.value, modifiers.toInt()).also {
                if (it) {
                    requireResponse()
                } else {
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
        val currentState = lifecycle.currentStateFlow.value
        Timber.w("🔍 [Rime] startup: 請求啟動RIME引擎，當前狀態: $currentState, fullCheck: $fullCheck")

        if (currentState != RimeLifecycle.State.STOPPED) {
            Timber.w("🔍 [Rime] startup: 跳過啟動，當前狀態不是STOPPED: $currentState")
            return
        }

        Timber.w("🔍 [Rime] startup: 註冊訊息處理器...")
        registerRimeMessageHandler(::handleRimeMessage)

        Timber.w("🔍 [Rime] startup: 設定狀態為 STARTING...")
        lifecycleImpl.emitState(RimeLifecycle.State.STARTING)

        Timber.w("🔍 [Rime] startup: 啟動 dispatcher...")
        try {
            dispatcher.start(fullCheck)
            Timber.w("🔍 [Rime] startup: dispatcher.start() 完成")
        } catch (e: Exception) {
            Timber.e(e, "🔍 [Rime] startup: dispatcher.start() 失敗")
            lifecycleImpl.emitState(RimeLifecycle.State.STOPPED)
            throw e
        }
    }

    fun finalize() {
        val currentState = lifecycle.currentStateFlow.value
        Timber.w("🔍 [Rime] finalize: 請求關閉RIME引擎，當前狀態: $currentState")

        // 獲取調用堆棧以追蹤誰調用了 finalize
        val stackTrace = Thread.currentThread().stackTrace
        val caller =
            stackTrace.drop(2).take(5).joinToString("\n") {
                "    at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})"
            }
        Timber.w("🔍 [Rime] finalize 調用堆棧:\n$caller")

        if (currentState != RimeLifecycle.State.READY) {
            Timber.w("🔍 [Rime] finalize: 跳過關閉，當前狀態不是READY: $currentState")
            return
        }
        lifecycleImpl.emitState(RimeLifecycle.State.STOPPING)
        Timber.w("🔍 [Rime] finalize: 開始關閉RIME引擎...")
        dispatcher.stop().let {
            if (it.isNotEmpty()) {
                Timber.w("${it.size} job(s) didn't get a chance to run!")
            }
        }
        lifecycleImpl.emitState(RimeLifecycle.State.STOPPED)
        Timber.w("🔍 [Rime] finalize: RIME引擎已關閉")
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

            val simulateResult =
                simulateRimeKeySequence(
                    sequence.toString().replace("{}", "{braceleft}{braceright}"),
                )
            if (!simulateResult) return false

            val commit = getRimeCommit()
            val ctx = getRimeContext()

            val hasCommitText = !commit?.text.isNullOrEmpty()
            val hasContextInput = !ctx?.input.isNullOrEmpty()

            return (hasCommitText || hasContextInput).also {
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
