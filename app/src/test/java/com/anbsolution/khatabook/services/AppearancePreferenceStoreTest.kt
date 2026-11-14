package com.anbsolution.khatabook.services

import org.junit.Assert.assertEquals
import org.junit.Test

class AppearancePreferenceStoreTest {
    @Test
    fun invalidOrMissingStorageValueUsesSystemAppearance() {
        assertEquals(AppAppearanceMode.SYSTEM, AppAppearanceMode.fromStorageValue(null))
        assertEquals(AppAppearanceMode.SYSTEM, AppAppearanceMode.fromStorageValue("unexpected"))
    }

    @Test
    fun everyAppearanceModeRoundTripsItsStorageValue() {
        AppAppearanceMode.entries.forEach { mode ->
            assertEquals(mode, AppAppearanceMode.fromStorageValue(mode.storageValue))
        }
    }
}
