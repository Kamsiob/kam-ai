package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Saying the user's content back in different words is not a reply (#122).
 *
 * Every case here is a real reply seen on the device, and the threshold was
 * calibrated against them rather than picked. The bad ones must be caught and
 * the good ones must survive, and the good ones matter more: discarding a
 * correct answer is the same failure as showing a copied one, and it has already
 * happened twice in this codebase.
 */
class RestatementTest {

    private val bread = "Bread needs a hot oven, around 230C."

    @Test
    fun theRestatementsSeenOnTheDeviceAreCaught() {
        listOf(
            "It needs a hot oven, around 230C.",
            // Caught only because of the stemmer: without it "ovens" and
            // "needed" miss "oven" and "needs" and this escapes on grammar.
            "Hot ovens are needed for bread.",
        ).forEach {
            assertThat(PromptEcho.isRestatement(it, bread)).isTrue()
        }
    }

    @Test
    fun oneRestatementThisCannotReach() {
        // "It needs a hot oven, around 230 degrees Celsius." is a restatement
        // that expands 230C into words. It shares four of seven content words
        // with the message, which is the same ratio as "It boils at 100 degrees
        // Celsius at standard atmospheric pressure." on its own message, and that
        // one is a good reply because it adds the condition the figure depends
        // on.
        //
        // Two replies with the same overlap, one bad and one good, is a limit of
        // counting words rather than a threshold that needs moving. Written down
        // rather than chased, because the way to catch this one is to catch the
        // good one too.
        assertThat(
            PromptEcho.isRestatement("It needs a hot oven, around 230 degrees Celsius.", bread),
        ).isFalse()
    }

    @Test
    fun therepliesThatSaidSomethingNewSurvive() {
        listOf(
            "It needs a high temperature to set the crust properly.",
            "That temperature should be maintained consistently for the baking process.",
        ).forEach {
            assertThat(PromptEcho.isRestatement(it, bread)).isFalse()
        }
    }

    @Test
    fun theBorderlineGoodReplyOnAnotherInputSurvives() {
        // "It boils at 100 degrees Celsius at standard atmospheric pressure."
        // shares most of its words with the message and is still a reply, because
        // it adds the condition the figure depends on. This is the case that puts
        // the threshold where it is.
        assertThat(
            PromptEcho.isRestatement(
                "It boils at 100 degrees Celsius at standard atmospheric pressure.",
                "Water boils at 100 degrees Celsius.",
            ),
        ).isFalse()
    }

    @Test
    fun aLongAnswerReusingTheSubjectsWordsIsNotARestatement() {
        // An answer that adds substance is longer than what it answers. Without
        // the length test this is the shape that would be wrongly discarded.
        val long = "A hot oven sets the crust before the inside finishes cooking, which " +
            "is why bread baked cool comes out pale and dense rather than risen."
        assertThat(PromptEcho.isRestatement(long, bread)).isFalse()
    }

    @Test
    fun shortExchangesAreLeftAlone() {
        // Too little content either side to judge, and acting on it would catch
        // correct short answers, which this app is often right about.
        assertThat(PromptEcho.isRestatement("Noted.", "Remember that.")).isFalse()
        assertThat(PromptEcho.isRestatement("Yes.", bread)).isFalse()
    }
}
