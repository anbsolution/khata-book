package com.anbsolution.khatabook.services

import android.content.Context

enum class AppAppearanceMode(val storageValue: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromStorageValue(value: String?): AppAppearanceMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

object AppearancePreferenceStore {
    private const val PREFERENCES = "khata_appearance"
    private const val MODE_KEY = "mode"

    fun load(context: Context): AppAppearanceMode =
        AppAppearanceMode.fromStorageValue(
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(MODE_KEY, null)
        )

    fun save(context: Context, mode: AppAppearanceMode) {
        val saved = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(MODE_KEY, mode.storageValue)
            .commit()
        check(saved) { "Unable to save appearance preference." }
    }
}
