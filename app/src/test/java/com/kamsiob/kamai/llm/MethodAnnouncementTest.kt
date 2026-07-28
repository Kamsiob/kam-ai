package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The detector for Brainstorm reading its method selection out loud (#58, #114,
 * #130).
 *
 * The false positives matter more than the misses, and by a wide margin. A miss
 * shows one clumsy reply. A false positive throws away a reply that was doing
 * its job and makes the user wait again, and this codebase has already shipped a
 * guard that did exactly that twice. So most of this file is replies that must
 * survive.
 */
class MethodAnnouncementTest {

    @Test
    fun theAnnouncementsSeenOnTheDeviceAreCaught() {
        listOf(
            "Only a topic or problem, no idea yet, or one vague idea. I'll use STARBURSTING.",
            "Only a topic, no idea yet, we will use STARBURSTING",
            "Let's use inversion here. What would guarantee this fails?",
            "I'm going to use the six questions on this.",
        ).forEach {
            assertThat(MethodAnnouncement.matched(it)).isNotNull()
        }
    }

    @Test
    fun aMethodNameWithoutAnAnnouncementSurvives() {
        // "Inversion" is an ordinary English word and the mode is allowed to use
        // it while doing the work. Requiring the announcing construction is what
        // separates the two, and without that test this check would reject valid
        // replies for containing a word.
        listOf(
            "That is an inversion of what you said earlier, which is worth sitting with.",
            "Your six questions all point at the same worry.",
            "The core of it seems to be trust, and branches from there.",
        ).forEach {
            assertThat(MethodAnnouncement.matched(it)).isNull()
        }
    }

    @Test
    fun namingWhatItIsAboutToDoIsNotAnAnnouncement() {
        // Brainstorm is *required* to say in one sentence what it is about to do
        // with the user's material. That is the mode's contract, and it reads
        // very like an announcement. If this check caught these, it would be
        // rejecting the prompt's own instruction.
        listOf(
            "Let's start from what you already notice rather than from a list of ideas. " +
                "What do you find yourself explaining more than once?",
            "Rather than hunting for new topics, let's find what is holding the old ones " +
                "in place. What would you have to stop believing?",
            "Let's look at where the deadlines are being missed. What kind of task slips?",
        ).forEach {
            assertThat(MethodAnnouncement.matched(it)).isNull()
        }
    }

    @Test
    fun anAcronymIsNotAMethodBeingShouted() {
        // The capitals rule exists because the prompt used to print eleven
        // capitalised labels. It must not fire on ordinary technical words, which
        // Workbench and General produce constantly.
        listOf(
            "The HTML is fine, the JSON underneath is what breaks.",
            "Send it over HTTPS and the PDF will open.",
        ).forEach {
            assertThat(MethodAnnouncement.matched(it)).isNull()
        }
    }

    @Test
    fun theAnnouncementComesOutAndTheQuestionStays() {
        // Rewriting beats regenerating: the question was built out of what the
        // user said and is worth keeping. Regenerating costs the user a minute
        // and often returns something worse.
        val draft = "Only a topic or problem, no idea yet. I'll use STARBURSTING.\n" +
            "What do you want this to achieve?"
        val fixed = MethodAnnouncement.strip(draft)
        assertThat(fixed).isNotNull()
        assertThat(fixed).doesNotContain("STARBURSTING")
        assertThat(fixed).contains("What do you want this to achieve?")
    }

    @Test
    fun aReplyThatIsNothingButTheAnnouncementCannotBeRewritten() {
        // Nothing is left to keep, so this has to go back to the model rather
        // than be trimmed into an empty bubble.
        assertThat(MethodAnnouncement.strip("I'll use STARBURSTING.")).isNull()
    }

    @Test
    fun strippingIsRefusedWhenItWouldRemoveTheQuestion() {
        // Brainstorm's contract is one thing you are doing and one question. A
        // reply that has lost its question is not a shorter reply, it is a
        // different mode, so this regenerates instead of shipping half of one.
        val draft = "Let's use inversion on this. It usually helps."
        assertThat(MethodAnnouncement.strip(draft)).isNull()
    }

    @Test
    fun anOrdinaryReplyIsLeftExactlyAlone() {
        val good = "Let's start with what already annoys you about the current name. " +
            "What is the first thing people get wrong when they hear it?"
        assertThat(MethodAnnouncement.matched(good)).isNull()
    }
}
