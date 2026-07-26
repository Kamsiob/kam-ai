package com.kamsiob.kamai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.ui.components.PrimaryButton
import com.kamsiob.kamai.ui.components.TextActionButton
import com.kamsiob.kamai.ui.theme.KamTheme

/**
 * What the chat shows when there is no model to answer with (#80).
 *
 * Before this, a new user who skipped setup reached the chat, typed, and got
 * "No model is set up yet. Download one in Settings to start." That is a dead
 * end dressed as a sentence: it names the problem, points at another screen, and
 * leaves them to find the right one. The screen that needs the model is the
 * screen that should offer it.
 *
 * So the empty state carries the offer itself, with the same three facts the
 * onboarding slide gives (what it is, what it costs in megabytes, what it means
 * for this phone) and one action that starts it. The path back that #75 asks
 * for is not a path at all; the thing is already here.
 *
 * Nothing about this is urgent or apologetic. It is a setup step that has not
 * happened yet, stated plainly.
 */
@Composable
fun ModelSetupCard(
    /** The recommended model's display name, or null when none fits this phone. */
    modelName: String?,
    /** Its real download size, already formatted. */
    downloadLabel: String?,
    /** One honest line about what this model means for this device. */
    explanation: String,
    onDownload: () -> Unit,
    onSeeOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors

    Column(
        modifier = modifier
            // Padding before the width cap, so the card can never touch the
            // screen edges on a narrow phone.
            .padding(horizontal = 20.dp)
            .widthIn(max = 380.dp)
            .clip(RoundedCornerShape(KamTheme.dimens.cardRadius))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(KamTheme.dimens.cardRadius))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Kam AI needs a model to answer",
            style = KamTheme.type.cardTitle,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            explanation,
            style = KamTheme.type.secondary,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )

        if (modelName != null && downloadLabel != null) {
            Spacer(Modifier.height(16.dp))
            // Name and size together, the size in the mono face because it is a
            // fact rather than a phrase.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(modelName, style = KamTheme.type.bodyEmphasis, color = colors.textPrimary)
                Text(downloadLabel, style = KamTheme.type.mono, color = colors.textTertiary)
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                "Download it",
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            // Not a dismissal, and not styled as one. Somebody who wants to weigh
            // the sizes themselves is being sensible, not awkward.
            TextActionButton("See the other options", onClick = onSeeOptions)
        } else {
            // No tier fits. Saying so beats offering a download that cannot work.
            Spacer(Modifier.height(16.dp))
            TextActionButton("See what is available", onClick = onSeeOptions)
        }
    }
}

/**
 * The compact line shown while the model is downloading and the user is already
 * in the chat (#78).
 *
 * The spec's rule is that nothing processes silently and anything slow is
 * cancellable, so this carries the progress and its own cancel. It is deliberately
 * one line rather than a card: the download is background work, and the chat is
 * the foreground.
 */
@Composable
fun ModelDownloadStrip(
    /** 0..1, or null while the size is still unknown. */
    progress: Float?,
    label: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceSecondary)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = KamTheme.type.secondary,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            TextActionButton("Cancel", onClick = onCancel)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.border),
        ) {
            // An indeterminate download shows a full-width track and no fill,
            // rather than a bar pretending to a position it does not have.
            if (progress != null) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.accent),
                )
            }
        }
    }
}

/**
 * Everything the chat needs to offer a model, gathered so the screen takes one
 * nullable rather than five parameters that only mean anything together (#80).
 */
data class ModelSetupOffer(
    val modelName: String?,
    val downloadLabel: String?,
    val explanation: String,
    val onDownload: () -> Unit,
    val onSeeOptions: () -> Unit,
)

/** A model download in flight, for the strip in the chat (#78). */
data class ModelDownloadState(
    val progress: Float?,
    val label: String,
    val onCancel: () -> Unit,
)
