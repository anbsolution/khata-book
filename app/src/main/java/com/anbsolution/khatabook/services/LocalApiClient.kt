package com.anbsolution.khatabook.services

import android.content.Context
import android.util.Base64
import com.anbsolution.khatabook.data.BusinessEntity
import com.anbsolution.khatabook.data.CustomerEntity
import com.anbsolution.khatabook.data.LedgerTransactionEntity
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalApiClient(context: Context) {
    private val appContext = context.applicationContext
    private val preferences
        get() = appContext.getSharedPreferences(LocalApiServer.PREFERENCES, Context.MODE_PRIVATE)

    suspend fun authStatus(): LocalAuthStatus = jsonRequest("GET", "/v1/auth/status", session = false).let {
        LocalAuthStatus(it.getBoolean("configured"), it.getBoolean("hasLegacyData"))
    }

    suspend fun register(name: String, email: String, password: String): LocalUser =
        jsonRequest("POST", "/v1/auth/register", JSONObject()
            .put("name", name).put("email", email).put("password", password), session = false)
            .getJSONObject("user").toLocalUser()
            .also(::saveSession)

    suspend fun login(email: String, password: String): LocalUser =
        jsonRequest("POST", "/v1/auth/login", JSONObject()
            .put("email", email).put("password", password), session = false)
            .getJSONObject("user").toLocalUser()
            .also(::saveSession)

    suspend fun currentUser(): LocalUser =
        jsonRequest("GET", "/v1/auth/me").getJSONObject("user").toLocalUser()

    suspend fun logout() {
        preferences.edit().remove(SESSION_USER).commit()
    }

    suspend fun getBusiness(): BusinessEntity? =
        jsonRequest("GET", "/v1/business").let {
            if (it.isNull("business")) null else it.getJSONObject("business").toBusiness()
        }

    suspend fun saveBusiness(item: BusinessEntity) {
        jsonRequest("PUT", "/v1/business", item.toJson())
    }

    suspend fun getCustomers(businessId: String): List<CustomerEntity> =
        jsonRequest("GET", "/v1/customers?businessId=${query(businessId)}").readItems { it.toCustomer() }

    suspend fun searchCustomers(businessId: String, search: String): List<CustomerEntity> =
        jsonRequest(
            "GET",
            "/v1/customers?businessId=${query(businessId)}&search=${query(search)}"
        ).readItems { it.toCustomer() }

    suspend fun saveCustomer(item: CustomerEntity) {
        jsonRequest("POST", "/v1/customers", item.toJson())
    }

    suspend fun updateCustomer(item: CustomerEntity) {
        jsonRequest("PUT", "/v1/customers", item.toJson())
    }

    suspend fun deleteCustomer(item: CustomerEntity) {
        jsonRequest("DELETE", "/v1/customers/${query(item.id)}")
    }

    suspend fun getCustomerTransactionCount(customerId: String): Int =
        jsonRequest("GET", "/v1/customers/${query(customerId)}/transaction-count")
            .getInt("count")

    suspend fun getBusinessTransactions(businessId: String): List<LedgerTransactionEntity> =
        jsonRequest("GET", "/v1/transactions?businessId=${query(businessId)}")
            .readItems { it.toLedgerTransaction() }

    suspend fun getCustomerTransactions(customerId: String): List<LedgerTransactionEntity> =
        jsonRequest("GET", "/v1/transactions?customerId=${query(customerId)}")
            .readItems { it.toLedgerTransaction() }

    suspend fun saveTransaction(item: LedgerTransactionEntity) {
        jsonRequest("POST", "/v1/transactions", item.toJson())
    }

    suspend fun updateTransaction(item: LedgerTransactionEntity) {
        jsonRequest("PUT", "/v1/transactions", item.toJson())
    }

    suspend fun deleteTransaction(item: LedgerTransactionEntity) {
        jsonRequest("DELETE", "/v1/transactions/${query(item.id)}")
    }

    suspend fun createBackup(password: String): ByteArray = request(
        "POST",
        "/v1/backup/export",
        JSONObject().put("password", password).toString().toByteArray(Charsets.UTF_8),
        "application/json"
    )

    suspend fun restoreBackup(bytes: ByteArray, password: String) {
        request(
            "POST",
            "/v1/backup/restore",
            bytes,
            "application/octet-stream",
            extraHeaders = mapOf(
                "X-Backup-Password" to Base64.encodeToString(
                    password.toByteArray(Charsets.UTF_8),
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                )
            )
        )
    }

    private fun saveSession(user: LocalUser) {
        check(preferences.edit().putString(SESSION_USER, user.id).commit()) {
            "Unable to save local sign-in state."
        }
    }

    private suspend fun jsonRequest(
        method: String,
        path: String,
        body: JSONObject? = null,
        session: Boolean = true
    ): JSONObject {
        val bytes = body?.toString()?.toByteArray(Charsets.UTF_8)
        val result = request(
            method,
            path,
            bytes,
            if (body == null) null else "application/json",
            session = session
        )
        return if (result.isEmpty()) JSONObject() else JSONObject(String(result, Charsets.UTF_8))
    }

    private suspend fun request(
        method: String,
        path: String,
        body: ByteArray? = null,
        contentType: String? = null,
        session: Boolean = true,
        extraHeaders: Map<String, String> = emptyMap()
    ): ByteArray = withContext(Dispatchers.IO) {
        val port = preferences.getInt(LocalApiServer.PORT_KEY, -1)
        check(port in 1..65535) { "Local API server is not running." }
        val apiToken = preferences.getString(LocalApiServer.TOKEN_KEY, null)
            ?: error("Local API authentication is unavailable.")
        val connection = (URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 5_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("Connection", "close")
            connection.setRequestProperty("X-Khata-Token", apiToken)
            if (session) {
                val userId = preferences.getString(SESSION_USER, null)
                    ?: error("Sign in to your local account first.")
                connection.setRequestProperty("X-Local-User", userId)
            }
            contentType?.let { connection.setRequestProperty("Content-Type", it) }
            extraHeaders.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (body != null) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val response = if (status in 200..299) {
                connection.inputStream.use(::readBounded)
            } else {
                val errorBody = connection.errorStream?.use(::readBounded)
                val message = try {
                    JSONObject(String(errorBody ?: ByteArray(0), Charsets.UTF_8))
                        .getJSONObject("error").getString("message")
                } catch (_: Exception) {
                    "Local API request failed (HTTP $status)."
                }
                throw LocalApiException(status, message)
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_RESPONSE_BYTES) throw IOException("Local API response is too large.")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun query(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        private const val SESSION_USER = "local_api_session_user"
        private const val MAX_RESPONSE_BYTES = 50 * 1024 * 1024
    }
}

class LocalApiException(val statusCode: Int, message: String) : IOException(message)
