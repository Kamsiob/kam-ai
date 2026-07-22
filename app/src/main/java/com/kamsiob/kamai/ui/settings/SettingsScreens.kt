package com.kamsiob.kamai.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.data.ArtifactEntity
import com.kamsiob.kamai.data.MemoryEntity
import com.kamsiob.kamai.model.TierModel
import com.kamsiob.kamai.model.formatBytes
import com.kamsiob.kamai.ui.Links
import com.kamsiob.kamai.ui.components.EmptyState
import com.kamsiob.kamai.ui.components.Eyebrow
import com.kamsiob.kamai.ui.components.KamCard
import com.kamsiob.kamai.ui.components.KamChip
import com.kamsiob.kamai.ui.components.KamMark
import com.kamsiob.kamai.ui.components.PrimaryButton
import com.kamsiob.kamai.ui.components.SecondaryButton
import com.kamsiob.kamai.ui.components.SettingsGroup
import com.kamsiob.kamai.ui.components.SettingsRow
import com.kamsiob.kamai.ui.components.SettingsToggleRow
import com.kamsiob.kamai.ui.components.edgeFade
import com.kamsiob.kamai.ui.onboarding.OnboardingCopy
import com.kamsiob.kamai.ui.theme.KamTheme

private val screenPad @Composable get() = KamTheme.dimens.screenPadding

/**
 * Settings: three grouped sections, then the Support this work button.
 *
 * Rows whose feature does not exist yet are not shown at all. DESIGN.md is
 * explicit that a visible row with honest coming-soon text is not acceptable,
 * so Voice, Backup and restore, and Web search appear as their phases land.
 */
@Composable
fun SettingsScreen(
    activeModel: TierModel?,
    storageBytes: Long,
    voiceInstalled: Boolean,
    backupAvailable: Boolean,
    webSearchAvailable: Boolean,
    onModel: () -> Unit,
    onVoice: () -> Unit,
    onStorage: () -> Unit,
    onWebSearch: () -> Unit,
    onBackup: () -> Unit,
    onDeleteEverything: () -> Unit,
    confirmChatDelete: Boolean,
    onConfirmChatDelete: (Boolean) -> Unit,
    onReplayOnboarding: () -> Unit,
    onAppearance: () -> Unit,
    onSafety: () -> Unit,
    onQuestions: () -> Unit,
    onAbout: () -> Unit,
    onMemory: () -> Unit,
    onSupport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = screenPad),
    ) {
        Text("Settings", style = KamTheme.type.screenTitle, color = colors.textPrimary)
        Spacer(Modifier.height(18.dp))

        SettingsGroup("On this device") {
            SettingsRow(
                title = "Model",
                subtitle = activeModel?.displayName ?: "Nothing downloaded yet",
                trailing = activeModel?.downloadLabel,
                onClick = onModel,
            )
            SettingsRow(
                title = "Memory",
                subtitle = "What Kam AI remembers about you",
                onClick = onMemory,
                showDivider = voiceInstalled || true,
            )
            if (voiceInstalled) {
                SettingsRow(title = "Voice", subtitle = "Read aloud", onClick = onVoice)
            }
            SettingsRow(
                title = "Storage",
                trailing = formatBytes(storageBytes),
                onClick = onStorage,
                showDivider = false,
            )
        }

        Spacer(Modifier.height(22.dp))

        SettingsGroup("Data and connections") {
            if (webSearchAvailable) {
                SettingsRow(title = "Web search", subtitle = "Off", onClick = onWebSearch)
            }
            if (backupAvailable) {
                SettingsRow(title = "Backup and restore", onClick = onBackup)
            }
            SettingsToggleRow(
                title = "Confirm before deleting a chat",
                subtitle = "Off means a single tap deletes it",
                checked = confirmChatDelete,
                onCheckedChange = onConfirmChatDelete,
            )
            SettingsRow(
                title = "Delete everything",
                subtitle = "Chats, memory, projects, follow-ups",
                destructive = true,
                onClick = onDeleteEverything,
                showDivider = false,
            )
        }

        Spacer(Modifier.height(22.dp))

        SettingsGroup("The app") {
            SettingsRow(title = "Appearance", subtitle = "Theme and accent colour", onClick = onAppearance)
            SettingsRow(title = "What Kam AI is for", onClick = onReplayOnboarding)
            SettingsRow(title = "Questions and answers", onClick = onQuestions)
            SettingsRow(title = "Kam AI can be wrong", subtitle = "What to double-check", onClick = onSafety)
            SettingsRow(title = "About", onClick = onAbout, showDivider = false)
        }

        Spacer(Modifier.height(26.dp))

        Text(
            OnboardingCopy.SUPPORT_LINE,
            style = KamTheme.type.secondary,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            OnboardingCopy.SUPPORT_BUTTON,
            onClick = onSupport,
            modifier = Modifier.fillMaxWidth(),
            amber = true,
        )
        Spacer(Modifier.height(28.dp))
    }
}

