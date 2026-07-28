package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import com.kamsiob.kamai.data.Mode
import org.junit.Test
import java.io.File

/**
 * The guard against a reply that is really the prompt read back.
 *
 * Two things have to hold. The guard must catch the copies that were actually
 * observed on the device, and it must stay in step with the prompts, since a
 * guard defending a sentence no prompt contains any more is worse than none: it
 * reads as covered while covering nothing.
 */
class PromptEchoTest {

    private fun promptSource(): String {
        val relative = File("../app/src/main/java/com/kamsiob/kamai/llm/SystemPrompts.kt")
        val fromRoot = File("app/src/main/java/com/kamsiob/kamai/llm/SystemPrompts.kt")
        return (if (relative.exists()) relative else fromRoot).readText()
    }

    @Test
    fun everyProtectedLineIsActuallyInAPrompt() {
        // Without this, deleting an example from a prompt leaves the guard
        // defending a line that can no longer be copied, and the list slowly
        // becomes fiction while looking like coverage.
        val source = promptSource()
        PromptEcho.protected.forEach {
            assertThat(source).contains(it)
        }
    }

    @Test
    fun theExampleAnswersInGeneralAreCovered() {
        // The rule this encodes: an example may stay in a prompt only if emitting
        // it verbatim at the wrong moment is harmless. Anything failing that test
        // must be covered here instead.
        val general = SystemPrompts.forMode(Mode.GENERAL)
        listOf(
            "Noted, I will keep to metric.",
            "It was finished in 1889, for the Paris World's Fair.",
        ).forEach {
            assertThat(general).contains(it)
            assertThat(PromptEcho.isEcho(it)).isTrue()
        }
    }

    @Test
    fun theCopiesSeenOnTheDeviceAreCaught() {
        // "Bread needs a hot oven, around 230C." answered with the metric
        // example's answer, on 27 July.
        assertThat(PromptEcho.isEcho("Noted, I will keep to metric.")).isTrue()
        // Shortened and repunctuated, which is how they actually arrive.
        assertThat(PromptEcho.isEcho("noted i will keep to metric")).isTrue()
        // Copied and then continued, which is still a copy.
        assertThat(
            PromptEcho.isEcho("It was finished in 1889, for the Paris World's Fair. Anything else?"),
        ).isTrue()
    }

    @Test
    fun theSafeExampleIsNotGuarded() {
        // The clarifying question is correct whenever it appears, so discarding
        // and regenerating it would throw away a right answer. It is the one
        // example that passes the harmless test, and the reason the test exists.
        assertThat(PromptEcho.isEcho("Fix what? Tell me what is broken and I will start there."))
            .isFalse()
    }

    @Test
    fun ordinaryRepliesAreNotTouched() {
        listOf(
            "I don't have enough context to know what is happening. Tell me more.",
            "Bread at 230C is about right for a hot oven.",
            "That depends on what the install log says.",
            "Metric is fine.",
        ).forEach {
            assertThat(PromptEcho.isEcho(it)).isFalse()
        }
    }

    @Test
    fun aShortReplyIsNeverJudgedOnTooLittle() {
        // Acting on one or two words would discard correct short answers, and a
        // short answer is the thing this app is most often right about.
        assertThat(PromptEcho.isEcho("Noted.")).isFalse()
        assertThat(PromptEcho.couldBecomeEcho("Noted")).isFalse()
    }

    @Test
    fun aCopyIsSpottedBeforeItFinishes() {
        // Early detection is what makes this affordable. Buffering whole replies
        // to check them would give back the time to first token that #38 won.
        assertThat(PromptEcho.couldBecomeEcho("Noted, I will keep")).isTrue()
        assertThat(PromptEcho.couldBecomeEcho("It was finished in 1889")).isTrue()
    }

    @Test
    fun anOrdinaryOpeningIsNotAbandonedEarly() {
        listOf(
            "It depends on the oven",
            "That is worth checking",
            "Bread needs a hot oven",
        ).forEach {
            assertThat(PromptEcho.couldBecomeEcho(it)).isFalse()
        }
    }
}
