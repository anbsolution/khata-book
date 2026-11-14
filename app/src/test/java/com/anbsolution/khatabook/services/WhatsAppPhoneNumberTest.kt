package com.anbsolution.khatabook.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhatsAppPhoneNumberTest {
    @Test
    fun normalizesIndianMobileNumbers() {
        assertEquals(
            "919876543210",
            normalizeIndianWhatsAppPhoneNumber("+91 98765-43210")
        )
        assertEquals(
            "919876543210",
            normalizeIndianWhatsAppPhoneNumber("9876543210")
        )
    }

    @Test
    fun rejectsInvalidOrIncompleteNumbers() {
        assertNull(normalizeIndianWhatsAppPhoneNumber("12345"))
        assertNull(normalizeIndianWhatsAppPhoneNumber("1234567890"))
        assertNull(normalizeIndianWhatsAppPhoneNumber("+1 415 555 0132"))
    }
}
