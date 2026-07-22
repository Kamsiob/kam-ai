package com.kamsiob.kamai.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Holds the passphrase that encrypts the SQLCipher database. PART 3.
 *
 * The passphrase itself is 32 random bytes, generated once on this device and
 * never derived from anything the user types. It is wrapped by an AES-256-GCM
 * key that lives in the Android Keystore, hardware-backed and StrongBox-backed
 * where the phone has it, and never leaves that secure hardware. The wrapped
 * passphrase (its ciphertext and IV) sits in a small file in the app sandbox;
 * on its own it is useless, because unwrapping it requires the Keystore key,
 * which cannot be exported.
 *
 * The result: the database file copied off the phone, for example at a repair
 * counter or during a transfer, is meaningless, and none of this asks anything
 * of the user or adds any friction to normal use.
 */
object DatabaseKey {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "kamai_db_wrapping_key"
    private const val WRAPPED_FILE = "kamai_db.key"
    private const val PASSPHRASE_BYTES = 32
    private const val GCM_TAG_BITS = 128

    /**
     * The database passphrase, creating and wrapping a new one on first call.
     * Returns the same bytes on every later call for the life of the install.
     */
    @Synchronized
    fun getOrCreate(context: Context): ByteArray {
        val file = File(context.filesDir, WRAPPED_FILE)
        return if (file.exists()) {
            unwrap(file.readBytes())
        } else {
            val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
            file.writeBytes(wrap(passphrase))
            passphrase
        }
    }

    /** True once a wrapped passphrase exists, i.e. the DB has been keyed. */
    fun exists(context: Context): Boolean =
        File(context.filesDir, WRAPPED_FILE).exists()

    /**
     * Throws the key and the wrapped passphrase away. After this the encrypted
     * database is permanently unreadable, which is exactly what the forgot-code
     * wipe relies on: the data is not recovered, it is made unopenable, and a
     * fresh passphrase is minted next time.
     */
    fun destroy(context: Context) {
        File(context.filesDir, WRAPPED_FILE).delete()
        runCatching {
            KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
    }

    private fun wrappingKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE,
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // No user-authentication requirement: this key protects data at
            // rest transparently. The optional app lock is a separate layer.
            .apply {
                // StrongBox, the dedicated secure chip, where the phone has one.
                // Falls back silently to TEE-backed keys where it does not.
                runCatching { setIsStrongBoxBacked(true) }
            }
            .build()

        return try {
            generator.init(spec)
            generator.generateKey()
        } catch (e: Exception) {
            // A device that claims StrongBox but cannot honour it throws here.
            // Retry once without it rather than failing to key the database.
            val fallback = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            generator.init(fallback)
            generator.generateKey()
        }
    }

    private fun wrap(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain)
        // Layout: [iv length][iv][ciphertext].
        return byteArrayOf(iv.size.toByte()) + iv + ciphertext
    }

    private fun unwrap(blob: ByteArray): ByteArray {
        val ivLen = blob[0].toInt()
        val iv = blob.copyOfRange(1, 1 + ivLen)
        val ciphertext = blob.copyOfRange(1 + ivLen, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            wrappingKey(),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        return cipher.doFinal(ciphertext)
    }
}
