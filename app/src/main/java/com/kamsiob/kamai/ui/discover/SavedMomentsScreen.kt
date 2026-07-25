package com.kamsiob.kamai.ui.discover

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.data.FollowUpEntity
import com.kamsiob.kamai.ui.components.EmptyState
import com.kamsiob.kamai.ui.theme.KamTheme

/**
 * Everything kept from Discover, on its own screen.
 *
 * These used to be printed straight under the moment card, so a few weeks of
 * reading turned Discover into a long scroll with the card it exists for
 * stranded at the top (owner feedback). A saved passage is something you come
 * back to deliberately, which is a different act from being dealt something new,
 * and it deserves its own screen rather than a tail on somebody else's.
 *
 * Deliberately plain: a card each, the passage, when it was saved, and a tap to
 * reopen it as a grounded discussion. No filters and no sorting, because a
 * handful of kept passages does not need managing, and Follow-ups already exists
 * for anybody who wants to see saves from everywhere at once.
 */
@Composable
fun SavedMomentsScreen(
    saved: List<FollowUpEntity>,
    onOpen: (FollowUpEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    val pad = KamTheme.dimens.screenPadding

    Column(modifier = modifier.fillMaxSize().padding(horizontal = pad)) {
        Text("Saved moments", style = KamTheme.type.screenTitle, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Passages you kept while reading. Open one to pick the conversation back up, still " +
                "held to that passage.",
            style = KamTheme.type.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(18.dp))

        if (saved.isEmpty()) {
            EmptyState(
                title = "Nothing kept yet",
                body = "Tap the bookmark on a moment while you are reading and it waits here.",
                modifier = Modifier.fillMaxWidth(),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            items(saved, key = { it.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(KamTheme.dimens.cardRadius))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(KamTheme.dimens.cardRadius))
                        .clickable { onOpen(item) }
                        .padding(15.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    // The book mark rather than a bookmark icon: this is a
                    // passage to read, and the screen is already about saving.
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(colors.tonalFill),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.MenuBook,
                            contentDescription = null,
                            tint = colors.tonalText,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.snippet.trim(),
                            style = KamTheme.type.body,
                            color = colors.textPrimary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Saved ${com.kamsiob.kamai.ui.chats.relativeTime(item.createdAt)} ago",
                            style = KamTheme.type.mono,
                            color = colors.textTertiary,
                        )
                    }
                }
            }
        }
    }
}
