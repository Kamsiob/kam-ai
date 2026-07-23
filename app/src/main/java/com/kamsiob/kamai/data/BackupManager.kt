package com.kamsiob.kamai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/**
 * Ties the pieces together: gather the whole database, encode it, encrypt it with
 * the user's passphrase, and write it to a file they chose; and the reverse. The
 * only two calls the UI needs.
 */
class BackupManager(
    private val repository: KamRepository,
    private val appVersion: String,
    private val schemaVersion: Int,
) {
    data class ImportResult(
        val ok: Boolean,
        val message: String,
        /** Models and packs the backup listed but this device lacks, to re-download. */
        val missingArtifacts: List<ArtifactEntity> = emptyList(),
    )

    suspend fun export(out: OutputStream, passphrase: String) = withContext(Dispatchers.IO) {
        val snapshot = repository.exportSnapshot()
        val json = BackupCodec.encode(snapshot, appVersion, schemaVersion).toString()
        BackupCrypto.encrypt(json.toByteArray(Charsets.UTF_8), passphrase, out)
    }

    suspend fun import(input: InputStream, passphrase: String, replace: Boolean): ImportResult =
        withContext(Dispatchers.IO) {
            val bytes = try {
                BackupCrypto.decrypt(input, passphrase)
            } catch (e: BackupCrypto.WrongPassphraseException) {
                return@withContext ImportResult(false, "That passphrase did not open the backup.")
            } catch (e: BackupCrypto.NotABackupException) {
                return@withContext ImportResult(false, "That file is not a Kam AI backup.")
            } catch (e: Exception) {
                return@withContext ImportResult(false, "That backup could not be read.")
            }
            val snapshot = try {
                BackupCodec.decode(JSONObject(String(bytes, Charsets.UTF_8)))
            } catch (e: Exception) {
                return@withContext ImportResult(false, "That backup is damaged and could not be read.")
            }
            repository.importSnapshot(snapshot, replace)

            // Which models and packs did the backup have that this phone does not?
            val here = repository.exportSnapshot().artifacts.map { it.id }.toSet()
            val missing = snapshot.artifacts.filter { art ->
                art.id !in here ||
                    !fileExists(art)
            }
            ImportResult(
                ok = true,
                message = if (missing.isEmpty()) "Restored." else
                    "Restored. Re-download your models and packs in Settings; backups don't include the large files.",
                missingArtifacts = missing,
            )
        }

    private fun fileExists(art: ArtifactEntity): Boolean = when (art.kind) {
        ArtifactKind.LLM -> java.io.File(repository.modelsDir(), art.fileName).exists()
        ArtifactKind.STT, ArtifactKind.TTS_VOICE -> java.io.File(repository.voiceDir(), art.fileName).exists()
        ArtifactKind.PACK -> java.io.File(repository.packsDir(), art.fileName).exists()
    }
}
