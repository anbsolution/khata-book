package com.anbsolution.khatabook.services

class AuthRepository(private val api: LocalApiClient) {
    suspend fun status(): LocalAuthStatus = api.authStatus()

    suspend fun register(name: String, email: String, password: String): LocalUser {
        require(name.isNotBlank()) { "Name is required." }
        require(email.isNotBlank()) { "Email is required." }
        require(password.length >= 8) { "Password must be at least 8 characters." }
        return api.register(name, email, password)
    }

    suspend fun login(email: String, password: String): LocalUser {
        require(email.isNotBlank()) { "Email is required." }
        require(password.isNotBlank()) { "Password is required." }
        return api.login(email, password)
    }

    suspend fun currentUser(): LocalUser = api.currentUser()

    suspend fun logout() = api.logout()
}
