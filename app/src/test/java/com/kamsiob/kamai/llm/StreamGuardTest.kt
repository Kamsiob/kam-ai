package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Issue #49: a model that types its own turn delimiter as ordinary text emits it
 * one token at a time, so the old per-piece check never matched and the
 * fragments reached the user.
 */
class StreamGuardTest {

    /** Feeds pieces in order and returns everything the user would have seen. */
    private fun run(vararg pieces: String): Pair<String, Boolean> {
        val guard = StreamGuard()
        val seen = StringBuilder()
        var stopped = false
        for (piece in pieces) {
            val step = guard.accept(piece)
            seen.append(step.emit)
            if (step.stop) {
                stopped = true
                break
            }
        }
        if (!stopped) seen.append(guard.flush())
        return seen.toString() to stopped
    }

    @Test
    fun ordinaryTextStreamsThroughUnchanged() {
        val (text, stopped) = run("Every", " season", " is", " about", " right.")
        assertThat(text).isEqualTo("Every season is about right.")
        assertThat(stopped).isFalse()
    }

    @Test
    fun aMarkerSplitAcrossTokensStopsTheAnswerAndNeverAppears() {
        val (text, stopped) = run("Sure.", " ", "<", "start", "_of", "_turn", ">", "user")
        assertThat(text).isEqualTo("Sure. ")
        assertThat(stopped).isTrue()
    }

    @Test
    fun aMarkerArrivingWholeAlsoStops() {
        val (text, stopped) = run("Done.", "<end_of_turn>")
        assertThat(text).isEqualTo("Done.")
        assertThat(stopped).isTrue()
    }

    @Test
    fun theOtherFamilysMarkerIsCaughtToo() {
        // Both families' markers are watched whatever model is loaded, since a
        // stray marker in a bubble is worse than a redundant check.
        val (text, stopped) = run("Answer.", "<|im", "_end", "|>")
        assertThat(text).isEqualTo("Answer.")
        assertThat(stopped).isTrue()
    }

    @Test
    fun textThatMerelyStartsLikeAMarkerIsReleasedNotSwallowed() {
        // "<" then "s" cannot become any marker once "sensible" continues, so it
        // must reach the user rather than being held forever.
        val (text, stopped) = run("Use ", "<", "sensible", " defaults>")
        assertThat(text).isEqualTo("Use <sensible defaults>")
        assertThat(stopped).isFalse()
    }

    @Test
    fun aTrailingPartialMarkerIsFlushedRatherThanLost() {
        // Generation can end while a suspicious tail is held. It was ordinary
        // text after all, so it is shown.
        val (text, stopped) = run("The tag is <end", "_of")
        assertThat(text).isEqualTo("The tag is <end_of")
        assertThat(stopped).isFalse()
    }

    @Test
    fun markdownAngleBracketsAreNotHeldBack() {
        val (text, _) = run("Compare ", "a < b ", "and c > d.")
        assertThat(text).isEqualTo("Compare a < b and c > d.")
    }
}
