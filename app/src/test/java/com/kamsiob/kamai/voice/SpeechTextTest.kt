package com.kamsiob.kamai.voice

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Whisper's annotations never reach the composer.
 *
 * Found on the phone: recording a few seconds of silence typed the literal
 * string "[BLANK_AUDIO]" into the message box. The engine only checked for
 * empty text, and whisper does not return empty text when it hears nothing.
 */
class SpeechTextTest {

    @Test
    fun `the marker that was actually seen on the phone counts as nothing`() {
        assertThat(SpeechText.heardNothing("[BLANK_AUDIO]")).isTrue()
    }

    @Test
    fun `the other shapes whisper uses count as nothing too`() {
        listOf("[ Silence ]", "(silence)", "[MUSIC]", "[NOISE]", "( Pause )", "[BLANK_AUDIO] [BLANK_AUDIO]")
            .forEach { assertThat(SpeechText.heardNothing(it)).isTrue() }
    }

    @Test
    fun `real speech survives untouched`() {
        val said = "remind me to buy bread on the way home"
        assertThat(SpeechText.spokenWords(said)).isEqualTo(said)
        assertThat(SpeechText.heardNothing(said)).isFalse()
    }

    @Test
    fun `an annotation beside real speech is removed and the speech kept`() {
        // "[BLANK_AUDIO] yes that is the one" is not what anybody said either.
        assertThat(SpeechText.spokenWords("[BLANK_AUDIO] yes that is the one"))
            .isEqualTo("yes that is the one")
        assertThat(SpeechText.spokenWords("so I said [laughs] never mind"))
            .isEqualTo("so I said never mind")
    }

    @Test
    fun `spacing is tidied rather than left doubled`() {
        assertThat(SpeechText.spokenWords("one [noise]  two")).isEqualTo("one two")
    }

    @Test
    fun `nothing in is nothing out`() {
        assertThat(SpeechText.heardNothing("")).isTrue()
        assertThat(SpeechText.heardNothing("   ")).isTrue()
    }

    @Test
    fun `ordinary brackets in speech are a known cost, and stated`() {
        // Somebody dictating "the total (before tax) was twelve pounds" loses the
        // parenthetical. Accepted: whisper rarely emits punctuation-only brackets
        // from speech, and the alternative is a list of markers that goes stale
        // with every model and language.
        assertThat(SpeechText.spokenWords("the total (before tax) was twelve"))
            .isEqualTo("the total was twelve")
    }
}
