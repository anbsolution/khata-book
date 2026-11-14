package com.anbsolution.khatabook.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPasswordCryptoTest {
    @Test
    fun usesIndependentSaltsAndVerifiesOnlyTheMatchingPassword() {
        val first = LocalPasswordCrypto.create("correct horse".toCharArray())
        val second = LocalPasswordCrypto.create("correct horse".toCharArray())

        assertNotEquals(first.salt, second.salt)
        assertTrue(
            LocalPasswordCrypto.verify(
                "correct horse".toCharArray(),
                first.salt,
                first.hash
            )
        )
        assertFalse(
            LocalPasswordCrypto.verify(
                "incorrect".toCharArray(),
                first.salt,
                first.hash
            )
        )
    }

    @Test
    fun rejectsMalformedStoredRecords() {
        assertFalse(LocalPasswordCrypto.verify("password".toCharArray(), "xyz", "abc"))
    }
}
