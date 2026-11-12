package com.anbsolution.khatabook.services

import android.content.Context
import com.anbsolution.khatabook.BuildConfig
import io.appwrite.Client
import io.appwrite.services.Account

object Appwrite {
    @Volatile
    private var initialized = false

    lateinit var client: Client
        private set

    lateinit var account: Account
        private set

    @Synchronized
    fun init(context: Context) {
        if (initialized) return

        require(BuildConfig.APPWRITE_ENDPOINT.isNotBlank()) {
            "Appwrite endpoint is missing"
        }
        require(BuildConfig.APPWRITE_PROJECT_ID.isNotBlank()) {
            "Appwrite project ID is missing"
        }

        client = Client(context.applicationContext)
            .setEndpoint(BuildConfig.APPWRITE_ENDPOINT)
            .setProject(BuildConfig.APPWRITE_PROJECT_ID)

        account = Account(client)
        initialized = true
    }

    fun isInitialized(): Boolean = initialized
}
