package com.kamsiob.kamai.ui.followups

import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.data.FollowUpEntity
import com.kamsiob.kamai.ui.chats.relativeTime
import com.kamsiob.kamai.ui.components.EmptyState
import com.kamsiob.kamai.ui.components.Eyebrow
import com.kamsiob.kamai.ui.theme.KamTheme
import com.kamsiob.kamai.ui.theme.expressiveSpec
import com.kamsiob.kamai.ui.theme.standardSpec
import kotlin.math.abs

/**
 * Follow-ups. Open items as cards with a circular checkbox, the flagged
 * snippet, and a mono source chip. Completing one springs the check in and
 * strikes the text through, then moves it into a Completed group that is
 * collapsed by default.
 *
 * This is deliberately not a task manager. There are no due dates, no
 * priorities, and no sorting controls.
 */
@Composable
fun FollowUpsScreen(
    open: List<FollowUpEntity>,
    completed: List<FollowUpEntity>,
    onToggle: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    var completedExpanded by remember { mutableStateOf(false) }
    // Filter by where an item came from. null means everything.
    var filter by remember { mutableStateOf<com.kamsiob.kamai.data.Mode?>(null) }
    val sources = remember(open, completed) {
        (open + completed).map { it.sourceMode }.distinct()
    }
    // A filter that no longer matches anything (its last item was removed) falls back to All.
    if (filter != null && filter !in sources) filter = null
    val shownOpen = if (filter == null) open else open.filter { it.sourceMode == filter }
    val shownCompleted = if (filter == null) completed else completed.filter { it.sourceMode == filter }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            "Follow-ups",
            style = KamTheme.type.screenTitle,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = KamTheme.dimens.screenPadding),
        )
        Spacer(Modifier.height(6.dp))

        if (open.isEmpty() && completed.isEmpty()) {
            EmptyState(
                title = "Nothing flagged yet",
                body = "Tap the bookmark under any answer you want to check properly " +
                    "later. It lands here so it does not get lost.",
                modifier = Modifier.fillMaxWidth(),
            )
            return@Column
        }

        // A quiet filter, shown only when items come from more than one place.
        if (sources.size > 1) {
            SourceFilterRow(sources = sources, selected = filter, onSelect = { filter = it })
            Spacer(Modifier.height(4.dp))
        }

        if (shownOpen.isEmpty() && shownCompleted.isEmpty()) {
            Text(
                "Nothing from ${filterLabel(filter)} yet.",
                style = KamTheme.type.body, color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = KamTheme.dimens.screenPadding, vertical = 12.dp),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = KamTheme.dimens.screenPadding,
                vertical = 6.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shownOpen, key = { it.id }) { item ->
                FollowUpCard(
                    item = item,
                    completed = false,
                    onToggle = { onToggle(item.id, true) },
                    onRemove = { onRemove(item.id) },
                    onOpenSource = { item.conversationId?.let(onOpenSource) },
                )
            }

            if (shownCompleted.isNotEmpty()) {
                item(key = "completed-header") {
                    CompletedHeader(
                        count = shownCompleted.size,
                        expanded = completedExpanded,
                        onToggle = { completedExpanded = !completedExpanded },
                    )
                }
                if (completedExpanded) {
                    items(shownCompleted, key = { "c-${it.id}" }) { item ->
                        FollowUpCard(
                            item = item,
                            completed = true,
                            onToggle = { onToggle(item.id, false) },
                            onRemove = { onRemove(item.id) },
                            onOpenSource = { item.conversationId?.let(onOpenSource) },
                        )
                    }
                }
            }
        }
    }
}

/** Short label for a source filter value; null is everything. */
private fun filterLabel(mode: com.kamsiob.kamai.data.Mode?): String = when (mode) {
    null -> "All"
    com.kamsiob.kamai.data.Mode.CHAT -> "Chat"
    com.kamsiob.kamai.data.Mode.LOGIC -> "Logic"
    com.kamsiob.kamai.data.Mode.DISCOVER -> "Discover"
    com.kamsiob.kamai.data.Mode.BENCH -> "Workbench"
    com.kamsiob.kamai.data.Mode.OVERLAY -> "Quick ask"
}

/**
 * A light, horizontal row of source filters: All plus each place items came from.
 * The active one fills with the tonal green so it is obvious, and All is always a
 * clear way back to everything.
 */
