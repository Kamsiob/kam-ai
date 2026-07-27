package com.kamsiob.kamai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.download.Downloads
import com.kamsiob.kamai.ui.theme.KamTheme
import com.kamsiob.kamai.ui.theme.reducedMotion
import com.kamsiob.kamai.ui.theme.standardSpec

/**
 * The one download indicator, shown on every screen while anything is
 * downloading (#81).
 *
 * A model is gigabytes and minutes. Somebody who starts one and then walks into
 * the chat should not have to go back to the screen they started it from to find
 * out whether it is still going, and should never be left wondering whether
 * anything is happening at all.
 *
 * Deliberately a slim line rather than a banner. It is on screen for minutes, so
 * it has to be the kind of thing you stop noticing, and it sits under the brand
 * bar where it is out of the way of everything a screen actually does.
 *
 * One treatment for all three kinds. A model, a voice and a content pack are the
 * same fact to a waiting user, and three indicators would be three things to
 * learn. Several at once show the current one and how many are behind it.
 */
@Composable
fun DownloadIndicator(
    items: List<Downloads.Item>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = remember(items) { DownloadSummary.active(items) }
    val reduced = reducedMotion()

    // Expanding and shrinking rather than appearing: the content below moves
    // smoothly instead of jumping under a finger already on its way to a tap.
    AnimatedVisibility(
        visible = active != null,
        enter = if (reduced) fadeIn() else expandVertically(standardSpec()) + fadeIn(),
        exit = if (reduced) fadeOut() else shrinkVertically(standardSpec()) + fadeOut(),
        modifier = modifier,
    ) {
        // Held so the row keeps its text through the shrink animation rather than
        // emptying a frame before it goes.
        val shown = remember { mutableStateOf(active) }
        if (active != null) shown.value = active
        shown.value?.let { Bar(it, onOpen) }
    }
}

@Composable
private fun Bar(summary: DownloadSummary, onOpen: () -> Unit) {
    val colors = KamTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = KamTheme.dimens.screenPadding)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceSecondary)
            .clickable(onClick = onOpen)
            // One node, one sentence. Polite so it does not cut across whatever
            // is being read, and it repeats as the percentage moves.
            .semantics {
                contentDescription = summary.spoken
                liveRegion = LiveRegionMode.Polite
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                summary.title,
                style = KamTheme.type.secondary,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            // Facts in the mono face, as everywhere else numbers appear.
            Text(
                summary.detail,
                style = KamTheme.type.mono,
                color = colors.textTertiary,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.border),
        ) {
            // No fill at all while the size is unknown, rather than a bar
            // claiming a position it does not have.
            summary.fraction?.let { f ->
                Box(
                    Modifier
                        .fillMaxWidth(f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.accent),
                )
            }
        }
    }
}

/**
 * What the indicator says, worked out from the download list.
 *
 * Pulled out of the composable so the wording and the arithmetic can be tested
 * without a device, which matters because most of the difficulty here is in what
 * counts as "active" and what to say when several are queued.
 */
