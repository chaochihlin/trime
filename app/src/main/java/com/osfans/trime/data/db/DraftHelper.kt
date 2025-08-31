// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.db

import android.content.Context
import android.view.inputmethod.InputConnection
import androidx.room.Room
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.util.matchesAny
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

object DraftHelper : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default) {
    private lateinit var applicationContext: Context // Store application context

    // Synchronization lock for database initialization
    private val lock = Any()

    // Lazy initialization of the database and DAO
    private val dftDb: Database by lazy {
        synchronized(lock) {
            Timber.d("Building Room database for DraftHelper...")
            Room
                .databaseBuilder(applicationContext, Database::class.java, "draft.db")
                .addMigrations(Database.MIGRATION_3_4)
                .build()
        }
    }

    private val dftDao: DatabaseDao by lazy {
        dftDb.databaseDao()
    }

    private val mutex = Mutex()

    var itemCount: Int = 0
        private set

    private suspend fun updateItemCount() {
        itemCount = dftDao.itemCount()
    }

    private val limit by AppPrefs.defaultInstance().clipboard.draftLimit
    private val output by lazy {
        val rules by AppPrefs.defaultInstance().clipboard.draftOutputRules
        rules
            .split('\n') // Use double backslash for literal backslash in string
            .map { Regex(it) }
            .toHashSet()
    }

    var lastBean: DatabaseBean? = null

    // This init function will now just store the context, not build the DB
    fun init(context: Context) {
        this.applicationContext = context.applicationContext
        Timber.d("DraftHelper initialized (context stored)")
        launch { updateItemCount() }
    }

    suspend fun get(id: Int) = dftDao.get(id)

    suspend fun haveUnpinned() = dftDao.haveUnpinned()

    suspend fun getAll() = dftDao.getAll()

    suspend fun pin(id: Int) = dftDao.updatePinned(id, true)

    suspend fun unpin(id: Int) = dftDao.updatePinned(id, false)

    suspend fun updateText(
        id: Int,
        text: String,
    ) {
        lastBean?.let {
            if (id == it.id) lastBean = it.copy(text = text)
        }
        dftDao.updateText(id, text)
    }

    suspend fun delete(id: Int) {
        dftDao.delete(id)
        updateItemCount()
    }

    suspend fun deleteAll(skipUnpinned: Boolean = true) {
        if (skipUnpinned) {
            dftDao.deleteAllUnpinned()
        } else {
            dftDao.deleteAll()
        }
        updateItemCount()
    }

    fun onExtractedTextChanged(inputConnection: InputConnection) {
        if (!(limit != 0 && this::applicationContext.isInitialized)) return

        inputConnection
            .let { DatabaseBean.fromInputConnection(it) }
            ?.takeIf {
                it.text!!.isNotBlank() &&
                    !it.text.matchesAny(output)
            }?.let { b ->
                Timber.d("Accept draft $b")
                launch {
                    mutex.withLock {
                        dftDao.find(b.text!!)?.let {
                            lastBean = it.copy(time = b.time)
                            dftDao.updateTime(it.id, b.time)
                            return@launch
                        }
                        val rowId = dftDao.insert(b)
                        removeOutdated()
                        updateItemCount()
                        dftDao.get(rowId)?.let { lastBean = it }
                    }
                }
            }
    }

    private suspend fun removeOutdated() {
        val unpinned = dftDao.getAllUnpinned()
        if (unpinned.size > limit) {
            val outdated =
                unpinned
                    .sortedBy { it.id }
                    .getOrNull(unpinned.size - limit)
            dftDao.deletedUnpinnedEarlierThan(outdated?.time ?: System.currentTimeMillis())
        }
    }
}
