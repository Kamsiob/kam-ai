package com.kamsiob.kamai.ui

import com.google.common.truth.Truth.assertThat
import com.kamsiob.kamai.data.Mode
import com.kamsiob.kamai.llm.SystemPrompts
import com.kamsiob.kamai.ui.onboarding.OnboardingCopy
import com.kamsiob.kamai.ui.settings.QuestionsAndAnswers
import org.junit.Test

/**
 * Guards the copy a new user reads first.
 *
 * Issue #42 existed because nothing checked this. The four-mode rename was
 * complete in the code and the tests, and the onboarding slides and the Q&A went
 * on describing Chat, omitting Brainstorm, calling Discover a mode, and pointing
 * at a switcher that had been deleted. Every test passed the whole time.
 *
 * These assertions are deliberately about *meaning that has already gone stale
 * once*, not about exact sentences. Pinning whole paragraphs would make ordinary
 * editing fail the build and teach people to update the expected string without
 * reading it, which guards nothing.
 */
class PublicCopyTest {

    private val allPublicCopy: String
        get() = buildString {
            append(OnboardingCopy.slide1.title).append(' ')
            append(OnboardingCopy.slide1.body).append(' ')
            append(OnboardingCopy.slide2.title).append(' ')
            append(OnboardingCopy.SLIDE2_CLOSING).append(' ')
            append(OnboardingCopy.slide3.title).append(' ')
            OnboardingCopy.slide3Modes.forEach { append(it.first).append(' ').append(it.second).append(' ') }
            append(OnboardingCopy.SLIDE3_CLOSING).append(' ')
            append(OnboardingCopy.SLIDE4_CLOSING).append(' ')
            append(OnboardingCopy.slide5.body).append(' ')
            QuestionsAndAnswers.entries.forEach { append(it.question).append(' ').append(it.answer).append(' ') }
        }

    @Test
    fun theOnboardingModeListIsTheFourRealModes() {
        val named = OnboardingCopy.slide3Modes.map { it.first }
        assertThat(named).containsExactly("General", "Logic Partner", "Brainstorm", "Workbench").inOrder()
    }

    @Test
    fun everyRealModeIsNamedSomewhereInThePublicCopy() {
        // Whatever the modes are called, a user who reads onboarding and the Q&A
        // should have met all of them. Reads from the enum, so adding a mode and
        // forgetting to introduce it fails here.
        //
        // DISCOVER and OVERLAY are excluded because neither is a mode the user
        // picks: Discover is a source with its own tab, and OVERLAY is the
        // assistant surface. BENCH is not excluded; it is Workbench.
        val copy = allPublicCopy
        val missing = Mode.entries
            .filterNot { it == Mode.DISCOVER || it == Mode.OVERLAY }
            .filterNot { copy.contains(displayNameOf(it), ignoreCase = true) }
            .map { it.name }
        assertThat(missing).isEmpty()
    }

    @Test
    fun discoverIsNeverPresentedAsAMode() {
        // Discover is a source with its own tab. Listing it beside the modes was
        // one of the three defects in #42, and it is a decision that has been
        // reversed by accident before.
        assertThat(OnboardingCopy.slide3Modes.map { it.first }).doesNotContain("Discover")
    }

    @Test
    fun noPublicCopyPointsAtTheDeletedSwitcher() {
        // The pills at the top of a chat no longer exist. The mode control sits
        // below the messages, above the composer.
        val copy = allPublicCopy.lowercase()
        assertThat(copy).doesNotContain("pills at the top")
        assertThat(copy).doesNotContain("at the top of a chat")
        assertThat(copy).doesNotContain("at the top of any chat")
    }

    @Test
    fun savingIsCalledBookmarkingEverywhereInPublicCopy() {
        // One bookmark, one destination. "Flag" was the old word and survived in
        // two places after the unification. See DECISIONS.md, Item 9.
        val copy = allPublicCopy.lowercase()
        assertThat(copy).doesNotContain("flag it")
        assertThat(copy).doesNotContain("flagged")
        assertThat(copy).doesNotContain("a small flag")
    }

    @Test
    fun chatIsNoLongerTheNameOfAMode() {
        // Mode.CHAT became Mode.GENERAL. The word "chat" is still fine as an
        // ordinary noun, so this checks the shapes that name it as a mode rather
        // than banning the word.
        val copy = allPublicCopy
        assertThat(copy).doesNotContain("Chat is everyday")
        assertThat(copy).doesNotContain("Chat mode")
    }

    @Test
    fun theQuestionsAndAnswersStillCoverTheModes() {
        val modes = QuestionsAndAnswers.entries.single { it.question == "What are the modes?" }
        listOf("General", "Logic Partner", "Brainstorm", "Workbench").forEach {
            assertThat(modes.answer).contains(it)
        }
    }

