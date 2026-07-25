package com.kamsiob.kamai.ui.followups

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * A saved item's card shows a heading and then the rest of it, and never the
 * same words twice.
 *
 * Found on the phone, twice. First a follow-up reading "History of navigation"
 * appeared as both the heading and the body of its own card. That was fixed, and
 * the fix left the other half: a saved paragraph put its first sixty characters
 * in bold and then repeated them at the start of the body. Saving an excerpt
 * from an answer (#64) produces a paragraph every time, which made the second
 * case the common one.
 */
class FollowUpTextTest {

    @Test
    fun `a short saved item is not printed twice`() {
        assertThat(FollowUpText.heading("History of navigation")).isEqualTo("History of navigation")
        assertThat(FollowUpText.body("History of navigation")).isNull()
    }

    @Test
    fun `a paragraph gets no heading, because its first words are not a title`() {
        val long = "Only a topic, no idea yet. I'll use STARBURSTING and ask questions across " +
            "all six angles before narrowing anything down."
        assertThat(FollowUpText.heading(long)).isNull()
        assertThat(FollowUpText.body(long)).isEqualTo(long)
    }

    @Test
    fun `a saved excerpt is shown once, not summarised by its own opening`() {
        // What "Save an excerpt to Follow-ups" produces.
        val excerpt = "Photosynthesis uses light energy to make food. Plants take in carbon " +
            "dioxide from the air and water through their roots."
        assertThat(FollowUpText.heading(excerpt)).isNull()
        assertThat(FollowUpText.body(excerpt)).isEqualTo(excerpt)
    }

    @Test
    fun `a multi-line item keeps a short first line as its title, and out of the body`() {
        val multi = "The plan\nBuy flour, then find a tin that fits the oven."
        assertThat(FollowUpText.heading(multi)).isEqualTo("The plan")
        assertThat(FollowUpText.body(multi))
            .isEqualTo("Buy flour, then find a tin that fits the oven.")
    }

    @Test
    fun `a multi-line item whose first line is a paragraph is left whole`() {
        // No title to lift out, so lifting the first line would silently hide it.
        val multi = "I asked about three things and only got an answer to the first one, " +
            "which is the part I cared least about.\nAsk again."
        assertThat(FollowUpText.heading(multi)).isNull()
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

    @Test
    fun `a line exactly at the limit is still a title, one past it is not`() {
        val exactly = "a".repeat(60)
        assertThat(FollowUpText.heading(exactly)).isEqualTo(exactly)
        assertThat(FollowUpText.heading("a".repeat(61))).isNull()
    }
}
