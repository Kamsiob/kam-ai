package com.kamsiob.kamai.data

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A backed-up entity must survive with every field set, not only the fields
 * somebody thought to set.
 *
 * This exists because one did not survive. `linkedConversationId` was added to
 * `ConversationEntity` by a later migration than the codec, nothing updated the
 * codec, and export quietly dropped the link between a chat and its Workbench
 * session. A restored backup looked complete and had lost something.
 *
 * The existing round trip test could not catch it, and the reason generalizes:
 * it builds entities and compares them afterwards, so it covers the fields its
 * author set, and a field nobody thought to set is exactly the one that gets
 * missed. A field left at its default round-trips perfectly through a codec that
 * drops it, because the default is what comes back.
 *
 * So every field here is set to something other than its default. That is the
 * whole technique, and it is the one that would have caught this the day the
 * column was added.
 */
@RunWith(RobolectricTestRunner::class)
class BackupFieldCoverageTest {

    /** An otherwise empty snapshot, so each test names only what it is about. */
    private fun snapshot(
        conversations: List<ConversationEntity> = emptyList(),
        messages: List<MessageEntity> = emptyList(),
        projects: List<ProjectEntity> = emptyList(),
        memory: List<MemoryEntity> = emptyList(),
        followUps: List<FollowUpEntity> = emptyList(),
    ) = BackupCodec.Snapshot(
        conversations = conversations, messages = messages, projects = projects,
        memory = memory, followUps = followUps, drawn = emptyList(),
        quizStats = emptyList(), artifacts = emptyList(), settings = emptyList(),
    )

    private fun roundTrip(snapshot: BackupCodec.Snapshot): BackupCodec.Snapshot =
        BackupCodec.decode(JSONObject(BackupCodec.encode(snapshot, "1.0", 3).toString()))

    @Test
    fun aConversationSurvivesWithEveryFieldSet() {
        // Including linkedConversationId, which is the field this file was
        // written for.
        val conversation = ConversationEntity(
            id = "c1",
            title = "A chat",
            mode = Mode.BRAINSTORM,
            modesUsed = "GENERAL,BRAINSTORM",
            projectId = "p1",
            createdAt = 11,
            updatedAt = 22,
            pinned = true,
            archived = true,
            titleIsManual = true,
            groundingMomentId = "moment-1",
            linkedConversationId = "c2",
        )
        val restored = roundTrip(snapshot(conversations = listOf(conversation)))
        assertThat(restored.conversations.single()).isEqualTo(conversation)
    }

    @Test
    fun aBackupWrittenBeforeTheFieldExistedStillOpens() {
        // Adding a field to the codec must not orphan the backups somebody already
        // has, which is the reason this went in without bumping the format
        // version. A missing key decodes to null rather than throwing, so an old
        // export restores with no link, which is exactly what it had.
        val old = JSONObject(
            """
            {
              "formatVersion": 3, "appVersion": "1.0", "schemaVersion": 3,
              "conversations": [
                {"id":"c1","title":"Old","mode":"GENERAL","modesUsed":"GENERAL",
                 "createdAt":1,"updatedAt":2}
              ],
              "messages": [], "projects": [], "memory": [], "followUps": [],
              "drawn": [], "quizStats": [], "artifacts": [], "settings": []
            }
            """,
        )
        val restored = BackupCodec.decode(old)
        assertThat(restored.conversations.single().id).isEqualTo("c1")
        assertThat(restored.conversations.single().linkedConversationId).isNull()
    }

    @Test
    fun aMessageSurvivesWithEveryFieldSet() {
        val message = MessageEntity(
            id = "m1",
            conversationId = "c1",
            role = Role.ASSISTANT,
            content = "Something said",
            createdAt = 33,
            incomplete = true,
            stoppedReason = "You stopped this one.",
            memoriesUsed = 3,
        )
        val restored = roundTrip(snapshot(messages = listOf(message)))
        assertThat(restored.messages.single()).isEqualTo(message)
    }

    @Test
    fun aFollowUpSurvivesWithEveryFieldSet() {
        val followUp = FollowUpEntity(
            id = "f1",
            snippet = "check this",
            sourceMode = Mode.LOGIC,
            conversationId = "c1",
            messageId = "m1",
            projectId = "p1",
            note = "a note",
            packId = "history",
            momentId = "moment-1",
            kind = FollowUpKind.CHECK,
            completed = true,
            createdAt = 44,
            completedAt = 55,
        )
        val restored = roundTrip(snapshot(followUps = listOf(followUp)))
        assertThat(restored.followUps.single()).isEqualTo(followUp)
    }

    @Test
    fun aMemorySurvivesWithEveryFieldSet() {
        val memory = MemoryEntity(
            id = "mem1",
            text = "The user's rowing club is called Verity Quay.",
            createdAt = 66,
            updatedAt = 77,
            sourceConversationId = "c1",
            auto = true,
        )
        val restored = roundTrip(snapshot(memory = listOf(memory)))
        assertThat(restored.memory.single()).isEqualTo(memory)
    }

    @Test
    fun aProjectSurvivesWithEveryFieldSet() {
        val project = ProjectEntity(
            id = "p1",
            name = "A project",
            instructions = "Follow these",
            notes = "Some notes",
            createdAt = 88,
            updatedAt = 99,
            archived = true,
        )
        val restored = roundTrip(snapshot(projects = listOf(project)))
        assertThat(restored.projects.single()).isEqualTo(project)
    }
}
