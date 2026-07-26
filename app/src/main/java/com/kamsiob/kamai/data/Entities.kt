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

/**
 * Which mode produced a conversation or a flagged snippet. The four user-facing
 * modes are GENERAL, LOGIC, BRAINSTORM, and BENCH (Workbench); DISCOVER and
 * OVERLAY are internal surfaces. GENERAL was formerly named CHAT; the rename went
 * with having four sibling modes, since calling one of them Chat implied the
 * others were not conversations. See DB MIGRATION_4_5 for the data migration.
 */
enum class Mode { GENERAL, LOGIC, BRAINSTORM, BENCH, DISCOVER, OVERLAY }

/**
 * A follow-up is one of two kinds. CHECK is the original meaning: something
 * flagged because it might be wrong and needs verifying. PURSUE is an idea,
 * option, or direction worth returning to, saved mostly from Brainstorm. One
 * list, told apart by a quiet label; the user can change an item's kind.
 */
enum class FollowUpKind { CHECK, PURSUE }

@Entity(
    tableName = "projects",
    indices = [Index("updatedAt")],
)
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Persistent instructions injected into every chat inside this project. */
    val instructions: String,
    /**
     * Background the model should know about this project, as distinct from
     * instructions about how to behave (#2).
     *
     * Both are injected into every chat here, and they are separate because they
     * are read differently: instructions are orders and notes are facts. Putting
     * "the client is a bakery in Leeds" under a heading that says "follow these
     * instructions" asks the model to obey a sentence that is not an
     * instruction, and mixing the two in one box made people write one and mean
     * the other.
     */
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean = false,
    /**
     * Logical time of the last write to this row, and which install made it.
     *
     * Together these are a [Stamp], which is what orders two versions of the same
     * row against each other. Zero and empty mean "written before this device
     * knew about sync", which every existing row is, and which sorts below any
     * real write. See [Sync.kt] for why this is not `updatedAt`.
     */
    val rev: Long = 0,
    val lastWriterId: String = "",
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
    /** The mode the conversation is currently in. */
    val mode: Mode,
    /**
     * Every mode this conversation has used, in first-use order, as a comma
     * separated list of mode names (for example "GENERAL,LOGIC"). This is what the
     * chat-row mode dots and the mode filter read from, so a conversation that
     * moved through several modes shows all of them. Kept denormalized here rather
     * than in a join table because the list is tiny and always read with the row.
     */
    val modesUsed: String = "GENERAL",
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
     * The other half of a Workbench pairing, if this conversation has one.
     *
     * A Workbench session and the chat discussing it each hold the other's id, so
     * the link can be followed from either side without a lookup table. Null for
     * everything else, which is nearly everything. Issue #32.
     */
    val linkedConversationId: String? = null,
    /**
     * Holds the Discover passage a conversation is grounded in, so its system
     * instructions can confine the model to that text. Stores the passage itself,
     * not just a reference, so a grounded chat keeps working even if the pack it
     * came from is later removed. Null for ordinary conversations.
     */
    val groundingMomentId: String? = null,
    /**
     * Logical time of the last write to this row, and which install made it.
     *
     * Together these are a [Stamp], which is what orders two versions of the same
     * row against each other. Zero and empty mean "written before this device
     * knew about sync", which every existing row is, and which sorts below any
     * real write. See [Sync.kt] for why this is not `updatedAt`.
     */
    val rev: Long = 0,
    val lastWriterId: String = "",
)

/**
 * SYSTEM is a display-only marker in the transcript, used for the quiet centered
 * note that records a mode switch. It is never sent to the model as a turn.
 */
