package com.kamsiob.kamai.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.selected
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.combinedClickable
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
    appLockEnabled: Boolean,
    onAppLock: () -> Unit,
    isDefaultAssistant: Boolean = false,
    onAssistant: () -> Unit = {},
    onReplayOnboarding: () -> Unit,
    onAppearance: () -> Unit,
    onSafety: () -> Unit,
    onQuestions: () -> Unit,
    onAbout: () -> Unit,
    onMemory: () -> Unit,
    onCustomInstructions: () -> Unit = {},
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
            )
            SettingsRow(
                title = "Custom instructions",
                subtitle = "Standing instructions for every chat",
                onClick = onCustomInstructions,
            )
            SettingsRow(
                title = "Voice",
                subtitle = if (voiceInstalled) "Voice typing is set up" else "Talk instead of typing",
                onClick = onVoice,
            )
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
            SettingsRow(
                title = "App lock",
                subtitle = if (appLockEnabled) "On" else "Off",
                onClick = onAppLock,
            )
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
            SettingsRow(
                title = "Open with the power button",
                subtitle = if (isDefaultAssistant) "Set up. Long-press power to ask." else "Make Kam AI your assistant app",
                onClick = onAssistant,
            )
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
    hasCrashReport: Boolean = false,
    onCrashReport: () -> Unit = {},
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
            SettingsRow(
                title = "Licenses",
                onClick = onLicenses,
                showDivider = hasCrashReport,
            )
            if (hasCrashReport) {
                SettingsRow(
                    title = "Crash report",
                    subtitle = "The app stopped unexpectedly. You can read it or share it.",
                    onClick = onCrashReport,
                    showDivider = false,
                )
            }
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

/**
 * Storage: every downloaded artifact, with its size and a delete action.
 *
 * A single download deletes from its own card. To remove several — or all — at
 * once, long-press any card to enter selection mode, tick what you want gone,
 * and delete the lot in one confirmation.
 */
