package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Joining a continued answer back together (#35).
 *
 * Found on the phone: killed the app mid-answer, tapped Continue, and read
 * "from inside the box to theoutside". The stored partial is trimmed, so the
 * space that ended it is gone by the time the continuation arrives.
 */
class ContinuationJoinTest {

    @Test
    fun `the case seen on the phone`() {
        assertThat(ContinuationJoin.join("A refrigerator moves heat to the", "outside. This"))
            .isEqualTo("A refrigerator moves heat to the outside. This")
    }

    @Test
    fun `a space already there is not doubled`() {
        assertThat(ContinuationJoin.join("to the ", "outside")).isEqualTo("to the outside")
        assertThat(ContinuationJoin.join("to the", " outside")).isEqualTo("to the outside")
    }

    @Test
    fun `punctuation is never pushed off the word it belongs to`() {
        listOf(".", ",", "!", "?", ";", ":", ")", "'s", "\"", "…", "%")
            .forEach { assertThat(ContinuationJoin.needsSpace("finished", it)).isFalse() }
    }

    @Test
    fun `a continuation starting on a new line is left alone`() {
        assertThat(ContinuationJoin.join("end of paragraph", "\nNext paragraph"))
            .isEqualTo("end of paragraph\nNext paragraph")
    }

    @Test
    fun `nothing to join means nothing is added`() {
        assertThat(ContinuationJoin.needsSpace("", "outside")).isFalse()
        assertThat(ContinuationJoin.needsSpace("to the", "")).isFalse()
        assertThat(ContinuationJoin.join("", "outside")).isEqualTo("outside")
    }

    @Test
    fun `the known wrong case is stated rather than hidden`() {
        // If the answer really did stop inside a word, this puts a space in the
        // middle of it. The stored text cannot tell the two apart, because the
        // space that would say so is what got trimmed, and a model told to carry
        // straight on begins at a word far more often than inside one.
        assertThat(ContinuationJoin.join("refrig", "erator")).isEqualTo("refrig erator")
    }

    @Test
    fun `a restarted last word is not printed twice`() {
        // Seen on the phone. The partial ended "...sea levels. They" and the
        // continuation began "They're caused by...", so the answer read
        // "sea levels. They They're caused by...".
        assertThat(ContinuationJoin.join("Tides are the rise and fall of sea levels. They", "They're caused by the moon."))
            .isEqualTo("Tides are the rise and fall of sea levels. They're caused by the moon.")
    }

    @Test
    fun `a restarted phrase is not printed twice either`() {
        assertThat(ContinuationJoin.join("the water on the side", "the side facing the moon"))
            .isEqualTo("the water on the side facing the moon")
    }

    @Test
    fun `the overlap has to start at a word boundary`() {
        // "on" ends "moon", but the continuation is not restarting a word, so
        // nothing may be dropped: "the moon" + "on the left" stays whole.
        assertThat(ContinuationJoin.join("the moon", "on the left"))
            .isEqualTo("the moon on the left")
    }

    @Test
    fun `a single repeated letter is not treated as a restart`() {
        assertThat(ContinuationJoin.overlap("a bulge of water a", "and then")).isEqualTo(0)
    }

    @Test
    fun `a continuation that only repeats adds nothing`() {
        assertThat(ContinuationJoin.join("sea levels. They", "They")).isEqualTo("sea levels. They")
    }

    @Test
    fun `case differences still count as a restart`() {
        assertThat(ContinuationJoin.join("it moves the", "The water rises"))
            .isEqualTo("it moves the water rises")
    }

    @Test
    fun aBulletBeforeTheRepeatDoesNotHideIt() {
        // Seen on the phone: stopped mid-list at "Inspect the", tapped Continue,
        // and the model restarted the bullet. The marker meant the repeat was no
        // longer at the start of the continuation, so the overlap check missed it
        // and the reader got "Inspect the - Inspect the engine bay".
        assertThat(
            ContinuationJoin.join("Inspect the", "- Inspect the engine bay for oil leaks."),
        ).isEqualTo("Inspect the engine bay for oil leaks.")
    }

    @Test
    fun aRealBulletIsKeptWhenNothingRepeats() {
        // The other side: a continuation that genuinely starts a new list item is
        // formatting, not a restart, and dropping the marker would flatten it.
        assertThat(
            ContinuationJoin.join("things to check:", "- Tyres and brakes."),
        ).isEqualTo("things to check: - Tyres and brakes.")
    }

    @Test
    fun punctuationAloneIsNotTreatedAsAMarker() {
        assertThat(ContinuationJoin.leadingMarker("-")).isEqualTo(0)
        assertThat(ContinuationJoin.leadingMarker("")).isEqualTo(0)
    }
}
