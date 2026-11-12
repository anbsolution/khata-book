package com.anbsolution.khatabook.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BusinessEntity::class,
        CustomerEntity::class,
        LedgerTransactionEntity::class,
        SyncQueueEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class KhataDatabase : RoomDatabase() {
    abstract fun khataDao(): KhataDao
}

object KhataDatabaseProvider {
    @Volatile
    private var INSTANCE: KhataDatabase? = null

    fun get(context: Context): KhataDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                KhataDatabase::class.java,
                "khata-book.db"
            ).build().also { INSTANCE = it }
        }
    }
}