/** Questions and answers, with live filtering and auto-expanding matches. */
@Composable
fun QuestionsScreen(modifier: Modifier = Modifier) {
    val colors = KamTheme.colors
    var query by remember { mutableStateOf("") }
    val matches = remember(query) { QuestionsAndAnswers.filter(query) }
    val filtering = query.isNotBlank()

    Column(modifier = modifier.fillMaxSize().padding(horizontal = screenPad)) {
        Text(
            "Questions and answers",
            style = KamTheme.type.screenTitle,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(colors.surfaceSecondary)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Search", style = KamTheme.type.body, color = colors.textTertiary)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = KamTheme.type.body.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = "Search questions and answers"
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (matches.isEmpty()) {
            EmptyState(title = "Nothing matches", body = "Try fewer words.")
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().edgeFade(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(matches, key = { it.question }) { entry ->
                // Matching entries auto-expand while filtering.
                QuestionCard(entry, startExpanded = filtering)
            }
            item(key = "closing") {
                Spacer(Modifier.height(10.dp))
                Text(
                    QuestionsAndAnswers.CLOSING,
                    style = KamTheme.type.secondary,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun QuestionCard(entry: QuestionsAndAnswers.Entry, startExpanded: Boolean) {
    val colors = KamTheme.colors
    var expanded by remember(entry.question, startExpanded) { mutableStateOf(startExpanded) }

    KamCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
    ) {
        Column(Modifier.padding(15.dp)) {
            Text(entry.question, style = KamTheme.type.cardTitle, color = colors.textPrimary)
            AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text(entry.answer, style = KamTheme.type.body, color = colors.textSecondary)
                }
            }
        }
    }
}

/** About: the mark, the version line, plain link rows all at equal weight. */
@Composable
fun AboutScreen(
    versionName: String,
    onLink: (String) -> Unit,
    onEmail: (String) -> Unit,
    onLicenses: () -> Unit,
    onRoadmap: () -> Unit,
    onSupport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = screenPad),
    ) {
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                KamMark(size = 62.dp, breathing = true)
                Spacer(Modifier.height(12.dp))
                Text("Kam AI", style = KamTheme.type.sectionTitle, color = colors.textPrimary)
                Spacer(Modifier.height(5.dp))
                Text(
                    "$versionName  AGPL-3.0  by Kamsiob",
                    style = KamTheme.type.mono,
                    color = colors.textTertiary,
                )
            }
        }
        Spacer(Modifier.height(26.dp))

        SettingsGroup(null) {
            SettingsRow(title = "YouTube", trailing = "@kamsiob") { onLink(Links.YOUTUBE) }
            SettingsRow(title = "GitHub", trailing = "github.com/kamsiob") { onLink(Links.GITHUB) }
            SettingsRow(title = "Website", trailing = "kamsiob.com") { onLink(Links.WEBSITE) }
            SettingsRow(title = "Telegram", trailing = "Kamsiob Lab") { onLink(Links.TELEGRAM) }
            SettingsRow(title = "Feedback", trailing = "hello@kamsiob.com") {
                onEmail(Links.FEEDBACK_EMAIL)
            }
            SettingsRow(
                title = "Privacy policy",
                subtitle = "The whole thing is short",
            ) { onLink(Links.PRIVACY) }
            SettingsRow(title = "Being considered, and not planned", onClick = onRoadmap)
            SettingsRow(title = "Licenses", onClick = onLicenses, showDivider = false)
        }

        Spacer(Modifier.height(26.dp))
        Text(
            OnboardingCopy.SUPPORT_LINE,
            style = KamTheme.type.secondary,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            OnboardingCopy.SUPPORT_BUTTON,
            onClick = onSupport,
            modifier = Modifier.fillMaxWidth(),
            amber = true,
        )
        Spacer(Modifier.height(28.dp))
    }
}

