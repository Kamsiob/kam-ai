package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import com.kamsiob.kamai.data.Role
import com.kamsiob.kamai.model.ModelCatalog
import com.kamsiob.kamai.model.Tier
import org.junit.Test

/**
 * Prompt layout for both families. A format bug does not crash: it produces
 * quietly worse answers and lets the guardrails slide off, which is exactly the
 * kind of thing that survives to release unless it is pinned by a test.
 */
class ChatFormatTest {

    private val system = "SYSTEM RULES"
    private val history = listOf(
        PromptBuilder.Turn(Role.USER, "first question"),
        PromptBuilder.Turn(Role.ASSISTANT, "first answer"),
        PromptBuilder.Turn(Role.USER, "second question"),
    )

    @Test
    fun `gemma folds the system prompt into the first user turn`() {
        val prompt = ChatFormat.GEMMA.build(system, history)

        // Gemma has no system role, so the rules ride on the first user turn.
        assertThat(prompt).startsWith("<bos><start_of_turn>user\nSYSTEM RULES")
        assertThat(prompt).doesNotContain("<|im_start|>")
        // Exactly once, not repeated on every user turn.
        assertThat(prompt.split("SYSTEM RULES").size - 1).isEqualTo(1)
    }

    @Test
    fun `gemma uses model rather than assistant and ends open for the reply`() {
        val prompt = ChatFormat.GEMMA.build(system, history)

        assertThat(prompt).contains("<start_of_turn>model\nfirst answer<end_of_turn>")
        assertThat(prompt).endsWith("<start_of_turn>model\n")
        assertThat(prompt).doesNotContain("assistant")
    }

    @Test
    fun `qwen keeps a real system role and closes the thinking block`() {
        val prompt = ChatFormat.QWEN.build(system, history)

        assertThat(prompt).startsWith("<|im_start|>system\nSYSTEM RULES<|im_end|>")
        // Thinking is closed before it opens so answers start straight away
        // instead of stalling behind a reasoning block on a phone.
        assertThat(prompt).endsWith("<|im_start|>assistant\n<think>\n\n</think>\n\n")
        assertThat(prompt).doesNotContain("<start_of_turn>")
    }

    @Test
    fun `a pending message is appended as a user turn in both formats`() {
        val gemma = ChatFormat.GEMMA.build(system, history, "pending thing")
        val qwen = ChatFormat.QWEN.build(system, history, "pending thing")

        assertThat(gemma).contains("<start_of_turn>user\npending thing<end_of_turn>")
        assertThat(qwen).contains("<|im_start|>user\npending thing<|im_end|>")
    }

    @Test
    fun `an empty conversation still puts the rules in front of the model`() {
        val gemma = ChatFormat.GEMMA.build(system, emptyList())
        val qwen = ChatFormat.QWEN.build(system, emptyList())

        assertThat(gemma).contains("SYSTEM RULES")
        assertThat(qwen).contains("SYSTEM RULES")
        assertThat(gemma).endsWith("<start_of_turn>model\n")
    }

    @Test
    fun `control tokens from either family are scrubbed from output`() {
        assertThat(PromptBuilder.cleanOutput("A real answer<end_of_turn>"))
            .isEqualTo("A real answer")
        assertThat(PromptBuilder.cleanOutput("A real answer<|im_end|>"))
            .isEqualTo("A real answer")
        assertThat(PromptBuilder.cleanOutput("<think>\n\n</think>\n\nA real answer"))
            .isEqualTo("A real answer")
        assertThat(PromptBuilder.isStopMarker("<end_of_turn>")).isTrue()
        assertThat(PromptBuilder.isStopMarker("<|im_end|>")).isTrue()
        assertThat(PromptBuilder.isStopMarker(" ordinary text")).isFalse()
    }

