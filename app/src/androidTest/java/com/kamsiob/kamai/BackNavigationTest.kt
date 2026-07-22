package com.kamsiob.kamai

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Back navigation. The bug this guards against was real and total: the system
 * back gesture closed the app from every screen instead of stepping back one
 * level, because nothing was consuming the back event.
 *
 * These exercise the same handler arrangement the app uses, which is that
 * handlers are registered by whichever surface owns the dismissible state, and
 * the dispatcher resolves innermost first.
 */
class BackNavigationTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun pressBack() {
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    @Test
    fun backPopsOneLevelRatherThanClearingTheStack() {
        val stack = mutableStateListOf<String>()

        rule.setContent {
            BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }
            Column {
                Text(stack.lastOrNull() ?: "Chats")
            }
        }

        rule.runOnIdle {
            stack.add("Settings")
            stack.add("About")
            stack.add("Licenses")
        }
        rule.onNodeWithText("Licenses").assertIsDisplayed()

        pressBack()
        rule.onNodeWithText("About").assertIsDisplayed()

        pressBack()
        rule.onNodeWithText("Settings").assertIsDisplayed()

        pressBack()
        rule.onNodeWithText("Chats").assertIsDisplayed()
        assertThat(stack).isEmpty()
    }

    @Test
    fun anOpenDialogTakesTheBackEventBeforeTheStackDoes() {
        val stack = mutableStateListOf("Settings")
        var dialogOpen by mutableStateOf(true)

        rule.setContent {
            // Declared inside, so it is the innermost handler and wins.
            BackHandler(enabled = dialogOpen) { dialogOpen = false }
            BackHandler(enabled = !dialogOpen && stack.isNotEmpty()) {
                stack.removeAt(stack.lastIndex)
            }
            Text(if (dialogOpen) "Dialog" else stack.lastOrNull() ?: "Chats")
        }

        pressBack()
        // The dialog closed, and the navigation stack was left alone.
        assertThat(dialogOpen).isFalse()
        assertThat(stack).containsExactly("Settings")

        pressBack()
        assertThat(stack).isEmpty()
    }

    @Test
    fun anOpenSwipeRowClosesBeforeAnyNavigationHappens() {
        val stack = mutableStateListOf("Chats")
        var rowOpen by mutableStateOf(true)

        rule.setContent {
            BackHandler(enabled = rowOpen) { rowOpen = false }
            BackHandler(enabled = !rowOpen && stack.isNotEmpty()) {
                stack.removeAt(stack.lastIndex)
            }
            Text("row=$rowOpen depth=${stack.size}")
        }

        pressBack()
        assertThat(rowOpen).isFalse()
        assertThat(stack).hasSize(1)
    }

    @Test
    fun aNonHomeTabReturnsToTheHomeTabBeforeExiting() {
        // The documented decision: with an empty stack on a tab other than
        // Chats, back returns to Chats rather than leaving. Somebody who
        // wandered into Follow-ups and pressed back almost never means
        // "close the app".
        val stack = mutableStateListOf<String>()
        var tab by mutableStateOf("FollowUps")
        var wouldExit = false

        rule.setContent {
            BackHandler(enabled = stack.isNotEmpty() || tab != "Chats") {
                when {
                    stack.isNotEmpty() -> stack.removeAt(stack.lastIndex)
                    tab != "Chats" -> tab = "Chats"
                }
            }
            BackHandler(enabled = stack.isEmpty() && tab == "Chats") { wouldExit = true }
            Text("$tab:${stack.size}")
        }

        pressBack()
        assertThat(tab).isEqualTo("Chats")
        assertThat(wouldExit).isFalse()

        // Only now, at the home root, does back fall through to leaving.
        pressBack()
        assertThat(wouldExit).isTrue()
    }

    @Test
    fun theHomeRootDoesNotConsumeBackAtAll() {
        val stack = mutableStateListOf<String>()
        var consumed = false

        rule.setContent {
            BackHandler(enabled = stack.isNotEmpty()) {
                consumed = true
                stack.removeAt(stack.lastIndex)
            }
            Text("home")
        }

        pressBack()
        // Nothing consumed it, which is what lets the activity finish.
        assertThat(consumed).isFalse()
    }
}
