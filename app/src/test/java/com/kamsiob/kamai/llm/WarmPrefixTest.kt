package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import com.kamsiob.kamai.data.Mode
import com.kamsiob.kamai.data.Role
import org.junit.Test

/**
 * The warm-up prefix must be a literal prefix of the prompt it is meant to warm.
 *
 * This exists because the warm-up was written twice and was wrong both times, and
 * neither mistake showed up as anything except a timing that failed to improve.
 *
 * The first version ingested the bare system prompt. Gemma has no system role and
 * folds the instructions into the first user turn, so the real prompt began with
 * a turn opener the warm-up never emitted and the common prefix was zero.
 *
 * The second version added the turn opener and still bought nothing, because
 * `build` also emits a leading `<bos>`. One token, at position zero, and the whole
 * cache was unusable: the phone decoded 739 tokens during the warm-up and then
 * decoded 788 again the moment the user sent a message.
 *
 * Both were invisible on the device except as a number that did not move, and both
 * are caught here in milliseconds. A prefix that is not a prefix is not a small
 * bug: it is exactly twice the work.
 */
class WarmPrefixTest {

    private val system = "SYSTEM INSTRUCTIONS HERE"

    @Test
    fun `every format's warm prefix is a real prefix of a first message prompt`() {
        ChatFormat.entries.forEach { format ->
            val real = format.build(system, emptyList(), "the user's first message")
            val warm = format.warmPrefix(system)
            assertThat(real).startsWith(warm)
        }
    }

    @Test
    fun `it is still a prefix when the conversation already has turns`() {
        // Warming happens once, and the prefix has to keep paying off on later
        // turns rather than only the first.
        ChatFormat.entries.forEach { format ->
            val history = listOf(
                PromptBuilder.Turn(Role.USER, "first question"),
                PromptBuilder.Turn(Role.ASSISTANT, "first answer"),
            )
            val real = format.build(system, history, "a follow up")
            assertThat(real).startsWith(format.warmPrefix(system))
        }
    }

    @Test
    fun `the prefix carries the whole system prompt, not a fragment of it`() {
        // Warming half the instructions would halve the benefit while looking
        // like it worked.
        ChatFormat.entries.forEach { format ->
            assertThat(format.warmPrefix(system)).contains(system)
        }
    }

    @Test
    fun `it holds for every mode's real system prompt`() {
        // The mode prompts differ in length and content, and one of them
        // containing something that changes tokenisation would break this
        // silently.
        Mode.entries.forEach { mode ->
            val prompt = SystemPrompts.forMode(mode)
            ChatFormat.entries.forEach { format ->
                val real = format.build(prompt, emptyList(), "hello")
                assertThat(real).startsWith(format.warmPrefix(prompt))
            }
        }
    }
}
