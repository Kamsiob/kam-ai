package com.kamsiob.kamai

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kamsiob.kamai.ui.components.KamToast
import com.kamsiob.kamai.ui.theme.KamTheme
import org.junit.Rule
import org.junit.Test

/**
 * Things the app says without being asked (#39).
 *
 * `liveRegion` appeared nowhere in the codebase, so everything the app told you
 * about something that had just happened was visual only. A toast is the app's
 * entire answer to "did that work?", and it passed in silence.
 *
 * This runs on the device because the property lives in the semantics tree,
 * which does not exist in a JVM test. It checks that the property is set and
 * polite, which is what can be checked mechanically. Whether the announcement
 * *sounds* right is a separate question that needs somebody listening to
 * TalkBack, and is called out in HANDOFF as needing the owner.
 */
class AnnouncementsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun isPoliteLiveRegion() = SemanticsMatcher.expectValue(
        SemanticsProperties.LiveRegion,
        LiveRegionMode.Polite,
    )

    @Test
    fun aToastAnnouncesItself() {
        rule.setContent {
            KamTheme { KamToast(message = "Saved to Follow-ups") }
        }

        rule.onNodeWithText("Saved to Follow-ups").assertIsDisplayed()
        // Polite, not assertive: a confirmation should follow whatever is being
        // read rather than cut across it.
        rule.onNode(isPoliteLiveRegion()).assertExists()
    }

    @Test
    fun aToastWithAnUndoAnnouncesItselfToo() {
        // The toast that matters most to hear, since it is offering something
        // that expires.
        rule.setContent {
            KamTheme {
                KamToast(
                    message = "Archived 3 old chats",
                    actionLabel = "Undo",
                    onAction = {},
                )
            }
        }

        rule.onNode(isPoliteLiveRegion()).assertExists()
    }

    @Test
    fun nothingIsAnnouncedWhenThereIsNoToast() {
        // The live region must not be a permanently present empty node, which
        // would announce nothing repeatedly.
        rule.setContent {
            KamTheme { KamToast(message = null) }
        }

        rule.onNode(isPoliteLiveRegion()).assertDoesNotExist()
    }
}
