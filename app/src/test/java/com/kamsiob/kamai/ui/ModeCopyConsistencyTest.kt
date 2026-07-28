package com.kamsiob.kamai.ui

import com.google.common.truth.Truth.assertThat
import com.kamsiob.kamai.ui.onboarding.OnboardingCopy
import org.junit.Test
import java.io.File

/**
 * The four modes are explained in three places, and they must not drift (#93).
 *
 * Onboarding introduces them, the one-time hint above the mode control repeats
 * the introduction for somebody who skipped onboarding, and Questions and
 * answers is where people go when they have forgotten. Three descriptions of the
 * same four things, written months apart, is how an app ends up telling somebody
 * two different stories about what Brainstorm does.
 *
 * The hint does not have its own copy at all: it renders OnboardingCopy's list,
 * so those two cannot disagree by construction. This covers the third.
 */
class ModeCopyConsistencyTest {

    private fun source(relative: String): String {
        val fromModule = File("../$relative")
        val text = (if (fromModule.exists()) fromModule else File(relative)).readText()
        // Kotlin string concatenation splits phrases across literals, so
        // "Logic " + "Partner" reads as two words that are never adjacent in the
        // file. Join the literals back together before looking for a phrase.
        return text.replace(Regex("\"\\s*\\+\\s*\""), "")
    }

    private val modeNames = listOf("General", "Logic Partner", "Brainstorm", "Workbench")

    @Test
    fun onboardingListsExactlyTheFourModes() {
        // Discover is deliberately absent: it is a source with its own tab, not a
        // mode. Listing it was issue #42.
        assertThat(OnboardingCopy.slide3Modes.map { it.first }).isEqualTo(modeNames)
        assertThat(OnboardingCopy.slide3Modes.map { it.second }.none { it.isBlank() }).isTrue()
    }

    @Test
    fun questionsAndAnswersDescribesTheSameFourModes() {
        val qa = source("app/src/main/java/com/kamsiob/kamai/ui/settings/QuestionsAndAnswers.kt")
        modeNames.forEach { assertThat(qa).contains(it) }
        // And does not introduce a fifth by describing Discover as a mode.
        assertThat(qa).doesNotContain("Discover mode")
    }

    @Test
    fun theHintReusesOnboardingsWordsRatherThanRepeatingThem() {
        // The check that keeps this true as the copy changes: if the hint ever
        // grows its own hardcoded list of mode descriptions, this fails and
        // somebody has to decide which of the two is right.
        val hint = source("app/src/main/java/com/kamsiob/kamai/ui/components/ModeBarHint.kt")
        assertThat(hint).contains("OnboardingCopy.slide3Modes")
        modeNames.drop(1).forEach { assertThat(hint).doesNotContain("\"$it\"") }
    }
}
