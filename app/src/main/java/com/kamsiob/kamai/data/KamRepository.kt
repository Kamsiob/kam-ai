package com.kamsiob.kamai.data

import android.app.ActivityManager
import android.content.Context
import com.kamsiob.kamai.download.Downloader
import com.kamsiob.kamai.model.ModelCatalog
import com.kamsiob.kamai.model.TierModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

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
     * Total device RAM in whole gigabytes. Read locally, never leaves the
     * device, and needs no Data Safety disclosure.
     *
     * Rounded rather than truncated because manufacturers report slightly under
     * the advertised figure once the kernel has taken its share, and a phone
     * sold as 8 GB reporting 7.6 should still be treated as an 8 GB phone.
     */
    fun totalRamGb(): Int {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return (info.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)).roundToInt()
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

    suspend fun registerModel(model: TierModel, file: File) {
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
        db.artifacts().setActive(ArtifactKind.LLM, model.id)
    }

    suspend fun deleteArtifact(id: String) {
        db.artifacts().byId(id)?.let { artifact ->
            when (artifact.kind) {
                ArtifactKind.LLM -> File(modelsDir(), artifact.fileName).delete()
                else -> File(downloader.directoryFor(artifact.kind.name.lowercase()), artifact.fileName).delete()
            }
        }
        db.artifacts().delete(id)
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

    suspend fun setTitle(conversationId: String, title: String) =
        db.conversations().setTitle(conversationId, title)

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

    suspend fun remember(text: String, sourceConversationId: String?) {
        // Never store the same fact twice; the memory screen has to stay
        // readable, and duplicates eat the context budget for no gain.
        if (db.memory().countMatching(text) > 0) return
        val now = System.currentTimeMillis()
        db.memory().upsert(
            MemoryEntity(
                id = UUID.randomUUID().toString(), text = text,
                createdAt = now, updatedAt = now,
                sourceConversationId = sourceConversationId,
            ),
        )
    }

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
    }
}
