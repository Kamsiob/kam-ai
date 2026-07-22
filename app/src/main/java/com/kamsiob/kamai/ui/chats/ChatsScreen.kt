package com.kamsiob.kamai.ui.chats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.data.ConversationSummary
import com.kamsiob.kamai.ui.AppViewModel.ChatsView
import com.kamsiob.kamai.ui.components.EmptyState
import com.kamsiob.kamai.ui.components.Eyebrow
import com.kamsiob.kamai.ui.components.IconAction
import com.kamsiob.kamai.ui.components.edgeFade
import com.kamsiob.kamai.ui.theme.KamTheme
import com.kamsiob.kamai.ui.theme.standardSpec
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Chats screen. Three switchable views, a collapsible Pinned section, a
 * Recent list, swipe actions, and search across every conversation.
 */
@Composable
fun ChatsScreen(
    conversations: List<ConversationSummary>,
    view: ChatsView,
    onViewChange: (ChatsView) -> Unit,
    onOpen: (String) -> Unit,
    onNewChat: () -> Unit,
    onPin: (String, Boolean) -> Unit,
    onArchive: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    var query by remember { mutableStateOf("") }
    var pinnedExpanded by remember { mutableStateOf(true) }

    val filtered = remember(conversations, query) {
        if (query.isBlank()) {
            conversations
        } else {
            conversations.filter { row ->
                row.title?.contains(query, ignoreCase = true) == true ||
                    row.snippet?.contains(query, ignoreCase = true) == true
            }
        }
    }
    val pinned = filtered.filter { it.pinned }
    val recent = filtered.filterNot { it.pinned }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = KamTheme.dimens.screenPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Chats", style = KamTheme.type.screenTitle, color = colors.textPrimary)
            Spacer(Modifier.weight(1f))
            ViewSwitcher(view, onViewChange)
        }

        SearchField(query, { query = it }, Modifier.padding(horizontal = KamTheme.dimens.screenPadding))

        if (conversations.isEmpty()) {
            EmptyState(
                title = "Nothing here yet",
                body = "Ask it something. Whatever is on your mind, it runs on this " +
                    "phone, so nothing you type leaves it.",
                modifier = Modifier.fillMaxWidth(),
            )
            return@Column
        }

        if (filtered.isEmpty()) {
            EmptyState(
                title = "Nothing matches",
                body = "Try fewer words.",
                modifier = Modifier.fillMaxWidth(),
            )
            return@Column
        }

        if (view == ChatsView.GRID) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().edgeFade(),
                contentPadding = PaddingValues(KamTheme.dimens.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.id }) { row ->
                    // Swipe is replaced by a long-press menu in grid view.
                    GridCell(row, onOpen, onPin, onArchive, onDelete)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().edgeFade(),
                contentPadding = PaddingValues(
                    horizontal = KamTheme.dimens.screenPadding,
                    vertical = 6.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // The Pinned section is hidden entirely when nothing is pinned.
                if (pinned.isNotEmpty()) {
                    item(key = "pinned-header") {
                        SectionHeader(
                            label = "Pinned",
                            count = pinned.size,
                            expanded = pinnedExpanded,
                            onToggle = { pinnedExpanded = !pinnedExpanded },
                        )
                    }
                    if (pinnedExpanded) {
                        items(pinned, key = { "p-${it.id}" }) { row ->
                            SwipeRow(row, view, onOpen, onPin, onArchive, onDelete)
                        }
                    }
                    item(key = "recent-label") {
                        Eyebrow("Recent", Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp))
                    }
                }

                items(recent, key = { it.id }) { row ->
                    SwipeRow(row, view, onOpen, onPin, onArchive, onDelete)
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
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
            if (value.isEmpty()) {
                Text("Search conversations", style = KamTheme.type.body, color = colors.textTertiary)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = KamTheme.type.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = "Search conversations"
                },
            )
        }
    }
}

/** A small three-icon pill next to the title. */
@Composable
private fun ViewSwitcher(current: ChatsView, onChange: (ChatsView) -> Unit) {
    val colors = KamTheme.colors
    val options = listOf(
        ChatsView.COMFORTABLE to (Icons.Rounded.ViewAgenda to "Comfortable list"),
        ChatsView.COMPACT to (Icons.Rounded.ViewList to "Compact list"),
        ChatsView.GRID to (Icons.Rounded.GridView to "Grid"),
    )

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.surfaceSecondary)
            .padding(2.dp),
    ) {
        options.forEach { (option, iconAndLabel) ->
            val (icon, label) = iconAndLabel
            val selected = option == current
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.surface else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onChange(option) }
                    .semantics {
                        contentDescription = label
                        this.selected = selected
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) colors.textPrimary else colors.textTertiary,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    label: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
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
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .semantics {
                contentDescription =
                    if (expanded) "$label, $count, expanded" else "$label, $count, collapsed"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Eyebrow("$label  $count")
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rotation },
        )
    }
}

/**
 * A row that swipes left to reveal Pin, Archive and Delete.
 *
 * The rail is drawn behind the row and is invisible and non-interactive until a
 * drag begins, so it can never peek through the rounded corners at rest. The
 * same three actions are also exposed as accessibility actions, because a swipe
 * gesture is not reachable with a screen reader.
 */
