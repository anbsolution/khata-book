package com.anbsolution.khatabook.services

import org.junit.Assert.assertEquals
import org.junit.Test

class StatementShareTargetTest {
    @Test
    fun automaticTriesWhatsAppThenBusiness() {
        assertEquals(
            listOf("com.whatsapp", "com.whatsapp.w4b"),
            StatementShareTarget.AUTOMATIC.whatsappPackages
        )
    }

    @Test
    fun systemShareTargetDoesNotLaunchWhatsAppDirectly() {
        assertEquals(emptyList<String>(), StatementShareTarget.SYSTEM_SHARE.whatsappPackages)
    }

    @Test
    fun unknownStoredPreferenceFallsBackToAutomatic() {
        assertEquals(
            StatementShareTarget.AUTOMATIC,
            StatementShareTarget.fromStorageValue("unknown")
        )
    }
}
