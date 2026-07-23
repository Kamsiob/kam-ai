package com.kamsiob.kamai.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.data.QuizStatsEntity
import com.kamsiob.kamai.data.SavedMomentEntity
import com.kamsiob.kamai.discover.Moment
import com.kamsiob.kamai.ui.components.Eyebrow
import com.kamsiob.kamai.ui.components.PrimaryButton
import com.kamsiob.kamai.ui.components.SecondaryButton
import com.kamsiob.kamai.ui.theme.KamTheme

@Composable
fun DiscoverScreen(
    current: Moment?,
    exhausted: Boolean,
    hasPacks: Boolean,
    currentSaved: Boolean,
    saved: List<SavedMomentEntity>,
    stats: List<QuizStatsEntity>,
    installedCount: Int,
    onDeal: () -> Unit,
    onOpenReader: () -> Unit,
    onToggleSave: () -> Unit,
    onQuiz: () -> Unit,
    onReshuffle: () -> Unit,
    onOpenPacks: () -> Unit,
    onOpenSaved: (SavedMomentEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KamTheme.dimens.screenPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Discover", style = KamTheme.type.screenTitle, color = colors.textPrimary)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.surfaceSecondary)
                    .clickable(onClick = onOpenPacks)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(
                    if (installedCount > 0) "Packs · $installedCount" else "Packs",
                    style = KamTheme.type.label,
                    color = colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        // A quiet running tally, no pressure mechanics.
        val totalQ = stats.sumOf { it.questionsAsked }
        if (totalQ > 0) {
            val right = stats.sumOf { it.questionsRight }
            val moments = stats.sumOf { it.momentsQuizzed }
            Text(
                "$moments quizzed, $right of $totalQ right",
                style = KamTheme.type.secondary,
                color = colors.textTertiary,
            )
        }
        Spacer(Modifier.height(14.dp))

        when {
            !hasPacks -> EmptyPacks(onOpenPacks)
            exhausted -> Exhausted(onReshuffle)
            current == null -> Box(Modifier.fillMaxWidth().height(200.dp))
            else -> MomentCard(
                moment = current,
                saved = currentSaved,
                onOpenReader = onOpenReader,
                onToggleSave = onToggleSave,
                onQuiz = onQuiz,
                onDeal = onDeal,
            )
        }

        if (saved.isNotEmpty()) {
            Spacer(Modifier.height(30.dp))
            Eyebrow("Saved")
            Spacer(Modifier.height(8.dp))
            saved.forEach { s ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpenSaved(s) }
                        .padding(vertical = 10.dp),
                ) {
                    Text(s.title, style = KamTheme.type.body, color = colors.textPrimary)
                    Text(s.topic.uppercase(), style = KamTheme.type.mono, color = colors.textTertiary)
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun MomentCard(
    moment: Moment,
    saved: Boolean,
    onOpenReader: () -> Unit,
    onToggleSave: () -> Unit,
    onQuiz: () -> Unit,
    onDeal: () -> Unit,
) {
    val colors = KamTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KamTheme.dimens.cardRadius))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(KamTheme.dimens.cardRadius))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                "${moment.topic.uppercase()} · DEALT AT RANDOM",
                style = KamTheme.type.mono,
                color = colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleSave)
                    .semantics { contentDescription = if (saved) "Saved" else "Save" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    contentDescription = null,
                    tint = if (saved) colors.accent else colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(moment.title, style = KamTheme.type.sectionTitle, color = colors.textPrimary)
        Spacer(Modifier.height(12.dp))
        Text(moment.preview, style = KamTheme.type.body, color = colors.textPrimary)
        Spacer(Modifier.height(14.dp))
        Text(
            "From Wikipedia, ${moment.license}",
            style = KamTheme.type.secondary,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Read the full moment",
            style = KamTheme.type.label,
            color = colors.accent,
            modifier = Modifier.clickable(onClick = onOpenReader).padding(vertical = 4.dp),
        )
        Spacer(Modifier.height(14.dp))
        Row {
            SecondaryButton("Quiz me", onClick = onQuiz, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            PrimaryButton("Deal another", onClick = onDeal, modifier = Modifier.weight(1.3f))
        }
    }
}

@Composable
private fun EmptyPacks(onOpenPacks: () -> Unit) {
    val colors = KamTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nothing to read yet", style = KamTheme.type.sectionTitle, color = colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Discover deals you something interesting to read and talk about, from offline " +
                "packs built from Wikipedia. Get one to start.",
            style = KamTheme.type.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Get packs", onClick = onOpenPacks)
    }
}

@Composable
private fun Exhausted(onReshuffle: () -> Unit) {
    val colors = KamTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("You've seen them all", style = KamTheme.type.sectionTitle, color = colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "That's every moment in your packs. Reshuffle to start over, or get another pack.",
            style = KamTheme.type.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Reshuffle", onClick = onReshuffle)
    }
}
