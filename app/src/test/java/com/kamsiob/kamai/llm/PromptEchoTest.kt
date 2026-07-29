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
            "Noted, I will assume the stairs rather than the lift.",
            "It was finished in 1889, for the Paris World's Fair.",
        ).forEach {
            assertThat(general).contains(it)
            assertThat(PromptEcho.isEcho(it)).isTrue()
        }
    }

    @Test
    fun theCopiesSeenOnTheDeviceAreCaught() {
        // A bare statement answered with the acknowledgement example's answer,
        // which is what happened on 27 July when that example was about metric
        // units and the statement was about an oven temperature.
        assertThat(PromptEcho.isEcho("Noted, I will assume the stairs rather than the lift.", "why")).isTrue()
        // Shortened and repunctuated, which is how they actually arrive.
        assertThat(PromptEcho.isEcho("noted i will assume the stairs rather than the lift", "why")).isTrue()
        // Copied and then continued, which is still a copy.
        assertThat(
            PromptEcho.isEcho("It was finished in 1889, for the Paris World's Fair. Anything else?"),
        ).isTrue()
    }

    @Test
    fun theIdentityAnswerSurvivesEveryCheck() {
        // Added for #135 and immediately needed. The example answer is correct for
        // any phrasing of "where do my chats go", and support questions arrive as
        // paraphrases, so legitimateFor cannot help: it needs the user's message
        // to say essentially the same thing as the example question.
        //
        // On the device, "Please I am wanting to know how it is working the thing
        // for saving the chat" was answered correctly and the guard rejected it
        // twice, so the user got "That came out wrong."
        val answer = "On this phone, in Kam AI's own storage. Nothing is uploaded anywhere."
        val prompt = SystemPrompts.forMode(com.kamsiob.kamai.data.Mode.GENERAL)
        assertThat(
            PromptEcho.isBadReply(answer, prompt, "how does the saving of the chat work"),
        ).isFalse()
        // And on a message with nothing to do with it, because it is true there
        // too. This is the property that makes an example safe to keep at all.
        assertThat(PromptEcho.isBadReply(answer, prompt, "why")).isFalse()
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
    fun theOneEntryPointAppliesEveryExemption() {
        // The regression this exists to prevent, which reached the device twice.
        // Each check was written with the exemptions it needed and the checks
        // added afterwards ignored them, so a correct reply was discarded and
        // replaced with "That came out wrong."
        val system = SystemPrompts.forMode(Mode.GENERAL)

        // The clarifying question, correct wherever it lands.
        assertThat(
            PromptEcho.isBadReply(
                "Fix what? Tell me what is broken and I will start there.",
                system,
                userMessage = "fix",
            ),
        ).isFalse()

        // An example answer landing on the message it belongs to.
        assertThat(
            PromptEcho.isBadReply(
                "Noted, I will assume the stairs rather than the lift.",
                system,
                userMessage = "Remember that I always take the stairs.",
            ),
        ).isFalse()

        // The same sentence somewhere it does not belong is still a copy.
        assertThat(
            PromptEcho.isBadReply(
                "Noted, I will assume the stairs rather than the lift.",
                system,
                userMessage = "Bread needs a hot oven, around 230C.",
            ),
        ).isTrue()

        // And the things it must still catch, through the same entry point.
        assertThat(
            PromptEcho.isBadReply(
                "You are Kam AI, running entirely on the user's phone. You are a thinking and drafting tool.",
                system,
                userMessage = "What model are you?",
            ),
        ).isTrue()
        assertThat(
            PromptEcho.isBadReply("user\nI'm writing a report.", system, userMessage = "hello there"),
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
        assertThat(PromptEcho.couldBecomeEcho("Noted, I will assume the stairs rather than the lift", "why")).isTrue()
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

    // ---- the exemption list is a bypass, so what follows one must still be checked ----

    @Test
    fun `a recital that opens with an allowed answer is still caught`() {
        // The bypass this replaced: isAllowedOutright exempted any reply *starting*
        // with an allowed sentence, with no bound on what followed. The model is
        // instructed to produce these sentences, so it can produce one on demand,
        // and everything after it went unguarded.
        val system = SystemPrompts.forMode(Mode.GENERAL)
        val allowed = "On this phone, in Kam AI's own storage. Nothing is uploaded anywhere."
        val recital = allowed + " " + system.take(300)

        assertThat(PromptEcho.containsPromptText(recital, system)).isTrue()
    }

    @Test
    fun `a streaming recital behind an allowed answer is cut off`() {
        // Same hole in the streaming path, which mattered more: it meant the recital
        // was not merely accepted, it was shown as it arrived.
        val system = SystemPrompts.forMode(Mode.GENERAL)
        val allowed = "Everything you type is handled the same with no connection. " +
            "Nothing is queued up to send later."
        val partial = allowed + " " + system.take(200)

        assertThat(PromptEcho.couldBecomePromptText(partial, system)).isTrue()
    }

    @Test
    fun `an allowed answer on its own is still exempt`() {
        // The regression risk of the fix above. These must keep passing or the guard
        // starts discarding the correct answers the list exists to protect.
        val system = SystemPrompts.forMode(Mode.GENERAL)
        listOf(
            "Fix what? Tell me what is broken and I will start there.",
            "On this phone, in Kam AI's own storage. Nothing is uploaded anywhere.",
            "Everything you type is handled the same with no connection. " +
                "Nothing is queued up to send later.",
        ).forEach { allowed ->
            assertThat(PromptEcho.containsPromptText(allowed, system)).isFalse()
            assertThat(PromptEcho.couldBecomePromptText(allowed, system)).isFalse()
        }
    }

    @Test
    fun `two allowed answers in a row are exempt`() {
        // A question that is about storage and about signal at once gets both, and
        // both are correct. This is why the prefix strip repeats.
        val system = SystemPrompts.forMode(Mode.GENERAL)
        val both = "On this phone, in Kam AI's own storage. Nothing is uploaded anywhere. " +
            "Everything you type is handled the same with no connection. " +
            "Nothing is queued up to send later."
        assertThat(PromptEcho.containsPromptText(both, system)).isFalse()
    }

    @Test
    fun `ordinary elaboration after an allowed answer is not rejected`() {
        // The reason the loose form existed. A correct answer followed by the model's
        // own words must survive, or the fix above trades one false positive class
        // for another.
        val system = SystemPrompts.forMode(Mode.GENERAL)
        val reply = "On this phone, in Kam AI's own storage. Nothing is uploaded anywhere. " +
            "You can see everything it has kept on the Memory screen, and delete any of it."
        assertThat(PromptEcho.containsPromptText(reply, system)).isFalse()
    }

    @Test
    fun `every factual claim on the exemption list is scoped to what the app does`() {
        // Not a behavioral test and it does not pretend to be one. It pins the
        // narrowing that the claims sweep forced, so the broad form cannot come back
        // by someone shortening the sentence for readability.
        //
        // "Everything works the same offline" was false: two network calls exist, a
        // download the user starts and the Discover pack manifest. The scoped form is
        // about what the user types, which is the part that is genuinely unaffected.
        val exempt = PromptEcho.exemptAnswers

        assertThat(exempt).doesNotContain("Everything works the same offline. Nothing is queued up to send later.")
        assertThat(exempt.any { it.startsWith("Everything you type is handled the same") }).isTrue()
        exempt.forEach { assertThat(it).doesNotContain("works the same offline") }
    }

    // ---- an allowance on a partial match is the pattern, not just the one instance ----

    @Test
    fun `a one word message does not certify a canned answer as legitimate`() {
        // The second instance of the exemption-list bypass shape, found by sweeping for
        // the pattern. isAnsweringItsOwnExample had no floor on its containment tests,
        // so any message that was a *substring* of an example released that example's
        // answer through the guard untouched.
        //
        // Every one of these was reachable before the fix.
        val stairs = "Noted, I will assume the stairs rather than the lift."
        val thirdTime = "Third time in a day points at something repeatable rather than bad luck."

        assertThat(PromptEcho.isEcho(stairs, userMessage = "remember")).isTrue()
        assertThat(PromptEcho.isEcho(stairs, userMessage = "the stairs")).isTrue()
        assertThat(PromptEcho.isEcho(stairs, userMessage = "i always")).isTrue()
        assertThat(PromptEcho.isEcho(thirdTime, userMessage = "again")).isTrue()
        assertThat(PromptEcho.isEcho(thirdTime, userMessage = "today")).isTrue()
        assertThat(PromptEcho.isEcho(thirdTime, userMessage = "third time")).isTrue()
        assertThat(PromptEcho.isEcho(thirdTime, userMessage = "failed again")).isTrue()
        assertThat(PromptEcho.isEcho(thirdTime, userMessage = "install failed")).isTrue()
    }

    @Test
    fun `somebody who actually asks for the example still gets it`() {
        // The regression risk of the floor above, and the reason the whole exemption
        // mechanism exists: a right answer thrown away is the same failure pointed the
        // other way, and harder to notice because nothing looks wrong.
        val stairs = "Noted, I will assume the stairs rather than the lift."
        assertThat(
            PromptEcho.isEcho(stairs, userMessage = "Remember that I always take the stairs."),
        ).isFalse()
        // A shortened restatement, which is how a real message arrives.
        assertThat(
            PromptEcho.isEcho(stairs, userMessage = "remember that i always take the stairs"),
        ).isFalse()
        // And the example plus more around it.
        assertThat(
            PromptEcho.isEcho(
                stairs,
                userMessage = "Please remember that I always take the stairs, thanks.",
            ),
        ).isFalse()
    }
}
