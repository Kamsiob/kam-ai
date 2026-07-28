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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.download.Downloader
import com.kamsiob.kamai.llm.MeasuredSpeed
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
    downloads: List<com.kamsiob.kamai.download.Downloads.Item>,
    onDownload: (TierModel) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onActivate: (TierModel) -> Unit,
    /** What this phone has measured for each model, by id (item 22). */
    measuredSpeeds: Map<String, String?> = emptyMap(),
    /**
     * What is free where models land, so a model that cannot fit says so on the
     * card rather than after it is chosen (#75).
     */
    freeBytes: Long = Long.MAX_VALUE,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    val recommended = TierRecommendation.recommended(totalRamGb)
    var advancedOpen by remember { mutableStateOf(false) }

    // Decoded once for the whole screen so every card can place itself against
    // what this phone has actually done, including models it has never run.
    val decodedSpeeds = remember(measuredSpeeds) {
        measuredSpeeds.mapNotNull { (id, stored) ->
            MeasuredSpeed.decode(stored)?.let { (tps, _) -> id to tps }
        }.toMap()
    }

    fun dl(id: String) = downloads.firstOrNull { it.id == id }

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
            ModelCard(
                model = model,
                locked = TierRecommendation.isLocked(model.tier, totalRamGb),
                fitsOnDisk = com.kamsiob.kamai.download.DownloadGuard
                    .fitsOnDisk(model.downloadBytes, freeBytes),
                installed = model.id in installedIds,
                active = model.id == activeId,
                recommended = model.tier == recommended,
                download = dl(model.id),
                onDownload = { onDownload(model) },
                onPause = { onPause(model.id) },
                onResume = { onResume(model.id) },
                onCancel = { onCancel(model.id) },
                onActivate = { onActivate(model) },
                measuredSpeed = measuredSpeeds[model.id],
                allMeasured = decodedSpeeds,
            )
            Spacer(Modifier.height(11.dp))
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Switching models keeps every conversation. Only the thing answering " +
                "them changes. You can download more than one at a time.",
            style = KamTheme.type.secondary,
            color = colors.textTertiary,
        )

        // Advanced: collapsed by default so it stays out of the way. Other models
        // to try, including Qwen, for people who want to reason about it themselves.
        if (advanced.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { advancedOpen = !advancedOpen }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.kamsiob.kamai.ui.components.Eyebrow("Advanced")
                Spacer(Modifier.weight(1f))
                Text(
                    if (advancedOpen) "Hide" else "${advanced.size} more",
                    style = KamTheme.type.secondary,
                    color = colors.textTertiary,
                )
            }
            if (advancedOpen) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Other models you can try, including Qwen. Bigger or heavier ones need " +
                        "more room. Keep several and switch any time.",
                    style = KamTheme.type.secondary,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(12.dp))
                advanced.forEach { model ->
                    ModelCard(
                        model = model,
                        locked = TierRecommendation.isLocked(model.tier, totalRamGb),
                        fitsOnDisk = com.kamsiob.kamai.download.DownloadGuard
                            .fitsOnDisk(model.downloadBytes, freeBytes),
                        installed = model.id in installedIds,
                        active = model.id == activeId,
                        recommended = false,
                        download = dl(model.id),
                        onDownload = { onDownload(model) },
                        onPause = { onPause(model.id) },
                        onResume = { onResume(model.id) },
                        onCancel = { onCancel(model.id) },
                        onActivate = { onActivate(model) },
                        measuredSpeed = measuredSpeeds[model.id],
                        allMeasured = decodedSpeeds,
                        advanced = true,
                    )
                    Spacer(Modifier.height(9.dp))
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ModelCard(
    model: TierModel,
    locked: Boolean,
    /** False when the download cannot land here, headroom included. */
    fitsOnDisk: Boolean = true,
    installed: Boolean,
    active: Boolean,
    recommended: Boolean,
    download: com.kamsiob.kamai.download.Downloads.Item?,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onActivate: () -> Unit,
    /** This phone's stored measurement for the model, if it has one. */
    measuredSpeed: String? = null,
    /**
     * Every measurement this phone holds, keyed by model id, so a model that has
     * never run here can still be placed. That is the case the picker exists for:
     * nobody is choosing between models they have already used.
     */
    allMeasured: Map<String, Double> = emptyMap(),
    advanced: Boolean = false,
) {
    val colors = KamTheme.colors
    val shape = RoundedCornerShape(KamTheme.dimens.cardRadius)
    var showRatingHelp by remember { mutableStateOf(false) }

    if (showRatingHelp) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showRatingHelp = false }) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(20.dp)).padding(20.dp),
            ) {
                Text("What the stars mean", style = KamTheme.type.cardTitle, color = colors.textPrimary)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Speed is how quickly this model writes, measured on this phone " +
                        "rather than quoted from someone else's. Until you have run a " +
                        "model here there is nothing to measure, so nothing is shown. " +
                        "Once one has run, the others are estimated from it and say so.",
                    style = KamTheme.type.body, color = colors.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Quality compares the models offered here with each other, not with " +
                        "anything else. Five stars means the best of these, not the best " +
                        "there is.",
                    style = KamTheme.type.body, color = colors.textSecondary,
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        "Got it", style = KamTheme.type.label, color = colors.accent,
                        modifier = Modifier.clip(CircleShape).clickable { showRatingHelp = false }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }

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
                    // Color never carries meaning alone.
                    if (locked) append(", locked, ").append(TierRecommendation.lockedNote(model.tier))
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Advanced cards name the model itself; the tier cards name the tier.
            Text(
                if (advanced) model.displayName else model.tier.displayName,
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
                if (advanced) "${model.parameterLabel}  ${model.quantisation}"
                else "${model.displayName}  ${model.quantisation}",
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

        // What this model can do, before downloading. Each chip explains itself
        // on tap, and unsupported abilities are shown muted rather than hidden so
        // models can be compared at a glance (item 22).
        Spacer(Modifier.height(8.dp))
        CapabilityRow(model)

        // Speed and quality in the same units for every model on offer, before
        // the download rather than after it, since after is too late to be of any
        // use. Speed is silent until this phone has measured something, because a
        // figure from another phone is worthless and an invented one is worse.
        val speedStars = com.kamsiob.kamai.model.ModelRatings.speedStars(model, allMeasured)
        val estimated = com.kamsiob.kamai.model.ModelRatings.isEstimated(allMeasured[model.id])
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.clickable { showRatingHelp = true }) {
            if (speedStars != null) {
                Text(
                    "Speed     ${com.kamsiob.kamai.model.ModelRatings.bar(speedStars)}" +
                        if (estimated) "  estimated" else "  on this phone",
                    style = KamTheme.type.secondary,
                    color = colors.textSecondary,
                )
            }
            Text(
                "Quality   ${com.kamsiob.kamai.model.ModelRatings.bar(
                    com.kamsiob.kamai.model.ModelRatings.quality(model),
                )}  against the models here",
                style = KamTheme.type.secondary,
                color = colors.textSecondary,
            )
        }

        // One plain line where this model is weaker at something, and nothing at
        // all where it is not. A caveat manufactured for every model teaches
        // people to skip all of them.
        if (model.modeNote.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(model.modeNote, style = KamTheme.type.secondary, color = colors.goldText)
        }

        // Measured here, on this phone, from generations the user has actually
        // run. Nothing is claimed for a model nobody has used, and nothing is
        // claimed from a single run (item 22).
        MeasuredSpeed.describe(measuredSpeed)?.let { line ->
            Spacer(Modifier.height(6.dp))
            Text(line, style = KamTheme.type.secondary, color = colors.textSecondary)
        }
        // Every advanced model states plainly whether it is likely to run here.
        if (advanced) {
            Spacer(Modifier.height(6.dp))
            when {
                locked -> Text(
                    "Unlikely to run on this phone. It needs more memory than this phone has.",
                    style = KamTheme.type.secondary, color = colors.goldText,
                )
                model.warning.isNotEmpty() -> Text(
                    model.warning, style = KamTheme.type.secondary, color = colors.goldText,
                )
                else -> Text(
                    "Should run on this phone.",
                    style = KamTheme.type.secondary, color = colors.textTertiary,
                )
            }
        }

        // Said here, on the card, rather than at download time. Offering
        // something and refusing it a tap later is the defect #75 names.
        if (!fitsOnDisk && !locked) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Not enough room on this phone for this one. Free up some space, " +
                    "or choose a smaller model.",
                style = KamTheme.type.secondary,
                color = colors.goldText,
                fontWeight = FontWeight.W600,
            )
            return@Column
        }

        if (locked) {
            Spacer(Modifier.height(10.dp))
            Text(
                "${TierRecommendation.lockedNote(model.tier)} of memory. This phone does " +
                    "not have enough for this one.",
                style = KamTheme.type.secondary,
                color = colors.goldText,
                fontWeight = FontWeight.W600,
            )
            return@Column
        }

        Spacer(Modifier.height(13.dp))
        when {
            download != null && download.status != com.kamsiob.kamai.download.Downloads.Status.DONE ->
                com.kamsiob.kamai.ui.components.DownloadControls(
                    item = download,
                    onPause = onPause,
                    onResume = onResume,
                    onCancel = onCancel,
                )

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

/**
 * The capability chips for a model (item 22): Text, Documents, Images. Supported
 * ones are solid, unsupported are muted, and tapping any chip explains what it
 * means in plain words. Labels carry the meaning, never color alone, and every
 * chip has a screen-reader description.
 */
@Composable
private fun CapabilityRow(model: TierModel) {
    val colors = KamTheme.colors
    var explaining by remember { mutableStateOf<com.kamsiob.kamai.model.Capability?>(null) }
    val shown = listOf(
        com.kamsiob.kamai.model.Capability.TEXT,
        com.kamsiob.kamai.model.Capability.DOCUMENTS,
        com.kamsiob.kamai.model.Capability.IMAGES,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        shown.forEach { capability ->
            val on = model.supports(capability)
            Text(
                if (on) capability.label else "No ${capability.label.lowercase()}",
                style = KamTheme.type.secondary,
                color = if (on) colors.tonalText else colors.textTertiary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) colors.tonalFill else colors.surfaceSecondary)
                    .clickable { explaining = capability }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
                    .semantics {
                        contentDescription =
                            (if (on) "Supports " else "Does not support ") + capability.label +
                                ". Tap to explain."
                    },
            )
        }
    }

    explaining?.let { cap ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { explaining = null }) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(20.dp)).padding(20.dp),
            ) {
                Text(cap.label, style = KamTheme.type.cardTitle, color = colors.textPrimary)
                Spacer(Modifier.height(8.dp))
                Text(cap.explanation, style = KamTheme.type.body, color = colors.textSecondary)
                if (!model.supports(cap)) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This model does not do this.",
                        style = KamTheme.type.secondary, color = colors.textTertiary,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        "Got it", style = KamTheme.type.label, color = colors.accent,
                        modifier = Modifier.clip(CircleShape).clickable { explaining = null }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}
