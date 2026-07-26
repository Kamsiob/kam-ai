package com.kamsiob.kamai.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.ui.theme.KamTheme
import com.kamsiob.kamai.ui.theme.expressiveSpec
import com.kamsiob.kamai.ui.theme.standardSpec

/**
 * The brand bar, above every screen: the mark plus the wordmark in small quiet
 * type, and the settings gear at the right. A back arrow appears at the left
 * when the user is a level deep.
 */
@Composable
fun BrandBar(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    /** The mark breathes only where it is acting as a status indicator. */
    breathing: Boolean = true,
) {
    val colors = KamTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconAction(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                description = "Back",
                onClick = onBack,
            )
            Spacer(Modifier.width(2.dp))
        } else {
            Spacer(Modifier.width(6.dp))
        }

        KamMark(size = KamTheme.dimens.markSize, breathing = breathing)
        Spacer(Modifier.width(8.dp))
        Text(
            "Kam AI",
            style = KamTheme.type.wordmark,
            color = colors.textSecondary,
        )

        Spacer(Modifier.weight(1f))

        if (onSettings != null) {
            IconAction(
                icon = Icons.Rounded.Settings,
                description = "Settings",
                onClick = onSettings,
            )
        }
    }
}

/** A 48dp touch target around a 20dp icon, with a real TalkBack label. */
@Composable
fun IconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    Box(
        modifier = modifier
            .size(KamTheme.dimens.minTouchTarget)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint ?: KamTheme.colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The bottom navigation destinations, in display order.
 *
 * Settings is not one of them, and there is no Today tab (canceled, see
 * DECISIONS.md and Not planned).
 *
 * Chats first because it is the home root: it is where an empty back stack falls
 * through to, it is where a new chat starts, and it is where most sessions live.
 * Projects sat first for a while and put a container ahead of the thing being
 * contained (#68).
 *
 * Declaration order is the display order, `NavItem.entries` being what the bar
 * iterates, and nothing anywhere depends on the ordinals, so reordering here is
 * the whole change.
 */
enum class NavItem(val label: String, val icon: ImageVector) {
    CHATS("Chats", Icons.Rounded.Forum),
    PROJECTS("Projects", Icons.Rounded.Folder),
    FOLLOW_UPS("Follow-ups", Icons.Rounded.BookmarkBorder),
    DISCOVER("Discover", Icons.Rounded.Explore),
}

/**
 * Bottom navigation: four items, each an icon in a small pill that fills with
 * tonal green when active, label below. The pill activating is one of the
 * signature moments that gets the expressive spring.
 */
@Composable
fun KamBottomNav(
    current: NavItem,
    onSelect: (NavItem) -> Unit,
    modifier: Modifier = Modifier,
    followUpCount: Int = 0,
) {
    val colors = KamTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem.entries.forEach { item ->
            NavCell(
                item = item,
                selected = item == current,
                badge = if (item == NavItem.FOLLOW_UPS) followUpCount else 0,
                onClick = { onSelect(item) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NavCell(
    item: NavItem,
    selected: Boolean,
    badge: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    val interaction = remember { MutableInteractionSource() }

    val pillColor by animateColorAsState(
        targetValue = if (selected) colors.tonalFill else Color.Transparent,
        animationSpec = expressiveSpec(),
        label = "nav-pill",
    )
    val pillWidth by animateDpAsState(
        targetValue = if (selected) 56.dp else 44.dp,
        animationSpec = expressiveSpec(),
        label = "nav-pill-width",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.tonalText else colors.textTertiary,
        animationSpec = standardSpec(),
        label = "nav-content",
    )

    Column(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 6.dp)
            .semantics {
                this.selected = selected
                contentDescription =
                    if (badge > 0) "${item.label}, $badge open" else item.label
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(pillWidth)
                .height(30.dp)
                .clip(CircleShape)
                .background(pillColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                item.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            // No badge dot.
            //
            // There used to be an amber dot on Follow-ups whenever anything was
            // open. It was reported as visually disruptive, and it is: a
            // permanent colored mark in the navigation reads as an alert, and
            // nothing on that screen is urgent. Follow-ups is a place you go when
            // you choose to, not a queue nagging to be emptied, and a count that
            // never reaches zero for most people is a worse nag than none.
            //
            // The count still reaches the screen reader through the item's own
            // label, so nothing is lost for anybody who wants to know.
        }
        Spacer(Modifier.height(3.dp))
        Text(
            item.label,
            style = KamTheme.type.secondary,
            color = contentColor,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}