/** Storage: every downloaded artifact, with its size and a delete action. */
@Composable
fun StorageScreen(
    artifacts: List<ArtifactEntity>,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    val total = artifacts.sumOf { it.sizeBytes }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = screenPad)) {
        Text("Storage", style = KamTheme.type.screenTitle, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "${formatBytes(total)} used by things you have downloaded.",
            style = KamTheme.type.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(16.dp))

        if (artifacts.isEmpty()) {
            EmptyState(
                title = "Nothing downloaded",
                body = "Models, voices and content packs show up here once you get them.",
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().edgeFade(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(artifacts, key = { it.id }) { artifact ->
                KamCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                artifact.displayName,
                                style = KamTheme.type.cardTitle,
                                color = colors.textPrimary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    formatBytes(artifact.sizeBytes),
                                    style = KamTheme.type.mono,
                                    color = colors.textTertiary,
                                )
                                if (artifact.active) KamChip("In use", tonal = true)
                            }
                        }
                        Text(
                            "Delete",
                            style = KamTheme.type.label,
                            color = colors.flagAmber,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onDelete(artifact.id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Memory: exactly what is remembered, with a delete on every entry.
 *
 * Transparency is the whole point of this screen. Nothing is summarised or
 * hidden behind a count.
 */
@Composable
fun MemoryScreen(
    entries: List<MemoryEntity>,
    onForget: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors

    Column(modifier = modifier.fillMaxSize().padding(horizontal = screenPad)) {
        Text("Memory", style = KamTheme.type.screenTitle, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Everything Kam AI has kept about you is listed here, in full. Delete " +
                "anything you would rather it forgot.",
            style = KamTheme.type.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(16.dp))

        if (entries.isEmpty()) {
            EmptyState(
                title = "Nothing remembered",
                body = "As you talk, durable facts you mention may end up here. You can " +
                    "always see and delete them.",
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().edgeFade(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(entries, key = { it.id }) { entry ->
                KamCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top) {
                        Text(
                            entry.text,
                            style = KamTheme.type.body,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Forget",
                            style = KamTheme.type.label,
                            color = colors.flagAmber,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onForget(entry.id) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Being considered, and Not planned. No dates, no promises. */
@Composable
fun RoadmapScreen(modifier: Modifier = Modifier) {
    val colors = KamTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = screenPad),
    ) {
        Text("Being considered", style = KamTheme.type.screenTitle, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Things that might happen. Each one names the real reason it has not yet.",
            style = KamTheme.type.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(14.dp))

        Roadmap.beingConsidered.forEach { item ->
            KamCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.padding(15.dp)) {
                    Text(item.title, style = KamTheme.type.cardTitle, color = colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.constraint,
                        style = KamTheme.type.secondary,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Text("Not planned", style = KamTheme.type.sectionTitle, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Decisions, not gaps.",
            style = KamTheme.type.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(14.dp))

        Roadmap.notPlanned.forEach { item ->
            KamCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.padding(15.dp)) {
                    Text(item.title, style = KamTheme.type.cardTitle, color = colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.constraint,
                        style = KamTheme.type.secondary,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(Roadmap.EXPECTATION, style = KamTheme.type.secondary, color = colors.textTertiary)
        Spacer(Modifier.height(28.dp))
    }
}

/** Licenses: every open source component, plus the Wikipedia CC BY-SA entry. */
@Composable
fun LicensesScreen(
    models: List<TierModel>,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors

    val components = buildList {
        add("Kam AI" to "AGPL-3.0")
        add("llama.cpp" to "MIT")
        add("Jetpack Compose and AndroidX" to "Apache-2.0")
        add("Kotlin and kotlinx.coroutines" to "Apache-2.0")
        add("OkHttp" to "Apache-2.0")
        add("Room" to "Apache-2.0")
        add("Sora" to "SIL Open Font License 1.1")
        add("Manrope" to "SIL Open Font License 1.1")
        add("JetBrains Mono" to "SIL Open Font License 1.1")
        models.forEach { add(it.displayName to it.licence) }
        add("Discover content, from Wikipedia" to "CC BY-SA 4.0")
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = screenPad)) {
        Text("Licenses", style = KamTheme.type.screenTitle, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Kam AI is built on other people's work. This is all of it.",
            style = KamTheme.type.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize().edgeFade(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                KamCard(Modifier.fillMaxWidth()) {
                    Column {
                        components.forEachIndexed { index, (name, licence) ->
                            SettingsRow(
                                title = name,
                                trailing = licence,
                                showDivider = index != components.lastIndex,
                            )
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Content packs are built from Wikipedia and stay under CC BY-SA 4.0. " +
                        "That licence covers the pack content, not the app.",
                    style = KamTheme.type.secondary,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

/**
 * PART 9. A plain, non-alarmist notice that the AI can be wrong. Honest voice:
 * no hype, no fear, just what to do about it.
 */
@Composable
fun SafetyScreen(modifier: Modifier = Modifier) {
    val colors = KamTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = screenPad),
    ) {
        Text("Kam AI can be wrong", style = KamTheme.type.screenTitle, color = colors.textPrimary)
        Spacer(Modifier.height(16.dp))

        val paragraphs = listOf(
            "The model runs on your phone, which means it is small. It knows less " +
                "than the big cloud AIs and it gets things wrong, especially dates, " +
                "names, numbers, and anything it would need to have memorised exactly.",
            "Treat its answers as a starting point, not a final word. When something " +
                "matters, check it against a source you trust. That is what the " +
                "bookmark and the Follow-ups tab are for: mark it, move on, and come " +
                "back to verify it properly.",
            "Kam AI is not a substitute for a qualified professional. Do not rely on " +
                "it for legal, medical, financial, or other expert advice. For those, " +
                "talk to someone who does it for a living.",
        )
        paragraphs.forEach { p ->
            Text(p, style = KamTheme.type.bodyLarge, color = colors.textSecondary)
            Spacer(Modifier.height(14.dp))
        }
        Spacer(Modifier.height(14.dp))
    }
}
