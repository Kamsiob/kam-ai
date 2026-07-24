package com.kamsiob.kamai.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns the whole app database into one portable JSON document and back. Written
 * by hand rather than with a serialization library so the backup format is
 * explicit, versioned, and does not shift when a data class changes shape. Every
 * field is mapped deliberately; a new field means a deliberate change here and a
 * bump of [FORMAT_VERSION].
 *
 * Downloaded models and packs are not embedded. Their artifact records are, so a
 * restored device can offer to re-download exactly what was installed.
 */
object BackupCodec {

    const val FORMAT_VERSION = 2

    // ---- helpers ----
    private fun JSONObject.s(k: String) = if (isNull(k)) null else optString(k, null)
    private fun JSONObject.b(k: String) = optBoolean(k, false)
    private fun JSONObject.l(k: String) = optLong(k)
    private fun JSONObject.i(k: String) = optInt(k)

    /** "CHAT" is the pre-four-modes name for GENERAL; map it so an older backup
     *  imports rather than throwing on an unknown enum value. */
    private fun parseMode(name: String): Mode =
        if (name == "CHAT") Mode.GENERAL else Mode.valueOf(name)

    private fun conv(e: ConversationEntity) = JSONObject().apply {
        put("id", e.id); put("title", e.title); put("mode", e.mode.name)
        put("modesUsed", e.modesUsed)
        put("projectId", e.projectId); put("createdAt", e.createdAt); put("updatedAt", e.updatedAt)
        put("pinned", e.pinned); put("archived", e.archived); put("titleIsManual", e.titleIsManual)
        put("groundingMomentId", e.groundingMomentId)
    }
    private fun conv(o: JSONObject): ConversationEntity {
        val mode = parseMode(o.getString("mode"))
        // Older backups predate modesUsed; seed it from the single mode, mapping
        // CHAT to GENERAL in the stored list too.
        val modesUsed = o.s("modesUsed")
            ?.split(",")?.joinToString(",") { if (it == "CHAT") "GENERAL" else it }
            ?: mode.name
        return ConversationEntity(
            id = o.getString("id"), title = o.s("title"), mode = mode, modesUsed = modesUsed,
            projectId = o.s("projectId"), createdAt = o.l("createdAt"), updatedAt = o.l("updatedAt"),
            pinned = o.b("pinned"), archived = o.b("archived"),
            titleIsManual = o.b("titleIsManual"), groundingMomentId = o.s("groundingMomentId"),
        )
    }

    private fun msg(e: MessageEntity) = JSONObject().apply {
        put("id", e.id); put("conversationId", e.conversationId); put("role", e.role.name)
        put("content", e.content); put("createdAt", e.createdAt); put("incomplete", e.incomplete)
        put("stoppedReason", e.stoppedReason)
    }
    private fun msg(o: JSONObject) = MessageEntity(
        o.getString("id"), o.getString("conversationId"), Role.valueOf(o.getString("role")),
        o.getString("content"), o.l("createdAt"), o.b("incomplete"), o.s("stoppedReason"),
    )

    private fun proj(e: ProjectEntity) = JSONObject().apply {
        put("id", e.id); put("name", e.name); put("instructions", e.instructions)
        put("createdAt", e.createdAt); put("updatedAt", e.updatedAt); put("archived", e.archived)
    }
    private fun proj(o: JSONObject) = ProjectEntity(
        o.getString("id"), o.getString("name"), o.getString("instructions"),
        o.l("createdAt"), o.l("updatedAt"), o.b("archived"),
    )

    private fun mem(e: MemoryEntity) = JSONObject().apply {
        put("id", e.id); put("text", e.text); put("createdAt", e.createdAt)
        put("updatedAt", e.updatedAt); put("sourceConversationId", e.sourceConversationId); put("auto", e.auto)
    }
    private fun mem(o: JSONObject) = MemoryEntity(
        o.getString("id"), o.getString("text"), o.l("createdAt"), o.l("updatedAt"),
        o.s("sourceConversationId"), o.b("auto"),
    )

    private fun fu(e: FollowUpEntity) = JSONObject().apply {
        put("id", e.id); put("snippet", e.snippet); put("sourceMode", e.sourceMode.name)
        put("conversationId", e.conversationId); put("messageId", e.messageId); put("projectId", e.projectId)
        put("note", e.note); put("packId", e.packId); put("momentId", e.momentId)
        put("kind", e.kind.name)
        put("completed", e.completed); put("createdAt", e.createdAt)
        put("completedAt", e.completedAt)
    }
    private fun fu(o: JSONObject) = FollowUpEntity(
        id = o.getString("id"), snippet = o.getString("snippet"),
        sourceMode = parseMode(o.getString("sourceMode")),
        conversationId = o.s("conversationId"), messageId = o.s("messageId"),
        projectId = o.s("projectId"), note = o.s("note"),
        packId = o.s("packId"), momentId = o.s("momentId"),
        // Older backups predate the kind; default to check.
        kind = o.s("kind")?.let { runCatching { FollowUpKind.valueOf(it) }.getOrNull() } ?: FollowUpKind.CHECK,
        completed = o.b("completed"), createdAt = o.l("createdAt"),
        completedAt = if (o.isNull("completedAt")) null else o.l("completedAt"),
    )

