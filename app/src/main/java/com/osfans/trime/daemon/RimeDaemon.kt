// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.daemon

import com.osfans.trime.TrimeApplication
import com.osfans.trime.core.Rime
import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.RimeLifecycle
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.core.lifecycleScope
import com.osfans.trime.core.whenReady
import com.osfans.trime.util.subprocess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Manage the singleton instance of [Rime]
 *
 * To use rime, client should call [createSession] to obtain a [RimeSession],
 * and call [destroySession] on client destroyed. Client should not leak the instance of [RimeApi],
 * and must use [RimeSession] to access rime functionalities.
 *
 * The instance of [Rime] always exists,but whether the dispatcher runs and callback works depend on clients, i.e.
 * if no clients are connected, [Rime.finalize] will be called.
 *
 * Functions are thread-safe in this class.
 *
 * Adapted from [fcitx5-android/FcitxDaemon.kt](https://github.com/fcitx5-android/fcitx5-android/blob/364afb44dcf0d9e3db3d43a21a32601b2190cbdf/app/src/main/java/org/fcitx/fcitx5/android/daemon/FcitxDaemon.kt)
 */
object RimeDaemon {
    private val realRime by lazy { Rime() }

    private val rimeImpl by lazy { object : RimeApi by realRime {} }

    private val sessions = mutableMapOf<String, RimeSession>()

    private val lock = ReentrantLock()

    private fun establish(name: String) =
        object : RimeSession {
            private inline fun <T> ensureEstablished(block: () -> T) =
                if (name in sessions) {
                    block()
                } else {
                    throw IllegalStateException("Session $name is not established")
                }

            override fun <T> run(block: suspend RimeApi.() -> T): T =
                ensureEstablished {
                    runBlocking { block(rimeImpl) }
                }

            override suspend fun <T> runOnReady(block: suspend RimeApi.() -> T): T =
                ensureEstablished {
                    realRime.lifecycle.whenReady { block(rimeImpl) }
                }

            override fun runIfReady(block: suspend RimeApi.() -> Unit) {
                ensureEstablished {
                    if (realRime.isReady) {
                        realRime.lifecycleScope.launch {
                            block(rimeImpl)
                        }
                    }
                }
            }

            override val lifecycleScope: CoroutineScope
                get() = realRime.lifecycle.lifecycleScope
        }

    fun createSession(name: String): RimeSession =
        lock.withLock {
            if (name in sessions) {
                return@withLock sessions.getValue(name)
            }
            if (realRime.lifecycle.currentStateFlow.value == RimeLifecycle.State.STOPPED) {
                realRime.startup(false)
            }
            val session = establish(name)
            sessions[name] = session
            return@withLock session
        }

    /**
     * 確保 RIME 引擎已啟動，主要用於初始化階段
     * 這個方法不會創建會話，只是確保引擎處於可用狀態
     *
     * 針對手錶設備記憶體限制進行優化，增加記憶體監控
     */
    fun ensureRimeStarted(): Unit =
        lock.withLock {
            if (realRime.lifecycle.currentStateFlow.value == RimeLifecycle.State.STOPPED) {
                // 記憶體監控 - 手錶設備記憶體限制分析
                val runtime = Runtime.getRuntime()
                val totalMemory = runtime.totalMemory() / 1024 / 1024 // MB
                val freeMemory = runtime.freeMemory() / 1024 / 1024 // MB
                val usedMemory = totalMemory - freeMemory

                Timber.i("記憶體狀態 - 總計: ${totalMemory}MB, 已用: ${usedMemory}MB, 可用: ${freeMemory}MB")

                // 手錶設備記憶體不足警告閾值 (可用記憶體低於 50MB)
                if (freeMemory < 50) {
                    Timber.w("記憶體不足警告: 可用記憶體僅 ${freeMemory}MB，可能影響 RIME 引擎啟動")
                }

                try {
                    Timber.i("開始啟動 RIME 引擎 (針對手錶設備優化)")
                    realRime.startup(false)

                    // 記錄啟動後記憶體狀態
                    val afterFreeMemory = runtime.freeMemory() / 1024 / 1024
                    Timber.i("RIME 引擎啟動完成 - 剩餘可用記憶體: ${afterFreeMemory}MB")
                } catch (e: Exception) {
                    val currentFreeMemory = runtime.freeMemory() / 1024 / 1024
                    Timber.w(
                        e,
                        "RIME 引擎啟動失敗: ${e.message}, 當前可用記憶體: ${currentFreeMemory}MB"
                    )

                    // 針對手錶設備記憶體限制的建議
                    if (currentFreeMemory < 30) {
                        Timber.w("建議: 手錶設備記憶體嚴重不足，請重啟設備或關閉其他應用程式")
                    }
                    // 不重新拋出異常，讓應用程式繼續運行以提供基本功能
                }
            }
        }

    fun destroySession(name: String): Unit =
        lock.withLock {
            if (name !in sessions) {
                return
            }
            sessions -= name
            if (sessions.isEmpty()) {
                realRime.finalize()
            }
        }

    /**
     * Reuse a session for remote service
     */
    fun getFirstSessionOrNull() = sessions.firstNotNullOfOrNull { it.value }

    init {
        TrimeApplication.getInstance().coroutineScope.launch {
            realRime.messageFlow.collect {
                handleRimeMessage(it)
            }
        }
    }

    /**
     * Restart Rime instance to deploy while keep the session
     */
    fun restartRime(fullCheck: Boolean = false) =
        lock.withLock {
            if (!fullCheck) {
                Timber.i("Restarting RIME engine...")
            } else {
                Timber.i("Restarting RIME engine with full check...")
            }
            realRime.finalize()
            realRime.startup(fullCheck)
            TrimeApplication.getInstance().coroutineScope.launch {
                realRime.lifecycle.whenReady {
                    Timber.i("RIME engine restart completed")
                }
            }
        }

    private suspend fun handleRimeMessage(it: RimeMessage<*>) {
        if (it is RimeMessage.DeployMessage) {
            when (it.data) {
                RimeMessage.DeployMessage.State.Start -> {
                    Timber.i("RIME deploy started")
                    withContext(Dispatchers.IO) { subprocess("logcat", "--clear") }
                }
                RimeMessage.DeployMessage.State.Success -> {
                    Timber.i("RIME deploy completed successfully")
                }
                RimeMessage.DeployMessage.State.Failure -> {
                    Timber.w("RIME deploy failed")
                }
            }
        }
    }
}
