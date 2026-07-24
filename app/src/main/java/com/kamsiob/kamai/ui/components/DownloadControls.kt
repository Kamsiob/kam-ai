package com.kamsiob.kamai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.download.Downloads
import com.kamsiob.kamai.ui.theme.KamTheme

/**
 * The download row for one model, voice, or pack: a progress bar and the controls
 * that match its state, so a download can be paused, resumed, or cancelled, and a
 * failure retried. Used by every download screen so they behave identically.
 */
@Composable
fun DownloadControls(
    item: Downloads.Item,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        val running = item.status == Downloads.Status.RUNNING || item.status == Downloads.Status.VERIFYING
        Box(
            Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(colors.surfaceSecondary),
        ) {
            Box(
                Modifier.fillMaxWidth(item.fraction).height(7.dp).clip(CircleShape).background(colors.accent),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (item.status) {
                    Downloads.Status.RUNNING -> "${(item.fraction * 100).toInt()}% downloaded"
                    Downloads.Status.VERIFYING -> "Checking the file"
                    Downloads.Status.PAUSED -> "Paused at ${(item.fraction * 100).toInt()}%"
                    Downloads.Status.FAILED -> item.message ?: "Download failed"
                    Downloads.Status.DONE -> "Done"
                },
                style = KamTheme.type.mono,
                color = if (item.status == Downloads.Status.FAILED) colors.goldText else colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            when (item.status) {
                Downloads.Status.RUNNING, Downloads.Status.VERIFYING ->
                    TextActionButton("Pause", onClick = onPause)
                Downloads.Status.PAUSED ->
                    TextActionButton("Resume", onClick = onResume)
                Downloads.Status.FAILED ->
                    TextActionButton("Retry", onClick = onResume)
                Downloads.Status.DONE -> Unit
            }
            if (running || item.status == Downloads.Status.PAUSED || item.status == Downloads.Status.FAILED) {
                Spacer(Modifier.width(4.dp))
                TextActionButton("Cancel", onClick = onCancel)
            }
        }
    }
}
