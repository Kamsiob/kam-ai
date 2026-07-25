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
    const val INSTRUCTION =
        "Close this session now. Write the summary itself, not a description of how you " +
            "would write it. Give it in three short parts: the themes in what I said, " +
            "which ideas have energy, and what is still unresolved. Then tell me the one " +
            "thing to do next. Do not name your method, do not describe these steps, and " +
            "do not end with a question."
}