    private fun drawn(e: DrawnMomentEntity) = JSONObject().apply {
        put("packId", e.packId); put("momentId", e.momentId); put("drawnAt", e.drawnAt); put("readerOpened", e.readerOpened)
    }
    private fun drawn(o: JSONObject) = DrawnMomentEntity(o.getString("packId"), o.getString("momentId"), o.l("drawnAt"), o.b("readerOpened"))

    /** Legacy saved-moment row from a pre-unification backup, folded into the
     *  single follow-ups list on import so nothing is lost. */
    private fun legacySavedAsFollowUp(o: JSONObject) = FollowUpEntity(
        id = "saved-" + o.getString("packId") + "-" + o.getString("momentId"),
        snippet = o.getString("title"),
        sourceMode = Mode.DISCOVER,
        packId = o.getString("packId"), momentId = o.getString("momentId"),
        createdAt = o.l("savedAt"),
    )

    private fun stats(e: QuizStatsEntity) = JSONObject().apply {
        put("packId", e.packId); put("momentsQuizzed", e.momentsQuizzed); put("questionsAsked", e.questionsAsked); put("questionsRight", e.questionsRight)
    }
    private fun stats(o: JSONObject) = QuizStatsEntity(o.getString("packId"), o.i("momentsQuizzed"), o.i("questionsAsked"), o.i("questionsRight"))

    private fun art(e: ArtifactEntity) = JSONObject().apply {
        put("id", e.id); put("kind", e.kind.name); put("displayName", e.displayName); put("fileName", e.fileName)
        put("sizeBytes", e.sizeBytes); put("sha256", e.sha256); put("version", e.version); put("installedAt", e.installedAt); put("active", e.active)
    }
    private fun art(o: JSONObject) = ArtifactEntity(
        o.getString("id"), ArtifactKind.valueOf(o.getString("kind")), o.getString("displayName"),
        o.getString("fileName"), o.l("sizeBytes"), o.getString("sha256"), o.getString("version"),
        o.l("installedAt"), o.b("active"),
    )

    private fun setting(e: SettingEntity) = JSONObject().apply { put("key", e.key); put("value", e.value) }
    private fun setting(o: JSONObject) = SettingEntity(o.getString("key"), o.getString("value"))

    private inline fun <T> JSONArray.map(f: (JSONObject) -> T): List<T> =
        (0 until length()).map { f(getJSONObject(it)) }

    // ---- top-level ----

    data class Snapshot(
        val conversations: List<ConversationEntity>,
        val messages: List<MessageEntity>,
        val projects: List<ProjectEntity>,
        val memory: List<MemoryEntity>,
        val followUps: List<FollowUpEntity>,
        val drawn: List<DrawnMomentEntity>,
        val quizStats: List<QuizStatsEntity>,
        val artifacts: List<ArtifactEntity>,
        val settings: List<SettingEntity>,
    )

    fun encode(snapshot: Snapshot, appVersion: String, schemaVersion: Int): JSONObject = JSONObject().apply {
        put("formatVersion", FORMAT_VERSION)
        put("appVersion", appVersion)
        put("schemaVersion", schemaVersion)
        put("conversations", JSONArray(snapshot.conversations.map { conv(it) }))
        put("messages", JSONArray(snapshot.messages.map { msg(it) }))
        put("projects", JSONArray(snapshot.projects.map { proj(it) }))
        put("memory", JSONArray(snapshot.memory.map { mem(it) }))
        put("followUps", JSONArray(snapshot.followUps.map { fu(it) }))
        put("drawn", JSONArray(snapshot.drawn.map { drawn(it) }))
        put("quizStats", JSONArray(snapshot.quizStats.map { stats(it) }))
        put("artifacts", JSONArray(snapshot.artifacts.map { art(it) }))
        put("settings", JSONArray(snapshot.settings.map { setting(it) }))
    }

    fun decode(root: JSONObject): Snapshot = Snapshot(
        conversations = root.optJSONArray("conversations")?.map { conv(it) } ?: emptyList(),
        messages = root.optJSONArray("messages")?.map { msg(it) } ?: emptyList(),
        projects = root.optJSONArray("projects")?.map { proj(it) } ?: emptyList(),
        memory = root.optJSONArray("memory")?.map { mem(it) } ?: emptyList(),
        // Follow-ups now hold saved Discover moments too. A legacy backup keeps its
        // moments in a separate "saved" array; fold those in so importing an older
        // file loses nothing.
        followUps = (root.optJSONArray("followUps")?.map { fu(it) } ?: emptyList()) +
            (root.optJSONArray("saved")?.map { legacySavedAsFollowUp(it) } ?: emptyList()),
        drawn = root.optJSONArray("drawn")?.map { drawn(it) } ?: emptyList(),
        quizStats = root.optJSONArray("quizStats")?.map { stats(it) } ?: emptyList(),
        artifacts = root.optJSONArray("artifacts")?.map { art(it) } ?: emptyList(),
        settings = root.optJSONArray("settings")?.map { setting(it) } ?: emptyList(),
    )
}
