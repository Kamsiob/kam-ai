package com.kamsiob.kamai.voice

/**
 * Cleans what whisper returns before it is shown to anybody.
 *
 * whisper does not return an empty string when it hears nothing. It returns a
 * marker: `[BLANK_AUDIO]` most often, and `[ Silence ]`, `(music)`, `[NOISE]`
 * and similar depending on the audio. The engine only checked for empty text, so
 * recording silence typed the literal string **[BLANK_AUDIO]** into the
 * composer, as though the user had said it. Found by recording nothing and
 * watching it appear.
 *
 * Rather than listing every marker whisper might emit, which is a losing game
 * across models and languages, the rule is structural: anything in square
 * brackets or parentheses is whisper annotating rather than transcribing. Strip
 * those, and if nothing is left then nothing was said.
 */
object SpeechText {

    private val ANNOTATION = Regex("""[\[(][^\])]*[\])]""")

    /**
     * The spoken words, with whisper's annotations removed, or an empty string
     * when it heard no speech at all.
     *
     * Annotations are stripped even when there is real speech beside them, since
     * "[BLANK_AUDIO] yes that is the one" is not what anybody said either.
     */
    fun spokenWords(raw: String): String =
        ANNOTATION.replace(raw, " ").replace(Regex("""\s+"""), " ").trim()

    /** True when whisper heard nothing worth putting in the composer. */
    fun heardNothing(raw: String): Boolean = spokenWords(raw).isEmpty()
}
