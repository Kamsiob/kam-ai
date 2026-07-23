package com.kamsiob.kamai.data

import android.app.ActivityManager
import android.content.Context
import com.kamsiob.kamai.download.Downloader
import com.kamsiob.kamai.model.ModelCatalog
import com.kamsiob.kamai.model.marketedRamGb
import com.kamsiob.kamai.model.TierModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID

/**
 * The single seam between the app's screens and everything that stores or
 * fetches. Screens never touch a DAO or the filesystem directly.
 */
class KamRepository(
    private val context: Context,
    private val db: KamDatabase,
    val downloader: Downloader = Downloader(context),
) {

    object Keys {
        const val ONBOARDING_DONE = "onboarding.done"
        const val CHATS_VIEW = "chats.view"
        const val THEME = "theme"
        const val WEB_SEARCH_ENABLED = "search.enabled"
        const val WEB_SEARCH_ENDPOINT = "search.endpoint"
        const val CONFIRM_CHAT_DELETE = "confirm.chat.delete"
        const val MEMORY_MODE = "memory.mode"
    }

    // Settings

    suspend fun setting(key: String): String? = db.settings().get(key)

    fun observeSetting(key: String): Flow<String?> = db.settings().observe(key)

    suspend fun putSetting(key: String, value: String) =
        db.settings().put(SettingEntity(key, value))

    suspend fun isOnboardingDone(): Boolean = setting(Keys.ONBOARDING_DONE) == "true"

    suspend fun markOnboardingDone() = putSetting(Keys.ONBOARDING_DONE, "true")

    /** Replaying onboarding from Settings must not wipe anything. */
    suspend fun replayOnboarding() = putSetting(Keys.ONBOARDING_DONE, "false")

    // Device

    /**
     * The memory this phone is sold with, in whole gigabytes. Read locally,
     * never leaves the device, and needs no Data Safety disclosure.
     *
     * See [marketedRamGb] for why the reported figure cannot be used directly.
     */
    fun totalRamGb(): Int {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return marketedRamGb(info.totalMem)
    }

    // Models and artifacts

    fun modelsDir(): File = downloader.directoryFor("models")

    fun fileFor(model: TierModel): File = File(modelsDir(), "${model.id}.gguf")

    fun observeArtifacts(): Flow<List<ArtifactEntity>> = db.artifacts().observeAll()

    fun observeActiveModel(): Flow<TierModel?> =
        db.artifacts().observeActive(ArtifactKind.LLM).map { entity ->
            entity?.let { ModelCatalog.byId(it.id) }
        }

    suspend fun activeModel(): TierModel? =
        db.artifacts().active(ArtifactKind.LLM)?.let { ModelCatalog.byId(it.id) }

    suspend fun registerModel(model: TierModel, file: File, makeActive: Boolean = true) {
        db.artifacts().upsert(
            ArtifactEntity(
                id = model.id,
                kind = ArtifactKind.LLM,
                displayName = model.displayName,
                fileName = file.name,
                sizeBytes = file.length(),
                sha256 = model.sha256,
                version = "1",
                installedAt = System.currentTimeMillis(),
            ),
        )
        // The manager decides activation now; only adopt eagerly when asked.
        if (makeActive) db.artifacts().setActive(ArtifactKind.LLM, model.id)
    }

    /** Clears the active-model reference entirely, for the no-model state. */
    suspend fun clearActiveModel() = db.artifacts().clearActive(ArtifactKind.LLM)

    /** Every installed LLM as a catalogue model, for the manager's fallbacks. */
    suspend fun installedModels(): List<TierModel> =
        installedModelIds().mapNotNull { ModelCatalog.byId(it) }

    /** Removes a partial .part download for a model that was mid-flight. */
    fun deletePartialDownload(model: TierModel) {
        java.io.File(fileFor(model).parentFile, fileFor(model).name + ".part").delete()
    }

    suspend fun setActiveModel(id: String) = db.artifacts().setActive(ArtifactKind.LLM, id)

    /** Installed LLM ids, so callers can reason about how many models remain. */
    suspend fun installedModelIds(): List<String> =
        db.artifacts().observeByKind(ArtifactKind.LLM).firstOrNull().orEmpty().map { it.id }

    suspend fun deleteArtifact(id: String) {
        db.artifacts().byId(id)?.let { artifact ->
            when (artifact.kind) {
                ArtifactKind.LLM -> File(modelsDir(), artifact.fileName).delete()
                else -> File(downloader.directoryFor(artifact.kind.name.lowercase()), artifact.fileName).delete()
            }
        }
        db.artifacts().delete(id)
    }

    /**
     * Picks the model to fall back to after [deletedId] is removed, so the app
     * is never left with no usable model. Returns null when nothing else is
     * installed, which sends the user back to the download flow.
     */
    suspend fun nextModelAfterDeleting(deletedId: String): TierModel? {
        val remaining = installedModelIds().filter { it != deletedId }
        return remaining.firstNotNullOfOrNull { ModelCatalog.byId(it) }
    }

    // Voice: speech-to-text models, stored as STT artifacts.

    fun voiceDir(): File = downloader.directoryFor("voice")

    fun fileForStt(model: com.kamsiob.kamai.voice.SttModel): File =
        File(voiceDir(), model.fileName)

    fun observeSttArtifacts(): Flow<List<ArtifactEntity>> =
        db.artifacts().observeByKind(ArtifactKind.STT)

    suspend fun activeSttModel(): com.kamsiob.kamai.voice.SttModel? =
        db.artifacts().active(ArtifactKind.STT)
            ?.let { com.kamsiob.kamai.voice.SttCatalog.byId(it.id) }

    fun observeActiveSttModel(): Flow<com.kamsiob.kamai.voice.SttModel?> =
        db.artifacts().observeActive(ArtifactKind.STT).map { entity ->
            entity?.let { com.kamsiob.kamai.voice.SttCatalog.byId(it.id) }
        }

    suspend fun installedSttIds(): List<String> =
        db.artifacts().observeByKind(ArtifactKind.STT).firstOrNull().orEmpty().map { it.id }

    suspend fun registerSttModel(
        model: com.kamsiob.kamai.voice.SttModel,
        file: File,
        makeActive: Boolean = true,
    ) {
        db.artifacts().upsert(
            ArtifactEntity(
                id = model.id,
                kind = ArtifactKind.STT,
                displayName = model.displayName,
                fileName = file.name,
                sizeBytes = file.length(),
                sha256 = model.sha256,
                version = "1",
                installedAt = System.currentTimeMillis(),
            ),
        )
        // First voice model becomes active; a later one only if asked.
        if (makeActive || db.artifacts().active(ArtifactKind.STT) == null) {
            db.artifacts().setActive(ArtifactKind.STT, model.id)
        }
    }

    suspend fun setActiveSttModel(id: String) = db.artifacts().setActive(ArtifactKind.STT, id)

    fun deletePartialSttDownload(model: com.kamsiob.kamai.voice.SttModel) {
        File(voiceDir(), model.fileName + ".part").delete()
    }

    // Conversations and messages

    fun observeConversations(): Flow<List<ConversationSummary>> =
        db.conversations().observeActive()

    fun searchConversations(query: String): Flow<List<ConversationSummary>> =
        db.conversations().search(query)

    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        db.messages().observe(conversationId)

    suspend fun conversation(id: String): ConversationEntity? = db.conversations().byId(id)

    suspend fun createConversation(mode: Mode, projectId: String? = null): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        db.conversations().upsert(
            ConversationEntity(
                id = id, title = null, mode = mode, projectId = projectId,
                createdAt = now, updatedAt = now,
            ),
        )
        return id
    }

    suspend fun addMessage(
        conversationId: String,
        role: Role,
        content: String,
        incomplete: Boolean = false,
    ): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        db.messages().insert(
            MessageEntity(
                id = id, conversationId = conversationId, role = role,
                content = content, createdAt = now, incomplete = incomplete,
            ),
        )
        db.conversations().touch(conversationId, now)
        return id
    }

    suspend fun updateMessage(id: String, content: String, incomplete: Boolean) =
        db.messages().setContent(id, content, incomplete)

    suspend fun finishMessage(id: String, reason: String?) = db.messages().finish(id, reason)

    suspend fun messages(conversationId: String): List<MessageEntity> =
        db.messages().forConversation(conversationId)

    /** Auto-title from the model; leaves a hand-renamed conversation alone. */
    suspend fun autoTitle(conversationId: String, title: String) =
        db.conversations().autoTitle(conversationId, title)

    /** The user renamed it. This sticks and is never auto-overwritten. */
    suspend fun renameConversation(conversationId: String, title: String) =
        db.conversations().setManualTitle(conversationId, title.trim())

    suspend fun setPinned(id: String, pinned: Boolean) = db.conversations().setPinned(id, pinned)

    suspend fun setArchived(id: String, archived: Boolean) =
        db.conversations().setArchived(id, archived, System.currentTimeMillis())

    suspend fun deleteConversation(id: String) = db.conversations().delete(id)

    /** Editing truncates the tail and re-answers. There is no branching. */
    suspend fun truncateAfter(conversationId: String, message: MessageEntity) =
        db.messages().deleteAfter(conversationId, message.createdAt)

    suspend fun deleteMessage(id: String) = db.messages().delete(id)

    /** Called at startup so a process death does not leave half a reply looking whole. */
    suspend fun repairIncompleteMessages(): Int = db.messages().repairIncomplete()

    // Follow-ups

    fun observeOpenFollowUps(): Flow<List<FollowUpEntity>> = db.followUps().observeOpen()

    fun observeCompletedFollowUps(): Flow<List<FollowUpEntity>> = db.followUps().observeCompleted()

    fun observeOpenFollowUpCount(): Flow<Int> = db.followUps().observeOpenCount()

    suspend fun flag(
        snippet: String,
        mode: Mode,
        conversationId: String?,
        messageId: String?,
    ): String {
        val id = UUID.randomUUID().toString()
        db.followUps().upsert(
            FollowUpEntity(
                id = id, snippet = snippet, sourceMode = mode,
                conversationId = conversationId, messageId = messageId,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return id
    }

    suspend fun setFollowUpCompleted(id: String, completed: Boolean) =
        db.followUps().setCompleted(
            id, completed, if (completed) System.currentTimeMillis() else null,
        )

    suspend fun setFollowUpNote(id: String, note: String?) = db.followUps().setNote(id, note)

    suspend fun deleteFollowUp(id: String) = db.followUps().delete(id)

    // Projects

    fun observeProjects(): Flow<List<ProjectEntity>> = db.projects().observeAll()

    suspend fun project(id: String): ProjectEntity? = db.projects().byId(id)

    suspend fun upsertProject(id: String?, name: String, instructions: String): String {
        val now = System.currentTimeMillis()
        val projectId = id ?: UUID.randomUUID().toString()
        val existing = id?.let { db.projects().byId(it) }
        db.projects().upsert(
            ProjectEntity(
                id = projectId, name = name, instructions = instructions,
                createdAt = existing?.createdAt ?: now, updatedAt = now,
            ),
        )
        return projectId
    }

    suspend fun deleteProject(id: String) = db.projects().delete(id)

    // Memory

    fun observeMemory(): Flow<List<MemoryEntity>> = db.memory().observeAll()

    suspend fun recentMemory(limit: Int): List<String> =
        db.memory().mostRecent(limit).map { it.text }

    suspend fun remember(text: String, sourceConversationId: String?, auto: Boolean = false) {
        // Never store the same fact twice; the memory screen has to stay
        // readable, and duplicates eat the context budget for no gain.
        if (db.memory().countMatching(text) > 0) return
        val now = System.currentTimeMillis()
        db.memory().upsert(
            MemoryEntity(
                id = UUID.randomUUID().toString(), text = text,
                createdAt = now, updatedAt = now,
                sourceConversationId = sourceConversationId, auto = auto,
            ),
        )
    }

    suspend fun forgetAllMemory() = db.memory().deleteAll()

    suspend fun memoryMode(): com.kamsiob.kamai.llm.MemoryMode =
        runCatching {
            com.kamsiob.kamai.llm.MemoryMode.valueOf(setting(Keys.MEMORY_MODE).orEmpty())
        }.getOrDefault(com.kamsiob.kamai.llm.MemoryMode.MANUAL)

    suspend fun setMemoryMode(mode: com.kamsiob.kamai.llm.MemoryMode) =
        putSetting(Keys.MEMORY_MODE, mode.name)

    suspend fun forget(id: String) = db.memory().deleteById(id)

    /**
     * Delete everything. Downloaded models are optional because re-downloading
     * several gigabytes is a real cost and not everyone means that by "delete
     * my data".
     */
    suspend fun deleteEverything(includeDownloads: Boolean) {
        db.conversations().deleteAll()
        db.projects().deleteAll()
        db.memory().deleteAll()
        db.followUps().deleteAll()
        db.discover().deleteAllDrawn()
        db.discover().deleteAllSaved()
        db.discover().deleteAllStats()

        if (includeDownloads) {
            db.artifacts().observeAll().let { }
            modelsDir().listFiles()?.forEach { it.delete() }
        }
    }

    companion object {
        @Volatile
        private var instance: KamRepository? = null

        fun get(context: Context): KamRepository =
            instance ?: synchronized(this) {
                instance ?: KamRepository(
                    context.applicationContext,
                    KamDatabase.get(context),
                ).also { instance = it }
            }

        /** Drops the cached repository so it is rebuilt against a fresh database
         *  after a forgot-code wipe. */
        @Synchronized
        fun forgetInstance() { instance = null }
    }
}
