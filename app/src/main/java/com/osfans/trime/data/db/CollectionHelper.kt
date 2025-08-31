// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.db

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import timber.log.Timber

object CollectionHelper : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default) {
    private lateinit var applicationContext: Context // Store application context

    // Synchronization lock for database initialization
    private val lock = Any()

    // Lazy initialization of the database and DAO, ensuring it's done on IO dispatcher
    private val cltDb: Database by lazy {
        synchronized(lock) {
            Timber.d("Building Room database for CollectionHelper...")
            Room
                .databaseBuilder(applicationContext, Database::class.java, "collection.db")
                .addMigrations(Database.MIGRATION_3_4)
                .build()
        }
    }

    private val cltDao: DatabaseDao by lazy {
        cltDb.databaseDao()
    }

    // This init function will now just store the context, not build the DB
    fun init(context: Context) {
        this.applicationContext = context.applicationContext
        Timber.d("CollectionHelper initialized (context stored)")
    }

    // All suspend functions should explicitly run on Dispatchers.IO
    suspend fun insert(bean: DatabaseBean) = withContext(Dispatchers.IO) { cltDao.insert(bean) }

    suspend fun haveUnpinned() = withContext(Dispatchers.IO) { cltDao.haveUnpinned() }

    suspend fun getAll() = withContext(Dispatchers.IO) { cltDao.getAll() }

    suspend fun pin(id: Int) = withContext(Dispatchers.IO) { cltDao.updatePinned(id, true) }

    suspend fun unpin(id: Int) = withContext(Dispatchers.IO) { cltDao.updatePinned(id, false) }

    suspend fun delete(id: Int) = withContext(Dispatchers.IO) { cltDao.delete(id) }

    suspend fun deleteAll(skipPinned: Boolean = true) =
        withContext(Dispatchers.IO) {
            if (skipPinned) {
                cltDao.deleteAllUnpinned()
            } else {
                cltDao.deleteAll()
            }
        }

    suspend fun updateText(
        id: Int,
        text: String,
    ) = withContext(Dispatchers.IO) { cltDao.updateText(id, text) }
}
