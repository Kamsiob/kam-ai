package com.kamsiob.kamai.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Issue #31 asks specifically for boundary and pinned tests, which are the two
 * places this can quietly take something it should not have.
 */
class AutoArchivePolicyTest {

    private val day = AutoArchivePolicy.MILLIS_PER_DAY

    /** A fixed "now" so the boundary can be sat on exactly. */
    private val now = 1_000_000_000_000L

    private fun conv(
        id: String,
        agoDays: Double,
        pinned: Boolean = false,
        archived: Boolean = false,
    ) = ConversationSummary(
        id = id,
        title = id,
        mode = Mode.GENERAL,
        modesUsed = "GENERAL",
        projectId = null,
        updatedAt = now - (agoDays * day).toLong(),
        pinned = pinned,
        archived = archived,
        snippet = null,
        messageCount = 2,
    )

    private fun due(
        conversations: List<ConversationSummary>,
        policy: AutoArchive = AutoArchive.DAYS_7,
        openId: String? = null,
    ) = AutoArchivePolicy.due(conversations, policy, now, openId)

    @Test
    fun offTakesNothingHoweverOldAnythingIs() {
        val ancient = listOf(conv("a", agoDays = 400.0), conv("b", agoDays = 9999.0))
        assertThat(due(ancient, policy = AutoArchive.OFF)).isEmpty()
    }

    @Test
    fun somethingOlderThanTheWindowIsTaken() {
        assertThat(due(listOf(conv("old", agoDays = 8.0)))).containsExactly("old")
    }

    @Test
    fun somethingNewerThanTheWindowIsLeft() {
        assertThat(due(listOf(conv("recent", agoDays = 6.0)))).isEmpty()
    }

    @Test
    fun exactlyOnTheBoundaryIsTaken() {
        // "7 days" means seven days have passed. Sitting exactly on the line
        // counts, rather than waiting another millisecond.
        assertThat(due(listOf(conv("exact", agoDays = 7.0)))).containsExactly("exact")
    }

    @Test
    fun justInsideTheBoundaryIsLeft() {
        val justShy = ConversationSummary(
            id = "shy", title = null, mode = Mode.GENERAL, modesUsed = "GENERAL",
            projectId = null, updatedAt = now - 7 * day + 1,
            pinned = false, archived = false, snippet = null, messageCount = 1,
        )
        assertThat(due(listOf(justShy))).isEmpty()
    }

    @Test
    fun aPinnedConversationIsNeverTakenHoweverOld() {
        val pinned = conv("pinned", agoDays = 500.0, pinned = true)
        assertThat(due(listOf(pinned))).isEmpty()
    }

    @Test
    fun pinnedIsExemptEvenAlongsideOthersThatAreTaken() {
        val rows = listOf(
            conv("pinned", agoDays = 500.0, pinned = true),
            conv("ordinary", agoDays = 500.0),
        )
        assertThat(due(rows)).containsExactly("ordinary")
    }

    @Test
    fun theOpenConversationIsNeverTaken() {
        // Archiving what somebody is looking at is indefensible however old it is.
        val rows = listOf(conv("open", agoDays = 90.0), conv("other", agoDays = 90.0))
        assertThat(due(rows, openId = "open")).containsExactly("other")
    }

    @Test
    fun alreadyArchivedIsNotTakenAgain() {
        // So a repeated pass is a no-op rather than something that keeps finding
        // work and re-announcing itself.
        val rows = listOf(conv("gone", agoDays = 90.0, archived = true))
        assertThat(due(rows)).isEmpty()
    }

    @Test
    fun aSecondPassOverTheSameSetFindsNothing() {
        val rows = listOf(conv("a", agoDays = 90.0), conv("b", agoDays = 90.0))
        val first = due(rows)
        assertThat(first).hasSize(2)
        val after = rows.map { if (it.id in first) it.copy(archived = true) else it }
        assertThat(due(after)).isEmpty()
    }

    @Test
    fun theWindowLengthIsRespected() {
        val fiveDaysOld = listOf(conv("x", agoDays = 5.0))
        assertThat(due(fiveDaysOld, policy = AutoArchive.DAYS_3)).containsExactly("x")
        assertThat(due(fiveDaysOld, policy = AutoArchive.DAYS_7)).isEmpty()
        assertThat(due(fiveDaysOld, policy = AutoArchive.DAYS_30)).isEmpty()
    }

    @Test
    fun theStoredValueSurvivesARoundTrip() {
        AutoArchive.entries.forEach {
            assertThat(AutoArchive.fromStored(it.stored)).isEqualTo(it)
        }
    }

    @Test
    fun anUnknownOrMissingStoredValueMeansOff() {
        // A setting that quietly moves the user's things defaults to not doing so.
        assertThat(AutoArchive.fromStored(null)).isEqualTo(AutoArchive.OFF)
        assertThat(AutoArchive.fromStored("")).isEqualTo(AutoArchive.OFF)
        assertThat(AutoArchive.fromStored("DAYS_14")).isEqualTo(AutoArchive.OFF)
    }
}
