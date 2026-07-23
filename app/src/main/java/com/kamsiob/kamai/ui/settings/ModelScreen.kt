package com.kamsiob.kamai.ui.settings

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.download.Downloader
import com.kamsiob.kamai.model.TierModel
import com.kamsiob.kamai.model.TierRecommendation
import com.kamsiob.kamai.ui.components.KamChip
import com.kamsiob.kamai.ui.components.PrimaryButton
import com.kamsiob.kamai.ui.components.SecondaryButton
import com.kamsiob.kamai.ui.theme.KamTheme

/**
 * The Model screen. Same tier cards as onboarding slide 4, reachable any time
 * from Settings so a person can change their mind.
 */
@Composable
fun ModelScreen(
    totalRamGb: Int,
    tiers: List<TierModel>,
    advanced: List<TierModel> = emptyList(),
    installedIds: Set<String>,
    activeId: String?,
    download: Downloader.Progress?,
    onDownload: (TierModel) -> Unit,
    onActivate: (TierModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    val recommended = TierRecommendation.recommended(totalRamGb)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KamTheme.dimens.screenPadding),
    ) {
        Text("Model", style = KamTheme.type.screenTitle, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            TierRecommendation.explain(totalRamGb),
            style = KamTheme.type.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(18.dp))

        tiers.forEach { model ->
            val locked = TierRecommendation.isLocked(model.tier, totalRamGb)
            val installed = model.id in installedIds
            val active = model.id == activeId
            val downloading = download is Downloader.Progress.Running &&
                !installed && model.tier == recommended

            ModelCard(
                model = model,
                locked = locked,
                installed = installed,
                active = active,
                recommended = model.tier == recommended,
                progress = (download as? Downloader.Progress.Running)
                    ?.takeIf { downloading }?.fraction,
                onDownload = { onDownload(model) },
                onActivate = { onActivate(model) },
            )
            Spacer(Modifier.height(11.dp))
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Switching models keeps every conversation. Only the thing answering " +
                "them changes.",
            style = KamTheme.type.secondary,
            color = colors.textTertiary,
        )

        // Advanced: other compatible models, for people who want to reason about
        // this themselves. Nothing here is required reading. PART 2.
        if (advanced.isNotEmpty()) {
            Spacer(Modifier.height(26.dp))
            com.kamsiob.kamai.ui.components.Eyebrow("Advanced")
            Spacer(Modifier.height(4.dp))
            Text(
                "Other models you can try. Bigger or heavier ones need more room. You " +
                    "can keep several and switch any time.",
                style = KamTheme.type.secondary,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(12.dp))
            advanced.forEach { model ->
                ModelCard(
                    model = model,
                    locked = TierRecommendation.isLocked(model.tier, totalRamGb),
                    installed = model.id in installedIds,
                    active = model.id == activeId,
                    recommended = false,
                    progress = (download as? Downloader.Progress.Running)
                        ?.takeIf { installedIds.none { id -> id == model.id } && false }?.fraction,
                    onDownload = { onDownload(model) },
                    onActivate = { onActivate(model) },
                    advanced = true,
                )
                Spacer(Modifier.height(11.dp))
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ModelCard(
    model: TierModel,
    locked: Boolean,
    installed: Boolean,
    active: Boolean,
    recommended: Boolean,
    progress: Float?,
    onDownload: () -> Unit,
    onActivate: () -> Unit,
    advanced: Boolean = false,
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
            .padding(16.dp)
            .semantics {
                contentDescription = buildString {
                    append(model.tier.displayName).append(", ").append(model.displayName)
                    append(", ").append(model.downloadLabel)
                    if (active) append(", in use")
                    if (recommended) append(", recommended")
                    // Colour never carries meaning alone.
                    if (locked) append(", locked, ").append(TierRecommendation.lockedNote(model.tier))
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                model.tier.displayName,
                style = KamTheme.type.cardTitle,
                color = if (locked) colors.textTertiary else colors.textPrimary,
            )
            Spacer(Modifier.width(8.dp))
            when {
                active -> KamChip("In use", tonal = true)
                recommended && !locked -> KamChip("Recommended", tonal = true)
            }
            Spacer(Modifier.weight(1f))
            Text(
                model.downloadLabel,
                style = KamTheme.type.mono,
                color = if (locked) colors.textTertiary else colors.textSecondary,
            )
        }

        Spacer(Modifier.height(5.dp))
        Row {
            Text(
                "${model.displayName}  ${model.quantisation}",
                style = KamTheme.type.secondary,
                color = colors.textTertiary,
            )
            Spacer(Modifier.weight(1f))
            Text(model.licence, style = KamTheme.type.mono, color = colors.textTertiary)
        }
        if (advanced && model.description.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(model.description, style = KamTheme.type.secondary, color = colors.textSecondary)
        }
        if (advanced && model.warning.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(model.warning, style = KamTheme.type.secondary, color = colors.flagAmber)
        }

        if (locked) {
            Spacer(Modifier.height(10.dp))
            Text(
                "${TierRecommendation.lockedNote(model.tier)} of memory. This phone does " +
                    "not have enough for this one.",
                style = KamTheme.type.secondary,
                color = colors.flagAmber,
                fontWeight = FontWeight.W600,
            )
            return@Column
        }

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
