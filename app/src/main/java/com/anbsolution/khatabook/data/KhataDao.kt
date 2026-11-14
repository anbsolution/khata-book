package com.anbsolution.khatabook.data
import androidx.room.*
@Dao
interface KhataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBusiness(item: BusinessEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCustomer(item: CustomerEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTransaction(item: LedgerTransactionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertLocalAccount(item: LocalAccountEntity)
    @Transaction suspend fun registerLocalAccount(item: LocalAccountEntity): Boolean {
        if (getLocalAccountCount() != 0 || getLocalAccountByEmail(item.email) != null) return false
        insertLocalAccount(item)
        claimLegacyBusinesses(item.id)
        return true
    }
    @Query("SELECT * FROM local_accounts WHERE email = :email LIMIT 1")
    suspend fun getLocalAccountByEmail(email: String): LocalAccountEntity?
    @Query("SELECT * FROM local_accounts WHERE id = :id LIMIT 1")
    suspend fun getLocalAccountById(id: String): LocalAccountEntity?
    @Query("SELECT COUNT(*) FROM local_accounts")
    suspend fun getLocalAccountCount(): Int
    @Query(
        "SELECT EXISTS(SELECT 1 FROM businesses) OR " +
            "EXISTS(SELECT 1 FROM customers) OR EXISTS(SELECT 1 FROM ledger_transactions)"
    )
    suspend fun hasLedgerData(): Boolean
    @Query("UPDATE customers SET businessId = :accountId")
    suspend fun remapLegacyCustomerBusinessIds(accountId: String)
    @Query("UPDATE ledger_transactions SET businessId = :accountId")
    suspend fun remapLegacyTransactionBusinessIds(accountId: String)
    @Query("UPDATE businesses SET accountId = :accountId")
    suspend fun updateBusinessOwners(accountId: String)
    suspend fun claimLegacyBusinesses(accountId: String) {
        remapLegacyCustomerBusinessIds(accountId)
        remapLegacyTransactionBusinessIds(accountId)
        updateBusinessOwners(accountId)
    }
    @Transaction suspend fun reassignBusinessesForRestore(accountId: String) =
        claimLegacyBusinesses(accountId)

    @Transaction suspend fun saveBusiness(item: BusinessEntity) = insertBusiness(item)
    @Transaction suspend fun saveCustomer(item: CustomerEntity) = insertCustomer(item)
    @Transaction suspend fun saveTransaction(item: LedgerTransactionEntity) = insertTransaction(item)

    @Query("SELECT * FROM businesses WHERE accountId = :accountId ORDER BY createdAt LIMIT 1")
    suspend fun getBusinessForAccount(accountId: String): BusinessEntity?
    @Query("SELECT * FROM businesses WHERE id = :businessId LIMIT 1")
    suspend fun getBusinessById(businessId: String): BusinessEntity?

    @Query("SELECT * FROM customers WHERE businessId = :businessId ORDER BY name")
    suspend fun getCustomers(businessId: String): List<CustomerEntity>
    @Query("SELECT * FROM customers WHERE id = :customerId LIMIT 1")
    suspend fun getCustomerById(customerId: String): CustomerEntity?
    @Query("SELECT * FROM customers WHERE businessId = :businessId AND (name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%') ORDER BY name")
    suspend fun searchCustomers(businessId: String, query: String): List<CustomerEntity>

    @Update suspend fun updateCustomerRow(item: CustomerEntity)
    @Transaction suspend fun updateCustomer(item: CustomerEntity) = updateCustomerRow(item)
    @Delete suspend fun deleteCustomerRow(item: CustomerEntity)
    @Transaction suspend fun deleteCustomer(item: CustomerEntity) = deleteCustomerRow(item)

    @Query("SELECT * FROM ledger_transactions WHERE customerId = :customerId ORDER BY createdAt DESC")
    suspend fun getCustomerTransactions(customerId: String): List<LedgerTransactionEntity>
    @Query("SELECT * FROM ledger_transactions WHERE id = :transactionId LIMIT 1")
    suspend fun getTransactionById(transactionId: String): LedgerTransactionEntity?
    @Query("SELECT * FROM ledger_transactions WHERE businessId = :businessId ORDER BY createdAt DESC")
    suspend fun getBusinessTransactions(businessId: String): List<LedgerTransactionEntity>
    @Query("SELECT COUNT(*) FROM ledger_transactions WHERE customerId = :customerId")
    suspend fun getCustomerTransactionCount(customerId: String): Int
    @Update suspend fun updateTransactionRow(item: LedgerTransactionEntity)
    @Transaction suspend fun updateTransaction(item: LedgerTransactionEntity) = updateTransactionRow(item)
    @Delete suspend fun deleteTransactionRow(item: LedgerTransactionEntity)
    @Transaction suspend fun deleteTransaction(item: LedgerTransactionEntity) = deleteTransactionRow(item)

    @Query("SELECT * FROM businesses ORDER BY createdAt") suspend fun getAllBusinessesForBackup(): List<BusinessEntity>
    @Query("SELECT * FROM customers ORDER BY createdAt") suspend fun getAllCustomersForBackup(): List<CustomerEntity>
    @Query("SELECT * FROM ledger_transactions ORDER BY createdAt") suspend fun getAllTransactionsForBackup(): List<LedgerTransactionEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBusinessesForRestore(items: List<BusinessEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCustomersForRestore(items: List<CustomerEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTransactionsForRestore(items: List<LedgerTransactionEntity>)
    @Query("DELETE FROM ledger_transactions") suspend fun clearTransactionsForRestore()
    @Query("DELETE FROM customers") suspend fun clearCustomersForRestore()
    @Query("DELETE FROM businesses") suspend fun clearBusinessesForRestore()
}
