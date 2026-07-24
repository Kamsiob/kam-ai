package com.kamsiob.kamai.ui.chats

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
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
import androidx.compose.runtime.mutableStateListOf
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
import com.kamsiob.kamai.data.Mode
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
    archivedCount: Int = 0,
    onOpenArchived: () -> Unit = {},
    onOpen: (String) -> Unit,
    onPin: (String, Boolean) -> Unit,
    onArchive: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDeleteMany: (List<String>) -> Unit = {},
    onNewChat: (Mode) -> Unit = {},
    onRename: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    var query by remember { mutableStateOf("") }
    var pinnedExpanded by remember { mutableStateOf(true) }

    // Bulk selection. Long-pressing a row enters it; tapping toggles a row.
    val selected = remember { mutableStateListOf<String>() }
    var selecting by remember { mutableStateOf(false) }
    fun exitSelection() { selecting = false; selected.clear() }

    // Rename target, driving the inline dialog.
    var renaming by remember { mutableStateOf<ConversationSummary?>(null) }

    // Filter by mode used. Empty means all. Combines with search: a query narrows
    // within the mode-filtered set. Matches any mode a conversation has used, the
    // same set the row dots read from.
    var modeFilter by remember { mutableStateOf<Set<Mode>>(emptySet()) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val filtered = remember(conversations, query, modeFilter) {
        conversations.filter { row ->
            val matchesQuery = query.isBlank() ||
                row.title?.contains(query, ignoreCase = true) == true ||
                row.snippet?.contains(query, ignoreCase = true) == true
            val matchesMode = modeFilter.isEmpty() ||
                com.kamsiob.kamai.ui.components.modesFromCsv(row.modesUsed).any { it in modeFilter }
            matchesQuery && matchesMode
        }
    }
    val pinned = filtered.filter { it.pinned }
    val recent = filtered.filterNot { it.pinned }

    // Back closes selection mode before anything else on this screen.
    BackHandler(enabled = selecting) { exitSelection() }

    val toggleSelect: (String) -> Unit = { id ->
        if (selected.contains(id)) selected.remove(id) else selected.add(id)
        if (selected.isEmpty()) selecting = false
    }
    val enterSelection: (String) -> Unit = { id ->
        selecting = true
        if (!selected.contains(id)) selected.add(id)
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (selecting) {
            SelectionBar(
                count = selected.size,
                allSelected = selected.size == filtered.size && filtered.isNotEmpty(),
                onSelectAll = { selected.clear(); selected.addAll(filtered.map { it.id }) },
                onSelectNone = { selected.clear() },
                onCancel = { exitSelection() },
                onDelete = {
                    onDeleteMany(selected.toList())
                    exitSelection()
                },
            )
        } else {
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
            SearchField(
                query, { query = it },
                Modifier.padding(horizontal = KamTheme.dimens.screenPadding),
                filterActive = modeFilter.isNotEmpty(),
                onFilter = { showFilterSheet = true },
            )
            // A plain, legible statement of an active filter, with a one-tap clear,
            // so a user never wonders why they are seeing fewer conversations.
            if (modeFilter.isNotEmpty()) {
                val names = com.kamsiob.kamai.ui.theme.ModeColors.let { mc ->
                    modeFilter.joinToString(", ") { mc.name(it) }
                }
                Row(
                    modifier = Modifier
                        .padding(horizontal = KamTheme.dimens.screenPadding)
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Showing: $names", style = KamTheme.type.secondary, color = colors.textSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Clear",
                        style = KamTheme.type.label,
                        color = colors.accent,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { modeFilter = emptySet() }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }

        // The list fills the middle so the new-chat bar can live at the bottom,
        // within thumb reach, instead of at the top of a tall screen.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                conversations.isEmpty() -> EmptyState(
                    title = "Nothing here yet",
                    body = "Ask it something. Whatever is on your mind, it runs on this " +
                        "phone, so nothing you type leaves it.",
                    modifier = Modifier.fillMaxWidth(),
                )

                filtered.isEmpty() -> EmptyState(
                    title = "Nothing matches",
                    body = "Try fewer words.",
                    modifier = Modifier.fillMaxWidth(),
                )

                view == ChatsView.GRID -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().edgeFade(),
                    contentPadding = PaddingValues(KamTheme.dimens.screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.id }) { row ->
                        // Swipe is replaced by a long-press menu in grid view.
                        GridCell(
                            row = row,
                            selecting = selecting,
                            selected = selected.contains(row.id),
                            onOpen = { if (selecting) toggleSelect(row.id) else onOpen(row.id) },
                            onEnterSelection = { enterSelection(row.id) },
                            onPin = onPin,
                            onArchive = onArchive,
                            onDelete = onDelete,
                            onRename = { renaming = row },
                        )
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().edgeFade(),
                    contentPadding = PaddingValues(
                        horizontal = KamTheme.dimens.screenPadding,
                        vertical = 6.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // The Pinned section is hidden entirely when nothing is pinned.
                    if (pinned.isNotEmpty() && !selecting) {
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
                                ChatRow(row, view, selecting, selected.contains(row.id),
                                    toggleSelect, enterSelection, onOpen, onPin, onArchive, onDelete) { renaming = row }
                            }
                        }
                        item(key = "recent-label") {
                            Eyebrow("Recent", Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp))
                        }
                    }

                    val listRows = if (selecting) filtered else recent
                    items(listRows, key = { it.id }) { row ->
                        ChatRow(row, view, selecting, selected.contains(row.id),
                            toggleSelect, enterSelection, onOpen, onPin, onArchive, onDelete) { renaming = row }
                    }

                    // A quiet way to the archived chats, shown only when some
                    // exist, so it never clutters the main list (item 20).
                    if (archivedCount > 0 && !selecting) {
                        item(key = "archived-link") {
                            Text(
                                "Archived ($archivedCount)",
                                style = KamTheme.type.label,
                                color = colors.textSecondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClick = onOpenArchived)
                                    .padding(vertical = 14.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        // The segmented mode control is both the new-chat action and the mode
        // selector: one tap on a segment starts a new conversation in that mode.
        // It sits directly above the bottom navigation, reachable one-handed.
        if (!selecting) {
            com.kamsiob.kamai.ui.components.SegmentedModeControl(
                onSelect = { onNewChat(it) },
                modifier = Modifier
                    .padding(horizontal = KamTheme.dimens.screenPadding)
                    .padding(top = 6.dp, bottom = 6.dp),
            )
        }
    }

    renaming?.let { RenameDialog(it, onRename) { renaming = null } }

    if (showFilterSheet) {
        ModeFilterSheet(
            selectedModes = modeFilter,
            onToggle = { m -> modeFilter = if (m in modeFilter) modeFilter - m else modeFilter + m },
            onClear = { modeFilter = emptySet(); showFilterSheet = false },
            onDismiss = { showFilterSheet = false },
        )
    }
}

/** A row that is either the swipe row (normal) or a selectable row (selecting). */
@Composable
private fun ChatRow(
    row: ConversationSummary,
    view: ChatsView,
    selecting: Boolean,
    isSelected: Boolean,
    onToggleSelect: (String) -> Unit,
    onEnterSelection: (String) -> Unit,
    onOpen: (String) -> Unit,
    onPin: (String, Boolean) -> Unit,
    onArchive: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: () -> Unit,
) {
    if (selecting) {
        SelectableRow(row, view, isSelected) { onToggleSelect(row.id) }
    } else {
        SwipeRow(row, view, onOpen, onPin, onArchive, onDelete, onRename) { onEnterSelection(row.id) }
    }
}

@Composable
private fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    filterActive: Boolean = false,
    onFilter: () -> Unit = {},
) {
    val colors = KamTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(CircleShape)
            .background(colors.surfaceSecondary)
            .padding(start = 14.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
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
        // The mode filter lives inside the search field, so it does not add a row.
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (filterActive) colors.tonalFill else androidx.compose.ui.graphics.Color.Transparent)
                .clickable(onClick = onFilter)
                .semantics {
                    contentDescription = if (filterActive) "Filter by mode, active" else "Filter by mode"
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.FilterList,
                contentDescription = null,
                tint = if (filterActive) colors.accent else colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * The mode filter sheet. Multiple choice across the four modes plus Discover, with
 * a clear reset to all. Designed so more filter types could be added later without
 * restructuring; only mode filtering ships now.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ModeFilterSheet(
    selectedModes: Set<Mode>,
    onToggle: (Mode) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = KamTheme.colors
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.background) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
        ) {
            Text("Filter by mode", style = KamTheme.type.sectionTitle, color = colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Show conversations that used any of these.",
                style = KamTheme.type.secondary, color = colors.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            val options = com.kamsiob.kamai.ui.theme.ModeColors.fourModes + Mode.DISCOVER
            options.forEach { mode ->
                val on = mode in selectedModes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggle(mode) }
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                        .semantics { selected = on },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.kamsiob.kamai.ui.components.ModeDot(mode, size = 9.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        com.kamsiob.kamai.ui.theme.ModeColors.name(mode),
                        style = KamTheme.type.body,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (on) {
                        Icon(
                            Icons.Rounded.Check, contentDescription = "Selected",
                            tint = colors.accent, modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Show all",
                style = KamTheme.type.label,
                color = colors.accent,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onClear() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
    onRename: () -> Unit,
    onLongPress: () -> Unit,
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

    // An open action rail is the innermost dismissible thing on this screen, so
    // it takes the back event before the navigation stack sees it.
    BackHandler(enabled = revealed, onBack = close)

    Box(Modifier.fillMaxWidth()) {
        if (revealed) {
            // The rail is matched to the row's exact height (item 4: the buttons
            // never stand taller than the row, in any view) and spans the full
            // RAIL_WIDTH, sized to fit all four actions so none is left hidden
            // under the row when it is open (item 19). Each button takes an equal
            // share of the width and the full height, with the row's corner radius.
            Box(Modifier.matchParentSize()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(RAIL_WIDTH)
                        .padding(start = RAIL_GAP),
                    horizontalArrangement = Arrangement.spacedBy(RAIL_GAP),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RailButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.DriveFileRenameOutline,
                        label = "Rename",
                        tint = colors.textSecondary,
                        background = colors.surfaceSecondary,
                    ) { close(); onRename() }
                    RailButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.PushPin,
                        label = if (row.pinned) "Unpin" else "Pin",
                        tint = colors.tonalText,
                        background = colors.tonalFill,
                    ) { close(); onPin(row.id, !row.pinned) }
                    RailButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Archive,
                        label = "Archive",
                        tint = colors.textSecondary,
                        background = colors.surfaceSecondary,
                    ) { close(); onArchive(row.id) }
                    RailButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Delete,
                        label = "Delete",
                        tint = colors.goldText,
                        background = colors.amberFill,
                    ) { close(); onDelete(row.id) }
                }
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
                        CustomAccessibilityAction("Rename") { onRename(); true },
                        CustomAccessibilityAction(
                            if (row.pinned) "Unpin" else "Pin",
                        ) { onPin(row.id, !row.pinned); true },
                        CustomAccessibilityAction("Archive") { onArchive(row.id); true },
                        CustomAccessibilityAction("Delete") { onDelete(row.id); true },
                        CustomAccessibilityAction("Select") { onLongPress(); true },
                    )
                },
        ) {
            ConversationRow(
                row = row,
                view = view,
                onClick = { if (revealed) close() else onOpen(row.id) },
                onLongClick = onLongPress,
            )
        }
    }
}

