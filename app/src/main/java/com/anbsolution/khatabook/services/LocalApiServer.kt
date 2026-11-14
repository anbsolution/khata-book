package com.anbsolution.khatabook.services

import android.content.Context
import android.util.Base64
import com.anbsolution.khatabook.data.BusinessEntity
import com.anbsolution.khatabook.data.CustomerEntity
import com.anbsolution.khatabook.data.KhataDatabaseProvider
import com.anbsolution.khatabook.data.LedgerTransactionEntity
import com.anbsolution.khatabook.data.LocalAccountEntity
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object LocalApiServer {
    const val PREFERENCES = "khata_local_api"
    const val PORT_KEY = "loopback_port"
    const val TOKEN_KEY = "app_private_token"

    private const val MAX_REQUEST_BYTES = 50 * 1024 * 1024
    private const val MAX_JSON_BYTES = 64 * 1024
    private const val MAX_HEADER_BYTES = 16 * 1024
    private const val MAX_PASSWORD_BYTES = 256
    private const val TOKEN_BYTES = 32

    private val requestWorkers = ThreadPoolExecutor(
        2, 4, 30, TimeUnit.SECONDS, ArrayBlockingQueue(16)
    )
    @Volatile private var listener: ServerSocket? = null
    @Volatile private var failedLogins = 0
    @Volatile private var loginLockedUntil = 0L

    @Synchronized
    fun start(context: Context) {
        if (listener?.isClosed == false) return
        val appContext = context.applicationContext
        val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val existingToken = preferences.getString(TOKEN_KEY, null)
        if (existingToken.isNullOrBlank()) {
            val token = ByteArray(TOKEN_BYTES).also(SecureRandom()::nextBytes)
                .joinToString("") { "%02x".format(it) }
            check(preferences.edit().putString(TOKEN_KEY, token).commit()) {
                "Could not initialize local API credentials."
            }
        }
        val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        server.soTimeout = 0
        check(preferences.edit().putInt(PORT_KEY, server.localPort).commit()) {
            server.close()
            "Could not store the local API port."
        }
        listener = server
        Thread({
            while (!server.isClosed) {
                try {
                    val socket = server.accept()
                    try {
                        requestWorkers.execute { handleSocket(appContext, socket) }
                    } catch (_: java.util.concurrent.RejectedExecutionException) {
                        socket.close()
                    }
                } catch (_: Exception) {
                    if (!server.isClosed) {
                        try {
                            Thread.sleep(100)
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                            return@Thread
                        }
                    }
                }
            }
        }, "khata-loopback-api").apply {
            isDaemon = true
            start()
        }
    }

    private fun handleSocket(context: Context, socket: Socket) {
        socket.use { client ->
            try {
                client.soTimeout = 10_000
                if (!client.inetAddress.isLoopbackAddress) {
                    respond(client.getOutputStream(), ApiResponse.error(403, "loopback_only", "Loopback connections only."))
                    return
                }
                val request = readRequest(client.getInputStream())
                val response = handle(context, request)
                respond(client.getOutputStream(), response)
            } catch (e: ApiFault) {
                respondQuietly(client, ApiResponse.error(e.status, e.code, e.message ?: "Request rejected."))
            } catch (e: IllegalArgumentException) {
                respondQuietly(client, ApiResponse.error(400, "invalid_request", e.message ?: "Request is invalid."))
            } catch (_: JSONException) {
                respondQuietly(client, ApiResponse.error(400, "invalid_request", "Request fields are invalid."))
            } catch (_: Exception) {
                respondQuietly(client, ApiResponse.error(500, "internal_error", "The local API could not process the request."))
            }
        }
    }

    private fun handle(context: Context, request: ApiRequest): ApiResponse =
        runBlocking { handleRequest(context, request) }

    private suspend fun handleRequest(context: Context, request: ApiRequest): ApiResponse {
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val expected = prefs.getString(TOKEN_KEY, null)?.toByteArray(Charsets.UTF_8)
            ?: throw ApiFault(503, "not_ready", "Local API authentication is unavailable.")
        val supplied = request.headers["x-khata-token"]?.toByteArray(Charsets.UTF_8)
            ?: throw ApiFault(401, "unauthorized", "Local API token is required.")
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw ApiFault(401, "unauthorized", "Local API token is invalid.")
        }
        val dao = KhataDatabaseProvider.get(context).khataDao()
        val route = request.path.substringBefore('?')
        val params = parseQuery(request.path.substringAfter('?', ""))

        if (request.method == "GET" && route == "/v1/auth/status") {
            val configured = dao.getLocalAccountCount() > 0
            val hasLegacyData = dao.hasLedgerData()
            return ApiResponse.json(200, JSONObject()
                .put("configured", configured)
                .put("hasLegacyData", hasLegacyData))
        }
        if (route == "/v1/auth/register" && request.method == "POST") {
            val body = request.json()
            val name = body.safeText("name", 100)
            val email = normalizeEmail(body.safeText("email", 254))
            val password = body.safeText("password", 128)
            if (name.isBlank()) throw ApiFault(400, "invalid_name", "Enter your name.")
            if (password.length < 8) throw ApiFault(400, "weak_password", "Password must contain at least 8 characters.")
            if (dao.getLocalAccountCount() > 0) {
                throw ApiFault(409, "account_exists", "A local account is already set up. Sign in instead.")
            }
            val id = java.util.UUID.randomUUID().toString()
            val record = LocalPasswordCrypto.create(password.toCharArray())
            val account = LocalAccountEntity(id, email, name, record.salt, record.hash, System.currentTimeMillis())
            val registered = dao.registerLocalAccount(account)
            if (!registered) {
                throw ApiFault(409, "account_exists", "Unable to create the local account.")
            }
            return ApiResponse.json(201, JSONObject().put("user", account.toUser().toJson()))
        }
        if (route == "/v1/auth/login" && request.method == "POST") {
            val now = System.currentTimeMillis()
            synchronized(this) {
                if (now < loginLockedUntil) {
                    throw ApiFault(429, "login_locked", "Too many sign-in attempts. Try again later.")
                }
            }
            val body = request.json()
            val email = normalizeEmail(body.safeText("email", 254))
            val password = body.safeText("password", 128)
            val account = dao.getLocalAccountByEmail(email)
            val valid = account != null && LocalPasswordCrypto.verify(
                password.toCharArray(), account.passwordSalt, account.passwordHash
            )
            if (!valid) {
                synchronized(this) {
                    failedLogins += 1
                    if (failedLogins >= 5) {
                        failedLogins = 0
                        loginLockedUntil = System.currentTimeMillis() + 60_000
                    }
                }
                throw ApiFault(401, "invalid_credentials", "Email or password is incorrect.")
            }
            synchronized(this) {
                failedLogins = 0
                loginLockedUntil = 0L
            }
            return ApiResponse.json(200, JSONObject().put("user", account!!.toUser().toJson()))
        }

        val accountId = request.headers["x-local-user"]
            ?: throw ApiFault(401, "sign_in_required", "Sign in to your local account first.")
        val account = dao.getLocalAccountById(accountId)
            ?: throw ApiFault(401, "sign_in_required", "Sign in to your local account first.")

        if (route == "/v1/auth/me" && request.method == "GET") {
            return ApiResponse.json(200, JSONObject().put("user", account.toUser().toJson()))
        }
        if (route == "/v1/business" && request.method == "GET") {
            val business = dao.getBusinessForAccount(account.id)
            return ApiResponse.json(200, JSONObject().put("business", business?.toJson()))
        }
        if (route == "/v1/business" && request.method == "PUT") {
            val body = request.json()
            val existing = dao.getBusinessForAccount(account.id)
            val requestedId = body.safeText("id", 100)
            if (existing != null && existing.id != requestedId) {
                throw ApiFault(403, "forbidden", "That business does not belong to this local account.")
            }
            if (existing == null && requestedId.isNotBlank() &&
                dao.getBusinessById(requestedId) != null
            ) {
                throw ApiFault(409, "already_exists", "Business already exists.")
            }
            val name = body.safeText("businessName", 120).trim()
            if (name.isBlank()) throw ApiFault(400, "invalid_business", "Business name is required.")
            val business = BusinessEntity(
                id = existing?.id ?: requestedId.takeIf(String::isNotBlank)
                    ?: java.util.UUID.randomUUID().toString(),
                accountId = account.id,
                businessName = name,
                createdAt = existing?.createdAt ?: body.optLong("createdAt", System.currentTimeMillis())
            )
            dao.saveBusiness(business)
            return ApiResponse.json(200, business.toJson())
        }
        if (route == "/v1/customers" && request.method == "GET") {
            val businessId = params["businessId"] ?: throw ApiFault(400, "invalid_business", "Business ID is required.")
            requireOwnedBusiness(dao, account.id, businessId)
            val search = params["search"]
            val customers = if (search.isNullOrBlank()) dao.getCustomers(businessId)
                else dao.searchCustomers(businessId, search.take(100))
            return ApiResponse.json(200, customers.toCustomerJson())
        }
        if (route == "/v1/customers" && request.method in setOf("POST", "PUT")) {
            val body = request.json()
            val customer = body.toCustomer()
            requireOwnedBusiness(dao, account.id, customer.businessId)
            val existing = dao.getCustomerById(customer.id)
            if (request.method == "POST" && existing != null) {
                throw ApiFault(409, "already_exists", "Customer already exists.")
            }
            if (request.method == "PUT" && existing == null) {
                throw ApiFault(404, "not_found", "Customer was not found.")
            }
            if (existing != null && existing.businessId != customer.businessId) {
                throw ApiFault(403, "forbidden", "That customer does not belong to this local account.")
            }
            if (customer.name.isBlank() || customer.name.length > 120 || customer.phone.length > 40) {
                throw ApiFault(400, "invalid_customer", "Customer details are invalid.")
            }
            if (request.method == "POST") dao.saveCustomer(customer) else dao.updateCustomer(customer)
            return ApiResponse.json(200, customer.toJson())
        }
        if (route.startsWith("/v1/customers/") && route.endsWith("/transaction-count") &&
            request.method == "GET"
        ) {
            val id = route.removePrefix("/v1/customers/").removeSuffix("/transaction-count")
            val customer = findOwnedCustomer(dao, account.id, id)
            return ApiResponse.json(200, JSONObject().put("count", dao.getCustomerTransactionCount(customer.id)))
        }
        if (route.startsWith("/v1/customers/") && request.method == "DELETE") {
            val id = decodePath(route.removePrefix("/v1/customers/"))
            val customer = findOwnedCustomer(dao, account.id, id)
            val count = dao.getCustomerTransactionCount(customer.id)
            if (count > 0) throw ApiFault(409, "customer_has_transactions", "Customer has $count ledger entries. Delete is blocked to protect account history.")
            dao.deleteCustomer(customer)
            return ApiResponse.json(200, JSONObject().put("deleted", true))
        }
        if (route == "/v1/transactions" && request.method == "GET") {
            val customerId = params["customerId"]
            val transactions = if (customerId != null) {
                val customer = findOwnedCustomer(dao, account.id, customerId)
                dao.getCustomerTransactions(customer.id)
            } else {
                val businessId = params["businessId"]
                    ?: throw ApiFault(400, "invalid_business", "Business ID or customer ID is required.")
                requireOwnedBusiness(dao, account.id, businessId)
                dao.getBusinessTransactions(businessId)
            }
            return ApiResponse.json(200, transactions.toTransactionJson())
        }
        if (route == "/v1/transactions" && request.method in setOf("POST", "PUT")) {
            val transaction = request.json().toLedgerTransaction()
            requireOwnedBusiness(dao, account.id, transaction.businessId)
            val customer = findOwnedCustomer(dao, account.id, transaction.customerId)
            if (customer.businessId != transaction.businessId) {
                throw ApiFault(400, "invalid_customer", "Transaction customer and business do not match.")
            }
            validateTransaction(transaction)
            val old = dao.getTransactionById(transaction.id)
            if (request.method == "POST" && old != null) {
                throw ApiFault(409, "already_exists", "Transaction already exists.")
            }
            if (request.method == "PUT" && old == null) {
                throw ApiFault(404, "not_found", "Transaction was not found.")
            }
            if (old != null && (old.businessId != transaction.businessId ||
                    old.customerId != transaction.customerId)
            ) {
                throw ApiFault(403, "forbidden", "That transaction does not belong to this local account.")
            }
            if (request.method == "POST") dao.saveTransaction(transaction) else dao.updateTransaction(transaction)
            return ApiResponse.json(200, transaction.toJson())
        }
        if (route.startsWith("/v1/transactions/") && request.method == "DELETE") {
            val id = decodePath(route.removePrefix("/v1/transactions/"))
            val transaction = dao.getBusinessTransactionsForOwner(account.id)
                .firstOrNull { it.id == id }
                ?: throw ApiFault(404, "not_found", "Transaction was not found.")
            dao.deleteTransaction(transaction)
            return ApiResponse.json(200, JSONObject().put("deleted", true))
        }
        if (route == "/v1/backup/export" && request.method == "POST") {
            val password = request.json().safeText("password", 128)
            if (password.length < 12) throw ApiFault(400, "invalid_password", "Backup password must contain at least 12 characters.")
            val bytes = KhataBackupManager.createBackup(KhataDatabaseProvider.get(context), password)
            return ApiResponse.binary(200, bytes)
        }
        if (route == "/v1/backup/restore" && request.method == "POST") {
            val encodedPassword = request.headers["x-backup-password"]
                ?: throw ApiFault(400, "invalid_password", "Backup password is required.")
            val password = try {
                String(Base64.decode(encodedPassword, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
            } catch (_: Exception) {
                throw ApiFault(400, "invalid_password", "Backup password encoding is invalid.")
            }
            if (password.length < 12 || password.toByteArray(Charsets.UTF_8).size > MAX_PASSWORD_BYTES) {
                throw ApiFault(400, "invalid_password", "Backup password length is invalid.")
            }
            KhataBackupManager.restoreBackup(
                KhataDatabaseProvider.get(context),
                request.body,
                password,
                account.id
            )
            return ApiResponse.json(200, JSONObject().put("restored", true))
        }

        throw ApiFault(404, "not_found", "API endpoint was not found.")
    }

    private suspend fun requireOwnedBusiness(
        dao: com.anbsolution.khatabook.data.KhataDao,
        accountId: String,
        businessId: String
    ): BusinessEntity {
        val business = dao.getBusinessForAccount(accountId)
        if (business == null || (business.id != businessId && business.accountId != businessId)) {
            throw ApiFault(403, "forbidden", "That business does not belong to this local account.")
        }
        return business
    }

    private suspend fun findOwnedCustomer(
        dao: com.anbsolution.khatabook.data.KhataDao,
        accountId: String,
        customerId: String
    ): CustomerEntity {
        val customer = dao.getCustomerById(customerId)
            ?: throw ApiFault(404, "not_found", "Customer was not found.")
        requireOwnedBusiness(dao, accountId, customer.businessId)
        return customer
    }

    private fun validateTransaction(transaction: LedgerTransactionEntity) {
        if (transaction.type !in setOf("UDHAR", "JAMA") || transaction.amountPaise <= 0 ||
            transaction.note.length > 2_000
        ) {
            throw ApiFault(400, "invalid_transaction", "Transaction details are invalid.")
        }
    }

    private suspend fun com.anbsolution.khatabook.data.KhataDao.getBusinessTransactionsForOwner(
        accountId: String
    ): List<LedgerTransactionEntity> {
        val business = getBusinessForAccount(accountId) ?: return emptyList()
        return getBusinessTransactions(business.accountId)
    }

    private fun readRequest(input: InputStream): ApiRequest {
        val stream = BufferedInputStream(input)
        var headerBytes = 0
        fun line(): String {
            val bytes = ByteArrayOutputStream()
            while (true) {
                val value = stream.read()
                if (value < 0) throw ApiFault(400, "bad_request", "Incomplete HTTP request.")
                headerBytes++
                if (headerBytes > MAX_HEADER_BYTES) throw ApiFault(413, "headers_too_large", "HTTP headers are too large.")
                if (value == '\n'.code) break
                if (value != '\r'.code) bytes.write(value)
            }
            return String(bytes.toByteArray(), Charsets.US_ASCII)
        }
        val requestLine = line().split(' ')
        if (requestLine.size != 3 || requestLine[2] != "HTTP/1.1") {
            throw ApiFault(400, "bad_request", "Only HTTP/1.1 requests are supported.")
        }
        val headers = linkedMapOf<String, String>()
        while (true) {
            val header = line()
            if (header.isEmpty()) break
            val separator = header.indexOf(':')
            if (separator <= 0) throw ApiFault(400, "bad_request", "Malformed HTTP header.")
            headers[header.substring(0, separator).trim().lowercase()] =
                header.substring(separator + 1).trim()
        }
        if (headers.containsKey("transfer-encoding")) {
            throw ApiFault(400, "unsupported_encoding", "Chunked request bodies are not supported.")
        }
        val length = headers["content-length"]?.toLongOrNull() ?: 0L
        val limit = if (headers["content-type"]?.startsWith("application/json") == true) {
            MAX_JSON_BYTES
        } else {
            MAX_REQUEST_BYTES
        }
        if (length < 0 || length > limit) {
            throw ApiFault(413, "body_too_large", "Request body exceeds the allowed size.")
        }
        val body = ByteArray(length.toInt())
        var offset = 0
        while (offset < body.size) {
            val count = stream.read(body, offset, body.size - offset)
            if (count < 0) throw ApiFault(400, "bad_request", "Incomplete request body.")
            offset += count
        }
        if (requestLine[0] !in setOf("GET", "POST", "PUT", "DELETE")) {
            throw ApiFault(405, "method_not_allowed", "HTTP method is not supported.")
        }
        return ApiRequest(requestLine[0], requestLine[1], headers, body)
    }

    private fun parseQuery(query: String): Map<String, String> =
        try {
            query.split('&').filter(String::isNotEmpty).associate {
                val parts = it.split('=', limit = 2)
                URLDecoder.decode(parts[0], "UTF-8") to
                    URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
            }
        } catch (_: IllegalArgumentException) {
            throw ApiFault(400, "invalid_query", "Query parameters are malformed.")
        }

    private fun decodePath(value: String): String =
        try {
            URLDecoder.decode(value, "UTF-8")
        } catch (_: IllegalArgumentException) {
            throw ApiFault(400, "invalid_path", "Request path is malformed.")
        }

    private fun normalizeEmail(value: String): String {
        val email = value.trim().lowercase()
        if (email.length !in 3..254 || !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email)) {
            throw ApiFault(400, "invalid_email", "Enter a valid email address.")
        }
        return email
    }

    private fun JSONObject.safeText(field: String, maxLength: Int): String {
        val value = optString(field, "")
        if (value.length > maxLength || value.contains('\u0000')) {
            throw ApiFault(400, "invalid_field", "Field '$field' is too long or invalid.")
        }
        return value
    }

    private fun ApiRequest.json(): JSONObject {
        if (body.isEmpty()) throw ApiFault(400, "missing_body", "Request body is required.")
        return try {
            JSONObject(String(body, Charsets.UTF_8))
        } catch (_: JSONException) {
            throw ApiFault(400, "invalid_json", "Request body must be valid JSON.")
        }
    }

    private fun LocalAccountEntity.toUser() = LocalUser(id, email, name)
    private fun LocalUser.toJson() = JSONObject().put("id", id).put("email", email).put("name", name)

    private fun respondQuietly(socket: Socket, response: ApiResponse) {
        try {
            if (!socket.isClosed) respond(socket.getOutputStream(), response)
        } catch (_: Exception) {
        }
    }

    private fun respond(output: OutputStream, response: ApiResponse) {
        val reason = when (response.status) {
            200 -> "OK"
            201 -> "Created"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            409 -> "Conflict"
            413 -> "Payload Too Large"
            429 -> "Too Many Requests"
            500 -> "Internal Server Error"
            else -> "Service Unavailable"
        }
        val body = response.body
        val header = "HTTP/1.1 ${response.status} $reason\r\n" +
            "Content-Type: ${response.contentType}\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.US_ASCII))
        output.write(body)
        output.flush()
    }

    private data class ApiRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: ByteArray
    )

    private data class ApiResponse(val status: Int, val contentType: String, val body: ByteArray) {
        companion object {
            fun json(status: Int, json: JSONObject) =
                ApiResponse(status, "application/json; charset=utf-8", json.toString().toByteArray(Charsets.UTF_8))

            fun binary(status: Int, bytes: ByteArray) =
                ApiResponse(status, "application/octet-stream", bytes)

            fun error(status: Int, code: String, message: String) = json(
                status,
                JSONObject().put("error", JSONObject().put("code", code).put("message", message))
            )
        }
    }

    private class ApiFault(val status: Int, val code: String, override val message: String?) :
        RuntimeException(message)
}
