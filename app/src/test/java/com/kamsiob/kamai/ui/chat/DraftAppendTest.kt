package com.kamsiob.kamai.ui.chat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * What happens to a recording the user walked away from (#65).
 *
 * Leaving the chat mid-recording used to discard the audio and say so. A
 * Brainstorm brain dump is minutes of someone thinking out loud, and losing it
 * to an incoming call was the app choosing the easy thing over the useful one.
 * It now transcribes and leaves the words in the draft.
 *
 * The joining is where this can go visibly wrong, since the result lands in a
 * composer the user reads: a doubled space, a leading space, or a stray
 * "[BLANK_AUDIO]" from whisper hearing a room. These pin each case.
 */
class DraftAppendTest {

    @Test
    fun `speech lands in an empty composer on its own`() {
        assertThat(appendToDraft("", "so the third option is the one"))
            .isEqualTo("so the third option is the one")
    }

    @Test
    fun `speech follows what was already typed with one space`() {
        assertThat(appendToDraft("Notes:", "so the third option is the one"))
            .isEqualTo("Notes: so the third option is the one")
    }

    @Test
    fun `a trailing space in the draft does not become two`() {
        // Typing a word and then hitting the microphone leaves exactly this.
        assertThat(appendToDraft("Notes: ", "the third option"))
            .isEqualTo("Notes: the third option")
    }

    @Test
    fun `whisper's leading space is not carried into the composer`() {
        // whisper.cpp prefixes its output with a space; on an empty draft that
        // would show as an indented first line.
        assertThat(appendToDraft("", " the third option")).isEqualTo("the third option")
    }

    @Test
    fun `hearing nothing leaves the draft exactly as it was`() {
        // The caller compares against the input to decide whether to say
        // anything, so this has to be the same string, not an equal-looking one.
        assertThat(appendToDraft("Notes:", "   ")).isEqualTo("Notes:")
        assertThat(appendToDraft("", "")).isEqualTo("")
    }

    @Test
    fun `a whitespace-only draft is replaced rather than appended to`() {
        // Otherwise the composer opens with the cursor pushed off a blank line.
        assertThat(appendToDraft("\n  ", "the third option")).isEqualTo("the third option")
    }
}