    @Test
    fun noUserFacingCopyUsesAnEmDash() {
        // A standing owner rule, and easy to reintroduce by pasting from a
        // document that autocorrects.
        assertThat(allPublicCopy).doesNotContain("—")
    }

    @Test
    fun theModeBannersAndNoticesSpellThingsTheSameWayTheRestOfTheAppDoes() {
        // The app writes -ize. The Workbench note said "reorganised" while the
        // Workbench's own chips said "Summarize" and "Reorganize", which is the
        // kind of thing nobody files a bug about and everybody notices. This
        // covers the mode copy specifically, because it is written in a
        // different file from everything else in this test and drifted alone.
        val modeCopy = Mode.entries.joinToString(" ") {
            SystemPrompts.topBanner(it) + " " + SystemPrompts.modeSwitchNotice(it)
        }
        listOf("organis", "summaris", "recognis", "customis").forEach {
            assertThat(modeCopy.lowercase()).doesNotContain(it)
        }
    }

    @Test
    fun noStringInTheAppStillSaysFlag() {
        // Item 9 settled that there is one saving action with one name. The
        // onboarding and Q&A were fixed then and are guarded above, and "flag"
        // quietly survived in seven other places for months: two toasts, the
        // Workbench blurb and its button, two Discover strings, the Follow-ups
        // placeholder, and the overlay's content description, which told a screen
        // reader "Flag this" about a control drawn as a bookmark (#60).
        //
        // This reads the source rather than a copy object because most of those
        // strings are written inline in composables. Clumsy, and the only thing
        // that would have caught them.
        //
        // Single-line literals only. Kotlin's raw strings hold the model
        // instructions, which the test below covers properly by reading the
        // composed prompts.
        val allowed = setOf(""""flagAmber"""", """"flag-scale"""", """"flag-rotation"""")
        val literal = Regex(""""[^"\n]*"""")
        val saysFlag = Regex("""\bflag""", RegexOption.IGNORE_CASE)

        val offenders = mutableListOf<String>()
        repoFile("app/src/main/java/com/kamsiob/kamai").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                literal.findAll(file.readText())
                    .map { it.value }
                    .filter { saysFlag.containsMatchIn(it) && it !in allowed }
                    .forEach { offenders += "${file.name}: ${it.take(60)}" }
            }

        assertThat(offenders).isEmpty()
    }

    @Test
    fun theModelIsNeverToldToTellPeopleToFlagThings() {
        // The worst two instances of #60 were in the instructions themselves, so
        // the assistant was being told to recommend "flagging" in an app where no
        // such action exists. Reads the composed prompts, so it covers the shared
        // hard rules whichever mode carries them.
        Mode.entries.forEach { mode ->
            assertThat(SystemPrompts.forMode(mode).lowercase()).doesNotContain("flag")
        }
        assertThat(SystemPrompts.grounded("a passage").lowercase()).doesNotContain("flag")
    }

    /** The name a mode is introduced by in public copy. BENCH is Workbench. */
    private fun displayNameOf(mode: Mode): String = when (mode) {
        Mode.GENERAL -> "General"
        Mode.LOGIC -> "Logic Partner"
        Mode.BRAINSTORM -> "Brainstorm"
        Mode.BENCH -> "Workbench"
        else -> mode.name
    }

    // The positioning line and the store listing (#36). The listing is read from
    // the file that is actually uploaded, so it cannot drift from the app.

    /**
     * A file at the repository root. Unit tests run with the app module as their
     * working directory, so a root-relative path needs the parent; the fallback
     * keeps this working if that ever changes.
     */
    private fun repoFile(path: String): java.io.File =
        java.io.File("../$path").takeIf { it.exists() } ?: java.io.File(path)

    @Test
    fun thePositioningLineSaysWhatTheModesAreFor() {
        assertThat(QuestionsAndAnswers.POSITIONING).contains("thinks with you, not for you")
    }

    @Test
    fun theStoreListingNamesAllFourModesAndNotTheOldOnes() {
        val listing = repoFile("tools/play/listing.json").readText()
        listOf("General:", "Logic Partner:", "Brainstorm:", "Workbench:").forEach {
            assertThat(listing).contains(it)
        }
        // The old three-mode wording, and the pre-unification saving word.
        assertThat(listing).doesNotContain("Chat about anything")
        assertThat(listing).doesNotContain("Flag any answer")
    }

    @Test
    fun theStoreListingCarriesThePositioning() {
        val listing = repoFile("tools/play/listing.json").readText()
        assertThat(listing).contains("thinks with you, not for you")
    }

    @Test
    fun theReadmeDescribesTheFourModes() {
        val readme = repoFile("README.md").readText()
        listOf("General", "Logic Partner", "Brainstorm", "Workbench").forEach {
            assertThat(readme).contains(it)
        }
        assertThat(readme).contains("thinks with you, not for you")
        // Discover is a source, not a mode, and the README must not blur that.
        assertThat(readme).contains("Discover is not a mode")
    }
}
