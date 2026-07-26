package com.kamsiob.kamai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kamsiob.kamai.ui.components.MarkdownText
import com.kamsiob.kamai.ui.components.PrimaryButton
import com.kamsiob.kamai.ui.components.TextActionButton
import com.kamsiob.kamai.ui.theme.KamTheme

/**
 * A summary of the open conversation, shown as the app's own output (#86).
 *
 * Deliberately not a message. A summary posted into the transcript would become
 * part of the conversation it describes, get summarized by the next summary, and
 * read a week later as something one of the two of you said. This is a sheet: it
 * can be read, copied, shared, kept or dismissed, and if it is kept it is
 * labelled as generated rather than filed as a quote.
 *
 * Every state it can be in says what is happening, and the working state carries
 * its own cancel, because a summary of a long conversation is several passes and
 * nothing slow in this app is allowed to be uninterruptible.
 */
@Composable
fun SummarySheet(
    state: ChatViewModel.SummaryState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    if (state is ChatViewModel.SummaryState.Idle) return
    val colors = KamTheme.colors

    Dialog(
        onDismissRequest = {
            // Dismissing mid-run has to stop the work, not orphan it.
            if (state is ChatViewModel.SummaryState.Working) onCancel() else onDismiss()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surface)
                .padding(22.dp),
        ) {
            Text("Summary", style = KamTheme.type.cardTitle, color = colors.textPrimary)

            when (state) {
                is ChatViewModel.SummaryState.Working -> {
                    Spacer(Modifier.height(14.dp))
                    Text(state.step, style = KamTheme.type.body, color = colors.textSecondary)
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextActionButton("Stop", onClick = onCancel)
                    }
                }

                is ChatViewModel.SummaryState.NotWorthIt -> {
                    Spacer(Modifier.height(10.dp))
                    Text(state.message, style = KamTheme.type.body, color = colors.textSecondary)
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextActionButton("Close", onClick = onDismiss)
                    }
                }

                is ChatViewModel.SummaryState.Failed -> {
                    Spacer(Modifier.height(10.dp))
                    Text(state.message, style = KamTheme.type.body, color = colors.textSecondary)
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextActionButton("Close", onClick = onDismiss)
                    }
                }

                is ChatViewModel.SummaryState.Ready -> {
                    Spacer(Modifier.height(6.dp))
                    // Said before the summary, not after it, so nobody reads the
                    // whole thing as a transcript and finds out at the bottom.
                    Text(
                        state.provenance,
                        style = KamTheme.type.secondary,
                        color = colors.textTertiary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        MarkdownText(text = state.text, color = colors.textPrimary)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextActionButton("Copy", onClick = { onCopy(state.text) })
                        TextActionButton("Share", onClick = { onShare(state.text) })
                        Spacer(Modifier.weight(1f))
                        TextActionButton("Close", onClick = onDismiss)
                    }
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton(
                        "Keep it in Follow-ups",
                        onClick = onSave,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                ChatViewModel.SummaryState.Idle -> Unit
            }
        }
    }
}