data class DownloadSummary(
    val title: String,
    val detail: String,
    /** 0..1, or null when the total size is not known yet. */
    val fraction: Float?,
    /** The whole thing as one sentence, for a screen reader. */
    val spoken: String,
) {
    companion object {

        /**
         * The summary for the current download, or null when there is nothing to
         * show.
         *
         * Paused counts as active: a paused download is still something the user
         * has on the go and can come back to, and hiding it would make the pause
         * look like a cancel. Finished and failed do not: finished has its own
         * quiet confirmation, and a failure belongs where the user can act on it
         * rather than in a bar that follows them around.
         */
        fun active(items: List<Downloads.Item>): DownloadSummary? {
            val running = items.filter {
                it.status == Downloads.Status.RUNNING ||
                    it.status == Downloads.Status.PAUSED ||
                    // Verifying is still work the user is waiting on, and it is
                    // the point where a big file is being hashed, which is slow
                    // enough to look like a hang if nothing says so.
                    it.status == Downloads.Status.VERIFYING
            }
            val current = running.firstOrNull { it.status == Downloads.Status.RUNNING }
                ?: running.firstOrNull { it.status == Downloads.Status.VERIFYING }
                ?: running.firstOrNull()
                ?: return null

            val behind = running.size - 1
            val title = buildString {
                append(current.displayName)
                when (current.status) {
                    Downloads.Status.PAUSED -> append(", paused")
                    Downloads.Status.VERIFYING -> append(", checking")
                    else -> Unit
                }
                // The count, not a list. "and 2 more" is all anybody needs from a
                // line this size, and the detail screen has the rest.
                if (behind > 0) append(if (behind == 1) ", and 1 more" else ", and $behind more")
            }

            val fraction = if (current.totalBytes > 0) current.fraction else null
            val detail = when {
                fraction == null -> "starting"
                else -> buildString {
                    append("${(fraction * 100).toInt()}%  ${remaining(current)}")
                    // How long, when that can be said honestly.
                    //
                    // "1.1 GB left" tells somebody nothing they can act on: it is
                    // two minutes on one connection and forty on another. A phone
                    // on a 2.4 GHz link behind a tunnel measured about 1 MB/s
                    // against the same network giving a desktop 26, and the app
                    // cannot fix that. What it can do is stop the wait being a
                    // mystery, so somebody can decide to go and do something else
                    // rather than watch a bar and conclude the app is broken.
                    eta(current)?.let { append("  ").append(it) }
                }
            }

            return DownloadSummary(
                title = title,
                detail = detail,
                fraction = fraction,
                spoken = spoken(title, fraction, current),
            )
        }

        /**
         * Roughly how long is left, or null when saying would be a guess.
         *
         * Deliberately coarse and deliberately hedged with "about". A precise
         * figure on a wireless link is false precision, and a countdown that
         * jumps around is worse than no countdown: it reads as the app not
         * knowing what it is doing.
         *
         * Null until enough has been transferred to have an average worth
         * quoting, since the first seconds of a download are the least
         * representative part of it.
         */
        private fun eta(item: Downloads.Item): String? {
            // The recent window first, because it describes the connection as it
            // is now. The attempt average is the fallback for the first seconds,
            // before a window has enough in it to be worth quoting.
            val bytesPerMs = item.recentBytesPerMs ?: run {
                val elapsedMs = item.elapsedMs ?: return null
                val done = item.bytesThisAttempt
                if (elapsedMs < 5_000 || done < 4L * 1024 * 1024) return null
                done.toDouble() / elapsedMs
            }
            if (bytesPerMs <= 0.0) return null
            val bytesLeft = (item.totalBytes - item.downloadedBytes).coerceAtLeast(0)
            val leftMs = (bytesLeft / bytesPerMs).toLong()
            val minutes = leftMs / 60_000
            return when {
                minutes < 1 -> "under a minute left"
                minutes < 60 -> "about $minutes min left"
                else -> "over an hour left"
            }
        }

        /** How much is left, in the same units the rest of the app uses. */
        private fun remaining(item: Downloads.Item): String {
            val left = (item.totalBytes - item.downloadedBytes).coerceAtLeast(0)
            return "${formatBytes(left)} left"
        }

        private fun spoken(title: String, fraction: Float?, item: Downloads.Item): String =
            if (fraction == null) {
                "Downloading $title, starting"
            } else {
                "Downloading $title, ${(fraction * 100).toInt()} percent, " +
                    "${formatBytes((item.totalBytes - item.downloadedBytes).coerceAtLeast(0))} left"
            }

        /** One decimal place above a gigabyte, none below, matching the catalog. */
        fun formatBytes(bytes: Long): String {
            val gb = bytes / 1_000_000_000.0
            if (gb >= 1.0) return "${"%.1f".format(gb)} GB"
            val mb = bytes / 1_000_000.0
            return "${mb.toInt()} MB"
        }
    }
}
