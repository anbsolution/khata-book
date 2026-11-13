package com.anbsolution.khatabook.data
import androidx.room.*
@Dao
interface KhataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBusiness(item: BusinessEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCustomer(item: CustomerEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTransaction(item: LedgerTransactionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSyncQueue(item: SyncQueueEntity)

    private suspend fun enqueueChange(type: String, id: String, operation: String) {
        upsertSyncQueue(SyncQueueEntity("$type:$id", type, id, operation, System.currentTimeMillis()))
    }

    @Transaction suspend fun saveBusiness(item: BusinessEntity) {
        insertBusiness(item); enqueueChange("business", item.id, "UPSERT")
    }
    @Transaction suspend fun saveCustomer(item: CustomerEntity) {
        insertCustomer(item); enqueueChange("customer", item.id, "UPSERT")
    }
    @Transaction suspend fun saveTransaction(item: LedgerTransactionEntity) {
        insertTransaction(item); enqueueChange("transaction", item.id, "UPSERT")
    }

    @Query("SELECT * FROM customers WHERE businessId = :businessId ORDER BY name")
    suspend fun getCustomers(businessId: String): List<CustomerEntity>
    @Query("SELECT * FROM customers WHERE businessId = :businessId AND (name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%') ORDER BY name")
    suspend fun searchCustomers(businessId: String, query: String): List<CustomerEntity>

    @Update suspend fun updateCustomerRow(item: CustomerEntity)
    @Transaction suspend fun updateCustomer(item: CustomerEntity) {
        updateCustomerRow(item); enqueueChange("customer", item.id, "UPSERT")
    }
    @Delete suspend fun deleteCustomerRow(item: CustomerEntity)
    @Transaction suspend fun deleteCustomer(item: CustomerEntity) {
        deleteCustomerRow(item); enqueueChange("customer", item.id, "DELETE")
    }

    @Query("SELECT * FROM ledger_transactions WHERE customerId = :customerId ORDER BY createdAt DESC")
    suspend fun getCustomerTransactions(customerId: String): List<LedgerTransactionEntity>
    @Query("SELECT COUNT(*) FROM ledger_transactions WHERE customerId = :customerId")
    suspend fun getCustomerTransactionCount(customerId: String): Int
    @Update suspend fun updateTransactionRow(item: LedgerTransactionEntity)
    @Transaction suspend fun updateTransaction(item: LedgerTransactionEntity) {
        updateTransactionRow(item); enqueueChange("transaction", item.id, "UPSERT")
    }
    @Delete suspend fun deleteTransactionRow(item: LedgerTransactionEntity)
    @Transaction suspend fun deleteTransaction(item: LedgerTransactionEntity) {
        deleteTransactionRow(item); enqueueChange("transaction", item.id, "DELETE")
    }

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY createdAt")
    suspend fun getPendingSync(): List<SyncQueueEntity>
    @Query("UPDATE sync_queue SET status='PROCESSING', attempts=attempts+1, lastError=NULL WHERE id=:id")
    suspend fun markSyncProcessing(id: String)
    @Query("UPDATE sync_queue SET status='PENDING', lastError=:message WHERE id=:id")
    suspend fun markSyncFailed(id: String, message: String)
    @Query("DELETE FROM sync_queue WHERE id=:id")
    suspend fun markSyncComplete(id: String)
    @Query("UPDATE sync_queue SET status='PENDING' WHERE status='PROCESSING'")
    suspend fun recoverInterruptedSync()

    @Query("SELECT * FROM businesses ORDER BY createdAt") suspend fun getAllBusinessesForBackup(): List<BusinessEntity>
    @Query("SELECT * FROM customers ORDER BY createdAt") suspend fun getAllCustomersForBackup(): List<CustomerEntity>
    @Query("SELECT * FROM ledger_transactions ORDER BY createdAt") suspend fun getAllTransactionsForBackup(): List<LedgerTransactionEntity>
    @Query("SELECT * FROM sync_queue ORDER BY createdAt") suspend fun getAllSyncQueueForBackup(): List<SyncQueueEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBusinessesForRestore(items: List<BusinessEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCustomersForRestore(items: List<CustomerEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTransactionsForRestore(items: List<LedgerTransactionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSyncQueueForRestore(items: List<SyncQueueEntity>)
    @Query("DELETE FROM sync_queue") suspend fun clearSyncQueueForRestore()
    @Query("DELETE FROM ledger_transactions") suspend fun clearTransactionsForRestore()
    @Query("DELETE FROM customers") suspend fun clearCustomersForRestore()
    @Query("DELETE FROM businesses") suspend fun clearBusinessesForRestore()
}
