package com.anbsolution.khatabook.data
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "businesses")
data class BusinessEntity(@PrimaryKey val id: String, val accountId: String, val businessName: String, val createdAt: Long)

@Entity(tableName = "customers")
data class CustomerEntity(@PrimaryKey val id: String, val businessId: String, val name: String, val phone: String = "", val createdAt: Long)

@Entity(tableName = "ledger_transactions")
data class LedgerTransactionEntity(@PrimaryKey val id: String, val businessId: String, val customerId: String, val type: String, val amountPaise: Long, val note: String = "", val createdAt: Long, val synced: Boolean = false)

@Entity(tableName = "local_accounts", indices = [Index(value = ["email"], unique = true)])
data class LocalAccountEntity(
    @PrimaryKey val id: String,
    val email: String,
    val name: String,
    val passwordSalt: String,
    val passwordHash: String,
    val createdAt: Long
)
