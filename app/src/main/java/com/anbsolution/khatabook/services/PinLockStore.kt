package com.anbsolution.khatabook.services

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PinLockStore {
    private const val PREFS = "khata_device_pin"
    private const val DATA = "encrypted_pin_data"
    private const val IV = "encrypted_pin_iv"
    private const val KEY_ALIAS = "khata_book_device_pin_key"
    private const val SALT_SIZE = 16
    private const val HASH_SIZE = 32
    private const val ITERATIONS = 120_000
    private const val MAX_ATTEMPTS = 5
    private const val LOCK_MILLIS = 60_000L
    private const val GCM_TAG_BITS = 128
    private const val RECORD_SIZE = SALT_SIZE + HASH_SIZE + 4 + 8

    sealed class VerifyResult {
        object Success : VerifyResult()
        data class Incorrect(val attemptsRemaining: Int) : VerifyResult()
        data class Locked(val secondsRemaining: Int) : VerifyResult()
        object StorageError : VerifyResult()
    }

    private data class PinRecord(
        val salt: ByteArray,
        val hash: ByteArray,
        val attempts: Int,
        val lockUntil: Long
    )

    fun hasPin(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.contains(DATA) || prefs.contains(IV)
    }

    suspend fun setPin(context: Context, pin: String) =
        withContext(Dispatchers.IO) { setPinBlocking(context, pin) }

    private fun setPinBlocking(context: Context, pin: String) {
        require(pin.matches(Regex("[0-9]{4}"))) {
            "PIN must contain exactly 4 digits"
        }

        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val hash = deriveHash(pin, salt)

        val record = ByteBuffer.allocate(RECORD_SIZE)
            .put(salt)
            .put(hash)
            .putInt(0)
            .putLong(0L)
            .array()

        encryptAndSave(context, record)
    }

    suspend fun verifyPin(context: Context, pin: String): VerifyResult =
        withContext(Dispatchers.IO) { verifyPinBlocking(context, pin) }

    private fun verifyPinBlocking(context: Context, pin: String): VerifyResult {
        if (!pin.matches(Regex("[0-9]{4}"))) {
            return VerifyResult.Incorrect(MAX_ATTEMPTS)
        }

        return try {
            val record = decryptRecord(context)
            val now = System.currentTimeMillis()

            if (record.lockUntil > now) {
                val seconds = ceil((record.lockUntil - now) / 1000.0).toInt()
                return VerifyResult.Locked(seconds)
            }

            val attempts = if (record.lockUntil != 0L && now >= record.lockUntil) {
                0
            } else {
                record.attempts
            }

            val candidate = deriveHash(pin, record.salt)
            if (MessageDigest.isEqual(candidate, record.hash)) {
                saveRecord(context, record.copy(attempts = 0, lockUntil = 0L))
                VerifyResult.Success
            } else {
                val nextAttempts = attempts + 1
                if (nextAttempts >= MAX_ATTEMPTS) {
                    val until = now + LOCK_MILLIS
                    saveRecord(context, record.copy(
                        attempts = MAX_ATTEMPTS,
                        lockUntil = until
                    ))
                    VerifyResult.Locked(60)
                } else {
                    saveRecord(context, record.copy(
                        attempts = nextAttempts,
                        lockUntil = 0L
                    ))
                    VerifyResult.Incorrect(MAX_ATTEMPTS - nextAttempts)
                }
            }
        } catch (_: Exception) {
            VerifyResult.StorageError
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        } catch (_: Exception) {
            // A missing key is already equivalent to cleared PIN storage.
        }
    }

    private fun deriveHash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, HASH_SIZE * 8)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun encryptAndSave(context: Context, plain: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plain)

        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()

        check(saved) { "Unable to save secure PIN data" }
    }

    private fun decryptRecord(context: Context): PinRecord {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encryptedText = prefs.getString(DATA, null)
            ?: error("PIN data not found")
        val ivText = prefs.getString(IV, null)
            ?: error("PIN encryption data not found")

        val encrypted = Base64.decode(encryptedText, Base64.NO_WRAP)
        val iv = Base64.decode(ivText, Base64.NO_WRAP)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        val plain = cipher.doFinal(encrypted)
        check(plain.size == RECORD_SIZE) { "Invalid PIN data" }

        val buffer = ByteBuffer.wrap(plain)
        val salt = ByteArray(SALT_SIZE).also { buffer.get(it) }
        val hash = ByteArray(HASH_SIZE).also { buffer.get(it) }
        val attempts = buffer.int
        val lockUntil = buffer.long

        check(attempts in 0..MAX_ATTEMPTS) { "Invalid PIN attempt counter" }
        return PinRecord(salt, hash, attempts, lockUntil)
    }

    private fun saveRecord(context: Context, record: PinRecord) {
        val plain = ByteBuffer.allocate(RECORD_SIZE)
            .put(record.salt)
            .put(record.hash)
            .putInt(record.attempts)
            .putLong(record.lockUntil)
            .array()

        encryptAndSave(context, plain)
    }
}
