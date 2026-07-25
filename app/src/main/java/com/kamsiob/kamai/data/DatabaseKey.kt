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
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

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
     *
     * @param userSecret set only when the separate-passphrase app lock is on. In
     *   that mode the wrapped passphrase is additionally encrypted with a key
     *   derived from what the user typed, so the database genuinely cannot be
     *   opened without it, not even by unwrapping the Keystore layer. That is
     *   what makes the separate-passphrase lock stronger, and what makes a
     *   forgotten passphrase unrecoverable: the data stays encrypted, and the
     *   only way forward is the wipe.
     */
    @Synchronized
    fun getOrCreate(context: Context, userSecret: CharArray? = null): ByteArray {
        val file = File(context.filesDir, WRAPPED_FILE)
        return if (file.exists()) {
            unwrap(peelUserLayer(file.readBytes(), userSecret))
        } else {
            val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
            file.writeBytes(applyUserLayer(wrap(passphrase), userSecret))
            passphrase
        }
    }

    /**
     * Re-wraps the stored passphrase so that [newSecret] is required to open it
     * (or none, when the passphrase lock is turned off). Used when the user
     * sets, changes, or removes a separate-passphrase lock, so the change takes
     * effect without ever re-encrypting the whole database.
     */
    @Synchronized
    fun rewrap(context: Context, currentSecret: CharArray?, newSecret: CharArray?) {
        val file = File(context.filesDir, WRAPPED_FILE)
        val keystoreWrapped = peelUserLayer(file.readBytes(), currentSecret)
        file.writeBytes(applyUserLayer(keystoreWrapped, newSecret))
    }

    /** True when the stored key carries a user-passphrase layer. */
    fun hasUserLayer(context: Context): Boolean {
        val file = File(context.filesDir, WRAPPED_FILE)
        if (!file.exists()) return false
        return file.readBytes().firstOrNull() == USER_LAYER_MARKER
    }

    // The passphrase layer is a PBKDF2-derived AES-GCM wrap around the
    // Keystore-wrapped bytes. A one-byte marker records whether it is present.
    private const val NO_USER_LAYER_MARKER: Byte = 0
    private const val USER_LAYER_MARKER: Byte = 1
    private const val PBKDF2_ITERATIONS = 200_000
    private const val SALT_BYTES = 16

    private fun applyUserLayer(keystoreWrapped: ByteArray, secret: CharArray?): ByteArray {
        if (secret == null) return byteArrayOf(NO_USER_LAYER_MARKER) + keystoreWrapped
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(secret, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ct = cipher.doFinal(keystoreWrapped)
        return byteArrayOf(USER_LAYER_MARKER, salt.size.toByte()) + salt +
            byteArrayOf(iv.size.toByte()) + iv + ct
    }

    private fun peelUserLayer(blob: ByteArray, secret: CharArray?): ByteArray {
        return when (blob[0]) {
            NO_USER_LAYER_MARKER -> blob.copyOfRange(1, blob.size)
            USER_LAYER_MARKER -> {
                requireNotNull(secret) { "this database is passphrase locked" }
                var i = 1
                val saltLen = blob[i++].toInt()
                val salt = blob.copyOfRange(i, i + saltLen); i += saltLen
                val ivLen = blob[i++].toInt()
                val iv = blob.copyOfRange(i, i + ivLen); i += ivLen
                val ct = blob.copyOfRange(i, blob.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, deriveKey(secret, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
                cipher.doFinal(ct)
            }
            // A key file written before the marker existed is the raw
            // Keystore-wrapped bytes, which begin with the IV length, never 0 or
            // 1. Treat it as a plain, no-user-layer key.
            else -> blob
        }
    }

    private fun deriveKey(secret: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(secret, salt, PBKDF2_ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2withHmacSHA256")
            .generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
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

    /**
     * A stream that encrypts on the way to [out], for data far too large to send
     * through the Keystore (#52).
     *
     * **The Keystore key never touches the bulk.** It cannot: it lives in
     * StrongBox or the TEE, so every block goes out to the secure element and
     * back. Measured on the phone, an eleven-megabyte conversation cache written
     * straight through a Keystore cipher produced thirteen bytes in twenty
     * seconds and was still going. That is not a slow path, it is a hang.
     *
     * So each file gets a fresh random data key, generated and used in software
     * where AES is a CPU instruction, and only that thirty-two byte key is
     * wrapped by the Keystore. Thirty-two bytes through StrongBox is instant, and
     * the file is still worthless without the Keystore entry, which is the whole
     * point. The same shape SQLCipher's key already uses.
     *
     * Layout: [wrapped key length as int32][wrapped key][iv length][iv][ciphertext].
     */
    fun encrypting(out: java.io.OutputStream): java.io.OutputStream {
        val dataKey = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val wrapped = wrap(dataKey)

        val header = java.io.DataOutputStream(out)
        header.writeInt(wrapped.size)
        header.write(wrapped)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(dataKey, "AES"))
        val iv = cipher.iv
        header.write(iv.size)
        header.write(iv)
        header.flush()
        return javax.crypto.CipherOutputStream(out, cipher)
    }

    /** The other half of [encrypting]. */
    fun decrypting(input: java.io.InputStream): java.io.InputStream {
        val header = java.io.DataInputStream(input)
        val wrappedLen = header.readInt()
        require(wrappedLen in 1..4096) { "bad wrapped key length" }
        val wrapped = ByteArray(wrappedLen).also(header::readFully)
        val dataKey = unwrap(wrapped)

        val ivLen = header.read()
        require(ivLen in 1..64) { "bad IV length" }
        val iv = ByteArray(ivLen).also(header::readFully)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(dataKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        return javax.crypto.CipherInputStream(input, cipher)
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
