package com.kamsiob.kamai.ui.workbench

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.ui.components.PrimaryButton
import com.kamsiob.kamai.ui.components.SecondaryButton
import com.kamsiob.kamai.ui.theme.KamTheme

/**
 * The Workbench: a paste-and-transform surface, distinct from a chat. Source at
 * the top, transformation buttons and a free instruction in the middle, result
 * below with copy, flag, and the option to run another transformation on it.
 */
@Composable
fun WorkbenchScreen(
    input: String,
    output: String,
    running: Boolean,
    notice: String?,
    voiceAvailable: Boolean,
    recording: Boolean,
    transcribing: Boolean,
    onInputChange: (String) -> Unit,
    onAction: (WorkbenchViewModel.Action) -> Unit,
    onCustom: (String) -> Unit,
    onChain: (WorkbenchViewModel.Action) -> Unit,
    onStop: () -> Unit,
    onCopied: () -> Unit,
    onFlag: (String) -> Unit,
    onMicStart: () -> Unit,
    onMicStop: () -> Unit,
    onDismissNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var custom by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = KamTheme.dimens.screenPadding),
    ) {
        Text("Workbench", style = KamTheme.type.screenTitle, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Paste something in and reshape it. The result stays here to copy, flag, or " +
                "run through another change.",
            style = KamTheme.type.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(16.dp))

        // Source
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(18.dp))
                .padding(14.dp),
        ) {
            if (input.isEmpty()) {
                Text(
                    if (recording) "Listening. Tap the mic when you are done."
                    else if (transcribing) "Turning your voice into text..."
                    else "Paste or type the text to work on",
                    style = KamTheme.type.body,
                    color = colors.textTertiary,
                )
            }
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                enabled = !recording && !transcribing,
                textStyle = KamTheme.type.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            )
        }

        if (voiceAvailable) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (recording) colors.flagAmber else colors.surfaceSecondary)
                        .clickable(enabled = !transcribing) { if (recording) onMicStop() else onMicStart() }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                        .semantics { contentDescription = if (recording) "Stop recording" else "Voice input" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (transcribing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.accent)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                contentDescription = null,
                                tint = if (recording) colors.onAccent else colors.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (recording) "Stop" else "Speak",
                                style = KamTheme.type.label,
                                color = if (recording) colors.onAccent else colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Transform actions
        val actions = WorkbenchViewModel.Action.entries
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            actions.forEach { action ->
                ActionChip(label = action.label, enabled = !running) { onAction(action) }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Free instruction
        Row(verticalAlignment = Alignment.Bottom) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            ) {
                if (custom.isEmpty()) {
                    Text("Or say what to do with it", style = KamTheme.type.body, color = colors.textTertiary)
                }
                BasicTextField(
                    value = custom,
                    onValueChange = { custom = it },
                    enabled = !running,
                    textStyle = KamTheme.type.body.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            if (running) {
                RoundIcon(icon = Icons.Rounded.Stop, desc = "Stop", onClick = onStop)
            } else {
                RoundIcon(icon = Icons.AutoMirrored.Rounded.Send, desc = "Run") {
                    onCustom(custom); custom = ""
                }
            }
        }

        // Result
        if (running || output.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            com.kamsiob.kamai.ui.components.Eyebrow("Result")
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.tonalFill)
                    .padding(14.dp),
            ) {
                Text(
                    output.ifEmpty { "Working..." },
                    style = KamTheme.type.body,
                    color = colors.textPrimary,
                )
            }
            if (output.isNotEmpty() && !running) {
                Spacer(Modifier.height(10.dp))
                Row {
                    SecondaryButton(
                        "Copy",
                        onClick = {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(output))
                            onCopied()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    SecondaryButton("Flag", onClick = { onFlag(output) }, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Run another change on this result:",
                    style = KamTheme.type.secondary,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WorkbenchViewModel.Action.entries.forEach { action ->
                        ActionChip(label = action.label, enabled = true) { onChain(action) }
                    }
                }
            }
        }

        notice?.let {
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceSecondary)
                    .clickable(onClick = onDismissNotice)
                    .padding(14.dp),
            ) {
                Text(it, style = KamTheme.type.secondary, color = colors.textSecondary)
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ActionChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = KamTheme.colors
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.surfaceSecondary)
            .border(1.dp, colors.border, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            style = KamTheme.type.label,
            color = if (enabled) colors.textPrimary else colors.textTertiary,
        )
    }
}

@Composable
private fun RoundIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
) {
    val colors = KamTheme.colors
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(colors.accent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(21.dp))
    }
}
