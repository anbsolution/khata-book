package com.anbsolution.khatabook.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Delete
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface KhataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBusiness(item: BusinessEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCustomer(item: CustomerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTransaction(item: LedgerTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueSync(item: SyncQueueEntity)

    @Query("SELECT * FROM customers WHERE businessId = :businessId ORDER BY name")
    suspend fun getCustomers(businessId: String): List<CustomerEntity>

    @Query("""
        SELECT * FROM customers
        WHERE businessId = :businessId
        AND (name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%')
        ORDER BY name
    """)
    suspend fun searchCustomers(
        businessId: String,
        query: String
    ): List<CustomerEntity>

    @Update
    suspend fun updateCustomer(item: CustomerEntity)

    @Delete
    suspend fun deleteCustomer(item: CustomerEntity)

    @Query("SELECT * FROM ledger_transactions WHERE customerId = :customerId ORDER BY createdAt DESC")
    suspend fun getCustomerTransactions(customerId: String): List<LedgerTransactionEntity>

    @Query("SELECT * FROM sync_queue ORDER BY createdAt")
    suspend fun getPendingSync(): List<SyncQueueEntity>
}
