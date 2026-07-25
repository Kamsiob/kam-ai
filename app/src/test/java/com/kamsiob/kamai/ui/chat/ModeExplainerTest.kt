package com.kamsiob.kamai.ui.chat

import com.google.common.truth.Truth.assertThat
import com.kamsiob.kamai.data.Mode
import org.junit.Test

/** Explaining a mode at the top of the first conversation started in it (#28). */
class ModeExplainerTest {

    @Test
    fun `a first conversation in a mode is explained`() {
        assertThat(
            ModeExplainer.shouldExplain(Mode.BRAINSTORM, historyIsEmpty = true, alreadyExplained = false),
        ).isTrue()
    }

    @Test
    fun `the second conversation in that mode is not`() {
        // Once ever. The tenth Brainstorm chat does not need the paragraph again.
        assertThat(
            ModeExplainer.shouldExplain(Mode.BRAINSTORM, historyIsEmpty = true, alreadyExplained = true),
        ).isFalse()
    }

    @Test
    fun `it never lands in the middle of a conversation`() {
        // That is the switch note's job, and doing both would read as the app
        // repeating itself at the user.
        assertThat(
            ModeExplainer.shouldExplain(Mode.LOGIC, historyIsEmpty = false, alreadyExplained = false),
        ).isFalse()
    }

    @Test
    fun `General is never explained`() {
        // The resting position, and it explains itself by being ordinary.
        assertThat(
            ModeExplainer.shouldExplain(Mode.GENERAL, historyIsEmpty = true, alreadyExplained = false),
        ).isFalse()
    }

    @Test
    fun `each mode is explained on its own first conversation`() {
        // Having met Brainstorm says nothing about having met Workbench.
        listOf(Mode.LOGIC, Mode.BRAINSTORM, Mode.BENCH).forEach { mode ->
            assertThat(
                ModeExplainer.shouldExplain(mode, historyIsEmpty = true, alreadyExplained = false),
            ).isTrue()
        }
    }
}
