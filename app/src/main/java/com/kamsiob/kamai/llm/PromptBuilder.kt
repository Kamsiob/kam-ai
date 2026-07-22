package com.kamsiob.kamai.llm

import com.kamsiob.kamai.data.Role

/**
 * Formats a conversation the way Qwen3 was trained to receive it.
 *
 * The format is written out here rather than run through llama.cpp's Jinja
 * template engine because the app needs to budget tokens, drop old turns, and
 * re-inject the system prompt on every request, all of which are far easier
 * when the string is built in Kotlin.
 *
 * Thinking is closed immediately with an empty block. Qwen3 will otherwise
 * reason at length before saying anything, which on a phone means a long wait
 * looking at a typing indicator. See [ModelCatalog] for the full reasoning.
 */
object PromptBuilder {

    private const val IM_START = "<|im_start|>"
    private const val IM_END = "<|im_end|>"

    /** Closes the reasoning block before it opens, so answers start straight away. */
    private const val EMPTY_THINKING = "<think>\n\n</think>\n\n"

    data class Turn(val role: Role, val content: String)

    /**
     * Builds the full prompt.
     *
     * @param history oldest first. Callers trim this to fit the context budget
     *   before calling; see [fitToBudget].
     */
    fun build(
        systemPrompt: String,
        history: List<Turn>,
        pendingUserMessage: String? = null,
    ): String = buildString {
        append(IM_START).append("system\n").append(systemPrompt).append(IM_END).append('\n')

        history.forEach { turn ->
            val role = if (turn.role == Role.USER) "user" else "assistant"
            append(IM_START).append(role).append('\n')
            if (turn.role == Role.ASSISTANT) append(EMPTY_THINKING)
            append(turn.content).append(IM_END).append('\n')
        }

        if (pendingUserMessage != null) {
            append(IM_START).append("user\n").append(pendingUserMessage).append(IM_END).append('\n')
        }

        // Open the assistant turn and close its thinking block for it.
        append(IM_START).append("assistant\n").append(EMPTY_THINKING)
    }

    /** A one-shot request that is not part of any conversation. */
    fun oneShot(instruction: String, input: String): String = buildString {
        append(IM_START).append("system\n").append(instruction).append(IM_END).append('\n')
        append(IM_START).append("user\n").append(input).append(IM_END).append('\n')
        append(IM_START).append("assistant\n").append(EMPTY_THINKING)
    }

    /** Text the model may emit that should never reach the user. */
    private val STOP_MARKERS = listOf(IM_END, IM_START, "<|endoftext|>")

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
        STOP_MARKERS.forEach { marker ->
            val at = text.indexOf(marker)
            if (at >= 0) text = text.substring(0, at)
        }
        return text.trim()
    }

    /** True when a streamed chunk means generation should stop now. */
    fun isStopMarker(piece: String): Boolean =
        STOP_MARKERS.any { piece.contains(it) }

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
    ): List<Turn> {
        if (history.isEmpty()) return history

        var used = 0
        val kept = ArrayDeque<Turn>()

        // Walk backwards so the most recent exchange is the last thing dropped.
        for (turn in history.asReversed()) {
            val cost = estimateTokens(turn.content) + PER_TURN_OVERHEAD
            if (used + cost > budgetTokens) break
            used += cost
            kept.addFirst(turn)
        }

        // Never start the history on an assistant turn: an answer with no
        // question in front of it reads as if the model said it unprompted.
        while (kept.isNotEmpty() && kept.first().role == Role.ASSISTANT) {
            kept.removeFirst()
        }

        return kept.toList()
    }

    /** Role tags and separators cost a few tokens per turn. */
    private const val PER_TURN_OVERHEAD = 8

    /** A rough token count for budgeting before a model is loaded. */
    fun roughTokenCount(text: String): Int = (text.length / 3.6).toInt() + 1
}
