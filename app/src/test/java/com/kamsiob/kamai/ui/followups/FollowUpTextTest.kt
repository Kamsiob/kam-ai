package com.kamsiob.kamai.ui.followups

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * A saved item's card shows a heading and then the rest of it.
 *
 * Found on the phone: a follow-up reading "History of navigation" appeared as
 * both the heading and the body of its own card, the same four words twice,
 * because the heading is the snippet's first sixty characters and the body was
 * the whole snippet.
 */
class FollowUpTextTest {

    @Test
    fun `a short saved item is not printed twice`() {
        assertThat(FollowUpText.heading("History of navigation")).isEqualTo("History of navigation")
        assertThat(FollowUpText.body("History of navigation")).isNull()
    }

    @Test
    fun `a long saved item keeps both, because the heading is then a summary`() {
        val long = "Only a topic, no idea yet. I'll use STARBURSTING and ask questions across " +
            "all six angles before narrowing anything down."
        assertThat(FollowUpText.heading(long)).hasLength(60)
        assertThat(FollowUpText.body(long)).isEqualTo(long)
    }

    @Test
    fun `a multi-line item keeps both even when the first line is short`() {
        val multi = "The plan\nBuy flour, then find a tin that fits the oven."
        assertThat(FollowUpText.heading(multi)).isEqualTo("The plan")
        assertThat(FollowUpText.body(multi)).isEqualTo(multi)
    }

    @Test
    fun `surrounding whitespace does not make it look like there is more`() {
        assertThat(FollowUpText.body("  History of navigation  ")).isNull()
    }

    @Test
    fun `an empty snippet still gets a heading and no body`() {
        assertThat(FollowUpText.heading("")).isEqualTo("Bookmarked note")
        assertThat(FollowUpText.body("")).isNull()
        assertThat(FollowUpText.body("   ")).isNull()
    }
}