@Composable
private fun SwipeRow(
    row: ConversationSummary,
    view: ChatsView,
    onOpen: (String) -> Unit,
    onPin: (String, Boolean) -> Unit,
    onArchive: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val colors = KamTheme.colors
    val density = LocalDensity.current
    val railWidthPx = with(density) { RAIL_WIDTH.toPx() }

    var offset by remember { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue = offset,
        animationSpec = standardSpec(),
        label = "swipe",
    )
    val revealed = abs(animatedOffset) > 1f

    val close = { offset = 0f }

    Box(Modifier.fillMaxWidth()) {
        if (revealed) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .height(ROW_HEIGHT),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RailButton(
                    icon = Icons.Rounded.PushPin,
                    label = if (row.pinned) "Unpin" else "Pin",
                    tint = colors.tonalText,
                    background = colors.tonalFill,
                ) { close(); onPin(row.id, !row.pinned) }
                RailButton(
                    icon = Icons.Rounded.Archive,
                    label = "Archive",
                    tint = colors.textSecondary,
                    background = colors.surfaceSecondary,
                ) { close(); onArchive(row.id) }
                RailButton(
                    icon = Icons.Rounded.Delete,
                    label = "Delete",
                    tint = colors.flagAmber,
                    background = colors.amberFill,
                ) { close(); onDelete(row.id) }
            }
        }

        Box(
            modifier = Modifier
                .graphicsLayer { translationX = animatedOffset }
                .pointerInput(row.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            // Past a third of the rail it springs open, otherwise back.
                            offset = if (offset < -railWidthPx / 3f) -railWidthPx else 0f
                        },
                    ) { _, delta ->
                        // Follows the finger, with resistance past the rail.
                        val next = offset + delta
                        offset = when {
                            next > 0f -> 0f
                            next < -railWidthPx -> -railWidthPx + (next + railWidthPx) * 0.25f
                            else -> next
                        }
                    }
                }
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(
                            if (row.pinned) "Unpin" else "Pin",
                        ) { onPin(row.id, !row.pinned); true },
                        CustomAccessibilityAction("Archive") { onArchive(row.id); true },
                        CustomAccessibilityAction("Delete") { onDelete(row.id); true },
                    )
                },
        ) {
            ConversationRow(row, view) { if (revealed) close() else onOpen(row.id) }
        }
    }
}

@Composable
private fun RailButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    background: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(start = 5.dp)
            .size(width = 52.dp, height = 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun ConversationRow(
    row: ConversationSummary,
    view: ChatsView,
    onClick: () -> Unit,
) {
    val colors = KamTheme.colors
    val shape = RoundedCornerShape(18.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = if (view == ChatsView.COMPACT) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The compact row carries no leading letter icon, just title and time.
        if (view == ChatsView.COMFORTABLE) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.tonalFill),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (row.title ?: "N").take(1).uppercase(),
                    style = KamTheme.type.label,
                    color = colors.tonalText,
                )
            }
            Spacer(Modifier.width(12.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(
                row.title ?: "New conversation",
                style = KamTheme.type.cardTitle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (view == ChatsView.COMFORTABLE && !row.snippet.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    row.snippet.orEmpty(),
                    style = KamTheme.type.secondary,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(10.dp))
        Text(relativeTime(row.updatedAt), style = KamTheme.type.mono, color = colors.textTertiary)
    }
}

@Composable
private fun GridCell(
    row: ConversationSummary,
    onOpen: (String) -> Unit,
    onPin: (String, Boolean) -> Unit,
    onArchive: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val colors = KamTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface)
                .border(1.dp, colors.border, shape)
                .combinedClickable(
                    onClick = { onOpen(row.id) },
                    onLongClick = { menuOpen = true },
                )
                .padding(13.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colors.tonalFill),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (row.title ?: "N").take(1).uppercase(),
                    style = KamTheme.type.secondary,
                    color = colors.tonalText,
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(
                row.title ?: "New conversation",
                style = KamTheme.type.cardTitle,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                row.snippet.orEmpty(),
                style = KamTheme.type.secondary,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            containerColor = colors.surface,
        ) {
            listOf(
                (if (row.pinned) "Unpin" else "Pin") to { onPin(row.id, !row.pinned) },
                "Archive" to { onArchive(row.id) },
                "Delete" to { onDelete(row.id) },
            ).forEach { (label, action) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            label,
                            style = KamTheme.type.body,
                            color = if (label == "Delete") colors.flagAmber else colors.textPrimary,
                        )
                    },
                    onClick = { menuOpen = false; action() },
                )
            }
        }
    }
}

private val RAIL_WIDTH = 175.dp
private val ROW_HEIGHT = 62.dp

/** Short, plain relative time for the mono timestamp column. */
fun relativeTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val delta = (now - epochMillis).coerceAtLeast(0)
    val minutes = delta / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        days < 365 -> "${days / 7}w"
        else -> "${days / 365}y"
    }
}
