package com.anbsolution.khatabook.data
import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [BusinessEntity::class, CustomerEntity::class, LedgerTransactionEntity::class, SyncQueueEntity::class], version = 2, exportSchema = false)
abstract class KhataDatabase : RoomDatabase() {
    abstract fun khataDao(): KhataDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sync_queue ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE sync_queue ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sync_queue ADD COLUMN lastError TEXT DEFAULT NULL")
            }
        }
    }
}
object KhataDatabaseProvider {
    @Volatile private var INSTANCE: KhataDatabase? = null
    fun get(context: Context): KhataDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, KhataDatabase::class.java, "khata-book.db")
                .addMigrations(KhataDatabase.MIGRATION_1_2).build().also { INSTANCE = it }
        }
    }
}