/** The bar shown while selecting several conversations. PART 0 bulk selection. */
@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = KamTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KamTheme.dimens.screenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$count selected",
            style = KamTheme.type.sectionTitle,
            color = colors.textPrimary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            if (allSelected) "Select none" else "Select all",
            style = KamTheme.type.label,
            color = colors.accent,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { if (allSelected) onSelectNone() else onSelectAll() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Text(
            "Delete",
            style = KamTheme.type.label,
            color = if (count > 0) colors.goldText else colors.textTertiary,
            modifier = Modifier
                .clip(CircleShape)
                .then(if (count > 0) Modifier.clickable(onClick = onDelete) else Modifier)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Text(
            "Cancel",
            style = KamTheme.type.label,
            color = colors.textSecondary,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onCancel)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** A conversation row in selection mode, with a leading checkbox. */
@Composable
private fun SelectableRow(
    row: ConversationSummary,
    view: ChatsView,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    val colors = KamTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSelected) colors.tonalFill else colors.surface)
            .border(1.dp, if (isSelected) colors.accent else colors.border, shape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { selected = isSelected },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isSelected) colors.accent else androidx.compose.ui.graphics.Color.Transparent)
                .border(if (isSelected) 0.dp else 2.dp, colors.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(
                    androidx.compose.material.icons.Icons.Rounded.Check,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            row.displayTitle(),
            style = KamTheme.type.cardTitle,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Text(relativeTime(row.updatedAt), style = KamTheme.type.mono, color = colors.textTertiary)
    }
}

