package com.kamsiob.kamai.data

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts a backup with a passphrase the user chooses, so the file is safe to
 * store anywhere (a cloud drive, a memory card) without exposing private
 * conversations. AES-256-GCM with a PBKDF2-derived key. The file is
 * self-describing: a magic marker, the salt, the nonce, then the ciphertext, so
 * import needs only the file and the passphrase.
 *
 * The app's at-rest key is device-bound in the Keystore and cannot travel; a
 * backup must be openable on a new phone, which is why it uses a passphrase
 * instead. There is no recovery if the passphrase is lost, which is the honest
 * cost of a backup no one else can read.
 */
object BackupCrypto {

    private val MAGIC = "KAMBAK01".toByteArray(Charsets.US_ASCII)
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    class WrongPassphraseException : Exception("That passphrase did not open the backup.")
    class NotABackupException : Exception("That file is not a Kam AI backup.")

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    fun encrypt(plaintext: ByteArray, passphrase: String, out: OutputStream) {
        val rng = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rng.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rng.nextBytes(it) }
        val key = deriveKey(passphrase.toCharArray(), salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        out.write(MAGIC)
        out.write(salt)
        out.write(iv)
        out.write(ciphertext)
        out.flush()
    }

    fun decrypt(input: InputStream, passphrase: String): ByteArray {
        val all = input.readBytes()
        if (all.size < MAGIC.size + SALT_LEN + IV_LEN || !all.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw NotABackupException()
        }
        var p = MAGIC.size
        val salt = all.copyOfRange(p, p + SALT_LEN); p += SALT_LEN
        val iv = all.copyOfRange(p, p + IV_LEN); p += IV_LEN
        val ciphertext = all.copyOfRange(p, all.size)
        val key = deriveKey(passphrase.toCharArray(), salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return try {
            cipher.doFinal(ciphertext)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw WrongPassphraseException()
        }
    }
}
