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
                }
            }

            // A conversation that somehow starts with no user turn still needs
            // the rules in front of the model.
            if (!systemAttached) {
                append("<start_of_turn>user\n").append(systemPrompt).append("<end_of_turn>\n")
            }

            append("<start_of_turn>model\n")
        }

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

        override val stopMarkers = listOf("<|im_end|>", "<|im_start|>", "<|endoftext|>")
    },
    ;

    abstract fun build(
        systemPrompt: String,
        history: List<PromptBuilder.Turn>,
        pendingUserMessage: String? = null,
    ): String

    abstract val stopMarkers: List<String>

    fun oneShot(instruction: String, input: String): String =
        build(instruction, listOf(PromptBuilder.Turn(Role.USER, input)))

    internal companion object {
        const val BOS = "<bos>"
        const val EMPTY_THINKING = "<think>\n\n</think>\n\n"
    }
}
