package com.kamsiob.kamai.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.discover.Moment
import com.kamsiob.kamai.discover.PackInfo
import com.kamsiob.kamai.download.Downloader
import com.kamsiob.kamai.ui.components.PrimaryButton
import com.kamsiob.kamai.ui.components.SecondaryButton
import com.kamsiob.kamai.ui.theme.KamTheme

/** The reader: full passage, attribution with source link, and the two ways to
 *  keep going, a grounded discussion or an open exploration. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSheet(
    moment: Moment,
    onDismiss: () -> Unit,
    onDiscuss: () -> Unit,
    onExplore: () -> Unit,
    onOpenSource: (String) -> Unit,
) {
    val colors = KamTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.background) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(moment.topic.uppercase(), style = KamTheme.type.mono, color = colors.textTertiary)
            Spacer(Modifier.height(6.dp))
            Text(moment.title, style = KamTheme.type.sectionTitle, color = colors.textPrimary)
            Spacer(Modifier.height(14.dp))
            Text(moment.passage, style = KamTheme.type.body, color = colors.textPrimary)
            Spacer(Modifier.height(16.dp))
            Text(
                "From Wikipedia, ${moment.license}",
                style = KamTheme.type.secondary,
                color = colors.textTertiary,
            )
            Text(
                "View the source article",
                style = KamTheme.type.label,
                color = colors.accent,
                modifier = Modifier
                    .clickable { onOpenSource(moment.sourceUrl) }
                    .padding(vertical = 6.dp),
            )
            Spacer(Modifier.height(16.dp))
            // The owner's two-button feature: grounded vs open.
            PrimaryButton("Discuss this passage", onClick = onDiscuss, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Explore this topic", onClick = onExplore, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(
                "Discuss stays with what this passage says. Explore opens a normal chat about " +
                    "the topic, where a small model can misremember, so flag anything to check.",
                style = KamTheme.type.secondary,
                color = colors.textTertiary,
            )
        }
    }
}

/** The quiz: questions from the full passage, one at a time, honest self-marked
 *  feedback that shows what the passage said, and a one-tap flag on a miss. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizSheet(
    state: DiscoverViewModel.QuizState,
    onReveal: () -> Unit,
    onMark: (Boolean) -> Unit,
    onFlag: (DiscoverViewModel.QuizQuestion) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = KamTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.background) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 260.dp, max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            when (state) {
                is DiscoverViewModel.QuizState.Generating -> {
                    Text("Making a few questions...", style = KamTheme.type.body, color = colors.textSecondary)
                }
                is DiscoverViewModel.QuizState.Asking -> {
                    val q = state.questions[state.index]
                    Text(
                        "Question ${state.index + 1} of ${state.questions.size}",
                        style = KamTheme.type.mono, color = colors.textTertiary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(q.question, style = KamTheme.type.sectionTitle, color = colors.textPrimary)
                    Spacer(Modifier.height(16.dp))
                    if (!state.revealed) {
                        Text(
                            "Think of your answer, then reveal it.",
                            style = KamTheme.type.secondary, color = colors.textTertiary,
                        )
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton("Reveal answer", onClick = onReveal, modifier = Modifier.fillMaxWidth())
                    } else {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(colors.tonalFill).padding(14.dp),
                        ) {
                            Text(q.answer, style = KamTheme.type.body, color = colors.textPrimary)
                            if (q.quote.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "The passage said: \"${q.quote}\"",
                                    style = KamTheme.type.secondary, color = colors.textTertiary,
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("Did you get it right?", style = KamTheme.type.body, color = colors.textSecondary)
                        Spacer(Modifier.height(8.dp))
                        Row {
                            PrimaryButton("Yes", onClick = { onMark(true) }, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            SecondaryButton("Missed it", onClick = { onMark(false) }, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Flag this for later",
                            style = KamTheme.type.label, color = colors.accent,
                            modifier = Modifier.clickable { onFlag(q) }.padding(vertical = 6.dp),
                        )
                    }
                }
                is DiscoverViewModel.QuizState.Done -> {
                    Text("${state.right} of ${state.total}", style = KamTheme.type.screenTitle, color = colors.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text("That's the quiz.", style = KamTheme.type.body, color = colors.textSecondary)
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
                }
                else -> Unit
            }
        }
    }
}

/** The packs sheet: available packs with Get or Remove, sizes, and the plain note
 *  that packs are one-time offline downloads. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacksSheet(
    manifest: List<PackInfo>,
    installedIds: Set<String>,
    download: Downloader.Progress?,
    downloadingPackId: String?,
    onGet: (PackInfo) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = KamTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.background) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("Packs", style = KamTheme.type.sectionTitle, color = colors.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "Offline snapshots of short reads, built from Wikipedia and downloaded once " +
                    "from GitHub. Nothing about you is sent. Remove them any time.",
                style = KamTheme.type.secondary,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(14.dp))

            if (manifest.isEmpty()) {
                Text(
                    "Could not reach the pack list. Check your connection and try again; " +
                        "packs you have already downloaded still work offline.",
                    style = KamTheme.type.body,
                    color = colors.textTertiary,
                )
            }

            manifest.forEach { pack ->
                val installed = pack.id in installedIds
                val progress = (download as? Downloader.Progress.Running)
                    ?.takeIf { downloadingPackId == pack.id }?.fraction
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                ) {
                    Row {
                        Text(pack.name, style = KamTheme.type.cardTitle, color = colors.textPrimary)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${pack.moments} · ${pack.sizeLabel}",
                            style = KamTheme.type.mono, color = colors.textSecondary,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(pack.description, style = KamTheme.type.secondary, color = colors.textTertiary)
                    Spacer(Modifier.height(10.dp))
                    when {
                        progress != null -> {
                            Box(
                                Modifier.fillMaxWidth().height(7.dp).clip(CircleShape)
                                    .background(colors.surfaceSecondary),
                            ) {
                                Box(
                                    Modifier.fillMaxWidth(progress).height(7.dp).clip(CircleShape)
                                        .background(colors.accent),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("${(progress * 100).toInt()}% downloaded",
                                style = KamTheme.type.mono, color = colors.textSecondary)
                        }
                        installed -> SecondaryButton("Remove", onClick = { onRemove(pack.id) })
                        else -> PrimaryButton("Get ${pack.sizeLabel}", onClick = { onGet(pack) })
                    }
                }
            }
        }
    }
}
