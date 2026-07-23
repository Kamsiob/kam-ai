package com.kamsiob.kamai.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A conversation with just enough extra to draw a row in the Chats list. */
data class ConversationSummary(
    val id: String,
    val title: String?,
    val mode: Mode,
    val projectId: String?,
    val updatedAt: Long,
    val pinned: Boolean,
    val archived: Boolean,
    val snippet: String?,
    val messageCount: Int,
)

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations")
    suspend fun allForBackup(): List<ConversationEntity>

    @Upsert
    suspend fun upsert(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun byId(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observe(id: String): Flow<ConversationEntity?>

    /**
     * The Chats list. The snippet is the most recent message in the
     * conversation, which is what a person expects to see, not the first.
     */
    @Query(
        """
        SELECT c.id, c.title, c.mode, c.projectId, c.updatedAt, c.pinned, c.archived,
               (SELECT m.content FROM messages m
                 WHERE m.conversationId = c.id AND m.role != 'SYSTEM'
                 ORDER BY m.createdAt DESC LIMIT 1) AS snippet,
               (SELECT COUNT(*) FROM messages m WHERE m.conversationId = c.id) AS messageCount
          FROM conversations c
         WHERE c.archived = 0
           AND ((:projectId IS NULL AND c.projectId IS NULL) OR c.projectId = :projectId)
         ORDER BY c.pinned DESC, c.updatedAt DESC
        """,
    )
    fun observeActive(projectId: String? = null): Flow<List<ConversationSummary>>

    @Query(
        """
        SELECT c.id, c.title, c.mode, c.projectId, c.updatedAt, c.pinned, c.archived,
               (SELECT m.content FROM messages m
                 WHERE m.conversationId = c.id AND m.role != 'SYSTEM'
                 ORDER BY m.createdAt DESC LIMIT 1) AS snippet,
               (SELECT COUNT(*) FROM messages m WHERE m.conversationId = c.id) AS messageCount
          FROM conversations c
         WHERE c.archived = 1
         ORDER BY c.updatedAt DESC
        """,
    )
    fun observeArchived(): Flow<List<ConversationSummary>>

    /**
     * Keyword search across titles and message bodies. This is deliberately
     * plain LIKE matching for now. Semantic search waits on an on-device
     * embedding model and is listed under Being considered.
     */
    @Query(
        """
        SELECT c.id, c.title, c.mode, c.projectId, c.updatedAt, c.pinned, c.archived,
               (SELECT m.content FROM messages m
                 WHERE m.conversationId = c.id AND m.role != 'SYSTEM'
                 ORDER BY m.createdAt DESC LIMIT 1) AS snippet,
               (SELECT COUNT(*) FROM messages m WHERE m.conversationId = c.id) AS messageCount
          FROM conversations c
         WHERE (c.title LIKE '%' || :query || '%'
                OR EXISTS (SELECT 1 FROM messages m
                            WHERE m.conversationId = c.id
                              AND m.content LIKE '%' || :query || '%'))
         ORDER BY c.pinned DESC, c.updatedAt DESC
        """,
    )
    fun search(query: String): Flow<List<ConversationSummary>>

    @Query("UPDATE conversations SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE conversations SET archived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, now: Long)

    /** Auto-title. Leaves a manually renamed conversation alone. */
    @Query("UPDATE conversations SET title = :title WHERE id = :id AND titleIsManual = 0")
    suspend fun autoTitle(id: String, title: String)

    /** The user renamed it by hand; this sticks. */
    @Query("UPDATE conversations SET title = :title, titleIsManual = 1 WHERE id = :id")
    suspend fun setManualTitle(id: String, title: String)

    @Query("UPDATE conversations SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long)

    @Query("UPDATE conversations SET mode = :mode, updatedAt = :now WHERE id = :id")
    suspend fun setMode(id: String, mode: Mode, now: Long)

    @Query("UPDATE conversations SET projectId = :projectId, updatedAt = :now WHERE id = :id")
    suspend fun setProject(id: String, projectId: String?, now: Long)

    @Query("SELECT id FROM conversations WHERE projectId = :projectId")
    suspend fun forProjectIds(projectId: String): List<String>

    @Query("UPDATE conversations SET groundingMomentId = :passage WHERE id = :id")
    suspend fun setGrounding(id: String, passage: String)

    /** Lifts the scope so the conversation becomes an ordinary open chat. */
    @Query("UPDATE conversations SET groundingMomentId = NULL WHERE id = :id")
    suspend fun clearGrounding(id: String)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun count(): Int
}

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages")
    suspend fun allForBackup(): List<MessageEntity>

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Update
    suspend fun update(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observe(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun forConversation(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun byId(id: String): MessageEntity?

    @Query("UPDATE messages SET content = :content, incomplete = :incomplete WHERE id = :id")
    suspend fun setContent(id: String, content: String, incomplete: Boolean)

    @Query("UPDATE messages SET incomplete = 0, stoppedReason = :reason WHERE id = :id")
    suspend fun finish(id: String, reason: String?)

    /**
     * Editing a user message truncates everything after it and re-answers.
     * There is deliberately no branching, so the tail is removed rather than
     * kept on a side path.
     */
    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND createdAt > :after")
    suspend fun deleteAfter(conversationId: String, after: Long)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: String)

    /** Repairs messages left mid-stream by a process death. */
    @Query(
        """
        UPDATE messages SET incomplete = 0,
               stoppedReason = 'Kam AI was closed while this was being written.'
         WHERE incomplete = 1
        """,
    )
    suspend fun repairIncomplete(): Int
}

@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects")
    suspend fun allForBackup(): List<ProjectEntity>

    @Upsert
    suspend fun upsert(project: ProjectEntity)

    @Query("SELECT * FROM projects WHERE archived = 0 ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun byId(id: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observe(id: String): Flow<ProjectEntity?>

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM projects")
    suspend fun deleteAll()
}

@Dao
interface MemoryDao {

    @Query("SELECT * FROM memory_entries")
    suspend fun allForBackup(): List<MemoryEntity>

    @Query("DELETE FROM memory_entries")
    suspend fun deleteAllMemory()

    @Upsert
    suspend fun upsert(entry: MemoryEntity)

    @Query("SELECT * FROM memory_entries ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memory_entries ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun mostRecent(limit: Int): List<MemoryEntity>

    @Query("SELECT COUNT(*) FROM memory_entries WHERE text = :text")
    suspend fun countMatching(text: String): Int

    @Delete
    suspend fun delete(entry: MemoryEntity)

    @Query("DELETE FROM memory_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM memory_entries")
    suspend fun deleteAll()
}

@Dao
interface FollowUpDao {

    @Query("SELECT * FROM follow_ups")
    suspend fun allForBackup(): List<FollowUpEntity>

    @Query("DELETE FROM follow_ups")
    suspend fun deleteAllFollowUps()

    @Upsert
    suspend fun upsert(followUp: FollowUpEntity)

    @Query("SELECT * FROM follow_ups WHERE completed = 0 ORDER BY createdAt DESC")
    fun observeOpen(): Flow<List<FollowUpEntity>>

    @Query("SELECT * FROM follow_ups WHERE completed = 1 ORDER BY completedAt DESC")
    fun observeCompleted(): Flow<List<FollowUpEntity>>

    @Query("SELECT * FROM follow_ups WHERE id = :id")
    suspend fun byId(id: String): FollowUpEntity?

    @Query("UPDATE follow_ups SET completed = :completed, completedAt = :at WHERE id = :id")
    suspend fun setCompleted(id: String, completed: Boolean, at: Long?)

    @Query("UPDATE follow_ups SET note = :note WHERE id = :id")
    suspend fun setNote(id: String, note: String?)

    @Query("UPDATE follow_ups SET projectId = :projectId WHERE id = :id")
    suspend fun setProject(id: String, projectId: String?)

    @Query("DELETE FROM follow_ups WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM follow_ups")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM follow_ups WHERE completed = 0")
    fun observeOpenCount(): Flow<Int>

    // Saved Discover moments, unified into follow-ups (one saving action, one list).

    @Query("SELECT * FROM follow_ups WHERE momentId IS NOT NULL ORDER BY createdAt DESC")
    fun observeSavedMoments(): Flow<List<FollowUpEntity>>

    @Query("SELECT COUNT(*) FROM follow_ups WHERE packId = :packId AND momentId = :momentId")
    suspend fun countMoment(packId: String, momentId: String): Int

    @Query("DELETE FROM follow_ups WHERE packId = :packId AND momentId = :momentId")
    suspend fun deleteMoment(packId: String, momentId: String)
}

@Dao
interface DiscoverDao {

    @Query("SELECT * FROM discover_drawn")
    suspend fun allDrawnForBackup(): List<DrawnMomentEntity>

    @Query("SELECT * FROM discover_quiz_stats")
    suspend fun allStatsForBackup(): List<QuizStatsEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markDrawn(entry: DrawnMomentEntity)

    @Query("SELECT momentId FROM discover_drawn WHERE packId = :packId")
    suspend fun drawnIds(packId: String): List<String>

    @Query("UPDATE discover_drawn SET readerOpened = 1 WHERE packId = :packId AND momentId = :momentId")
    suspend fun markReaderOpened(packId: String, momentId: String)

    @Query("SELECT readerOpened FROM discover_drawn WHERE packId = :packId AND momentId = :momentId")
    suspend fun wasReaderOpened(packId: String, momentId: String): Boolean?

    /** The plain reshuffle at true exhaustion. */
    @Query("DELETE FROM discover_drawn WHERE packId = :packId")
    suspend fun reshuffle(packId: String)

    @Upsert
    suspend fun upsertStats(stats: QuizStatsEntity)

    @Query("SELECT * FROM discover_quiz_stats WHERE packId = :packId")
    suspend fun stats(packId: String): QuizStatsEntity?

    @Query("SELECT * FROM discover_quiz_stats")
    fun observeAllStats(): Flow<List<QuizStatsEntity>>

    @Transaction
    suspend fun recordQuiz(packId: String, asked: Int, right: Int) {
        val current = stats(packId) ?: QuizStatsEntity(packId)
        upsertStats(
            current.copy(
                momentsQuizzed = current.momentsQuizzed + 1,
                questionsAsked = current.questionsAsked + asked,
                questionsRight = current.questionsRight + right,
            ),
        )
    }

    @Query("DELETE FROM discover_drawn")
    suspend fun deleteAllDrawn()

    @Query("DELETE FROM discover_quiz_stats")
    suspend fun deleteAllStats()
}

@Dao
interface ArtifactDao {

    @Query("SELECT * FROM artifacts")
    suspend fun allForBackup(): List<ArtifactEntity>

    @Upsert
    suspend fun upsert(artifact: ArtifactEntity)

    @Query("SELECT * FROM artifacts ORDER BY kind, displayName")
    fun observeAll(): Flow<List<ArtifactEntity>>

    @Query("SELECT * FROM artifacts WHERE kind = :kind ORDER BY displayName")
    fun observeByKind(kind: ArtifactKind): Flow<List<ArtifactEntity>>

    @Query("SELECT * FROM artifacts WHERE kind = :kind AND active = 1 LIMIT 1")
    suspend fun active(kind: ArtifactKind): ArtifactEntity?

    @Query("SELECT * FROM artifacts WHERE kind = :kind AND active = 1 LIMIT 1")
    fun observeActive(kind: ArtifactKind): Flow<ArtifactEntity?>

    @Query("SELECT * FROM artifacts WHERE id = :id")
    suspend fun byId(id: String): ArtifactEntity?

    @Transaction
    suspend fun setActive(kind: ArtifactKind, id: String) {
        clearActive(kind)
        markActive(id)
    }

    @Query("UPDATE artifacts SET active = 0 WHERE kind = :kind")
    suspend fun clearActive(kind: ArtifactKind)

    @Query("UPDATE artifacts SET active = 1 WHERE id = :id")
    suspend fun markActive(id: String)

    @Query("DELETE FROM artifacts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM artifacts")
    fun observeTotalBytes(): Flow<Long>
}

@Dao
interface SettingsDao {

    @Upsert
    suspend fun put(setting: SettingEntity)

    @Query("SELECT value FROM settings WHERE key = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM settings WHERE key = :key")
    fun observe(key: String): Flow<String?>

    @Query("SELECT * FROM settings")
    suspend fun all(): List<SettingEntity>

    @Query("DELETE FROM settings WHERE key = :key")
    suspend fun remove(key: String)
}
