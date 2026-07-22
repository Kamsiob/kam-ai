package com.kamsiob.kamai

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithTag
import com.google.common.truth.Truth.assertThat
import com.kamsiob.kamai.ui.components.ConfirmDialog
import com.kamsiob.kamai.ui.components.ConfirmRequest
import com.kamsiob.kamai.ui.components.ConfirmTier
import com.kamsiob.kamai.ui.theme.KamTheme
import org.junit.Rule
import org.junit.Test

/**
 * The shared confirmation system. Tier one confirms in one tap; tier two needs a
 * second step; the largest wipes need a typed word before the button is even
 * enabled.
 */
class ConfirmDialogTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tierOneConfirmsInASingleTap() {
        var confirmed = false
        rule.setContent {
            KamTheme {
                ConfirmDialog(
                    request = ConfirmRequest(
                        tier = ConfirmTier.SINGLE,
                        title = "Delete this chat?",
                        body = "It will be removed.",
                        confirmLabel = "Delete",
                        onConfirm = { confirmed = true },
                    ),
                    onDismiss = {},
                )
            }
        }
        // No "Continue" step, just the delete action.
        rule.onNodeWithText("Delete").performClick()
        assertThat(confirmed).isTrue()
    }

    @Test
    fun tierTwoRequiresTwoSteps() {
        var confirmed = false
        rule.setContent {
            KamTheme {
                ConfirmDialog(
                    request = ConfirmRequest(
                        tier = ConfirmTier.MAJOR,
                        title = "Delete 12 chats?",
                        body = "This removes 12 conversations.",
                        undoneNote = "Gone for good.",
                        confirmLabel = "Delete 12",
                        onConfirm = { confirmed = true },
                    ),
                    onDismiss = {},
                )
            }
        }
        // First step shows Continue, not the final action.
        rule.onNodeWithText("Continue").performClick()
        // Second step states it cannot be undone, then confirms.
        rule.onNodeWithText("This cannot be undone").assertIsDisplayed()
        assertThat(confirmed).isFalse()
        rule.onNodeWithText("Delete 12").performClick()
        assertThat(confirmed).isTrue()
    }

    @Test
    fun theLargestWipeNeedsTheWordTyped() {
        var confirmed = false
        rule.setContent {
            KamTheme {
                ConfirmDialog(
                    request = ConfirmRequest(
                        tier = ConfirmTier.MAJOR,
                        title = "Delete everything?",
                        body = "All of it.",
                        undoneNote = "Cannot be undone.",
                        confirmWord = "delete",
                        confirmLabel = "Delete everything",
                        onConfirm = { confirmed = true },
                    ),
                    onDismiss = {},
                )
            }
        }
        rule.onNodeWithText("Continue").performClick()
        // Tapping confirm before typing the word does nothing.
        rule.onNodeWithText("Delete everything").performClick()
        assertThat(confirmed).isFalse()
        // Type the word, and now it confirms.
        rule.onNodeWithTag("confirmWordField").performTextInput("delete")
        rule.onNodeWithText("Delete everything").performClick()
        assertThat(confirmed).isTrue()
    }
}