    @Test
    fun `history is trimmed oldest first and never starts on an answer`() {
        val long = listOf(
            PromptBuilder.Turn(Role.USER, "a".repeat(400)),
            PromptBuilder.Turn(Role.ASSISTANT, "b".repeat(400)),
            PromptBuilder.Turn(Role.USER, "keep me"),
        )

        val fitted = PromptBuilder.fitToBudget(long, budgetTokens = 40) {
            PromptBuilder.roughTokenCount(it)
        }

        assertThat(fitted.turns.map { it.content }).containsExactly("keep me")
        assertThat(fitted.turns.first().role).isEqualTo(Role.USER)
        // The two older turns genuinely did not fit the budget.
        assertThat(fitted.droppedForBudget).isEqualTo(2)
    }

    @Test
    fun `a leading greeting trimmed for structure is not counted as a budget drop`() {
        // A Discover chat opens with an assistant greeting. On the first question
        // both turns fit, but the greeting is stripped so history does not start
        // on an answer. That structural trim must not report a budget drop, or
        // the app would falsely warn "the conversation is too long" at message one.
        val turns = listOf(
            PromptBuilder.Turn(Role.ASSISTANT, "Let's talk about this passage."),
            PromptBuilder.Turn(Role.USER, "who wrote it?"),
        )

        val fitted = PromptBuilder.fitToBudget(turns, budgetTokens = 4096) {
            PromptBuilder.roughTokenCount(it)
        }

        assertThat(fitted.turns.map { it.content }).containsExactly("who wrote it?")
        assertThat(fitted.droppedForBudget).isEqualTo(0)
    }

    @Test
    fun `trimming drops a dangling answer rather than leading with it`() {
        val turns = listOf(
            PromptBuilder.Turn(Role.USER, "x".repeat(4000)),
            PromptBuilder.Turn(Role.ASSISTANT, "short answer"),
        )

        val fitted = PromptBuilder.fitToBudget(turns, budgetTokens = 30) {
            PromptBuilder.roughTokenCount(it)
        }

        // The question no longer fits, so its answer must not lead the history.
        assertThat(fitted.turns).isEmpty()
        // The oversized question was a real budget drop; the dangling answer trim
        // is structural and not counted, so exactly one budget drop is reported.
        assertThat(fitted.droppedForBudget).isEqualTo(1)
    }

    @Test
    fun `every tier ships Gemma 4, which covers the whole range`() {
        // Gemma 4's size range fills the band that once forced Qwen at the top,
        // so the app is one family, one licence, one prompt format.
        assertThat(ModelCatalog.forTier(Tier.BASIC).format).isEqualTo(ChatFormat.GEMMA)
        assertThat(ModelCatalog.forTier(Tier.BALANCED).format).isEqualTo(ChatFormat.GEMMA)
        assertThat(ModelCatalog.forTier(Tier.BEST).format).isEqualTo(ChatFormat.GEMMA)
    }

    @Test
    fun `each tier names the Gemma 4 variant sized for it`() {
        assertThat(ModelCatalog.forTier(Tier.BASIC).parameterLabel).isEqualTo("E2B")
        assertThat(ModelCatalog.forTier(Tier.BALANCED).parameterLabel).isEqualTo("E4B")
        assertThat(ModelCatalog.forTier(Tier.BEST).parameterLabel).isEqualTo("E4B")
    }

    @Test
    fun `every default tier model is Apache licensed with no asterisk`() {
        ModelCatalog.defaults.forEach {
            assertThat(it.licence).isEqualTo("Apache-2.0")
        }
    }

    @Test
    fun `every model has a real hash and an official source`() {
        ModelCatalog.all.forEach { model ->
            assertThat(model.sha256).hasLength(64)
            assertThat(model.sha256).matches("[0-9a-f]{64}")
            assertThat(model.sourceUrl).startsWith("https://huggingface.co/")
            assertThat(model.downloadBytes).isGreaterThan(0L)
            assertThat(model.licence).isNotEmpty()
        }
    }
}
