package com.kamsiob.kamai.ui.discover

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.ui.theme.reducedMotion
import com.kamsiob.kamai.ui.theme.KamTheme

/**
 * A Discover discussion happens on a surface that slides up over Discover,
 * rather than in the full chat window (#11).
 *
 * The distinction matters because the two are not the same kind of thing. A chat
 * is somewhere you go and stay; a grounded discussion is a question asked about
 * the passage you are looking at, and when it is over you are still looking at
 * the passage. Pushing the full chat screen said the opposite: it replaced
 * Discover, gave the discussion a conversation header with rename and archive
 * beside it, and left going back as the only way out.
 *
 * So the surface keeps Discover visible behind it, dimmed, and names what it is
 * scoped to at the top: the moment's title and where the passage came from. The
 * scope note that used to be a banner inside the transcript is part of the
 * header here, because on this surface the scope is not an interruption to the
 * chat, it is the whole reason the chat exists.
 *
 * Two ways out, and they mean different things. Close leaves the discussion and
 * returns to the passage; the conversation is saved and shows up in Chats like
 * any other. Expand lifts the grounding and moves the whole history into the
 * full chat window, which is the escape from a scope somebody has hit the edge
 * of (item 21). Neither loses anything.
 */
@Composable
fun GroundedSheet(
    title: String,
    source: String,
    onClose: () -> Unit,
    onExpand: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = KamTheme.colors
    val reduced = reducedMotion()

    // Animating away means staying composed until the animation has finished, so
    // dismissal runs the exit first and reports back afterwards. Without this the
    // sheet is removed the instant it is closed and vanishes in one frame, which
    // reads as a crash rather than as a sheet going away.
    // targetState is set inside remember rather than in an effect, so it is
    // already true on the first composition. Set it later and the effect below
    // sees false/false for one frame and closes the sheet before it opens.
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(shown.currentState, shown.targetState) {
        if (!shown.currentState && !shown.targetState) onClose()
    }
    val dismiss: () -> Unit = { shown.targetState = false }

    BackHandler(enabled = true, onBack = dismiss)

    Box(Modifier.fillMaxSize()) {
        // The scrim is part of the message: Discover is still there, this is on
        // top of it. Tapping it closes, which is what everybody tries first.
        AnimatedVisibility(
            visibleState = shown,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        // No ripple: this is a dismiss area, not a button, and a
                        // ripple in the middle of the scrim looks like a mistake.
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = dismiss,
                    )
                    .semantics { contentDescription = "Close this discussion" },
            )
        }

        AnimatedVisibility(
            visibleState = shown,
            // Arrives from the bottom, which is where a sheet comes from. With
            // reduced motion it fades, since the movement is the decoration and
            // the surface is the point.
            enter = if (reduced) fadeIn() else slideInVertically { it } + fadeIn(),
            exit = if (reduced) fadeOut() else slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    // Not the full height. The strip of Discover left showing at
                    // the top is what tells you this is over something rather
                    // than instead of it.
                    .fillMaxHeight(SHEET_HEIGHT)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(colors.background),
            ) {
                SheetHeader(title, source, dismiss, onExpand)
                content()
            }
        }
    }
}

/**
 * How much of the screen the sheet takes, leaving Discover visible above it.
 *
 * 0.92 was the first try and read as a full screen: the only strip left over was
 * the status bar, so there was nothing behind the scrim to see. At 0.86 the app
 * bar and the top of the Discover card show through, which is the whole point of
 * the surface.
 */
private const val SHEET_HEIGHT = 0.86f

@Composable
private fun SheetHeader(
    title: String,
    source: String,
    onClose: () -> Unit,
    onExpand: () -> Unit,
) {
    val colors = KamTheme.colors
    Column(Modifier.fillMaxWidth()) {
        // The grabber says "this is a sheet" before anything is read.
        Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.border),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.MenuBook,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = KamTheme.type.cardTitle,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                // The scope, stated as a fact about this surface rather than as
                // a warning inside the transcript, next to where the passage
                // came from. Between them the two lines answer "what is this
                // about" and "where do the answers come from", which is exactly
                // what the banner inside the chat used to have to say.
                Text(
                    "$source · answers come from this passage only",
                    style = KamTheme.type.secondary,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HeaderAction(Icons.Rounded.OpenInFull, "Open in full chat", onExpand)
            HeaderAction(Icons.Rounded.Close, "Close this discussion", onClose)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
private fun HeaderAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = KamTheme.colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}
