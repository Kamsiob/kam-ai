package com.kamsiob.kamai

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kamsiob.kamai.ui.components.SettingsToggleRow
import com.kamsiob.kamai.ui.theme.KamTheme
import org.junit.Rule
import org.junit.Test

/**
 * A settings toggle has to announce its own state (#144).
 *
 * The row is built from a clickable container with a Material `Switch` inside it,
 * and that is two plausible ways for a screen reader to be told something useless:
 * the row reads its name with no state, and the switch reads a state with no name.
 * Somebody hearing "Note under each answer" followed by "off, switch" has to join
 * those up; somebody hearing only the first has no idea which way it is set.
 *
 * This matters more on this row than on most, because the row above it in the same
 * group is the control that genuinely stops memory being used. A user who cannot
 * hear which setting they just changed, on that screen, could believe they had
 * turned memory off when they had turned off a footnote.
 *
 * On the device because the semantics tree does not exist in a JVM test, same
 * reason as `AnnouncementsTest`. What is checked here is mechanical: that the state
 * is exposed and attached to the name. Whether it *sounds* right still needs
 * somebody listening to TalkBack, and that is called out in HANDOFF.
 */
class MemoryNoteToggleSemanticsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val title = "Note under each answer"
    private val description =
        "Off means no line saying what was included. What Kam AI remembers does not change."

    private fun hasToggleState(state: ToggleableState) = SemanticsMatcher.expectValue(
        SemanticsProperties.ToggleableState,
        state,
    )

    private fun content(checked: Boolean) {
        rule.setContent {
            KamTheme {
                Column {
                    SettingsToggleRow(
                        title = title,
                        subtitle = description,
                        checked = checked,
                        onCheckedChange = {},
                        showDivider = false,
                    )
                }
            }
        }
    }

    @Test
    fun theRowIsRead() {
        content(checked = false)
        rule.onNodeWithText(title).assertIsDisplayed()
        // The description says what off means, which is the settings rule this
        // screen follows. It has to reach a screen reader too, not only the eye.
        rule.onNodeWithText(description, substring = true).assertIsDisplayed()
    }

    @Test
    fun theRowCarriesItsOwnStateWhenOff() {
        content(checked = false)
        // The node found by the row's *name* must be the node carrying the state.
        // If this fails, the name and the state are on separate nodes and the
        // announcement is split, which is the defect described above.
        rule.onNodeWithText(title, useUnmergedTree = false)
            .assert(hasToggleState(ToggleableState.Off))
    }

    @Test
    fun theRowCarriesItsOwnStateWhenOn() {
        content(checked = true)
        rule.onNodeWithText(title, useUnmergedTree = false)
            .assert(hasToggleState(ToggleableState.On))
    }
}
