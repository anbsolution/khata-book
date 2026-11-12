package com.anbsolution.khatabook.data

import androidx.room.Database
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
