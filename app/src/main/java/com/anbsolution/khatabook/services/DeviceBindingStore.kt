package com.anbsolution.khatabook.services

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Signature
import java.security.MessageDigest

/**
 * Per-installation identity stored in Android Keystore.
 * This is local device binding, not server-enforced account/device registration.
 */
object DeviceBindingStore {
    private const val ALIAS = "khata_book_device_binding_ec"
    private const val PREFS = "khata_device_binding"
    private const val PUBLIC_KEY = "registered_public_key"

    fun isRegistered(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .contains(PUBLIC_KEY)

    fun register(context: Context) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        if (!keyStore.containsAlias(ALIAS)) {
            val generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                "AndroidKeyStore"
            )
            generator.initialize(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setAlgorithmParameterSpec(
                        java.security.spec.ECGenParameterSpec("secp256r1")
                    )
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build()
            )
            generator.generateKeyPair()
        }

        val publicKey = keyStore.getCertificate(ALIAS)?.publicKey
            ?: error("Device public key is unavailable")

        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(PUBLIC_KEY, Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP))
            .commit()
        check(saved) { "Unable to save device registration" }
    }

    fun verify(context: Context): Boolean = try {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedText = prefs.getString(PUBLIC_KEY, null) ?: return false

        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val privateKey = keyStore.getKey(ALIAS, null) as? java.security.PrivateKey
            ?: return false
        val publicKey = keyStore.getCertificate(ALIAS)?.publicKey ?: return false

        val savedBytes = Base64.decode(savedText, Base64.NO_WRAP)
        if (!MessageDigest.isEqual(savedBytes, publicKey.encoded)) return false

        val challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(challenge)
        val signed = signer.sign()

        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(challenge)
        verifier.verify(signed)
    } catch (_: Exception) {
        false
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS)
        } catch (_: Exception) {
            // Key may already have been invalidated or removed.
        }
    }
}
