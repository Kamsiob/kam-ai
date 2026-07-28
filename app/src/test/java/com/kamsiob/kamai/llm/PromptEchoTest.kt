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
    fun theSafeExampleSurvivesEveryCheck() {
        // Regression. containsPromptText was added after the exemption existed
        // and knew nothing about it, so it caught the one sentence deliberately
        // allowed: "fix" started answering with the fallback instead of the
        // clarifying question. Found by running the battery after the change,
        // which is the only reason it was noticed.
        val system = SystemPrompts.forMode(Mode.GENERAL)
        val safe = "Fix what? Tell me what is broken and I will start there."
        assertThat(PromptEcho.containsPromptText(safe, system)).isFalse()
        assertThat(PromptEcho.couldBecomePromptText(safe, system)).isFalse()
        assertThat(PromptEcho.couldBecomePromptText("Fix what? Tell me what is broken", system)).isFalse()
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
    fun obeyingTheInstructionsIsNotRecitingThem() {
        // The trap in comparing replies against the prompt: the prompt says how
        // to write, partly in the words to use. "say when you are unsure or might
        // be wrong, and that it is worth checking and bookmarking" means a reply
        // doing exactly that shares a long run with the instructions. It is the
        // model obeying, not copying, and discarding it would punish the
        // behaviour the prompt asks for.
        val system = SystemPrompts.forMode(Mode.GENERAL)
        listOf(
            "I might be wrong about the date, and it is worth checking and bookmarking.",
            "I am not certain, so it is worth checking.",
        ).forEach {
            assertThat(PromptEcho.containsPromptText(it, system)).isFalse()
        }

        // And the other side of the line, so the threshold is pinned from both
        // directions: instruction text quoted at length is still reciting, even
        // though it is the same prompt these replies are obeying.
        assertThat(
            PromptEcho.containsPromptText(
                "Match the shape of the answer to what was asked. Four examples of shape only.",
                system,
            ),
        ).isTrue()
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
    fun aReplyThatWritesTheTranscriptIsCaught() {
        // Seen in Logic Partner, 28 July. The reply opened with a bare "user"
        // line, invented a message from the user, and then recited the prompt's
        // format examples. The invented text differs every time, so matching
        // against the prompt cannot be relied on, but the opening is always one
        // of a few words and is never a legitimate way to begin an answer.
        assertThat(
            PromptEcho.startsWithRoleMarker("user\nI'm trying to write a short report."),
        ).isTrue()
        assertThat(PromptEcho.startsWithRoleMarker("model:\nHere is the answer.")).isTrue()
        assertThat(PromptEcho.startsWithRoleMarker("assistant")).isTrue()
    }

    @Test
    fun anAnswerThatMerelyMentionsAUserIsNotCaught() {
        // The check is on the reply opening with a bare speaker label, not on the
        // word appearing. "User" is an ordinary noun in this app's subject matter.
        assertThat(PromptEcho.startsWithRoleMarker("User accounts are not needed here.")).isFalse()
        assertThat(PromptEcho.startsWithRoleMarker("The user decides what is kept.")).isFalse()
    }

    @Test
    fun reorderedExamplesAreNotCaughtByTheRunCheck() {
        // Recorded rather than fixed, because the honest answer is that this
        // check cannot catch it and should not be stretched until it can.
        //
        // The reply seen on 28 July recited the format examples with the headings
        // in the opposite order, which cuts the longest exact run to 25
        // characters. Matching on runs that short would start catching ordinary
        // answers, and discarding a good reply is the same failure as showing a
        // copied one.
        //
        // That reply is caught anyway, by startsWithRoleMarker, because it opened
        // by announcing a speaker. This test exists so the limitation is written
        // down rather than assumed away.
        val system = SystemPrompts.forMode(Mode.LOGIC)
        val recited = "## What to watch\nA paragraph.\n## Cost\nA paragraph."
        assertThat(PromptEcho.containsPromptText(recited, system)).isFalse()
        assertThat(
            PromptEcho.startsWithRoleMarker("user\nI'm writing a report.\n$recited"),
        ).isTrue()
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
