package com.anbsolution.khatabook.services

import com.anbsolution.khatabook.data.BusinessEntity
import com.anbsolution.khatabook.data.CustomerEntity
import com.anbsolution.khatabook.data.LedgerTransactionEntity
import org.json.JSONObject

data class LocalUser(val id: String, val email: String, val name: String)
data class LocalAuthStatus(val configured: Boolean, val hasLegacyData: Boolean)

internal fun BusinessEntity.toJson(): JSONObject = JSONObject()
    .put("id", id).put("accountId", accountId).put("businessName", businessName)
    .put("createdAt", createdAt)

internal fun CustomerEntity.toJson(): JSONObject = JSONObject()
    .put("id", id).put("businessId", businessId).put("name", name)
    .put("phone", phone).put("createdAt", createdAt)

internal fun LedgerTransactionEntity.toJson(): JSONObject = JSONObject()
    .put("id", id).put("businessId", businessId).put("customerId", customerId)
    .put("type", type).put("amountPaise", amountPaise).put("note", note)
    .put("createdAt", createdAt).put("synced", synced)

internal fun JSONObject.toBusiness(): BusinessEntity = BusinessEntity(
    id = getString("id"),
    accountId = getString("accountId"),
    businessName = getString("businessName"),
    createdAt = getLong("createdAt")
)

internal fun JSONObject.toCustomer(): CustomerEntity = CustomerEntity(
    id = getString("id"),
    businessId = getString("businessId"),
    name = getString("name"),
    phone = optString("phone", ""),
    createdAt = getLong("createdAt")
)

internal fun JSONObject.toLedgerTransaction(): LedgerTransactionEntity = LedgerTransactionEntity(
    id = getString("id"),
    businessId = getString("businessId"),
    customerId = getString("customerId"),
    type = getString("type"),
    amountPaise = getLong("amountPaise"),
    note = optString("note", ""),
    createdAt = getLong("createdAt"),
    synced = optBoolean("synced", false)
)

internal fun JSONObject.toLocalUser() = LocalUser(
    id = getString("id"),
    email = getString("email"),
    name = optString("name", "")
)

internal fun List<BusinessEntity>.toBusinessJson(): JSONObject =
    JSONObject().put("items", org.json.JSONArray().apply { forEach { put(it.toJson()) } })

internal fun List<CustomerEntity>.toCustomerJson(): JSONObject =
    JSONObject().put("items", org.json.JSONArray().apply { forEach { put(it.toJson()) } })

internal fun List<LedgerTransactionEntity>.toTransactionJson(): JSONObject =
    JSONObject().put("items", org.json.JSONArray().apply { forEach { put(it.toJson()) } })

internal inline fun <T> JSONObject.readItems(convert: (JSONObject) -> T): List<T> {
    val items = getJSONArray("items")
    return (0 until items.length()).map { convert(items.getJSONObject(it)) }
}