@Composable
fun StorageScreen(
    artifacts: List<ArtifactEntity>,
    onDelete: (String) -> Unit,
    onDeleteMany: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    val total = artifacts.sumOf { it.sizeBytes }
    val selectedIds = remember { mutableStateListOf<String>() }
    var selecting by remember { mutableStateOf(false) }
    fun exit() { selecting = false; selectedIds.clear() }
    androidx.activity.compose.BackHandler(enabled = selecting) { exit() }

    // A card that vanishes (deleted from under us) should never linger as a
    // ghost selection.
    val liveIds = artifacts.map { it.id }.toSet()
    selectedIds.retainAll(liveIds)

    Column(modifier = modifier.fillMaxSize().padding(horizontal = screenPad)) {
        if (selecting) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${selectedIds.size} selected", style = KamTheme.type.sectionTitle, color = colors.textPrimary)
                Spacer(Modifier.weight(1f))
                Text(
                    if (selectedIds.size == artifacts.size) "Select none" else "Select all",
                    style = KamTheme.type.label, color = colors.accent,
                    modifier = Modifier.clip(CircleShape).clickable {
                        if (selectedIds.size == artifacts.size) selectedIds.clear()
                        else { selectedIds.clear(); selectedIds.addAll(artifacts.map { it.id }) }
                    }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Text(
                    "Delete", style = KamTheme.type.label,
                    color = if (selectedIds.isNotEmpty()) colors.flagAmber else colors.textTertiary,
                    modifier = Modifier.clip(CircleShape)
                        .then(if (selectedIds.isNotEmpty()) Modifier.clickable { onDeleteMany(selectedIds.toList()); exit() } else Modifier)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Text("Cancel", style = KamTheme.type.label, color = colors.textSecondary,
                    modifier = Modifier.clip(CircleShape).clickable { exit() }.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        } else {
            Text("Storage", style = KamTheme.type.screenTitle, color = colors.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "${formatBytes(total)} used by things you have downloaded." +
                    if (artifacts.size > 1) " Long-press to select several." else "",
                style = KamTheme.type.body,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(16.dp))
        }

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
                val isSel = selectedIds.contains(artifact.id)
                KamCard(
                    Modifier.fillMaxWidth().then(
                        if (selecting) Modifier.semantics {
                            selected = isSel
                            contentDescription = "${artifact.displayName}, ${formatBytes(artifact.sizeBytes)}"
                        } else Modifier,
                    ),
                    onClick = if (selecting) {
                        { if (isSel) selectedIds.remove(artifact.id) else selectedIds.add(artifact.id) }
                    } else {
                        null
                    },
                ) {
                    Row(
                        Modifier
                            .padding(15.dp)
                            .then(
                                if (!selecting) {
                                    Modifier.combinedClickable(
                                        onClick = {},
                                        onLongClick = { selecting = true; selectedIds.add(artifact.id) },
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selecting) {
                            Box(
                                Modifier.size(22.dp).clip(CircleShape)
                                    .background(if (isSel) colors.accent else androidx.compose.ui.graphics.Color.Transparent)
                                    .border(if (isSel) 0.dp else 2.dp, colors.border, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSel) Icon(Icons.Rounded.Check, null, tint = colors.onAccent, modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                        }
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
                        if (!selecting) {
                            Spacer(Modifier.width(8.dp))
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
    mode: com.kamsiob.kamai.llm.MemoryMode,
    onModeChange: (com.kamsiob.kamai.llm.MemoryMode) -> Unit,
    onForget: (String, String) -> Unit,
    onForgetMany: (List<String>) -> Unit,
    onForgetAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    val selectedIds = remember { mutableStateListOf<String>() }
    var selecting by remember { mutableStateOf(false) }
    fun exit() { selecting = false; selectedIds.clear() }
    androidx.activity.compose.BackHandler(enabled = selecting) { exit() }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = screenPad)) {
        if (selecting) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${selectedIds.size} selected", style = KamTheme.type.sectionTitle, color = colors.textPrimary)
                Spacer(Modifier.weight(1f))
                Text(
                    if (selectedIds.size == entries.size) "Select none" else "Select all",
                    style = KamTheme.type.label, color = colors.accent,
                    modifier = Modifier.clip(CircleShape).clickable {
                        if (selectedIds.size == entries.size) selectedIds.clear()
                        else { selectedIds.clear(); selectedIds.addAll(entries.map { it.id }) }
                    }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Text(
                    "Forget", style = KamTheme.type.label,
                    color = if (selectedIds.isNotEmpty()) colors.flagAmber else colors.textTertiary,
                    modifier = Modifier.clip(CircleShape)
                        .then(if (selectedIds.isNotEmpty()) Modifier.clickable { onForgetMany(selectedIds.toList()); exit() } else Modifier)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Text("Cancel", style = KamTheme.type.label, color = colors.textSecondary,
                    modifier = Modifier.clip(CircleShape).clickable { exit() }.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        } else {
            Text("Memory", style = KamTheme.type.screenTitle, color = colors.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "Everything Kam AI has kept about you is listed here, in full. You choose " +
                    "how much it remembers, and you can delete anything.",
                style = KamTheme.type.body,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(16.dp))

            // The mode selector. Manual is the safe default. PART 7.
            Eyebrow("What Kam AI remembers")
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clip(CircleShape).background(colors.surfaceSecondary).padding(3.dp),
            ) {
                listOf(
                    com.kamsiob.kamai.llm.MemoryMode.MANUAL to "Only when I ask",
                    com.kamsiob.kamai.llm.MemoryMode.AUTO to "Automatically",
                    com.kamsiob.kamai.llm.MemoryMode.OFF to "Off",
                ).forEach { (m, label) ->
                    val on = m == mode
                    val bg by androidx.compose.animation.animateColorAsState(
                        if (on) colors.surface else androidx.compose.ui.graphics.Color.Transparent, label = "mem-mode",
                    )
                    Box(
                        modifier = Modifier.weight(1f).clip(CircleShape).background(bg)
                            .clickable { onModeChange(m) }.padding(vertical = 9.dp)
                            .semantics { selected = on },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = KamTheme.type.secondary,
                            color = if (on) colors.textPrimary else colors.textTertiary)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when (mode) {
                    com.kamsiob.kamai.llm.MemoryMode.MANUAL -> "Say \"remember that ...\" and it keeps that. Nothing else is kept."
                    com.kamsiob.kamai.llm.MemoryMode.AUTO -> "It also keeps durable facts it notices, like preferences and ongoing projects. Auto entries are marked so you can prune them."
                    com.kamsiob.kamai.llm.MemoryMode.OFF -> "Nothing is remembered between conversations."
                },
                style = KamTheme.type.secondary,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(16.dp))
        }

        if (entries.isEmpty()) {
            if (!selecting) {
                EmptyState(
                    title = "Nothing remembered",
                    body = "As you talk, durable facts you mention can end up here. You can " +
                        "always see and delete them.",
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().edgeFade(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(entries, key = { it.id }) { entry ->
                val isSel = selectedIds.contains(entry.id)
                KamCard(
                    Modifier.fillMaxWidth(),
                    onClick = if (selecting) {
                        { if (isSel) selectedIds.remove(entry.id) else selectedIds.add(entry.id) }
                    } else {
                        null
                    },
                ) {
                    Row(
                        Modifier
                            .padding(15.dp)
                            .then(
                                if (!selecting) {
                                    Modifier.combinedClickable(
                                        onClick = {},
                                        onLongClick = { selecting = true; selectedIds.add(entry.id) },
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (selecting) {
                            Box(
                                Modifier.size(22.dp).clip(CircleShape)
                                    .background(if (isSel) colors.accent else androidx.compose.ui.graphics.Color.Transparent)
                                    .border(if (isSel) 0.dp else 2.dp, colors.border, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSel) Icon(androidx.compose.material.icons.Icons.Rounded.Check, null, tint = colors.onAccent, modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(entry.text, style = KamTheme.type.body, color = colors.textPrimary)
                            if (entry.auto) {
                                Spacer(Modifier.height(5.dp))
                                KamChip("Saved automatically", mono = true)
                            }
                        }
                        if (!selecting) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Forget",
                                style = KamTheme.type.label,
                                color = colors.flagAmber,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onForget(entry.id, entry.text) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
            if (!selecting && entries.size > 1) {
                item(key = "forget-all") {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Forget everything",
                        style = KamTheme.type.label,
                        color = colors.flagAmber,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onForgetAll)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
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
        add("SQLCipher for Android" to "BSD-style (Zetetic LLC)")
        add("whisper.cpp, for voice typing" to "MIT")
        add("sherpa-onnx and ONNX Runtime, for the reading voice" to "Apache-2.0")
        add("Piper voices" to "MIT")
        add("espeak-ng data, used by the voices" to "GPL-3.0")
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
 * The last recorded crash, shown in full. Kam AI has no telemetry, so this is the
 * only way a crash becomes visible, and it never leaves the phone unless the user
 * taps share.
 */
@Composable
fun CrashReportScreen(
    report: String?,
    onShare: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    Column(modifier = modifier.fillMaxSize().padding(horizontal = screenPad)) {
        Text("Crash report", style = KamTheme.type.screenTitle, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Kam AI keeps nothing about crashes off your phone. This is the last one, " +
                "here for you to read or send if you want to report it.",
            style = KamTheme.type.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(16.dp))

        if (report == null) {
            Text(
                "There is no crash report right now.",
                style = KamTheme.type.body,
                color = colors.textTertiary,
            )
            return
        }

        LazyColumn(
            modifier = Modifier.weight(1f).edgeFade(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                KamCard(Modifier.fillMaxWidth()) {
                    Text(
                        report,
                        style = KamTheme.type.mono,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            PrimaryButton(text = "Share", onClick = onShare, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            SecondaryButton(text = "Clear", onClick = onClear, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
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

/**
 * System-wide custom instructions (item 15). A single field applied to every
 * conversation, in addition to any project instructions. It is re-injected with
 * every turn (small models drift), and the app's own safety and identity rules
 * always win over it. Capped at a sensible length, with the remaining room shown
 * so the user is never silently truncated.
 */
@Composable
fun CustomInstructionsScreen(
    initial: String,
    maxChars: Int,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    var text by remember {
        mutableStateOf(
            androidx.compose.ui.text.input.TextFieldValue(
                initial, androidx.compose.ui.text.TextRange(initial.length),
            ),
        )
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = screenPad)) {
        Text("Custom instructions", style = KamTheme.type.screenTitle, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Standing instructions Kam AI follows in every conversation, for example " +
                "how you like answers structured or what you are working on. Kept on this " +
                "phone. Its safety and identity rules always come first and cannot be " +
                "overridden here.",
            style = KamTheme.type.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            if (text.text.isEmpty()) {
                Text(
                    "For example: keep answers short and skip the preamble.",
                    style = KamTheme.type.body,
                    color = colors.textTertiary,
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { if (it.text.length <= maxChars) text = it },
                textStyle = KamTheme.type.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(8.dp))
        val remaining = maxChars - text.text.length
        Text(
            if (remaining <= 200) "$remaining characters left" else "Saved on this phone",
            style = KamTheme.type.secondary,
            color = if (remaining <= 0) colors.flagAmber else colors.textTertiary,
        )

        Spacer(Modifier.height(18.dp))
        PrimaryButton("Save", onClick = { onSave(text.text.trim()) }, modifier = Modifier.fillMaxWidth())
    }
}