@Composable
private fun SourceFilterRow(
    sources: List<com.kamsiob.kamai.data.Mode>,
    selected: com.kamsiob.kamai.data.Mode?,
    onSelect: (com.kamsiob.kamai.data.Mode?) -> Unit,
) {
    val colors = KamTheme.colors
    val options = listOf<com.kamsiob.kamai.data.Mode?>(null) + sources
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = KamTheme.dimens.screenPadding, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val on = option == selected
            Text(
                filterLabel(option),
                style = KamTheme.type.label,
                color = if (on) colors.tonalText else colors.textSecondary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (on) colors.tonalFill else colors.surfaceSecondary)
                    .clickable { onSelect(option) }
                    .semantics { this.selected = on }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun CompletedHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val colors = KamTheme.colors
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = standardSpec(),
        label = "chevron",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .semantics {
                contentDescription =
                    if (expanded) "Completed, $count, expanded" else "Completed, $count, collapsed"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Eyebrow("Completed  $count")
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rotation },
        )
    }
}

@Composable
private fun FollowUpCard(
    item: FollowUpEntity,
    completed: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onOpenSource: () -> Unit,
) {
    val colors = KamTheme.colors
    val density = LocalDensity.current
    val removeWidthPx = with(density) { REMOVE_WIDTH.toPx() }

    var offset by remember { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue = offset,
        animationSpec = standardSpec(),
        label = "swipe",
    )
    val revealed = abs(animatedOffset) > 1f

    BackHandler(enabled = revealed) { offset = 0f }

    Box(Modifier.fillMaxWidth()) {
        // Completed items swipe left to remove.
        if (revealed && completed) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(vertical = 4.dp)
                    .size(width = 56.dp, height = 56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.amberFill)
                    .clickable { offset = 0f; onRemove() }
                    .semantics { contentDescription = "Remove" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = colors.flagAmber,
                    modifier = Modifier.size(19.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = animatedOffset }
                .clip(RoundedCornerShape(KamTheme.dimens.cardRadius))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(KamTheme.dimens.cardRadius))
                .then(
                    if (completed) {
                        Modifier.pointerInput(item.id) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    offset = if (offset < -removeWidthPx / 2f) -removeWidthPx else 0f
                                },
                            ) { _, delta ->
                                val next = offset + delta
                                offset = when {
                                    next > 0f -> 0f
                                    next < -removeWidthPx -> -removeWidthPx + (next + removeWidthPx) * 0.25f
                                    else -> next
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                )
                .clickable(onClick = onOpenSource)
                .padding(14.dp)
                .semantics {
                    customActions = buildList {
                        add(
                            CustomAccessibilityAction(
                                if (completed) "Mark not done" else "Mark done",
                            ) { onToggle(); true },
                        )
                        if (completed) add(CustomAccessibilityAction("Remove") { onRemove(); true })
                    }
                },
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(checked = completed, onToggle = onToggle)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                // Compact: a short title plus the flagged snippet truncated after
                // about two lines, not the whole conversation. PART 5.
                Text(
                    item.snippet.lineSequence().firstOrNull()?.take(60).orEmpty()
                        .ifBlank { "Flagged note" },
                    style = KamTheme.type.cardTitle,
                    color = if (completed) colors.textTertiary else colors.textPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textDecoration = if (completed) TextDecoration.LineThrough else null,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    item.snippet,
                    style = KamTheme.type.body,
                    color = if (completed) colors.textTertiary else colors.textSecondary,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textDecoration = if (completed) TextDecoration.LineThrough else null,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        sourceLabel(item),
                        style = KamTheme.type.mono,
                        color = colors.textTertiary,
                    )
                }
                if (!item.note.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        item.note.orEmpty(),
                        style = KamTheme.type.secondary,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

/** The mono source chip: which mode or surface it came from, and when. */
private fun sourceLabel(item: FollowUpEntity): String {
    val where = when (item.sourceMode) {
        com.kamsiob.kamai.data.Mode.CHAT -> "CHAT"
        com.kamsiob.kamai.data.Mode.LOGIC -> "LOGIC"
        com.kamsiob.kamai.data.Mode.BENCH -> "BENCH"
        com.kamsiob.kamai.data.Mode.DISCOVER -> "DISCOVER"
        com.kamsiob.kamai.data.Mode.OVERLAY -> "QUICK PANEL"
    }
    return "$where  ${relativeTime(item.createdAt)}"
}

/** The check springs in with overshoot. One of the signature moments. */
@Composable
private fun Checkbox(checked: Boolean, onToggle: () -> Unit) {
    val colors = KamTheme.colors
    val scale by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = expressiveSpec(),
        label = "check",
    )

    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (checked) colors.accent else androidx.compose.ui.graphics.Color.Transparent)
            .border(
                width = if (checked) 0.dp else 2.dp,
                color = colors.border,
                shape = CircleShape,
            )
            .clickable(onClick = onToggle)
            .semantics { contentDescription = if (checked) "Done" else "Not done" },
        contentAlignment = Alignment.Center,
    ) {
        if (scale > 0.01f) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier
                    .size(15.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale },
            )
        }
    }
}

private val REMOVE_WIDTH = 66.dp
