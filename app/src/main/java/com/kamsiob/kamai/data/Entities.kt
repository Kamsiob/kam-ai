package com.kamsiob.kamai.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// One SQLite database is the single store for everything: conversations,
// messages, projects, memory, follow-ups, Discover state, and the settings that
// need to persist. The schema is deliberately flat and free of device-specific
// values so that Phase 7 can write the whole thing to one portable file and read
// it back on a different phone.

/** Which mode produced a conversation or a flagged snippet. */
enum class Mode { CHAT, LOGIC, BENCH, DISCOVER, OVERLAY }

@Entity(
    tableName = "projects",
    indices = [Index("updatedAt")],
)
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Persistent instructions injected into every chat inside this project. */
    val instructions: String,
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean = false,
)

@Entity(
    tableName = "conversations",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("projectId"), Index("updatedAt"), Index("pinned"), Index("archived")],
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    /** Null until the model has titled it after the first exchange. */
    val title: String?,
    val mode: Mode,
    val projectId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    /**
     * True once the user has renamed the conversation by hand. After that the
     * title is theirs and auto-titling never overwrites it. PART 4.
     */
    val titleIsManual: Boolean = false,
    /**
     * Holds the Discover passage a conversation is grounded in, so its system
     * instructions can confine the model to that text. Stores the passage itself,
     * not just a reference, so a grounded chat keeps working even if the pack it
     * came from is later removed. Null for ordinary conversations.
     */
    val groundingMomentId: String? = null,
)

enum class Role { USER, ASSISTANT }

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversationId"), Index(value = ["conversationId", "createdAt"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: Role,
    val content: String,
    val createdAt: Long,
    /**
     * True while a response is still streaming. A message left incomplete by a
     * process death is repaired on next load rather than shown as if finished.
     */
    val incomplete: Boolean = false,
    /** Set when generation stopped early, so the UI can say why in plain words. */
    val stoppedReason: String? = null,
)

@Entity(
    tableName = "memory_entries",
    indices = [Index("updatedAt")],
)
data class MemoryEntity(
    @PrimaryKey val id: String,
    /** The durable fact, in the model's own words, shown verbatim to the user. */
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Where it came from, so the memory screen can be honest about it. */
    val sourceConversationId: String? = null,
    /** True when the app decided to remember this rather than the user asking.
     *  Surfaced so a person can tell auto entries apart and prune them. PART 7. */
    val auto: Boolean = false,
)

@Entity(
    tableName = "follow_ups",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("conversationId"), Index("projectId"), Index("completed"), Index("createdAt")],
)
data class FollowUpEntity(
    @PrimaryKey val id: String,
    /** The flagged text, or the selected portion of it. */
    val snippet: String,
    /** Which mode or surface it came from, shown as a mono source chip. */
    val sourceMode: Mode,
    val conversationId: String? = null,
    val messageId: String? = null,
    val projectId: String? = null,
    val note: String? = null,
    val completed: Boolean = false,
    val createdAt: Long,
    val completedAt: Long? = null,
)

// Discover state. Pack contents live in their own downloaded pack files; only
// what the user has done is kept here.

@Entity(tableName = "discover_drawn", primaryKeys = ["packId", "momentId"])
data class DrawnMomentEntity(
    val packId: String,
    val momentId: String,
    val drawnAt: Long,
    /** Set once the reader has been opened, which skips the pre-quiz prompt. */
    val readerOpened: Boolean = false,
)

@Entity(tableName = "discover_saved", primaryKeys = ["packId", "momentId"])
data class SavedMomentEntity(
    val packId: String,
    val momentId: String,
    val title: String,
    val topic: String,
    val savedAt: Long,
)

/**
 * The quiet running tally. Moments quizzed and questions right out of asked,
 * overall and per pack. There are deliberately no streaks, goals, XP, levels,
 * badges, or leaderboards anywhere near this.
 */
@Entity(tableName = "discover_quiz_stats")
data class QuizStatsEntity(
    @PrimaryKey val packId: String,
    val momentsQuizzed: Int = 0,
    val questionsAsked: Int = 0,
    val questionsRight: Int = 0,
)

/**
 * Everything downloaded and sitting on the device: language models, speech
 * models, voices, and content packs. The Storage screen is a view of this table.
 */
enum class ArtifactKind { LLM, STT, TTS_VOICE, PACK }

@Entity(tableName = "artifacts", indices = [Index("kind")])
data class ArtifactEntity(
    @PrimaryKey val id: String,
    val kind: ArtifactKind,
    val displayName: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val version: String,
    val installedAt: Long,
    /** True for the model, voice, or pack currently in use. */
    val active: Boolean = false,
)

/**
 * Settings that need to survive a reinstall-from-backup rather than living in
 * DataStore. Kept as a key-value table so Phase 7's export stays one file.
 */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
