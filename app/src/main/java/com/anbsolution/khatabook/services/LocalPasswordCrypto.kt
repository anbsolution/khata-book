package com.anbsolution.khatabook.services

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object LocalPasswordCrypto {
    private const val ITERATIONS = 260_000
    private const val SALT_BYTES = 16
    private const val HASH_BITS = 256

    data class Record(val salt: String, val hash: String)
    private data class Kdf(val id: String, val factory: String)

    fun create(password: CharArray): Record {
        return try {
            val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
            val kdf = supportedKdf()
            val hash = derive(password, salt, kdf)
            Record("${kdf.id}:${encode(salt)}", encode(hash))
        } finally {
            password.fill('\u0000')
        }
    }

    fun verify(password: CharArray, salt: String, expectedHash: String): Boolean {
        return try {
            val parts = salt.split(':', limit = 2)
            val kdf = when (parts.firstOrNull()) {
                "pbkdf2-sha256" -> Kdf("pbkdf2-sha256", "PBKDF2WithHmacSHA256")
                "pbkdf2-sha1" -> Kdf("pbkdf2-sha1", "PBKDF2WithHmacSHA1")
                else -> return false
            }
            val saltBytes = decode(parts.getOrElse(1) { "" })
            val expectedBytes = decode(expectedHash)
            if (saltBytes.size != SALT_BYTES || expectedBytes.size != HASH_BITS / 8) return false
            MessageDigest.isEqual(derive(password, saltBytes, kdf), expectedBytes)
        } finally {
            password.fill('\u0000')
        }
    }

    private fun supportedKdf(): Kdf = try {
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        Kdf("pbkdf2-sha256", "PBKDF2WithHmacSHA256")
    } catch (_: NoSuchAlgorithmException) {
        Kdf("pbkdf2-sha1", "PBKDF2WithHmacSHA1")
    }

    private fun derive(password: CharArray, salt: ByteArray, kdf: Kdf): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, HASH_BITS)
        return try {
            SecretKeyFactory.getInstance(kdf.factory).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun decode(value: String): ByteArray =
        try {
            require(value.length % 2 == 0 && value.all { it in "0123456789abcdef" })
            ByteArray(value.length / 2) { index ->
                value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        } catch (_: Exception) {
            ByteArray(0)
        }
}
