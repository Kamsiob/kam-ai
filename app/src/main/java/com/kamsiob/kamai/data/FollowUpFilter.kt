package com.kamsiob.kamai.data

/**
 * The two filters over the Follow-ups list: where an item came from, and which
 * kind it is. Issue #33.
 *
 * Pure and separate from the screen because the interesting behavior is not the
 * filtering itself but what happens when a filter stops matching anything. An
 * item's kind can be changed by the user while a kind filter is active, and
 * completing or deleting the last item from a source can empty a source filter.
 * Either leaves the list looking empty for a reason the user cannot see, so both
 * fall back rather than stranding them on a filter with nothing behind it.
 */
object FollowUpFilter {

    /**
     * The filter to actually apply, given what the user picked and what is
     * present. A selection that matches nothing collapses to "everything".
     */
    fun <T> resolve(selected: T?, available: Collection<T>): T? =
        selected?.takeIf { it in available }

    /** Everything matching both filters. Null on either means "any". */
    fun apply(
        items: List<FollowUpEntity>,
        source: Mode?,
        kind: FollowUpKind?,
    ): List<FollowUpEntity> = items.filter { item ->
        (source == null || item.sourceMode == source) &&
            (kind == null || item.kind == kind)
    }

    /** The sources present, in first-seen order so the row does not reshuffle. */
    /**
     * Every source a saved item can come from, in a fixed order, whether or not
     * anything has arrived from it yet.
     *
     * This used to return only the sources actually present, so the row showed
     * two chips and the screen looked like it collected saves from two places.
     * It collects from all of them. Showing the whole set says so, and the ones
     * with nothing in them are drawn quietly rather than hidden (owner
     * feedback).
     *
     * DISCOVER is included and OVERLAY is Quick ask; both are real save sources
     * even though neither is a mode the user picks.
     */
    val allSources: List<Mode> = listOf(
        Mode.GENERAL, Mode.LOGIC, Mode.BRAINSTORM, Mode.BENCH, Mode.DISCOVER, Mode.OVERLAY,
    )

    fun sourcesIn(vararg lists: List<FollowUpEntity>): List<Mode> =
        lists.asSequence().flatten().map { it.sourceMode }.distinct().toList()

    /** How many saved items came from [mode], for the count on its chip. */
    fun countFor(mode: Mode, vararg lists: List<FollowUpEntity>): Int =
        lists.asSequence().flatten().count { it.sourceMode == mode }

    /**
     * The kinds present, always in CHECK then PURSUE order rather than in
     * whatever order the data happens to arrive, so the two chips do not swap
     * places as items are added.
     */
    fun kindsIn(vararg lists: List<FollowUpEntity>): List<FollowUpKind> {
        val present = lists.asSequence().flatten().map { it.kind }.toSet()
        return FollowUpKind.entries.filter { it in present }
    }

    /**
     * The line shown when both filters are set and nothing matches, naming both
     * so the user can see which one to clear.
     */
    fun emptyLine(source: Mode?, kind: FollowUpKind?): String = when {
        source != null && kind != null ->
            "Nothing to ${kindWord(kind)} from ${sourceLabel(source)} yet."
        source != null -> "Nothing from ${sourceLabel(source)} yet."
        kind != null -> "Nothing to ${kindWord(kind)} yet."
        else -> "Nothing saved yet."
    }

    /**
     * The kind a new follow-up gets from where it was saved.
     *
     * Something saved out of a Brainstorm is an idea to pursue; everything else
     * is something to check. The user can override it afterwards, which is why
     * this is a default rather than a rule. Part 5 of the four-mode update.
     *
     * Here rather than inline in the view model so it can be tested, since
     * HANDOFF listed this path as written but never verified.
     */
    fun kindFor(source: Mode): FollowUpKind =
        if (source == Mode.BRAINSTORM) FollowUpKind.PURSUE else FollowUpKind.CHECK

    fun kindWord(kind: FollowUpKind): String = when (kind) {
        FollowUpKind.CHECK -> "check"
        FollowUpKind.PURSUE -> "pursue"
    }

    fun sourceLabel(mode: Mode?): String = when (mode) {
        null -> "All"
        Mode.GENERAL -> "General"
        Mode.LOGIC -> "Logic"
        Mode.BRAINSTORM -> "Brainstorm"
        Mode.DISCOVER -> "Discover"
        Mode.BENCH -> "Workbench"
        Mode.OVERLAY -> "Quick ask"
    }
}
