package com.kamsiob.kamai.data

/**
 * How long a conversation sits untouched before it is archived on its own.
 *
 * Off is the default and stays the default. Archiving is not deletion, and the
 * archived view keeps everything reachable, but a setting that quietly moves the
 * user's things should be one they turned on deliberately.
 */
enum class AutoArchive(val days: Int?) {
    OFF(null),
    DAYS_3(3),
    DAYS_7(7),
    DAYS_30(30),
    ;

    /** The stored value. Names rather than day counts, so the set can change. */
    val stored: String get() = name

    companion object {
        fun fromStored(value: String?): AutoArchive =
            entries.firstOrNull { it.name == value } ?: OFF
    }
}

/**
 * Decides which conversations an auto-archive pass should take.
 *
 * Pure, and deliberately separate from the database and the clock, because the
 * interesting parts are the exclusions and the boundary and both are far easier
 * to get wrong than to test. Issue #31.
 */
object AutoArchivePolicy {

    const val MILLIS_PER_DAY: Long = 24L * 60 * 60 * 1000

    /**
     * The ids to archive, given everything currently unarchived.
     *
     * Four things are never taken, and each is a rule rather than an accident:
     *
     * - nothing at all when the setting is Off,
     * - pinned conversations, which the user has explicitly said matter,
     * - the conversation that is open right now, because archiving what somebody
     *   is looking at is indefensible however old it is,
     * - anything already archived, so a repeated pass is a no-op rather than
     *   something that keeps finding work to do.
     *
     * @param now the current time in millis, passed in rather than read, so a
     *   test can sit exactly on the boundary.
     * @param openConversationId the conversation on screen, if any.
     */
    fun due(
        conversations: List<ConversationSummary>,
        policy: AutoArchive,
        now: Long,
        openConversationId: String? = null,
    ): List<String> {
        val days = policy.days ?: return emptyList()
        val cutoff = now - days * MILLIS_PER_DAY
        return conversations
            .asSequence()
            .filterNot { it.archived }
            .filterNot { it.pinned }
            .filterNot { it.id == openConversationId }
            // Inclusive: "3 days" means three days have passed, so a conversation
            // last touched exactly three days ago is due rather than waiting for
            // another millisecond.
            .filter { it.updatedAt <= cutoff }
            .map { it.id }
            .toList()
    }
}
