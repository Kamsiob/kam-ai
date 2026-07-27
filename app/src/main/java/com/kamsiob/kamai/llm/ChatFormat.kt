package com.kamsiob.kamai.llm

import com.kamsiob.kamai.data.Role

/**
 * How a model wants its conversation laid out.
 *
 * Kam AI ships Gemma 4 across every tier today, but the format is kept per
 * model rather than hardcoded so a future Qwen or other family drops in cleanly.
 * Each format is written out here rather than run through llama.cpp's Jinja template
 * engine, because the app needs to budget tokens, drop old turns, and re-inject
 * the system prompt on every request, all of which are far easier when the
 * string is built in Kotlin.
 */
enum class ChatFormat {

    /**
     * Gemma 3. Turns are `<start_of_turn>user` and `<start_of_turn>model`.
     *
     * Gemma has no separate system role: the system prompt is folded into the
     * first user turn, which is what its own template does.
     */
    GEMMA {
        override fun build(
            systemPrompt: String,
            history: List<PromptBuilder.Turn>,
            pendingUserMessage: String?,
        ): String = buildString {
            append(BOS)

            val turns = history + listOfNotNull(
                pendingUserMessage?.let { PromptBuilder.Turn(Role.USER, it) },
            )

            var systemAttached = false
            turns.forEach { turn ->
                when (turn.role) {
                    Role.USER -> {
                        append("<start_of_turn>user\n")
                        if (!systemAttached) {
                            append(systemPrompt).append("\n\n")
                            systemAttached = true
                        }
                        append(turn.content).append("<end_of_turn>\n")
                    }

                    Role.ASSISTANT -> {
                        append("<start_of_turn>model\n")
                            .append(turn.content).append("<end_of_turn>\n")
                    }

                    // Display-only markers are filtered out before the prompt is
                    // built; never emit them as a turn.
                    Role.SYSTEM -> Unit
                }
            }

            // A conversation that somehow starts with no user turn still needs
            // the rules in front of the model.
            if (!systemAttached) {
                append("<start_of_turn>user\n").append(systemPrompt).append("<end_of_turn>\n")
            }

            append("<start_of_turn>model\n")
        }

        /**
         * Gemma has no system role, so the instructions are folded into the first
         * user turn. That makes the warmable prefix the turn opener plus the
         * system text, and everything after it is the user's own message.
         */
        override fun warmPrefix(systemPrompt: String): String =
            // BOS included, because build() emits it and the prefix has to be
            // byte for byte what the real prompt starts with. Leaving it out made
            // the very first token differ, so the common prefix was zero and the
            // warm up decoded 739 tokens that were then decoded again. It cost
            // nothing to get wrong and nothing to detect except measuring, which
            // is the only reason it was found: the timing did not move.
            BOS + "<start_of_turn>user\n" + systemPrompt + "\n\n"

        override val stopMarkers = listOf("<end_of_turn>", "<start_of_turn>", "<eos>")
    },

    /**
     * Qwen3. Turns are ChatML, with a real system role.
     *
     * Qwen3 will otherwise reason at length before answering, which on a phone
     * means a long wait looking at a typing indicator, so the thinking block is
     * closed before it opens.
     */
    QWEN {
        override fun build(
            systemPrompt: String,
            history: List<PromptBuilder.Turn>,
            pendingUserMessage: String?,
        ): String = buildString {
            append("<|im_start|>system\n").append(systemPrompt).append("<|im_end|>\n")

            history.forEach { turn ->
                val role = if (turn.role == Role.USER) "user" else "assistant"
                append("<|im_start|>").append(role).append('\n')
                if (turn.role == Role.ASSISTANT) append(EMPTY_THINKING)
                append(turn.content).append("<|im_end|>\n")
            }

            if (pendingUserMessage != null) {
                append("<|im_start|>user\n").append(pendingUserMessage).append("<|im_end|>\n")
            }

            append("<|im_start|>assistant\n").append(EMPTY_THINKING)
        }

        /** Qwen has a real system role, so the whole block is the prefix. */
        override fun warmPrefix(systemPrompt: String): String =
            "<|im_start|>system\n" + systemPrompt + "<|im_end|>\n"

        override val stopMarkers = listOf("<|im_end|>", "<|im_start|>", "<|endoftext|>")
    },
    ;

    abstract fun build(
        systemPrompt: String,
        history: List<PromptBuilder.Turn>,
        pendingUserMessage: String? = null,
    ): String

    /**
     * The leading run of text every prompt in this format begins with, given a
     * system prompt, so it can be decoded before the user types (#38).
     *
     * This has to be the real templated opening rather than the system prompt on
     * its own, and the difference is not academic. The first version of the warm
     * up ingested the bare system text, and measuring showed it bought nothing at
     * all: the next prompt still prefilled 792 tokens of 792, because the token
     * streams diverged at the very first token where the template opener should
     * have been. A prefix that is not actually a prefix is just wasted work done
     * twice.
     */
    abstract fun warmPrefix(systemPrompt: String): String

    abstract val stopMarkers: List<String>

    fun oneShot(instruction: String, input: String): String =
        build(instruction, listOf(PromptBuilder.Turn(Role.USER, input)))

    internal companion object {
        const val BOS = "<bos>"
        const val EMPTY_THINKING = "<think>\n\n</think>\n\n"
    }
}