/** The inline rename dialog. Saves immediately. */
@Composable
private fun RenameDialog(
    row: ConversationSummary,
    onRename: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = KamTheme.colors
    var text by remember {
        mutableStateOf(
            androidx.compose.ui.text.input.TextFieldValue(
                row.title.orEmpty(),
                androidx.compose.ui.text.TextRange(row.title.orEmpty().length),
            ),
        )
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        BackHandler(enabled = true, onBack = onDismiss)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                .padding(22.dp),
        ) {
            Text("Rename chat", style = KamTheme.type.cardTitle, color = colors.textPrimary)
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceSecondary)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = KamTheme.type.body.copy(color = colors.textPrimary),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "Cancel",
                    style = KamTheme.type.label,
                    color = colors.textSecondary,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Save",
                    style = KamTheme.type.label,
                    color = if (text.text.isNotBlank()) colors.accent else colors.textTertiary,
                    modifier = Modifier.clip(CircleShape)
                        .then(if (text.text.isNotBlank()) Modifier.clickable { onRename(row.id, text.text); onDismiss() } else Modifier)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun RailButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    background: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(KamTheme.dimens.cardRadius))
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
    onLongClick: () -> Unit,
) {
    val colors = KamTheme.colors
    val shape = RoundedCornerShape(18.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
                row.displayTitle(),
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
        com.kamsiob.kamai.ui.components.ModeDots(
            row.modesUsed,
            modifier = Modifier.semantics {
                contentDescription = "Modes used: ${com.kamsiob.kamai.ui.components.modesLabel(row.modesUsed)}"
            },
        )
        Spacer(Modifier.width(8.dp))
        Text(relativeTime(row.updatedAt), style = KamTheme.type.mono, color = colors.textTertiary)
    }
}

