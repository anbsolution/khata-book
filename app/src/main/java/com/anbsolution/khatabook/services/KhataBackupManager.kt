package com.anbsolution.khatabook.services

import androidx.room.withTransaction
import com.anbsolution.khatabook.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object KhataBackupManager {
    private val MAGIC = "KHATAV01".toByteArray(Charsets.US_ASCII)
    private const val ITERATIONS = 260_000
    private const val MAX_BYTES = 50 * 1024 * 1024
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12

    private data class Snapshot(
        val businesses: List<BusinessEntity>,
        val customers: List<CustomerEntity>,
        val transactions: List<LedgerTransactionEntity>
    )

    suspend fun createBackup(database: KhataDatabase, password: String): ByteArray {
        require(password.length >= 12) { "Backup password must contain at least 12 characters." }
        val snapshot = database.withTransaction {
            val dao = database.khataDao()
            Snapshot(
                dao.getAllBusinessesForBackup(),
                dao.getAllCustomersForBackup(),
                dao.getAllTransactionsForBackup()
            )
        }
        return encrypt(encode(snapshot).toString().toByteArray(Charsets.UTF_8), password)
            .also { require(it.size <= MAX_BYTES) { "Backup exceeds the 50 MB limit." } }
    }

    suspend fun restoreBackup(
        database: KhataDatabase,
        bytes: ByteArray,
        password: String,
        accountId: String? = null
    ) {
        require(password.length >= 12) { "Backup password must contain at least 12 characters." }
        require(bytes.size in (MAGIC.size + SALT_SIZE + IV_SIZE + 16)..MAX_BYTES) {
            "Backup file size or format is invalid."
        }
        val snapshot = decode(decrypt(bytes, password))
        database.withTransaction {
            val dao = database.khataDao()
            dao.clearTransactionsForRestore()
            dao.clearCustomersForRestore()
            dao.clearBusinessesForRestore()
            if (snapshot.businesses.isNotEmpty()) dao.insertBusinessesForRestore(snapshot.businesses)
            if (snapshot.customers.isNotEmpty()) dao.insertCustomersForRestore(snapshot.customers)
            if (snapshot.transactions.isNotEmpty()) dao.insertTransactionsForRestore(snapshot.transactions)
            if (accountId != null) dao.reassignBusinessesForRestore(accountId)
        }
    }

    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt, ITERATIONS, 256)
        return try {
            SecretKeySpec(
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).encoded, "AES"
            )
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }

    private fun encrypt(plain: ByteArray, password: String): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        val iv = ByteArray(IV_SIZE).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        val encrypted = cipher.doFinal(plain)
        return ByteArrayOutputStream().apply {
            write(MAGIC); write(salt); write(iv); write(encrypted)
        }.toByteArray()
    }

    private fun decrypt(file: ByteArray, password: String): ByteArray {
        require(file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            "This is not a supported Khata Book backup."
        }
        val saltAt = MAGIC.size
        val ivAt = saltAt + SALT_SIZE
        val dataAt = ivAt + IV_SIZE
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,
                key(password, file.copyOfRange(saltAt, ivAt)),
                GCMParameterSpec(128, file.copyOfRange(ivAt, dataAt)))
            cipher.updateAAD(MAGIC)
            cipher.doFinal(file.copyOfRange(dataAt, file.size))
        } catch (_: AEADBadTagException) {
            throw IllegalArgumentException("Wrong password or backup file was modified.")
        }
    }

    private fun encode(s: Snapshot): JSONObject {
        val root = JSONObject().put("format", "khata-book")
            .put("schemaVersion", 3).put("exportedAt", System.currentTimeMillis())
        root.put("businesses", JSONArray().apply {
            s.businesses.forEach { put(JSONObject().put("id", it.id)
                .put("accountId", it.accountId).put("businessName", it.businessName)
                .put("createdAt", it.createdAt)) }
        })
        root.put("customers", JSONArray().apply {
            s.customers.forEach { put(JSONObject().put("id", it.id)
                .put("businessId", it.businessId).put("name", it.name)
                .put("phone", it.phone).put("createdAt", it.createdAt)) }
        })
        root.put("transactions", JSONArray().apply {
            s.transactions.forEach { put(JSONObject().put("id", it.id)
                .put("businessId", it.businessId).put("customerId", it.customerId)
                .put("type", it.type).put("amountPaise", it.amountPaise)
                .put("note", it.note).put("createdAt", it.createdAt).put("synced", it.synced)) }
        })
        return root
    }

    private fun decode(plain: ByteArray): Snapshot {
        val root = JSONObject(String(plain, Charsets.UTF_8))
        require(root.getString("format") == "khata-book") { "Backup format is not recognized." }
        val backupVersion = root.getInt("schemaVersion")
        require(backupVersion in 1..3) { "This backup version is not supported." }

        fun requiredText(o: JSONObject, field: String): String =
            o.getString(field).also { require(it.isNotBlank()) { "Missing $field in backup." } }

        fun <T> JSONArray.mapObjects(mapper: (JSONObject) -> T): List<T> =
            (0 until length()).map { mapper(getJSONObject(it)) }

        val businesses = root.getJSONArray("businesses").mapObjects {
            BusinessEntity(requiredText(it, "id"), requiredText(it, "accountId"),
                requiredText(it, "businessName"), it.getLong("createdAt"))
        }
        val customers = root.getJSONArray("customers").mapObjects {
            CustomerEntity(requiredText(it, "id"), requiredText(it, "businessId"),
                requiredText(it, "name"), it.optString("phone", ""), it.getLong("createdAt"))
        }
        val transactions = root.getJSONArray("transactions").mapObjects {
            LedgerTransactionEntity(requiredText(it, "id"), requiredText(it, "businessId"),
                requiredText(it, "customerId"), requiredText(it, "type"),
                it.getLong("amountPaise"), it.optString("note", ""),
                it.getLong("createdAt"), it.optBoolean("synced", false))
        }
        fun unique(ids: List<String>, table: String) {
            require(ids.size == ids.toSet().size) { "Duplicate IDs in $table." }
        }
        unique(businesses.map { it.id }, "businesses")
        unique(customers.map { it.id }, "customers")
        unique(transactions.map { it.id }, "transactions")
        val businessIds = businesses.flatMap { listOf(it.id, it.accountId) }.toSet()
        val customerById = customers.associateBy { it.id }
        require(customers.all { it.businessId in businessIds }) {
            "A customer refers to a missing business."
        }
        require(transactions.all {
            val customer = customerById[it.customerId]
            customer != null && customer.businessId == it.businessId &&
                it.businessId in businessIds
        }) { "A transaction refers to a missing or mismatched customer." }
        return Snapshot(businesses, customers, transactions)
    }
}
