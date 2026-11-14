package com.anbsolution.khatabook.services

import android.content.Context

object StatementSharePreferenceStore {
    private const val PREFERENCES = "khata_statement_sharing"
    private const val TARGET_KEY = "target"

    fun load(context: Context): StatementShareTarget {
        val value = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(TARGET_KEY, null)
        return StatementShareTarget.fromStorageValue(value)
    }

    fun save(context: Context, target: StatementShareTarget) {
        val saved = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(TARGET_KEY, target.storageValue)
            .commit()
        check(saved) { "Unable to save statement sharing preference." }
    }
}
