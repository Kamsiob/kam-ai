package com.kamsiob.kamai.llm

import com.kamsiob.kamai.data.Role

/**
 * Turns a conversation into a prompt, and cleans what comes back.
 *
 * The layout itself belongs to [ChatFormat], because Kam AI ships models from
 * two families and Gemma and Qwen want quite different things. What lives here
 * is everything that is the same either way: fitting history to a token budget,
 * and scrubbing control tokens out of streamed output.
 */
object PromptBuilder {

    data class Turn(val role: Role, val content: String)

    /**
     * Builds the full prompt in the layout the given model expects.
     *
     * @param history oldest first. Callers trim this to fit the context budget
     *   before calling; see [fitToBudget].
     */
    fun build(
        format: ChatFormat,
        systemPrompt: String,
        history: List<Turn>,
        pendingUserMessage: String? = null,
    ): String = format.build(systemPrompt, history, pendingUserMessage)

    /** A one-shot request that is not part of any conversation. */
    fun oneShot(format: ChatFormat, instruction: String, input: String): String =
        format.oneShot(instruction, input)

    /**
     * Every control token either family can emit. Both lists are scrubbed
     * regardless of which model is loaded, because a stray marker leaking into
     * a bubble is worse than a redundant check.
     */
    private val ALL_STOP_MARKERS: List<String> =
        ChatFormat.entries.flatMap { it.stopMarkers }.distinct()

    /**
     * Strips control tokens and any stray thinking block from streamed output.
     * Small models occasionally emit these despite the template.
     */
    fun cleanOutput(raw: String): String {
        var text = raw
        val thinkEnd = text.indexOf("</think>")
        if (thinkEnd >= 0) {
            text = text.substring(thinkEnd + "</think>".length)
        }
        ALL_STOP_MARKERS.forEach { marker ->
            val at = text.indexOf(marker)
            if (at >= 0) text = text.substring(0, at)
        }
        return text.trim()
    }

    /** True when a streamed chunk means generation should stop now. */
    fun isStopMarker(piece: String): Boolean =
        ALL_STOP_MARKERS.any { piece.contains(it) }

    /**
     * The turns that fit the budget, plus how many were dropped because they did
     * not fit. [droppedForBudget] counts only budget drops — it deliberately
     * excludes the leading-assistant trim below, which removes a turn that fit
     * for a purely structural reason. Callers use it to decide whether to warn
     * that the conversation has outgrown the model's memory; warning when only a
     * greeting was trimmed would be a lie on the very first message.
     */
    data class Fitted(val turns: List<Turn>, val droppedForBudget: Int)

    /**
     * Drops the oldest turns until the history fits the budget, always keeping
     * whole turns so the model never sees half an exchange.
     *
     * @param estimateTokens counts tokens for a string. Injected so this can be
     *   tested without a loaded model.
     */
    fun fitToBudget(
        history: List<Turn>,
        budgetTokens: Int,
        estimateTokens: (String) -> Int,
    ): Fitted {
        if (history.isEmpty()) return Fitted(history, 0)

        var used = 0
        val kept = ArrayDeque<Turn>()

        // Walk backwards so the most recent exchange is the last thing dropped.
        for (turn in history.asReversed()) {
            val cost = estimateTokens(turn.content) + PER_TURN_OVERHEAD
            if (used + cost > budgetTokens) break
            used += cost
            kept.addFirst(turn)
        }

        // Turns the budget could not hold. Counted here, before the structural
        // trim below, so a stripped greeting is never mistaken for an overflow.
        val droppedForBudget = history.size - kept.size

        // Never start the history on an assistant turn: an answer with no
        // question in front of it reads as if the model said it unprompted. A
        // Discover chat opens with an assistant greeting, so this fires on the
        // first question — which is exactly why it must not count as a drop.
        while (kept.isNotEmpty() && kept.first().role == Role.ASSISTANT) {
            kept.removeFirst()
        }

        return Fitted(kept.toList(), droppedForBudget)
    }

    /** Role tags and separators cost a few tokens per turn. */
    private const val PER_TURN_OVERHEAD = 8

    /** A rough token count for budgeting before a model is loaded. */
    fun roughTokenCount(text: String): Int = (text.length / 3.6).toInt() + 1
}
