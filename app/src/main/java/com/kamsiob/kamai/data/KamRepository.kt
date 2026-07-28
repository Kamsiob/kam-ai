package com.kamsiob.kamai.data

import androidx.room.withTransaction
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
import kotlinx.coroutines.withContext
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
        const val ONBOARDING_SLIDE = "onboarding.slide"

        /** Whether the one-time explanation of the mode control has been seen (#93). */
        const val MODE_HINT_SEEN = "modebar.hint.seen"
        const val CHATS_VIEW = "chats.view"

        /** Projects keeps its own density, since the two screens hold different
         *  things and somebody may well want a grid of projects and a list of
         *  chats (#50). */
        const val PROJECTS_VIEW = "projects.view"

        /** Measured decode speed per model, on this phone (item 22). */
        fun measuredSpeed(modelId: String) = "perf.decode.$modelId"
        const val THEME = "theme"
        const val WEB_SEARCH_ENABLED = "search.enabled"
        const val WEB_SEARCH_ENDPOINT = "search.endpoint"
        const val CONFIRM_CHAT_DELETE = "confirm.chat.delete"
        const val MEMORY_MODE = "memory.mode"

        /** How many times the app has been opened, for the bookmark note (#84). */
        const val SESSION_COUNT = "reminder.sessions"

        /** Set once the user dismisses the bookmark note, which settles it. */
        const val REMINDER_DISMISSED = "reminder.dismissed"
        const val WORKBENCH_INPUT = "workbench.input"
        const val WORKBENCH_OUTPUT = "workbench.output"
        const val SYSTEM_INSTRUCTIONS = "system.instructions"
        const val ASSISTANT_DEFAULT_VOICE = "assistant.default.voice"
        const val AUTO_ARCHIVE = "chats.autoarchive"
    }

    /** The cap on the user's system-wide instructions, in characters. Roughly
     *  500 tokens, a sensible slice of a small model's window. */
    val systemInstructionsMax: Int get() = 2000

    // Settings

    suspend fun setting(key: String): String? = db.settings().get(key)

    fun observeSetting(key: String): Flow<String?> = db.settings().observe(key)

    suspend fun putSetting(key: String, value: String) =
        db.settings().put(SettingEntity(key, value))

    /**
     * Whether the user has already been told, in a transcript, what [mode] does.
     *
     * Switching mode mid-conversation writes a note explaining the mode it is
     * switching to. Starting a chat *in* a mode wrote nothing, because the note
     * is only written when there is already something to mark, so somebody whose
     * first Brainstorm conversation began from the Chats control was never told
     * that Brainstorm will not hand them ideas. It simply started asking
     * questions (#28).
     *
     * Once per mode, ever, rather than on every new conversation: the tenth
     * Brainstorm chat does not need the paragraph again.
     */
    suspend fun wasModeExplained(mode: Mode): Boolean =
        setting("mode.explained.${mode.name}") == "1"

    suspend fun markModeExplained(mode: Mode) = putSetting("mode.explained.${mode.name}", "1")

    /** The user's system-wide instructions, applied to every conversation. */
    suspend fun userInstructions(): String = setting(Keys.SYSTEM_INSTRUCTIONS).orEmpty()

    fun observeUserInstructions(): Flow<String?> = observeSetting(Keys.SYSTEM_INSTRUCTIONS)

    suspend fun setUserInstructions(text: String) =
        putSetting(Keys.SYSTEM_INSTRUCTIONS, text.take(systemInstructionsMax))

    suspend fun isOnboardingDone(): Boolean = setting(Keys.ONBOARDING_DONE) == "true"

    suspend fun markOnboardingDone() = putSetting(Keys.ONBOARDING_DONE, "true")

    /**
     * The slide onboarding was last showing, so leaving and coming back resumes
     * there instead of at the beginning (#117).
     *
     * Persisted rather than held in memory because the case that matters is the
     * process being gone: the model slide is where a multi-gigabyte download
     * starts, which is exactly when somebody leaves to check their wifi or their
     * storage, and it is the furthest slide to have to walk back to.
     */
    suspend fun onboardingSlide(): Int = setting(Keys.ONBOARDING_SLIDE)?.toIntOrNull() ?: 0

    /**
     * The mode control is how a new chat starts, and it reads as a filter. The
     * explanation for that is shown once, ever, rather than living permanently at
     * the bottom of the screen: the confusion is about learning a control, and a
     * label sitting there for years to teach something learned in one tap is a
     * poor trade (#93).
     */
    suspend fun modeHintSeen(): Boolean = setting(Keys.MODE_HINT_SEEN) == "true"

    suspend fun markModeHintSeen() = putSetting(Keys.MODE_HINT_SEEN, "true")

    suspend fun setOnboardingSlide(index: Int) =
        putSetting(Keys.ONBOARDING_SLIDE, index.toString())

    /** Replaying onboarding from Settings must not wipe anything. */
    suspend fun replayOnboarding() {
        putSetting(Keys.ONBOARDING_DONE, "false")
        // Replaying means from the start; a resume point left over from the
        // first run would drop the user into the middle of it.
        putSetting(Keys.ONBOARDING_SLIDE, "0")
        // The mode control explanation is part of the introduction, so somebody
        // asking to see the introduction again is asking for that too. "Shown
        // once, ever" means not shown twice by accident, not that asking for it
        // is refused (#93).
        putSetting(Keys.MODE_HINT_SEEN, "false")
    }

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

    /**
     * Free space on the volume downloads land on (#79).
     *
     * Checked before a download is offered, not when it fails. The existing
     * `Downloader.freeSpaceProblem` runs at download time, by which point the
     * user has already chosen and waited.
     */
    fun freeDownloadBytes(): Long = runCatching { modelsDir().usableSpace }.getOrDefault(0L)

    /**
     * What a model really costs on disk, including any separate vision
     * projection file, which is a real size and easy to forget (#79).
     */
    fun requiredBytesFor(model: com.kamsiob.kamai.model.TierModel): Long = model.downloadBytes

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

    /** Every installed LLM as a catalog model, for the manager's fallbacks. */
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

    /**
     * The application context, for the few things that need one and are not
     * database work: the conversation KV state files (#52) live on disk beside
     * the database and are keyed by the same Keystore entry.
     */
    val appContext: Context get() = context

    /**
     * Whether a model download is running right now (#78).
     *
     * Used to tell "still downloading, hold on" apart from "there is no model and
     * nothing is coming", which are the same failure to the engine and two
     * completely different sentences to a person.
     */
    fun hasModelDownloadInFlight(): Boolean =
        com.kamsiob.kamai.download.Downloads.items.value.any {
            it.kind == "model" &&
                it.status != com.kamsiob.kamai.download.Downloads.Status.DONE &&
                it.status != com.kamsiob.kamai.download.Downloads.Status.FAILED
        }

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

    // Voice: text-to-speech voices, stored as TTS_VOICE artifacts.

    fun fileForTts(voice: com.kamsiob.kamai.voice.TtsVoice): File =
        File(voiceDir(), voice.fileName)

    suspend fun activeTtsVoice(): com.kamsiob.kamai.voice.TtsVoice? =
        db.artifacts().active(ArtifactKind.TTS_VOICE)
            ?.let { com.kamsiob.kamai.voice.TtsCatalog.byId(it.id) }

    fun observeActiveTtsVoice(): Flow<com.kamsiob.kamai.voice.TtsVoice?> =
        db.artifacts().observeActive(ArtifactKind.TTS_VOICE).map { entity ->
            entity?.let { com.kamsiob.kamai.voice.TtsCatalog.byId(it.id) }
        }

    suspend fun installedTtsIds(): List<String> =
        db.artifacts().observeByKind(ArtifactKind.TTS_VOICE).firstOrNull().orEmpty().map { it.id }

    fun observeTtsArtifacts(): Flow<List<ArtifactEntity>> =
        db.artifacts().observeByKind(ArtifactKind.TTS_VOICE)

    suspend fun registerTtsVoice(
        voice: com.kamsiob.kamai.voice.TtsVoice,
        file: File,
        makeActive: Boolean = true,
    ) {
        db.artifacts().upsert(
            ArtifactEntity(
                id = voice.id,
                kind = ArtifactKind.TTS_VOICE,
                displayName = voice.displayName,
                fileName = file.name,
                sizeBytes = file.length(),
                sha256 = voice.sha256,
                version = "1",
                installedAt = System.currentTimeMillis(),
            ),
        )
        if (makeActive || db.artifacts().active(ArtifactKind.TTS_VOICE) == null) {
            db.artifacts().setActive(ArtifactKind.TTS_VOICE, voice.id)
        }
    }

    suspend fun setActiveTtsVoice(id: String) = db.artifacts().setActive(ArtifactKind.TTS_VOICE, id)

    fun deletePartialTtsDownload(voice: com.kamsiob.kamai.voice.TtsVoice) {
        File(voiceDir(), voice.fileName + ".part").delete()
    }

    // Discover: content packs and moments.

    fun packsDir(): File = downloader.directoryFor("packs")

    /**
     * Fetches the pack manifest from the GitHub release. Returns the available
     * packs, or an empty list if offline or the manifest cannot be read. Installed
     * packs still work offline; the manifest is only needed to discover and get
     * new ones.
     */
    suspend fun fetchDiscoverManifest(): List<com.kamsiob.kamai.discover.PackInfo> =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val req = okhttp3.Request.Builder().url(DISCOVER_MANIFEST_URL).build()
                downloader.httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList()
                    val body = resp.body?.string() ?: return@use emptyList()
                    val root = org.json.JSONObject(body)
                    val arr = root.getJSONArray("packs")
                    (0 until arr.length()).map { i ->
                        val o = arr.getJSONObject(i)
                        com.kamsiob.kamai.discover.PackInfo(
                            id = o.getString("id"),
                            name = o.getString("name"),
                            description = o.getString("description"),
                            moments = o.getInt("moments"),
                            sizeBytes = o.getLong("sizeBytes"),
                            version = o.getInt("version"),
                            fileName = o.getString("fileName"),
                            downloadUrl = o.getString("downloadUrl"),
                            sha256 = o.getString("sha256"),
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }

    fun fileForPack(fileName: String): File = File(packsDir(), fileName)

    fun observePackArtifacts(): Flow<List<ArtifactEntity>> =
        db.artifacts().observeByKind(ArtifactKind.PACK)

    suspend fun installedPackIds(): List<String> =
        db.artifacts().observeByKind(ArtifactKind.PACK).firstOrNull().orEmpty().map { it.id }

    /**
     * The version of each installed pack, so a newer one in the manifest can be
     * offered rather than silently ignored.
     *
     * Without this, whatever pack somebody downloads on the day they install is
     * the pack they keep forever: installed packs are matched by id, so an
     * improved pack under the same id reads as already installed. That was very
     * nearly shipped, and it is the kind of thing that becomes permanent at
     * launch rather than merely wrong.
     */
    suspend fun installedPackVersions(): Map<String, String> =
        db.artifacts().observeByKind(ArtifactKind.PACK).firstOrNull().orEmpty()
            .associate { it.id to it.version }

    suspend fun installedPackFileNames(): Map<String, String> =
        db.artifacts().observeByKind(ArtifactKind.PACK).firstOrNull().orEmpty()
            .associate { it.id to it.fileName }

    suspend fun registerPack(pack: com.kamsiob.kamai.discover.PackInfo, file: File) {
        db.artifacts().upsert(
            ArtifactEntity(
                id = pack.id,
                kind = ArtifactKind.PACK,
                displayName = pack.name,
                fileName = file.name,
                sizeBytes = file.length(),
                sha256 = pack.sha256,
                version = pack.version.toString(),
                installedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun deletePartialPackDownload(fileName: String) {
        File(packsDir(), "$fileName.part").delete()
    }

    /**
     * Deals a moment the user has not seen, drawn at random across the installed
     * packs (or one pack if [onlyPackId] is set). Returns null only when every
     * moment in scope has been seen, which the UI turns into a reshuffle offer.
     */
    suspend fun dealMoment(onlyPackId: String? = null): com.kamsiob.kamai.discover.Moment? {
        val installed = db.artifacts().observeByKind(ArtifactKind.PACK).firstOrNull().orEmpty()
            .filter { onlyPackId == null || it.id == onlyPackId }
        val pool = mutableListOf<Pair<String, String>>() // packId to momentId
        for (artifact in installed) {
            val file = File(packsDir(), artifact.fileName)
            if (!file.exists()) continue
            val drawn = db.discover().drawnIds(artifact.id).toSet()
            for (id in com.kamsiob.kamai.discover.PackReader.allIds(file)) {
                if (id !in drawn) pool.add(artifact.id to id)
            }
        }
        if (pool.isEmpty()) return null
        val (packId, momentId) = pool[kotlin.random.Random.nextInt(pool.size)]
        val fileName = installed.first { it.id == packId }.fileName
        return com.kamsiob.kamai.discover.PackReader.byId(packId, File(packsDir(), fileName), momentId)
    }

    suspend fun setDiscoverGrounding(conversationId: String, passage: String) =
        db.conversations().setGrounding(conversationId, passage)

    /** Lifts a grounded chat's scope so it continues as an ordinary open chat,
     *  carrying its history forward. Backs the "continue in open chat" escape. */
    suspend fun clearGrounding(conversationId: String) =
        db.conversations().clearGrounding(conversationId)

    suspend fun momentById(packId: String, momentId: String): com.kamsiob.kamai.discover.Moment? {
        val fileName = installedPackFileNames()[packId] ?: return null
        return com.kamsiob.kamai.discover.PackReader.byId(packId, File(packsDir(), fileName), momentId)
    }

    /** True when [onlyPackId] (or any installed pack) still has unseen moments. */
    suspend fun hasUnseen(onlyPackId: String? = null): Boolean = dealMoment(onlyPackId) != null

    // Discover state passthroughs, all backed by DiscoverDao.
    suspend fun markDrawn(packId: String, momentId: String) =
        db.discover().markDrawn(DrawnMomentEntity(packId, momentId, System.currentTimeMillis()))
    suspend fun markReaderOpened(packId: String, momentId: String) =
        db.discover().markReaderOpened(packId, momentId)
    suspend fun wasReaderOpened(packId: String, momentId: String): Boolean =
        db.discover().wasReaderOpened(packId, momentId) ?: false
    suspend fun reshuffle(packId: String) = db.discover().reshuffle(packId)
    suspend fun reshuffleAll() = installedPackIds().forEach { db.discover().reshuffle(it) }
    /** Saving a moment is the same bookmark action as flagging anything else: it
     *  lands in the single follow-ups list, carrying the moment so it can be
     *  reopened. The title is the snippet; the source is DISCOVER. */
    suspend fun saveMoment(m: com.kamsiob.kamai.discover.Moment) {
        if (isMomentSaved(m.packId, m.id)) return
        flag(m.title, Mode.DISCOVER, conversationId = null, messageId = null, packId = m.packId, momentId = m.id)
    }
    suspend fun unsaveMoment(packId: String, momentId: String) =
        db.followUps().deleteMoment(packId, momentId)

    /**
     * Opens a saved Discover moment as a grounded discussion, the same handoff the
     * Discover reader uses. Returns the new conversation id, or null when the pack
     * that holds the moment is no longer installed. Lives here so the Follow-ups
     * list can open a saved moment without pulling in the Discover view model.
     */
    /**
     * What the scoped Discover surface needs to introduce itself: the
     * conversation to run, and the passage it is confined to (#11).
     *
     * The title and source travel with the id because the surface is a sheet
     * over Discover, not a chat screen, and a sheet that opened with only a
     * conversation id could say nothing about what it is scoped to until the
     * titler had been round.
     */
    data class GroundedDiscussion(
        val conversationId: String,
        val title: String,
        /**
         * The moment's topic, not its `sourceTitle`. For a Wikipedia pack the
         * source title is the article name, which is also the moment title, so
         * the sheet header read "Merovingian dynasty" over "from Merovingian
         * dynasty". The topic is the one thing the header does not already say.
         */
        val source: String,
    )

    suspend fun openMomentDiscussion(packId: String, momentId: String): GroundedDiscussion? {
        val m = momentById(packId, momentId) ?: return null
        val id = createConversation(Mode.DISCOVER)
        addMessage(
            id, Role.ASSISTANT,
            "Let's talk about \"${m.title}\". Ask me anything about this passage.",
            incomplete = false,
        )
        setDiscoverGrounding(id, m.passage)
        return GroundedDiscussion(id, m.title, m.topic)
    }
    fun observeSavedMoments(): Flow<List<FollowUpEntity>> = db.followUps().observeSavedMoments()
    suspend fun isMomentSaved(packId: String, momentId: String): Boolean =
        db.followUps().countMoment(packId, momentId) > 0
    suspend fun recordQuiz(packId: String, asked: Int, right: Int) =
        db.discover().recordQuiz(packId, asked, right)
    fun observeQuizStats(): Flow<List<QuizStatsEntity>> = db.discover().observeAllStats()

    // Conversation attachments (a document the model reads), kept in settings so
    // no schema change is needed and a large document stores as one value.

    suspend fun setAttachment(conversationId: String, name: String, text: String) {
        putSetting("attach.name.$conversationId", name)
        putSetting("attach.text.$conversationId", text)
    }

    suspend fun attachmentName(conversationId: String): String? = setting("attach.name.$conversationId")
    suspend fun attachmentText(conversationId: String): String? = setting("attach.text.$conversationId")

    suspend fun clearAttachment(conversationId: String) {
        db.settings().remove("attach.name.$conversationId")
        db.settings().remove("attach.text.$conversationId")
    }

    // Backup and restore

    suspend fun exportSnapshot(): BackupCodec.Snapshot = BackupCodec.Snapshot(
        conversations = db.conversations().allForBackup(),
        messages = db.messages().allForBackup(),
        projects = db.projects().allForBackup(),
        memory = db.memory().allForBackup(),
        followUps = db.followUps().allForBackup(),
        drawn = db.discover().allDrawnForBackup(),
        quizStats = db.discover().allStatsForBackup(),
        artifacts = db.artifacts().allForBackup(),
        settings = db.settings().all(),
    )

    /**
     * Restores a snapshot. In [replace] mode the existing content is cleared
     * first; otherwise it is merged (rows with the same id are overwritten). The
     * artifacts table is deliberately not touched: it reflects which large files
     * are physically present on this device, which a backup does not carry, so the
     * caller offers to re-download anything the backup listed but this phone lacks.
     */
    /**
     * Restores a backup. In replace mode this wipes everything first, so it runs
     * as one transaction or not at all.
     *
     * Without the transaction the sequence was: delete every message,
     * conversation, project, memory, follow-up and Discover row, then insert the
     * backup's rows one at a time. Anything that interrupted the second half left
     * the user with the first half already done. A single malformed row, a
     * process death, or simply backing out of the screen mid-restore, and their
     * conversations were gone and the replacement only partly written. There is
     * no undo for that and the backup file cannot help, because the failure is
     * mid-import.
     *
     * `withTransaction` makes the whole thing atomic: either the restore lands
     * completely or the database is exactly as it was.
     */
    suspend fun importSnapshot(s: BackupCodec.Snapshot, replace: Boolean) = db.withTransaction {
        if (replace) {
            db.messages().deleteAll()
            db.conversations().deleteAll()
            db.projects().deleteAll()
            db.memory().deleteAllMemory()
            db.followUps().deleteAllFollowUps()
            db.discover().deleteAllDrawn()
            db.discover().deleteAllStats()
        }
        s.projects.forEach { db.projects().upsert(it) }
        s.conversations.forEach { db.conversations().upsert(it) }
        s.messages.forEach { db.messages().insert(it) }
        s.memory.forEach { db.memory().upsert(it) }
        // Follow-ups carry saved Discover moments too, since saving is unified. A
        // legacy backup's separate saved moments are folded into this list by the
        // codec, so importing an old file loses nothing.
        s.followUps.forEach { db.followUps().upsert(it) }
        s.drawn.forEach { db.discover().markDrawn(it) }
        s.quizStats.forEach { db.discover().upsertStats(it) }
        // Settings merge in both modes so the restored preferences take effect.
        s.settings.forEach { db.settings().put(it) }
    }

    // Conversations and messages

    fun observeConversations(): Flow<List<ConversationSummary>> =
        db.conversations().observeActive()

    /** Archived conversations, for the separate archived view. */
    fun observeArchived(): Flow<List<ConversationSummary>> =
        db.conversations().observeArchived()

    /** Reactive single conversation, so an open chat's title updates live. */
    fun observeConversation(id: String): Flow<ConversationEntity?> =
        db.conversations().observe(id)

    /**
     * Everything a search should reach, in one place (#87).
     *
     * Search used to be a client-side filter over the already-loaded conversation
     * list, matching a title and the single newest message. A phrase said in the
     * middle of a conversation was unfindable, and follow-ups, projects and saved
     * Discover moments were not searched at all. The full-text query existed and
     * nothing called it.
     *
     * Combined here rather than in the screen so the scope is one readable thing
     * and can be widened again without touching the UI.
     */
    fun search(query: String): Flow<SearchResults> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return kotlinx.coroutines.flow.flowOf(SearchResults())
        return kotlinx.coroutines.flow.combine(
            db.conversations().search(trimmed),
            db.followUps().search(trimmed),
            db.projects().search(trimmed),
        ) { conversations, followUps, projects ->
            SearchResults(conversations, followUps, projects)
        }
    }

    /** What a search found, by kind, so the screen can say where each came from. */
    data class SearchResults(
        val conversations: List<ConversationSummary> = emptyList(),
        val followUps: List<FollowUpEntity> = emptyList(),
        val projects: List<ProjectEntity> = emptyList(),
    ) {
        val isEmpty: Boolean
            get() = conversations.isEmpty() && followUps.isEmpty() && projects.isEmpty()
    }

    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        db.messages().observe(conversationId)

    suspend fun conversation(id: String): ConversationEntity? = db.conversations().byId(id)

    suspend fun createConversation(mode: Mode, projectId: String? = null): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        db.conversations().upsert(
            ConversationEntity(
                id = id, title = null, mode = mode, modesUsed = mode.name, projectId = projectId,
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
        /** Remembered facts put in front of the model for this answer (#16). */
        memoriesUsed: Int = 0,
    ): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        db.messages().insert(
            MessageEntity(
                id = id, conversationId = conversationId, role = role,
                content = content, createdAt = now, incomplete = incomplete,
                memoriesUsed = memoriesUsed,
            ),
        )
        db.conversations().touch(conversationId, now)
        return id
    }

    /** Persists a conversation's mode so an in-chat switch survives reopening. */
    suspend fun setConversationMode(id: String, mode: Mode) {
        db.conversations().setMode(id, mode, System.currentTimeMillis())
        // Record the mode in the conversation's used-set, appending in first-use
        // order and never duplicating, so the row dots and the mode filter show
        // every mode this conversation has been through.
        val current = db.conversations().modesUsed(id)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        if (mode.name !in current) {
            db.conversations().setModesUsed(id, (current + mode.name).joinToString(","))
        }
    }

    /** The set of modes a conversation has used, parsed from its stored list. */
    fun parseModesUsed(modesUsed: String): List<Mode> =
        modesUsed.split(",").mapNotNull { name ->
            runCatching { if (name == "CHAT") Mode.GENERAL else Mode.valueOf(name.trim()) }.getOrNull()
        }.distinct()

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

    // Workbench sessions and links (#32).

    /**
     * Records a Workbench run as a conversation so it appears in Chats with the
     * rest of the user's work rather than living in two settings strings that the
     * next run overwrites.
     *
     * The session is an ordinary conversation in BENCH mode holding two messages:
     * what was pasted in, and what came back. That is deliberate rather than a new
     * table. Everything the chat list already does, titling, pinning, archiving,
     * search, export, mode dots, then works on a Workbench session for free, and
     * the alternative was reimplementing all of it against a parallel store.
     *
     * Returns the session id, creating one on the first run and updating it
     * afterwards, so repeated transforms of the same text stay one session rather
     * than filling the list with near-duplicates.
     */
    suspend fun saveWorkbenchSession(
        sessionId: String?,
        instruction: String,
        input: String,
        output: String,
    ): String {
        val id = sessionId ?: createConversation(Mode.BENCH)
        // Rewritten rather than appended, because a Workbench session is the
        // current state of one piece of text, not a transcript of every attempt.
        db.messages().deleteForConversation(id)
        addMessage(id, Role.USER, "$instruction\n\n$input")
        addMessage(id, Role.ASSISTANT, output, incomplete = false)
        return id
    }

    /** The two halves of a Workbench pairing, each pointing at the other. */
    suspend fun linkConversations(a: String, b: String) {
        db.conversations().setLink(a, b)
        db.conversations().setLink(b, a)
    }

    /** Breaks the pairing from both sides, so neither is left pointing at a
     *  conversation that no longer considers itself linked. */
    suspend fun unlinkConversation(id: String) {
        val other = db.conversations().linkOf(id)
        db.conversations().setLink(id, null)
        if (other != null) db.conversations().setLink(other, null)
    }

    suspend fun linkedConversation(id: String): String? = db.conversations().linkOf(id)

    suspend fun mostRecentWorkbenchSession(): String? =
        db.conversations().mostRecentWorkbenchSession()

    fun observeLinkedConversation(id: String): Flow<String?> =
        observeConversation(id).map { it?.linkedConversationId }

    // Auto-archive (#31).

    /** The chosen window, live, for the settings row to reflect. */
    fun observeAutoArchive(): Flow<AutoArchive> =
        observeSetting(Keys.AUTO_ARCHIVE).map { AutoArchive.fromStored(it) }

    suspend fun autoArchive(): AutoArchive =
        AutoArchive.fromStored(setting(Keys.AUTO_ARCHIVE))

    suspend fun setAutoArchive(value: AutoArchive) =
        putSetting(Keys.AUTO_ARCHIVE, value.stored)

    /**
     * Which conversations an auto-archive pass would take right now, without
     * taking them.
     *
     * Separate from [runAutoArchive] so the setting can say how many it is about
     * to move before the user commits to it, which is the count-before-confirm
     * the issue asks for.
     */
    suspend fun autoArchiveCandidates(
        policy: AutoArchive? = null,
        openConversationId: String? = null,
    ): List<String> = AutoArchivePolicy.due(
        conversations = db.conversations().activeForAutoArchive(),
        // Null means "whatever is currently set". Read here rather than in a
        // default argument, which cannot call a suspend function.
        policy = policy ?: autoArchive(),
        now = System.currentTimeMillis(),
        openConversationId = openConversationId,
    )

    /**
     * Runs a pass and returns what it took, so the caller can offer an undo over
     * that exact set rather than guessing at it afterwards.
     */
    suspend fun runAutoArchive(openConversationId: String? = null): List<String> {
        val ids = autoArchiveCandidates(openConversationId = openConversationId)
        if (ids.isNotEmpty()) db.conversations().setArchivedBulk(ids, archived = true)
        return ids
    }

    /** Puts back exactly what a pass took. Timestamps were never touched. */
    suspend fun undoAutoArchive(ids: List<String>) {
        if (ids.isNotEmpty()) db.conversations().setArchivedBulk(ids, archived = false)
    }

    /**
     * Deletes a conversation, and releases the other half of a Workbench pairing
     * first (#32).
     *
     * The link is a plain id with no foreign key, so deleting one side would
     * otherwise leave the other pointing at a conversation that no longer exists.
     * That is not a crash, it is worse: an "Open the Workbench session" menu item
     * that opens an empty Workbench, with nothing to tell the user why. Clearing
     * it means the surviving half simply stops offering a link it cannot honour.
     */
    suspend fun deleteConversation(id: String) {
        unlinkConversation(id)
        db.conversations().delete(id)
        buryRow("conversations", id)
    }

    /** Editing truncates the tail and re-answers. There is no branching. */
    suspend fun truncateAfter(conversationId: String, message: MessageEntity) =
        db.messages().deleteAfter(conversationId, message.createdAt)

    suspend fun deleteMessage(id: String) {
        db.messages().delete(id)
        buryRow("messages", id)
    }

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
        packId: String? = null,
        momentId: String? = null,
        kind: FollowUpKind = FollowUpKind.CHECK,
    ): String {
        // One follow-up per message. The bookmark is the app's single save
        // action, and Follow-ups is meant to be the one place saved things live,
        // so the same reply saved twice is that list disagreeing with itself.
        //
        // This mattered because the icon forgot its state on reopening a
        // conversation (#128), so the honest response to a grey bookmark on an
        // already-saved reply was to tap it, and tapping it made a duplicate.
        // The display is fixed too; this makes the data right regardless.
        if (messageId != null) {
            db.followUps().existingFor(messageId)?.let { return it.id }
        }
        val id = UUID.randomUUID().toString()
        db.followUps().upsert(
            FollowUpEntity(
                id = id, snippet = snippet, sourceMode = mode,
                conversationId = conversationId, messageId = messageId,
                packId = packId, momentId = momentId, kind = kind,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return id
    }

    /** Change a follow-up's kind when the automatic guess was wrong (Part 5). */
    suspend fun setFollowUpKind(id: String, kind: FollowUpKind) =
        db.followUps().setKind(id, kind)

    suspend fun setFollowUpCompleted(id: String, completed: Boolean) =
        db.followUps().setCompleted(
            id, completed, if (completed) System.currentTimeMillis() else null,
        )

    suspend fun setFollowUpNote(id: String, note: String?) = db.followUps().setNote(id, note)

    suspend fun deleteFollowUp(id: String) {
        db.followUps().delete(id)
        buryRow("follow_ups", id)
    }

    // Projects

    /** The cap on project instructions, in characters. Same reasoning as the
     *  system-wide instructions: a project competes with everything else for a
     *  small window. */
    val projectInstructionsMax: Int get() = 2000

    /** The cap on project notes. Same window, same reasoning, same size: notes
     *  compete with the instructions and the conversation for the same room. */
    val projectNotesMax: Int get() = 2000

    fun observeProjects(): Flow<List<ProjectEntity>> = db.projects().observeAll()

    /** Live chat counts per project, keyed by project id, for the folder tiles. */
    fun observeProjectCounts(): Flow<Map<String, Int>> =
        db.conversations().observeProjectCounts().map { rows ->
            rows.associate { it.projectId to it.count }
        }

    fun observeProject(id: String): Flow<ProjectEntity?> = db.projects().observe(id)

    suspend fun project(id: String): ProjectEntity? = db.projects().byId(id)

    /** Conversations that belong to [projectId]. */
    fun conversationsInProject(projectId: String): Flow<List<ConversationSummary>> =
        db.conversations().observeActive(projectId)

    suspend fun upsertProject(
        id: String?,
        name: String,
        instructions: String,
        notes: String = "",
    ): String {
        val now = System.currentTimeMillis()
        val projectId = id ?: UUID.randomUUID().toString()
        val existing = id?.let { db.projects().byId(it) }
        db.projects().upsert(
            ProjectEntity(
                id = projectId, name = name.trim(),
                instructions = instructions.take(projectInstructionsMax),
                notes = notes.take(projectNotesMax),
                createdAt = existing?.createdAt ?: now, updatedAt = now,
            ),
        )
        return projectId
    }

    /**
     * Assigns a conversation to a project, or removes it from any project when
     * [projectId] is null. The change applies to subsequent turns only, never
     * retroactively, since buildPrompt reads the current project each turn.
     */
    suspend fun assignConversationToProject(conversationId: String, projectId: String?) =
        db.conversations().setProject(conversationId, projectId, System.currentTimeMillis())

    /**
     * Deletes a project. Its conversations are never silently destroyed: either
     * they are returned to the general chat list ([deleteConversations] = false)
     * or deleted along with it.
     */
    suspend fun deleteProject(id: String, deleteConversations: Boolean) {
        if (deleteConversations) {
            db.conversations().forProjectIds(id).forEach {
                db.conversations().delete(it)
                buryRow("conversations", it)
            }
        } else {
            val now = System.currentTimeMillis()
            db.conversations().forProjectIds(id).forEach {
                db.conversations().setProject(it, null, now)
            }
        }
        db.projects().delete(id)
        buryRow("projects", id)
    }

    // Memory

    fun observeMemory(): Flow<List<MemoryEntity>> = db.memory().observeAll()

    suspend fun recentMemory(limit: Int): List<String> =
        db.memory().mostRecent(limit).map { it.text }

    /**
     * The memories most relevant to [query], selected by keyword overlap and
     * recency within a character budget, so only what matters for this message is
     * injected rather than the whole store. Scans a bounded recent window to keep
     * the cost small. The current list of stored texts is also returned so the
     * caller (and the extractor) can avoid duplicates.
     */
    suspend fun relevantMemory(query: String, budgetChars: Int, max: Int): List<String> {
        // Off means not used, not merely not added to (#123). The mode gated
        // storing and never gated this, so somebody who turned memory off still
        // had everything already stored sent with every message, while the screen
        // told them "Nothing is remembered between conversations."
        //
        // Enforced here rather than at the one call site, so a second caller
        // cannot reintroduce it by forgetting to ask.
        if (memoryMode() == com.kamsiob.kamai.llm.MemoryMode.OFF) return emptyList()
        val items = db.memory().mostRecent(200).map {
            com.kamsiob.kamai.llm.MemoryRetrieval.Item(it.text, it.updatedAt)
        }
        return com.kamsiob.kamai.llm.MemoryRetrieval.select(
            items, query, System.currentTimeMillis(), budgetChars, max,
        )
    }

    /** Every stored memory text, for giving the extractor what is already known. */
    suspend fun allMemoryTexts(): List<String> = db.memory().mostRecent(500).map { it.text }

    /**
     * What storing a fact actually did (#16).
     *
     * [removed] carries the texts this fact superseded, so the caller can say so.
     * Silently deleting something the user asked to be remembered is the one
     * thing this must not do: they would find out the next time it failed to
     * come up. [stored] is false for a retraction, which exists to remove a fact
     * rather than to add one.
     */
    data class Remembered(val stored: Boolean, val removed: List<String>) {
        companion object {
            val NOTHING = Remembered(stored = false, removed = emptyList())
        }
    }

    /** Stores a fact, replacing anything it supersedes (#16). */
    suspend fun remember(
        text: String,
        sourceConversationId: String?,
        auto: Boolean = false,
    ): Remembered {
        // Never store the same fact twice; the memory screen has to stay
        // readable, and duplicates eat the context budget for no gain. Compare on
        // a normalized form so trivial punctuation or spacing differences still
        // count as the same fact.
        val target = com.kamsiob.kamai.llm.MemoryExtractor.normalise(text)
        if (target.isBlank()) return Remembered.NOTHING
        val existing = db.memory().mostRecent(500)
        if (existing.any { com.kamsiob.kamai.llm.MemoryExtractor.normalise(it.text) == target }) {
            return Remembered.NOTHING
        }

        // What this fact does to the ones already there. A move replaces the old
        // address; "no longer learning Spanish" removes that fact and is not
        // itself worth keeping.
        val verdict = com.kamsiob.kamai.llm.MemorySupersession.verdict(
            text, existing.map { it.text },
        )
        val superseded = when (verdict) {
            is com.kamsiob.kamai.llm.MemorySupersession.Verdict.Store -> verdict.replaces
            is com.kamsiob.kamai.llm.MemorySupersession.Verdict.RetractOnly -> verdict.removes
        }
        existing.filter { it.text in superseded }.forEach { db.memory().delete(it) }
        if (verdict is com.kamsiob.kamai.llm.MemorySupersession.Verdict.RetractOnly) {
            return Remembered(stored = false, removed = superseded)
        }

        val now = System.currentTimeMillis()
        db.memory().upsert(
            MemoryEntity(
                id = UUID.randomUUID().toString(), text = text,
                createdAt = now, updatedAt = now,
                sourceConversationId = sourceConversationId, auto = auto,
            ),
        )
        return Remembered(stored = true, removed = superseded)
    }

    suspend fun forgetAllMemory() = db.memory().deleteAll()

    suspend fun memoryMode(): com.kamsiob.kamai.llm.MemoryMode =
        runCatching {
            com.kamsiob.kamai.llm.MemoryMode.valueOf(setting(Keys.MEMORY_MODE).orEmpty())
        }.getOrDefault(com.kamsiob.kamai.llm.MemoryMode.MANUAL)

    suspend fun setMemoryMode(mode: com.kamsiob.kamai.llm.MemoryMode) =
        putSetting(Keys.MEMORY_MODE, mode.name)

    suspend fun forget(id: String) {
        db.memory().deleteById(id)
        buryRow("memory_entries", id)
    }

    /**
     * Delete everything. Downloaded models are optional because re-downloading
     * several gigabytes is a real cost and not everyone means that by "delete
     * my data".
     *
     * **This deliberately writes no tombstones, and clears the ones that exist.**
     * Single deletes record a mark so that sync can carry the deletion to the
     * other phone. A wipe must not: "erase what is on this phone" and "erase
     * everything I own everywhere" are different requests, the screen asks the
     * first, and turning it into the second would destroy a second device's copy
     * on the strength of a confirmation that never mentioned it. If propagating a
     * wipe is ever wanted it needs its own wording and its own confirmation.
     */
    suspend fun deleteEverything(includeDownloads: Boolean) {
        db.conversations().deleteAll()
        db.projects().deleteAll()
        db.memory().deleteAll()
        db.followUps().deleteAll()
        db.discover().deleteAllDrawn()
        db.discover().deleteAllStats()
        db.tombstones().clear()

        if (includeDownloads) {
            db.artifacts().observeAll().let { }
            modelsDir().listFiles()?.forEach { it.delete() }
        }
    }

    companion object {
        const val DISCOVER_MANIFEST_URL =
            "https://github.com/Kamsiob/kam-ai/releases/download/discover-packs-v1/manifest.json"

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

    // ---------------------------------------------------------------------
    // Sync readiness. No sync, no transport, no network: see Sync.kt.
    // ---------------------------------------------------------------------

    /**
     * This install's id, made once and then kept.
     *
     * Read through the settings table rather than held in a field so that it
     * survives the database being closed and reopened, which the app lock does on
     * every lock change.
     */
    private suspend fun deviceId(): String {
        db.settings().get(SyncKeys.DEVICE_ID)?.let { return it }
        val fresh = UUID.randomUUID().toString()
        db.settings().put(SettingEntity(SyncKeys.DEVICE_ID, fresh))
        return fresh
    }

    /**
     * The next logical stamp, persisted before it is handed out.
     *
     * Persisted first on purpose. A stamp given to a caller and then lost to a
     * process death would be issued a second time, and two different writes
     * sharing a stamp is the one thing the ordering cannot recover from. Writing
     * first means a crash costs a skipped number, and skipped numbers are free.
     */
    private suspend fun nextStamp(): Stamp {
        val current = db.settings().get(SyncKeys.LAMPORT)?.toLongOrNull() ?: 0L
        val next = current + 1
        db.settings().put(SettingEntity(SyncKeys.LAMPORT, next.toString()))
        return Stamp(next, deviceId())
    }

    /**
     * Records that a row was deleted, so the deletion can outlive the row.
     *
     * Called after the delete rather than before: if the delete fails there is
     * nothing to record, and a tombstone for a row that still exists would hide
     * it from the interface once sync starts reading this table.
     *
     * Failures are swallowed. A tombstone is bookkeeping for a feature that does
     * not exist yet, and refusing to delete somebody's conversation because the
     * bookkeeping failed would be the wrong way round.
     *
     * **Cascades leave one tombstone, not many, and that is correct.** Deleting a
     * conversation takes its messages with it through the foreign key, and none of
     * them get a tombstone of their own. Watching a real delete on the phone
     * record exactly one is what prompted checking this rather than assuming it:
     * the conversation's tombstone is enough, because the other device applies the
     * same delete and its own foreign key removes the same messages there. Writing
     * a tombstone per message would mean thousands of rows saying what one already
     * says.
     */
    private suspend fun buryRow(table: String, id: String) {
        runCatching {
            val stamp = nextStamp()
            db.tombstones().put(
                TombstoneEntity(
                    entityType = table,
                    entityId = id,
                    rev = stamp.lamport,
                    deviceId = stamp.deviceId,
                    deletedAt = System.currentTimeMillis(),
                ),
            )
            db.tombstones().count()
        }.onSuccess { total ->
            // Logged because the catch below is deliberately silent, and a step
            // that fails quietly and is checked by nothing is a step that can be
            // broken for months. The table name and a count only: an entity id is
            // a handle on somebody's private content and does not belong in a log
            // that any app on the phone could once read.
            android.util.Log.d("KamSync", "tombstone recorded for $table, $total held")
        }.onFailure {
            android.util.Log.w("KamSync", "could not record a tombstone for $table", it)
        }
    }

}
