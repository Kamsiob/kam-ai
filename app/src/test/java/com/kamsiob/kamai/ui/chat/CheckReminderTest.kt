package com.kamsiob.kamai.ui.chat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * When the bookmark reminder appears (#84).
 *
 * Almost every test here is about it *not* appearing. The feature is one line of
 * text; the whole design is the restraint, and restraint is the thing that
 * silently erodes unless something holds it in place.
 */
class CheckReminderTest {

    private fun show(
        dismissed: Boolean = false,
        session: Int = 1,
        answers: Int = 3,
        shownAlready: Boolean = false,
    ) = CheckReminder.shouldShow(dismissed, session, answers, shownAlready)

    @Test
    fun `it appears a couple of answers into an early session`() {
        assertThat(show()).isTrue()
    }

    @Test
    fun `dismissing it once ends it for good`() {
        // Somebody who has said no has said no.
        assertThat(show(dismissed = true)).isFalse()
        assertThat(show(dismissed = true, session = 1, answers = 99)).isFalse()
    }

    @Test
    fun `it never appears twice in one session`() {
        assertThat(show(shownAlready = true)).isFalse()
    }

    @Test
    fun `it stops after the first few sessions`() {
        assertThat(show(session = CheckReminder.SESSIONS)).isTrue()
        assertThat(show(session = CheckReminder.SESSIONS + 1)).isFalse()
        assertThat(show(session = 40)).isFalse()
    }

    @Test
    fun `it does not arrive on the first answer of a session`() {
        // Arriving instantly reads as boilerplate attached to the product rather
        // than a note about this answer.
        assertThat(show(answers = 0)).isFalse()
        assertThat(show(answers = 1)).isFalse()
        assertThat(show(answers = 2)).isTrue()
    }

    @Test
    fun `the wording is a note, not a disclaimer`() {
        // It must not claim the app doubts this particular answer, which is a
        // judgement a small model cannot honestly make.
        assertThat(CheckReminder.TEXT).contains("worth checking")
        assertThat(CheckReminder.TEXT).contains("Follow-ups")
        listOf("sorry", "may be wrong", "cannot be trusted", "warning", "!")
            .forEach { assertThat(CheckReminder.TEXT.lowercase()).doesNotContain(it) }
    }
}
