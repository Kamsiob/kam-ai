package com.kamsiob.kamai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.download.Downloader
import com.kamsiob.kamai.ui.components.Eyebrow
import com.kamsiob.kamai.ui.components.KamChip
import com.kamsiob.kamai.ui.components.PrimaryButton
import com.kamsiob.kamai.ui.components.SecondaryButton
import com.kamsiob.kamai.ui.theme.KamTheme
import com.kamsiob.kamai.voice.SttModel

/**
 * The Voice screen. Speech to text now; text to speech joins it here. Sets honest
 * expectations rather than overselling: these are good on-device models that run
 * with no network and no account, and voice typing is the flagship of the two.
 */
@Composable
fun VoiceScreen(
    sttModels: List<SttModel>,
    installedSttIds: Set<String>,
    activeSttId: String?,
    recommendedSttId: String,
    download: Downloader.Progress?,
    downloadingSttId: String?,
    onDownloadStt: (SttModel) -> Unit,
    onActivateStt: (SttModel) -> Unit,
    ttsVoices: List<com.kamsiob.kamai.voice.TtsVoice> = emptyList(),
    installedTtsIds: Set<String> = emptySet(),
    activeTtsId: String? = null,
    downloadingTtsId: String? = null,
    onDownloadTts: (com.kamsiob.kamai.voice.TtsVoice) -> Unit = {},
    onActivateTts: (com.kamsiob.kamai.voice.TtsVoice) -> Unit = {},
    onPreviewTts: (com.kamsiob.kamai.voice.TtsVoice) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KamTheme.dimens.screenPadding),
    ) {
        Text("Voice", style = KamTheme.type.screenTitle, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Talk instead of typing. Kam AI turns your voice into text on the phone " +
                "itself, with no network and no account, then you can ask it to tidy the " +
                "ramble into notes or a draft.",
            style = KamTheme.type.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(18.dp))

        Eyebrow("Voice typing")
        Spacer(Modifier.height(10.dp))

        sttModels.forEach { model ->
            SttCard(
                model = model,
                installed = model.id in installedSttIds,
                active = model.id == activeSttId,
                recommended = model.id == recommendedSttId,
                progress = (download as? Downloader.Progress.Running)
                    ?.takeIf { downloadingSttId == model.id }?.fraction,
                onDownload = { onDownloadStt(model) },
                onActivate = { onActivateStt(model) },
            )
            Spacer(Modifier.height(11.dp))
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "These are good on-device voice models. They are not quite as sharp as the " +
                "big cloud services, and that is the trade for everything staying on your " +
                "phone. The better model is more accurate in noise but larger and slower.",
            style = KamTheme.type.secondary,
            color = colors.textTertiary,
        )

        // Reading voice: text to speech, so answers can be read aloud.
        Spacer(Modifier.height(28.dp))
        Eyebrow("Reading voice")
        Spacer(Modifier.height(4.dp))
        Text(
            "Have answers read aloud. Pick a voice and tap play under any reply. These " +
                "run on the phone too, and sit below the big cloud voices in polish.",
            style = KamTheme.type.secondary,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(12.dp))

        ttsVoices.forEach { voice ->
            TtsCard(
                voice = voice,
                installed = voice.id in installedTtsIds,
                active = voice.id == activeTtsId,
                progress = (download as? Downloader.Progress.Running)
                    ?.takeIf { downloadingTtsId == voice.id }?.fraction,
                onDownload = { onDownloadTts(voice) },
                onActivate = { onActivateTts(voice) },
                onPreview = { onPreviewTts(voice) },
            )
            Spacer(Modifier.height(11.dp))
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun TtsCard(
    voice: com.kamsiob.kamai.voice.TtsVoice,
    installed: Boolean,
    active: Boolean,
    progress: Float?,
    onDownload: () -> Unit,
    onActivate: () -> Unit,
    onPreview: () -> Unit,
) {
    val colors = KamTheme.colors
    val shape = RoundedCornerShape(KamTheme.dimens.cardRadius)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (active) colors.tonalFill else colors.surface)
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) colors.accent else colors.border,
                shape = shape,
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(voice.displayName, style = KamTheme.type.cardTitle, color = colors.textPrimary)
            Spacer(Modifier.width(8.dp))
            if (active) KamChip("In use", tonal = true)
            Spacer(Modifier.weight(1f))
            Text(voice.downloadLabel, style = KamTheme.type.mono, color = colors.textSecondary)
        }

        Spacer(Modifier.height(5.dp))
        Text(
            "${voice.gender}. ${voice.description}",
            style = KamTheme.type.secondary,
            color = colors.textTertiary,
        )

        Spacer(Modifier.height(13.dp))
        when {
            progress != null -> {
                Box(
                    Modifier.fillMaxWidth().height(7.dp).clip(CircleShape)
                        .background(colors.surfaceSecondary),
                ) {
                    Box(
                        Modifier.fillMaxWidth(progress).height(7.dp).clip(CircleShape)
                            .background(colors.accent),
                    )
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    "${(progress * 100).toInt()}% downloaded",
                    style = KamTheme.type.mono,
                    color = colors.textSecondary,
                )
            }

            installed -> Row {
                if (!active) {
                    SecondaryButton("Use this one", onClick = onActivate, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                }
                SecondaryButton("Preview", onClick = onPreview, modifier = Modifier.weight(1f))
            }

            else -> PrimaryButton(
                "Download ${voice.downloadLabel}",
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SttCard(
    model: SttModel,
    installed: Boolean,
    active: Boolean,
    recommended: Boolean,
    progress: Float?,
    onDownload: () -> Unit,
    onActivate: () -> Unit,
) {
    val colors = KamTheme.colors
    val shape = RoundedCornerShape(KamTheme.dimens.cardRadius)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (active) colors.tonalFill else colors.surface)
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) colors.accent else colors.border,
                shape = shape,
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(model.displayName, style = KamTheme.type.cardTitle, color = colors.textPrimary)
            Spacer(Modifier.width(8.dp))
            when {
                active -> KamChip("In use", tonal = true)
                recommended -> KamChip("Recommended", tonal = true)
            }
            Spacer(Modifier.weight(1f))
            Text(model.downloadLabel, style = KamTheme.type.mono, color = colors.textSecondary)
        }

        Spacer(Modifier.height(5.dp))
        Text(model.description, style = KamTheme.type.secondary, color = colors.textTertiary)

        Spacer(Modifier.height(13.dp))
        when {
            progress != null -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceSecondary),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(7.dp)
                            .clip(CircleShape)
                            .background(colors.accent),
                    )
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    "${(progress * 100).toInt()}% downloaded",
                    style = KamTheme.type.mono,
                    color = colors.textSecondary,
                )
            }

            active -> Unit

            installed -> SecondaryButton(
                "Use this one",
                onClick = onActivate,
                modifier = Modifier.fillMaxWidth(),
            )

            else -> PrimaryButton(
                "Download ${model.downloadLabel}",
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
