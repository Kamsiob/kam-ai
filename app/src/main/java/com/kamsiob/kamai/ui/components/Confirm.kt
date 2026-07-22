package com.kamsiob.kamai.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kamsiob.kamai.ui.theme.KamTheme

/**
 * The one confirmation system every destructive action in the app runs through.
 * PART 0.
 *
 * There are two tiers, and they differ only in how hard they are to confirm, not
 * in how they look or where they live:
 *
 * - [Tier.SINGLE] is one clear step, for routine deletions of a single item: one
 *   chat, one memory, one follow-up, one downloaded model. Deleting a chat is
 *   never a two-step gauntlet.
 * - [Tier.MAJOR] is two steps, for irreversible bulk loss: delete-all, batch
 *   delete, the data wipe, the forgot-code wipe. The first step asks; the second
 *   states plainly that it cannot be undone.
 * - [Tier.MAJOR] with [confirmWord] set adds a deliberate gesture to that second
 *   step: the user types a short word. Reserved for the very largest wipes, the
 *   delete-everything and the forgot-code reset, so they can never be a single
 *   easy tap.
 *
 * Destructive confirmations use the app's normal destructive styling, the amber
 * label on a plain surface. The reserved amber is the flag/lock/support colour;
 * here it marks the one dangerous button, which is its legitimate destructive
 * use, and nothing else in the dialog borrows it.
 */
enum class ConfirmTier { SINGLE, MAJOR }

data class ConfirmRequest(
    val tier: ConfirmTier,
    val title: String,
    val body: String,
    /** The plain, final line for a MAJOR action's second step. */
    val undoneNote: String? = null,
    /** When set, the MAJOR second step requires typing this word exactly. */
    val confirmWord: String? = null,
    val confirmLabel: String = "Delete",
    val onConfirm: () -> Unit,
)

@Composable
fun ConfirmDialog(
    request: ConfirmRequest?,
    onDismiss: () -> Unit,
) {
    if (request == null) return
    val colors = KamTheme.colors

    // MAJOR requests advance through a second step; SINGLE confirm immediately.
    var step by remember(request) { mutableStateOf(1) }
    var typed by remember(request) { mutableStateOf("") }

    val onSecondStepReady: () -> Unit = {
        onDismiss()
        request.onConfirm()
    }

    Dialog(onDismissRequest = onDismiss) {
        BackHandler(enabled = true, onBack = onDismiss)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                .padding(22.dp),
        ) {
            val showingFinal = request.tier == ConfirmTier.MAJOR && step == 2

            Text(
                if (showingFinal) "This cannot be undone" else request.title,
                style = KamTheme.type.cardTitle,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (showingFinal) (request.undoneNote ?: request.body) else request.body,
                style = KamTheme.type.body,
                color = colors.textSecondary,
            )

            if (showingFinal && request.confirmWord != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Type ${request.confirmWord} to confirm.",
                    style = KamTheme.type.secondary,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceSecondary)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    if (typed.isEmpty()) {
                        Text(request.confirmWord, style = KamTheme.type.body, color = colors.textTertiary)
                    }
                    BasicTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        singleLine = true,
                        textStyle = KamTheme.type.body.copy(color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.fillMaxWidth().testTag("confirmWordField"),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DialogTextButton("Keep it", colors.textSecondary, onClick = onDismiss)
                Spacer(Modifier.width(6.dp))

                val wordSatisfied = request.confirmWord == null ||
                    typed.trim().equals(request.confirmWord, ignoreCase = true)

                when {
                    request.tier == ConfirmTier.SINGLE -> DialogTextButton(
                        request.confirmLabel, colors.flagAmber,
                        onClick = onSecondStepReady,
                    )
                    step == 1 -> DialogTextButton(
                        "Continue", colors.flagAmber,
                        onClick = { step = 2 },
                    )
                    else -> DialogTextButton(
                        request.confirmLabel,
                        if (wordSatisfied) colors.flagAmber else colors.textTertiary,
                        enabled = wordSatisfied,
                        onClick = onSecondStepReady,
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogTextButton(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .then(
                if (enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text, style = KamTheme.type.label, color = color, textAlign = TextAlign.Center)
    }
}

