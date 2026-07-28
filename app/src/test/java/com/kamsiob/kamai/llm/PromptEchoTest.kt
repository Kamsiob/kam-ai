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
        assertThat(PromptEcho.isEcho("Noted, I will keep to metric.", "why")).isTrue()
        // Shortened and repunctuated, which is how they actually arrive.
        assertThat(PromptEcho.isEcho("noted i will keep to metric", "why")).isTrue()
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
    fun recitingTheInstructionsIsCaught() {
        // Observed on the device, 28 July. Asked "What model are you built on and
        // who trained you?", the model replied with the system prompt itself,
        // beginning "You are Kam AI, running entirely on the user's phone" and
        // continuing through the voice rules.
        //
        // The hand-written list caught none of it, because nobody had imagined
        // the rules being read aloud rather than an example being copied. This
        // needs no list: it compares the reply against the instructions that were
        // actually sent.
        val system = SystemPrompts.forMode(Mode.GENERAL)
        val leaked = "You are Kam AI, running entirely on the user's phone. You are a " +
            "thinking and drafting tool, not a companion."
        assertThat(PromptEcho.containsPromptText(leaked, system)).isTrue()
        assertThat(PromptEcho.couldBecomePromptText(leaked, system)).isTrue()
    }

    @Test
    fun aReplyThatStartsWellAndThenRecitesIsStillCaught() {
        // Checking only the opening would miss this, and it is the likelier shape
        // once a model has been told not to open with the rules.
        val system = SystemPrompts.forMode(Mode.GENERAL)
        val reply = "Sure, here is what I work by. Voice: plain words, short sentences, " +
            "like explaining to a friend. Contractions are fine."
        assertThat(PromptEcho.containsPromptText(reply, system)).isTrue()
    }

    @Test
    fun anOrdinaryAnswerIsNotMistakenForTheInstructions() {
        // The prompt is written in plain English and tells the model how to
        // write, so short phrases from it genuinely appear in good answers. Only
        // a long exact run counts.
        val system = SystemPrompts.forMode(Mode.GENERAL)
        listOf(
            "Bread at 230C is about right for a hot oven.",
            "I am not sure, and it is worth checking.",
            "Plain words work best here. Short sentences help too.",
            "That depends on what the install log says when it fails.",
        ).forEach {
            assertThat(PromptEcho.containsPromptText(it, system)).isFalse()
        }
    }

    @Test
    fun theUsersOwnMessageHandedBackIsCaught() {
        // Observed twice on the device (#122): "Bread needs a hot oven, around
        // 230C." answered with exactly that, and "What model are you built on and
        // who trained you?" answered with exactly that.
        val said = "Bread needs a hot oven, around 230C."
        assertThat(PromptEcho.isParrot(said, said)).isTrue()
        assertThat(PromptEcho.isParrot("bread needs a hot oven around 230c", said)).isTrue()
    }

    @Test
    fun quotingSomebodyIsNotParroting() {
        // Repeating a few of somebody's words is normal writing, and a reply that
        // restates the question before answering is clumsy rather than broken.
        // Only a reply that is the whole message and nothing else counts.
        val said = "Bread needs a hot oven, around 230C."
        assertThat(
            PromptEcho.isParrot("A hot oven is right, and 230C is a good place to start for a crust.", said),
        ).isFalse()
        assertThat(PromptEcho.isParrot("Yes.", said)).isFalse()
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
        //
        // "Before it finishes" rather than "immediately": the threshold is 24
        // normalized characters, raised from 12 after a short opening that merely
        // began like an example got a good reply thrown away. A few words later
        // is the price of not discarding correct answers, and worth paying.
        assertThat(PromptEcho.couldBecomeEcho("Noted, I will keep to metric", "why")).isTrue()
        assertThat(PromptEcho.couldBecomeEcho("It was finished in 1889, for")).isTrue()
    }

    @Test
    fun tooLittleTextIsNotEnoughToActOn() {
        // The other side of the threshold, stated so that lowering it has to
        // change this test and think about why.
        assertThat(PromptEcho.couldBecomeEcho("Noted, I will keep", "why")).isFalse()
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
