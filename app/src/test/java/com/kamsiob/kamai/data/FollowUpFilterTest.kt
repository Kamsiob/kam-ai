package com.kamsiob.kamai.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Issue #33: filtering follow-ups by kind alongside source. */
class FollowUpFilterTest {

    private var seq = 0

    private fun item(source: Mode, kind: FollowUpKind) = FollowUpEntity(
        id = "f${seq++}",
        snippet = "s",
        sourceMode = source,
        kind = kind,
        createdAt = 0L,
    )

    private val check1 = item(Mode.GENERAL, FollowUpKind.CHECK)
    private val check2 = item(Mode.LOGIC, FollowUpKind.CHECK)
    private val pursue1 = item(Mode.BRAINSTORM, FollowUpKind.PURSUE)
    private val all = listOf(check1, check2, pursue1)

    @Test
    fun noFiltersShowsEverything() {
        assertThat(FollowUpFilter.apply(all, source = null, kind = null)).isEqualTo(all)
    }

    @Test
    fun filteringByKindAlone() {
        assertThat(FollowUpFilter.apply(all, source = null, kind = FollowUpKind.PURSUE))
            .containsExactly(pursue1)
    }

    @Test
    fun filteringBySourceAlone() {
        assertThat(FollowUpFilter.apply(all, source = Mode.LOGIC, kind = null))
            .containsExactly(check2)
    }

    @Test
    fun theTwoFiltersCombineRatherThanReplacingEachOther() {
        assertThat(FollowUpFilter.apply(all, Mode.LOGIC, FollowUpKind.CHECK))
            .containsExactly(check2)
        // A combination nothing satisfies is empty, not "whichever matched".
        assertThat(FollowUpFilter.apply(all, Mode.LOGIC, FollowUpKind.PURSUE)).isEmpty()
    }

    @Test
    fun aFilterThatNoLongerMatchesAnythingFallsBackToEverything() {
        // The case this exists for. The user filters to Pursue, then changes that
        // item's kind to Check. Without the fallback the list is empty and the
        // reason is invisible.
        assertThat(FollowUpFilter.resolve(FollowUpKind.PURSUE, listOf(FollowUpKind.CHECK)))
            .isNull()
        assertThat(FollowUpFilter.resolve(Mode.DISCOVER, listOf(Mode.GENERAL))).isNull()
    }

    @Test
    fun aFilterThatStillMatchesIsKept() {
        assertThat(FollowUpFilter.resolve(FollowUpKind.CHECK, listOf(FollowUpKind.CHECK)))
            .isEqualTo(FollowUpKind.CHECK)
    }

    @Test
    fun kindsAreAlwaysListedCheckThenPursue() {
        // Not in whatever order the data arrives, so the two chips never swap
        // places under the user's finger as items are added.
        val pursueFirst = listOf(pursue1, check1)
        assertThat(FollowUpFilter.kindsIn(pursueFirst))
            .containsExactly(FollowUpKind.CHECK, FollowUpKind.PURSUE).inOrder()
    }

    @Test
    fun onlyKindsActuallyPresentAreOffered() {
        assertThat(FollowUpFilter.kindsIn(listOf(check1))).containsExactly(FollowUpKind.CHECK)
    }

    @Test
    fun sourcesKeepFirstSeenOrderSoTheRowDoesNotReshuffle() {
        assertThat(FollowUpFilter.sourcesIn(all))
            .containsExactly(Mode.GENERAL, Mode.LOGIC, Mode.BRAINSTORM).inOrder()
    }

    @Test
    fun sourcesAndKindsSpanOpenAndCompletedTogether() {
        // Both lists feed the chips, or completing the last open Pursue item
        // would make the chip vanish while the item is still on screen.
        assertThat(FollowUpFilter.kindsIn(listOf(check1), listOf(pursue1)))
            .containsExactly(FollowUpKind.CHECK, FollowUpKind.PURSUE)
        assertThat(FollowUpFilter.sourcesIn(listOf(check1), listOf(pursue1)))
            .containsExactly(Mode.GENERAL, Mode.BRAINSTORM)
    }

    @Test
    fun theEmptyLineNamesWhicheverFiltersAreSet() {
        assertThat(FollowUpFilter.emptyLine(Mode.LOGIC, FollowUpKind.PURSUE))
            .isEqualTo("Nothing to pursue from Logic yet.")
        assertThat(FollowUpFilter.emptyLine(Mode.LOGIC, null))
            .isEqualTo("Nothing from Logic yet.")
        assertThat(FollowUpFilter.emptyLine(null, FollowUpKind.CHECK))
            .isEqualTo("Nothing to check yet.")
        assertThat(FollowUpFilter.emptyLine(null, null)).isEqualTo("Nothing saved yet.")
    }

    // The auto-assignment HANDOFF listed as written but never verified.

    @Test
    fun somethingSavedFromBrainstormIsAnIdeaToPursue() {
        assertThat(FollowUpFilter.kindFor(Mode.BRAINSTORM)).isEqualTo(FollowUpKind.PURSUE)
    }

    @Test
    fun everythingElseDefaultsToSomethingToCheck() {
        val others = Mode.entries.filterNot { it == Mode.BRAINSTORM }
        others.forEach {
            assertThat(FollowUpFilter.kindFor(it)).isEqualTo(FollowUpKind.CHECK)
        }
        // Named explicitly too, so adding a mode cannot quietly change what an
        // existing one does without this failing.
        assertThat(FollowUpFilter.kindFor(Mode.GENERAL)).isEqualTo(FollowUpKind.CHECK)
        assertThat(FollowUpFilter.kindFor(Mode.LOGIC)).isEqualTo(FollowUpKind.CHECK)
        assertThat(FollowUpFilter.kindFor(Mode.BENCH)).isEqualTo(FollowUpKind.CHECK)
        assertThat(FollowUpFilter.kindFor(Mode.DISCOVER)).isEqualTo(FollowUpKind.CHECK)
        assertThat(FollowUpFilter.kindFor(Mode.OVERLAY)).isEqualTo(FollowUpKind.CHECK)
    }
}
