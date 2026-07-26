package com.kamsiob.kamai.llm

/**
 * Summarizing a conversation, on request (#86).
 *
 * Summarizing is transformation rather than recall, which is where a small
 * on-device model is at its best, so this should work well. That is not a reason
 * to overclaim: a summary is the model's reading of the conversation, and the
 * sheet says so rather than presenting it as a record.
 *
 * Never automatic and never on opening. The user decides when it is worth the
 * time and the battery, which on a phone are the same thing.
 *
 * The parts that decide *what* to summarize live here, pure, because they are
 * the parts that go wrong: a conversation too short to be worth it, and one too
 * long to fit, which must be covered honestly rather than silently truncated.
 */
object Summarizer {

    /**
     * Below this, a summary is not worth making.
     *
     * Four messages is two exchanges. Anybody can read two exchanges faster than
     * the phone can summarize them, and offering a summary of them makes the
     * feature look like it does not know what it is for.
     */
    const val MIN_MESSAGES = 4

    /** And below this many characters, however many messages there are. */
    const val MIN_CHARS = 600

    sealed interface Plan {
        /** Short enough to read, so say that rather than produce a summary. */
        data class TooShort(val message: String) : Plan

        /** One pass over the whole conversation. */
        data class Whole(val text: String) : Plan

        /**
         * Several passes, combined.
         *
         * A conversation longer than the window is summarized in sections and the
         * sections are summarized together, rather than quietly summarizing only
         * the part that happened to fit.
         */
        data class Sectioned(val sections: List<String>) : Plan
    }

    /**
     * How to summarize [transcript], given how much room the model has.
     *
     * @param budgetChars roughly what fits in the context alongside the
     *   instruction and the reply. Characters rather than tokens because the
     *   caller knows the model's window in tokens and the conversion is the same
     *   rough one used everywhere else.
     */
    fun plan(transcript: List<String>, budgetChars: Int): Plan {
        val kept = transcript.filter { it.isNotBlank() }
        val total = kept.sumOf { it.length }
        if (kept.size < MIN_MESSAGES || total < MIN_CHARS) {
            return Plan.TooShort(
                "This one is short enough to read. A summary would take longer than " +
                    "scrolling up.",
            )
        }

        val joined = kept.joinToString("\n\n")
        if (joined.length <= budgetChars) return Plan.Whole(joined)

        // Sections are whole messages, never split mid-message: half an answer
        // summarized on its own produces a section about nothing.
        val sections = mutableListOf<String>()
        val current = StringBuilder()
        for (message in kept) {
            val piece = if (message.length > budgetChars) message.take(budgetChars) else message
            if (current.isNotEmpty() && current.length + piece.length + 2 > budgetChars) {
                sections += current.toString()
                current.setLength(0)
            }
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(piece)
        }
        if (current.isNotEmpty()) sections += current.toString()
        return Plan.Sectioned(sections)
    }

    /** The instruction for one pass over a whole conversation. */
    val WHOLE_INSTRUCTION = """
        Summarize the conversation below for the person who had it. Give it in
        three short parts: what was discussed, what was decided or concluded, and
        anything left open. Write the summary itself, not a description of how you
        would write it. Do not add advice, do not add anything that was not said,
        and do not end with a question.
    """.trimIndent()

    /** The instruction for one section of a long conversation. */
    val SECTION_INSTRUCTION = """
        Summarize this part of a longer conversation in a few sentences. Keep the
        points and the decisions; drop the pleasantries. Do not add anything that
        was not said.
    """.trimIndent()

    /** The instruction that folds the section summaries into one. */
    val COMBINE_INSTRUCTION = """
        Below are summaries of consecutive parts of one conversation. Combine them
        into a single summary in three short parts: what was discussed, what was
        decided or concluded, and anything left open. Do not repeat a point twice
        and do not add anything new.
    """.trimIndent()

    /**
     * The line shown above a summary, which changes with how it was made.
     *
     * A sectioned summary says so. Summarizing a long conversation in pieces and
     * combining them loses more than one pass would, and the person reading it
     * should know that rather than be told a clean story about how it was made.
     */
    fun provenance(plan: Plan, sections: Int = 0): String = when (plan) {
        is Plan.TooShort -> ""
        is Plan.Whole -> "Kam AI's reading of this conversation, not a record of it."
        is Plan.Sectioned ->
            "This conversation is longer than the model can hold at once, so it was " +
                "summarized in $sections parts and those were combined. Kam AI's " +
                "reading of it, not a record of it."
    }
}
