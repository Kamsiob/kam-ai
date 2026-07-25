package com.kamsiob.kamai.llm

import com.kamsiob.kamai.data.Mode

/**
 * Ending a Brainstorm session on purpose (#58).
 *
 * Brainstorm is told to converge when asked, and told plainly not to recite its
 * own method or answer a wrap-up with another question. In a short conversation
 * it obeys. Several exchanges deep it produced this, on the phone:
 *
 * > We've done STARBURSTING. ... To converge, group into themes, name which
 * > ideas have energy from what you engaged with, say what is unresolved, and
 * > ask you to pick. What feels like the most promising starting point here.
 *
 * It read the instructions aloud and then asked another question. The same words
 * work when the context is short, so the rule is not misunderstood; it is losing
 * against a long history.
 *
 * This puts the request where it cannot lose: the instruction goes in as the
 * final user turn, immediately before the model answers, rather than sitting at
 * the top of a prompt with a whole session in between.
 */
object WrapUp {

    /** Which modes have something to wrap up. */
    fun availableIn(mode: Mode): Boolean = mode == Mode.BRAINSTORM

    /** The quiet note left in the transcript, so the history shows what happened. */
    const val NOTE = "Wrapping up."

    /**
     * The instruction the model actually receives, as the last thing it reads.
     *
     * Spells out the shape of the answer rather than naming a method, because
     * naming a method is exactly what it echoes back. Ends by forbidding the
     * question, since that is the specific failure.
     */
    /**
     * Whether a typed message is asking to finish (#58).
     *
     * The Wrap-up control puts [INSTRUCTION] where the model cannot lose track of
     * it, and typing the same request in the composer went through the ordinary
     * path and hit the same failure the control was built to fix: several
     * exchanges deep, "let's wrap up" got another question. Most people will
     * type it rather than find the control, so the typed path has to reach the
     * same place.
     *
     * Matched on phrases rather than keywords. "summary" alone would fire on
     * "give me a summary of what a hub and spoke is", which is a question inside
     * the session and not a request to end it.
     *
     * Only consulted in Brainstorm, where the cost of a false positive is a
     * summary somebody did not ask for, one turn earlier than they wanted.
     */
    fun isRequest(text: String): Boolean {
        val t = text.lowercase().trim()
        if (t.length > 120) return false
        return REQUESTS.any { t.contains(it) }
    }

    private val REQUESTS = listOf(
        "wrap up", "wrap this up", "wrap it up",
        "let's stop", "lets stop", "let's finish", "lets finish",
        "that's enough", "thats enough", "enough for now",
        "sum up", "sum this up", "summarise what", "summarize what",
        "pull it together", "pull this together",
        "what have we got", "where have we got to",
        "i'm done", "im done", "we're done", "were done",
        "converge",
    )

    const val INSTRUCTION =
        "Close this session now. Write the summary itself, not a description of how you " +
            "would write it. Give it in three short parts: the themes in what I said, " +
            "which ideas have energy, and what is still unresolved. Then tell me the one " +
            "thing to do next. Do not name your method, do not describe these steps, and " +
            "do not end with a question."
}
