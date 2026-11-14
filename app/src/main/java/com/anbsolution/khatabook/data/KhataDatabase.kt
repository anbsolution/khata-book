package com.anbsolution.khatabook.data
import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [BusinessEntity::class, CustomerEntity::class, LedgerTransactionEntity::class, LocalAccountEntity::class], version = 3, exportSchema = false)
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
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS local_accounts (" +
                        "id TEXT NOT NULL, email TEXT NOT NULL, name TEXT NOT NULL, passwordSalt TEXT NOT NULL, " +
                        "passwordHash TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_local_accounts_email " +
                        "ON local_accounts(email)"
                )
                db.execSQL("DROP TABLE IF EXISTS sync_queue")
            }
        }
    }
}
object KhataDatabaseProvider {
    @Volatile private var INSTANCE: KhataDatabase? = null
    fun get(context: Context): KhataDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, KhataDatabase::class.java, "khata-book.db")
                .addMigrations(KhataDatabase.MIGRATION_1_2, KhataDatabase.MIGRATION_2_3)
                .build().also { INSTANCE = it }
        }
    }
}
