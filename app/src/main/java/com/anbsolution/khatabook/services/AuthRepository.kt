package com.anbsolution.khatabook.services

import io.appwrite.ID
import io.appwrite.models.Session
import io.appwrite.models.User

class AuthRepository {
    private val account
        get() {
            check(Appwrite.isInitialized()) {
                "Appwrite must be initialized first"
            }
            return Appwrite.account
        }

    suspend fun register(
        name: String,
        email: String,
        password: String
    ): User<Map<String, Any>> {
        require(name.isNotBlank()) { "Name is required" }
        require(email.isNotBlank()) { "Email is required" }
        require(password.length >= 8) { "Password must be at least 8 characters" }

        return account.create(
            userId = ID.unique(),
            email = email.trim(),
            password = password,
            name = name.trim()
        )
    }

    suspend fun login(
        email: String,
        password: String
    ): Session {
        require(email.isNotBlank()) { "Email is required" }
        require(password.isNotBlank()) { "Password is required" }

        return account.createEmailPasswordSession(
            email = email.trim(),
            password = password
        )
    }

    suspend fun currentUser(): User<Map<String, Any>> {
        return account.get()
    }

    suspend fun logout() {
        account.deleteSession(sessionId = "current")
    }
}