@Composable
private fun GridCell(
    row: ConversationSummary,
    selecting: Boolean,
    selected: Boolean,
    onOpen: (String) -> Unit,
    onEnterSelection: (String) -> Unit,
    onPin: (String, Boolean) -> Unit,
    onArchive: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: () -> Unit,
) {
    val colors = KamTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (selected) colors.tonalFill else colors.surface)
                .border(1.dp, if (selected) colors.accent else colors.border, shape)
                .combinedClickable(
                    onClick = { onOpen(row.id) },
                    // Long-press opens the action menu in grid view, matching
                    // DESIGN.md; it also enters selection mode.
                    onLongClick = { if (selecting) onOpen(row.id) else menuOpen = true },
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
                row.displayTitle(),
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
            Spacer(Modifier.height(7.dp))
            com.kamsiob.kamai.ui.components.ModeDots(
                row.modesUsed,
                modifier = Modifier.semantics {
                    contentDescription = "Modes used: ${com.kamsiob.kamai.ui.components.modesLabel(row.modesUsed)}"
                },
            )
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            containerColor = colors.surface,
        ) {
            listOf(
                "Rename" to onRename,
                (if (row.pinned) "Unpin" else "Pin") to { onPin(row.id, !row.pinned) },
                "Archive" to { onArchive(row.id) },
                "Select" to { onEnterSelection(row.id) },
                "Delete" to { onDelete(row.id) },
            ).forEach { (label, action) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            label,
                            style = KamTheme.type.body,
                            color = if (label == "Delete") colors.goldText else colors.textPrimary,
                        )
                    },
                    onClick = { menuOpen = false; action() },
                )
            }
        }
    }
}

// Wide enough to reveal all four rail actions (Rename, Pin, Archive, Delete) with
// none hidden under the row. Buttons split this width evenly. See items 4 and 19.
private val RAIL_WIDTH = 232.dp
private val RAIL_GAP = 6.dp

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


/**
 * The title to show in the list. A real title wins; otherwise a short excerpt of
 * the conversation stands in, which is specific and honest, rather than a generic
 * "New conversation" placeholder (item 17).
 */
private fun com.kamsiob.kamai.data.ConversationSummary.displayTitle(): String =
    title ?: snippet?.trim()?.take(48)?.takeIf { it.isNotBlank() } ?: "Untitled chat"

/**
 * The archived chats view (item 20). Archived conversations live here, off the
 * main list. Each can be opened, moved back to Chats (archiving is reversible),
 * or deleted (which is not). Reached from the quiet "Archived" link on Chats.
 */
@Composable
fun ArchivedScreen(
    conversations: List<ConversationSummary>,
    onOpen: (String) -> Unit,
    onUnarchive: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    val pad = KamTheme.dimens.screenPadding

    Column(modifier = modifier.fillMaxSize().padding(horizontal = pad)) {
        Text("Archived", style = KamTheme.type.screenTitle, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Chats you moved out of the way. Open one, move it back to Chats, or delete it. " +
                "Archiving is reversible; deleting is not.",
            style = KamTheme.type.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(16.dp))

        if (conversations.isEmpty()) {
            EmptyState(
                title = "Nothing archived",
                body = "When you archive a chat it moves here, out of your main list, and stays " +
                    "until you bring it back or delete it.",
                modifier = Modifier.fillMaxWidth(),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().edgeFade(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(conversations, key = { it.id }) { row ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(KamTheme.dimens.cardRadius))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(KamTheme.dimens.cardRadius))
                        .clickable { onOpen(row.id) }
                        .padding(15.dp),
                ) {
                    Text(row.displayTitle(), style = KamTheme.type.cardTitle, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    row.snippet?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = KamTheme.type.secondary, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row {
                        Text(
                            "Move to Chats", style = KamTheme.type.label, color = colors.accent,
                            modifier = Modifier.clip(CircleShape).clickable { onUnarchive(row.id) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Delete", style = KamTheme.type.label, color = colors.goldText,
                            modifier = Modifier.clip(CircleShape).clickable { onDelete(row.id) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