enum class Role { USER, ASSISTANT, SYSTEM }

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
    /**
     * How many remembered facts were put in front of the model for this answer
     * (#16).
     *
     * The Memory screen shows what the app has stored and offers to delete it,
     * which answers "what does it know" and not "did that change this answer".
     * Without the second, memory is an invisible influence on everything, and
     * an answer that seems to know something can only be explained by guessing.
     *
     * A count rather than the memories themselves: the answer is on screen next
     * to a Memory screen listing all of them, and storing copies would mean a
     * deleted memory living on in the transcript of every answer that used it.
     */
    val memoriesUsed: Int = 0,
    /**
     * Logical time of the last write to this row, and which install made it.
     *
     * Together these are a [Stamp], which is what orders two versions of the same
     * row against each other. Zero and empty mean "written before this device
     * knew about sync", which every existing row is, and which sorts below any
     * real write. See [Sync.kt] for why this is not `updatedAt`.
     */
    val rev: Long = 0,
    val lastWriterId: String = "",
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
    /**
     * Logical time of the last write to this row, and which install made it.
     *
     * Together these are a [Stamp], which is what orders two versions of the same
     * row against each other. Zero and empty mean "written before this device
     * knew about sync", which every existing row is, and which sorts below any
     * real write. See [Sync.kt] for why this is not `updatedAt`.
     */
    val rev: Long = 0,
    val lastWriterId: String = "",
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
    /** For a saved Discover moment, which moment it is, so it can be reopened.
     *  Null for follow-ups flagged from a chat. Part of the unified saving: one
     *  bookmark, one Follow-ups list, distinguished by source. */
    val packId: String? = null,
    val momentId: String? = null,
    /** Check (verify later) or pursue (an idea worth returning to). Set from the
     *  source at save time and overridable by the user. */
    val kind: FollowUpKind = FollowUpKind.CHECK,
    val completed: Boolean = false,
    val createdAt: Long,
    val completedAt: Long? = null,
    /**
     * Logical time of the last write to this row, and which install made it.
     *
     * Together these are a [Stamp], which is what orders two versions of the same
     * row against each other. Zero and empty mean "written before this device
     * knew about sync", which every existing row is, and which sorts below any
     * real write. See [Sync.kt] for why this is not `updatedAt`.
     */
    val rev: Long = 0,
    val lastWriterId: String = "",
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
    /**
     * Logical time of the last write to this row, and which install made it.
     *
     * Together these are a [Stamp], which is what orders two versions of the same
     * row against each other. Zero and empty mean "written before this device
     * knew about sync", which every existing row is, and which sorts below any
     * real write. See [Sync.kt] for why this is not `updatedAt`.
     */
    val rev: Long = 0,
    val lastWriterId: String = "",
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
    val rev: Long = 0,
    val lastWriterId: String = "",
)

/**
 * A record that a row was deleted, so that a delete can be told apart from never
 * having heard of it.
 *
 * **Why a table of its own instead of a `deleted` flag on each row.** The flag is
 * the more obvious design and it is the more dangerous one here. Every existing
 * read would need `AND deleted = 0` added to it: forty-odd queries in
 * [Daos.kt][com.kamsiob.kamai.data.ConversationDao], and the cost of missing one
 * is deleted content appearing in the interface as though it were still there.
 * That is a bad failure to leave available. A separate table leaves every read
 * already correct, because a row that is gone is still gone.
 *
 * The price is that deleting now writes two statements instead of one, which is a
 * single repository helper, and that this table grows. Growth is bounded by
 * pruning: a tombstone exists to inform other devices, so once every device has
 * been told it has no further use. Nothing prunes yet, because nothing syncs yet,
 * and a tombstone is a row of two short strings and two integers.
 *
 * @param entityType the table name, matching the keys of [SyncPolicy.TABLES].
 * @param entityId the deleted row's primary key. For `discover_drawn`, whose key
 *   is composite, the two parts joined by `/`.
 */
@Entity(
    tableName = "tombstones",
    primaryKeys = ["entityType", "entityId"],
    // Declared here and not only in the migration. Room validates indices as well
    // as columns, so an index the migration creates and the entity does not
    // mention makes the migration fail its own verification, which is what
    // happened the first time this shipped to the phone. The name Room derives,
    // index_tombstones_rev, is the one the migration creates.
    indices = [Index("rev")],
)
data class TombstoneEntity(
    val entityType: String,
    val entityId: String,
    /** Logical time of the deletion. See [LamportClock]. */
    val rev: Long,
    /** Which install deleted it, for the [Stamp] tiebreak. */
    val deviceId: String,
    /** Wall clock, for showing a person. Never used to order anything. */
    val deletedAt: Long,
)
