package com.kamsiob.kamai.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.model.Tier
import com.kamsiob.kamai.model.TierModel
import com.kamsiob.kamai.model.TierRecommendation
import com.kamsiob.kamai.ui.components.Eyebrow
import com.kamsiob.kamai.ui.components.KamChip
import com.kamsiob.kamai.ui.components.KamMark
import com.kamsiob.kamai.ui.components.PrimaryButton
import com.kamsiob.kamai.ui.components.SecondaryButton
import com.kamsiob.kamai.ui.components.TextActionButton
import com.kamsiob.kamai.ui.theme.KamMotion
import com.kamsiob.kamai.ui.theme.KamTheme
import com.kamsiob.kamai.ui.theme.expressiveSpec
import com.kamsiob.kamai.ui.theme.reducedMotion
import com.kamsiob.kamai.ui.theme.standardSpec
import kotlinx.coroutines.launch

/**
 * The five onboarding slides. Fully opaque themed background, never transparent
 * over the app. Swipeable, with tappable dots that stretch when active, and a
 * quiet skip link that disappears on the last slide.
 */
@Composable
fun OnboardingScreen(
    totalRamGb: Int,
    tiers: List<TierModel>,
    downloadProgress: Float?,
    onDownload: (TierModel) -> Unit,
    /** What is free where models land, so a tier that cannot fit says so (#75). */
    freeBytes: Long = Long.MAX_VALUE,
    /** The recommended speech setup for this phone, with its combined size (#77). */
    voiceLabel: String = "",
    /** True once it has been queued, so the card stops offering it again. */
    voiceQueued: Boolean = false,
    onAddVoice: () -> Unit = {},
    onFinish: () -> Unit,
    onSupport: () -> Unit,
    /** Where to resume, so leaving does not send the user back to slide one (#117). */
    startSlide: Int = 0,
    onSlideChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    val pager = rememberPagerState(
        initialPage = startSlide.coerceIn(0, OnboardingCopy.SLIDE_COUNT - 1),
        pageCount = { OnboardingCopy.SLIDE_COUNT },
    )
    // Recorded as each slide settles rather than on the way out. Back from
    // onboarding leaves the app outright, so there is no exit path to hook: by
    // the time anything would run, the process may already be gone.
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { onSlideChanged(it) }
    }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding(),
    ) {
        // Skip vanishes on the last slide.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            AnimatedVisibility(
                visible = pager.currentPage < OnboardingCopy.SLIDE_COUNT - 1,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                TextActionButton(OnboardingCopy.SKIP, onClick = onFinish)
            }
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
            pageSpacing = 0.dp,
        ) { page ->
            val advance: () -> Unit = {
                scope.launch { pager.animateScrollToPage(page + 1) }
            }
            when (page) {
                0 -> SlideOne(advance)
                1 -> SlideTwo(advance)
                2 -> SlideThree(advance)
                3 -> SlideExtras(advance)
                4 -> SlideFour(
                    totalRamGb = totalRamGb,
                    tiers = tiers,
                    downloadProgress = downloadProgress,
                    freeBytes = freeBytes,
                    onDownload = onDownload,
                    onContinue = advance,
                )
                5 -> SlideOptional(
                    voiceLabel = voiceLabel,
                    voiceQueued = voiceQueued,
                    onAddVoice = onAddVoice,
                    onContinue = advance,
                )
                6 -> SlideFive(onSupport = onSupport, onFinish = onFinish)
            }
        }

        Dots(
            count = OnboardingCopy.SLIDE_COUNT,
            current = pager.currentPage,
            onTap = { scope.launch { pager.animateScrollToPage(it) } },
            modifier = Modifier.padding(bottom = 18.dp),
        )
    }
}

/** Progress dots that stretch when active and are tappable. */
@Composable
private fun Dots(
    count: Int,
    current: Int,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == current
            val width by animateDpAsState(
                targetValue = if (active) 22.dp else 7.dp,
                animationSpec = expressiveSpec(),
                label = "dot",
            )
            Box(
                modifier = Modifier
                    // The dot itself stays small, but the tap area meets the
                    // minimum touch target.
                    .size(width = 34.dp, height = KamTheme.dimens.minTouchTarget)
                    .clickable(onClick = { onTap(index) })
                    .semantics { contentDescription = "Slide ${index + 1} of $count" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(width)
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(if (active) colors.accent else colors.border),
                )
            }
        }
    }
}

/** Content staggers in per slide: header first, then items at 40ms increments. */
@Composable
private fun Staggered(
    index: Int,
    content: @Composable () -> Unit,
) {
    val reduced = reducedMotion()
    var visible by remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            kotlinx.coroutines.delay(index * KamMotion.STAGGER_MS.toLong())
            visible = true
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(KamMotion.MEDIUM_MS)) +
            slideInVertically(standardSpec<IntOffset>()) { it / 6 },
    ) {
        content()
    }
}

@Composable
private fun SlideScaffold(
    eyebrow: String,
    title: String,
    hero: (@Composable () -> Unit)? = null,
    body: @Composable () -> Unit,
    footer: @Composable () -> Unit,
) {
    val colors = KamTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (hero != null) {
                Spacer(Modifier.height(20.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { hero() }
                Spacer(Modifier.height(28.dp))
            } else {
                Spacer(Modifier.height(28.dp))
            }
            Staggered(0) { Eyebrow(eyebrow) }
            Spacer(Modifier.height(10.dp))
            Staggered(1) {
                Text(title, style = KamTheme.type.screenTitle, color = colors.textPrimary)
            }
            Spacer(Modifier.height(16.dp))
            body()
            Spacer(Modifier.height(24.dp))
        }
        footer()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SlideOne(onContinue: () -> Unit) {
    val colors = KamTheme.colors
    SlideScaffold(
        eyebrow = OnboardingCopy.slide1.eyebrow,
        title = OnboardingCopy.slide1.title,
        hero = { RippleMark() },
        body = {
            Staggered(2) {
                Text(
                    OnboardingCopy.slide1.body.orEmpty(),
                    style = KamTheme.type.bodyLarge,
                    color = colors.textSecondary,
                )
            }
            Spacer(Modifier.height(18.dp))
            Staggered(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OnboardingCopy.slide1Chips.forEach { KamChip(it) }
                }
            }
        },
        footer = {
            PrimaryButton(
                OnboardingCopy.slide1.button,
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

/** The mark with slow ripple rings. One of the two permitted glow effects. */
@Composable
private fun RippleMark() {
    val colors = KamTheme.colors
    val reduced = reducedMotion()

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
        if (!reduced) {
            val transition = rememberInfiniteTransition(label = "ripple")
            repeat(3) { ring ->
                val phase by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(4200, delayMillis = ring * 1400),
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "ring-$ring",
                )
                Canvas(Modifier.size(150.dp)) {
                    val radius = size.minDimension / 2f * (0.36f + phase * 0.62f)
                    drawCircle(
                        color = colors.accent,
                        radius = radius,
                        alpha = (1f - phase) * 0.28f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }
        KamMark(size = 84.dp, breathing = true)
    }
}

@Composable
private fun SlideTwo(onContinue: () -> Unit) {
    val colors = KamTheme.colors
    SlideScaffold(
        eyebrow = OnboardingCopy.slide2.eyebrow,
        title = OnboardingCopy.slide2.title,
        body = {
            Staggered(2) { Eyebrow("Good for") }
            Spacer(Modifier.height(10.dp))
            OnboardingCopy.slide2GoodFor.forEachIndexed { i, line ->
                Staggered(3 + i) { MarkedLine(line, positive = true) }
                Spacer(Modifier.height(9.dp))
            }
            Spacer(Modifier.height(14.dp))
            Staggered(7) { Eyebrow("Not for") }
            Spacer(Modifier.height(10.dp))
            OnboardingCopy.slide2NotFor.forEachIndexed { i, line ->
                Staggered(8 + i) { MarkedLine(line, positive = false) }
                Spacer(Modifier.height(9.dp))
            }
            Spacer(Modifier.height(16.dp))
            Staggered(12) {
                Text(
                    OnboardingCopy.SLIDE2_CLOSING,
                    style = KamTheme.type.secondary,
                    color = colors.textTertiary,
                )
            }
        },
        footer = {
            PrimaryButton(
                OnboardingCopy.slide2.button,
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun MarkedLine(text: String, positive: Boolean) {
    val colors = KamTheme.colors
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            if (positive) Icons.Rounded.Check else Icons.Rounded.Close,
            contentDescription = null,
            tint = if (positive) colors.accent else colors.textTertiary,
            modifier = Modifier.size(17.dp).padding(top = 2.dp),
        )
        Spacer(Modifier.width(11.dp))
        Text(
            text,
            style = KamTheme.type.body,
            color = if (positive) colors.textPrimary else colors.textSecondary,
        )
    }
}

@Composable
private fun SlideExtras(onContinue: () -> Unit) {
    val colors = KamTheme.colors
    SlideScaffold(
        eyebrow = OnboardingCopy.slideExtras.eyebrow,
        title = OnboardingCopy.slideExtras.title,
        body = {
            // Tighter than the modes slide, because there are six of these and
            // they are places to find rather than behaviors to understand.
            OnboardingCopy.slideExtrasItems.forEachIndexed { i, (name, description) ->
                Staggered(2 + i) {
                    Column {
                        Text(name, style = KamTheme.type.cardTitle, color = colors.textPrimary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            description,
                            style = KamTheme.type.secondary,
                            color = colors.textSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(4.dp))
            Staggered(8) {
                Text(
                    OnboardingCopy.SLIDE_EXTRAS_CLOSING,
                    style = KamTheme.type.secondary,
                    color = colors.textTertiary,
                )
            }
        },
        footer = {
            PrimaryButton(
                OnboardingCopy.slideExtras.button,
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun SlideThree(onContinue: () -> Unit) {
    val colors = KamTheme.colors
    SlideScaffold(
        eyebrow = OnboardingCopy.slide3.eyebrow,
        title = OnboardingCopy.slide3.title,
        body = {
            OnboardingCopy.slide3Modes.forEachIndexed { i, (name, description) ->
                Staggered(2 + i) {
                    Column {
                        Text(name, style = KamTheme.type.cardTitle, color = colors.textPrimary)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            description,
                            style = KamTheme.type.body,
                            color = colors.textSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(15.dp))
            }
            Spacer(Modifier.height(6.dp))
            Staggered(6) {
                Text(
                    OnboardingCopy.SLIDE3_CLOSING,
                    style = KamTheme.type.secondary,
                    color = colors.textTertiary,
                )
            }
        },
        footer = {
            PrimaryButton(
                OnboardingCopy.slide3.button,
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun SlideFour(
    totalRamGb: Int,
    tiers: List<TierModel>,
    downloadProgress: Float?,
    freeBytes: Long,
    onDownload: (TierModel) -> Unit,
    onContinue: () -> Unit,
) {
    val colors = KamTheme.colors

    // First run offers the smallest tier that actually answers well, which is not
    // the same as the smallest tier.
    //
    // It used to offer the smallest outright, because 5 GB of downloading before
    // the app does anything is how an app gets deleted. That reasoning still
    // holds. What changed is what counts as acceptable: measured on the device,
    // Basic answered "I am Kam AI." to three of ten awkward inputs, including
    // somebody saying their father had died, and Balanced answered that message
    // properly and never produced the line at all. See TierRecommendation.
    val recommended = TierRecommendation.forFirstRun(totalRamGb)
        ?: TierRecommendation.recommended(totalRamGb)
    var chosen by remember(recommended) { mutableStateOf(recommended) }

    // One recommendation, with the full list a tap away (#76).
    //
    // This slide used to open with every tier as a card and ask the user to
    // choose, which is a menu handed to somebody at the moment they know least
    // about the thing being chosen. The recommendation is already computed from
    // real memory; leading with it and keeping the list for anybody who wants to
    // reason about it themselves turns three decisions into none.
    var showingAll by remember { mutableStateOf(false) }
    val recommendedModel = tiers.firstOrNull { it.tier == recommended }

    SlideScaffold(
        eyebrow = OnboardingCopy.slide4.eyebrow,
        title = OnboardingCopy.slide4.title,
        body = {
            Staggered(2) {
                Text(
                    OnboardingCopy.SLIDE4_SMALLEST_FIRST,
                    style = KamTheme.type.bodyLarge,
                    color = colors.textSecondary,
                )
            }
            Spacer(Modifier.height(20.dp))

            if (showingAll || recommendedModel == null) {
                tiers.forEachIndexed { i, model ->
                    Staggered(3 + i) {
                        TierCard(
                            model = model,
                            locked = TierRecommendation.isLocked(model.tier, totalRamGb),
                            fitsOnDisk = com.kamsiob.kamai.download.DownloadGuard
                                .fitsOnDisk(model.downloadBytes, freeBytes),
                            recommended = model.tier == recommended,
                            selected = model.tier == chosen,
                            onSelect = { chosen = model.tier },
                        )
                    }
                    Spacer(Modifier.height(11.dp))
                }
            } else {
                Staggered(3) {
                    TierCard(
                        model = recommendedModel,
                        locked = false,
                        recommended = true,
                        selected = true,
                        onSelect = { chosen = recommended },
                    )
                }
                Spacer(Modifier.height(11.dp))
                Staggered(4) {
                    // Not a dismissal and not styled as one. Wanting to weigh the
                    // sizes yourself is sensible.
                    TextActionButton(
                        "See the other options",
                        onClick = { showingAll = true },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Staggered(7) {
                Text(
                    OnboardingCopy.SLIDE4_CLOSING,
                    style = KamTheme.type.secondary,
                    color = colors.textTertiary,
                )
            }
        },
        footer = {
            val model = tiers.firstOrNull { it.tier == chosen }
            when {
                downloadProgress != null -> DownloadBar(downloadProgress, onContinue)
                model == null -> PrimaryButton("Continue", onClick = onContinue, modifier = Modifier.fillMaxWidth())
                else -> PrimaryButton(
                    // The button says exactly what it does, including the size.
                    "Download ${model.tier.displayName}, ${model.downloadLabel}",
                    onClick = { onDownload(model) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun DownloadBar(progress: Float, onContinue: () -> Unit) {
    val colors = KamTheme.colors
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(CircleShape)
                .background(colors.surfaceSecondary),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(colors.accent),
            )
        }
        Spacer(Modifier.height(12.dp))
        if (progress >= 1f) {
            PrimaryButton("Continue", onClick = onContinue, modifier = Modifier.fillMaxWidth())
        } else {
            // A way forward while it downloads (#78).
            //
            // This screen used to show the percentage and nothing else: no button
            // at all, not a disabled one. Measured on a fresh install, the
            // Balanced model downloads at about two percent a minute on home
            // wifi, so a new user's first experience of the app was a progress bar
            // and roughly fifty minutes of it, with the only exit a link in the
            // corner reading "Skip for now", which sounds like giving up on the
            // thing they are waiting for.
            //
            // Nothing about the app needs them to wait there. The download runs in
            // a foreground service, the indicator from #81 follows them onto every
            // screen, and a message typed before the model is ready is held and
            // sent when it arrives. So they can go and look around, which is a far
            // better first ten minutes than watching a bar fill.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${(progress * 100).toInt()}% downloaded",
                    style = KamTheme.type.mono,
                    color = colors.textSecondary,
                )
            }
            Spacer(Modifier.height(12.dp))
            SecondaryButton(
                // Says what happens to the download, because the fear this answers
                // is that leaving cancels it.
                "Look around while it downloads",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TierCard(
    model: TierModel,
    locked: Boolean,
    /**
     * False when the download cannot land on this phone. Treated as another way
     * of being locked, so a tier nobody can download is never selectable, and it
     * says which of the two reasons applies (#75).
     */
    fitsOnDisk: Boolean = true,
    recommended: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = KamTheme.colors
    val shape = RoundedCornerShape(KamTheme.dimens.cardRadius)
    val unavailable = locked || !fitsOnDisk

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected && !unavailable) colors.tonalFill else colors.surface)
            .border(
                width = if (selected && !unavailable) 2.dp else 1.dp,
                color = if (selected && !unavailable) colors.accent else colors.border,
                shape = shape,
            )
            .then(if (unavailable) Modifier else Modifier.clickable(onClick = onSelect))
            .padding(16.dp)
            .semantics {
                contentDescription = buildString {
                    append(model.tier.displayName)
                    append(", ").append(model.downloadLabel)
                    if (recommended) append(", recommended")
                    // Color is never the only carrier of meaning: the locked
                    // reason is spoken as well as shown.
                    if (unavailable) append(", locked, ").append(lockNote(model, fitsOnDisk))
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                model.tier.displayName,
                style = KamTheme.type.cardTitle,
                color = if (unavailable) colors.textTertiary else colors.textPrimary,
            )
            Spacer(Modifier.width(9.dp))
            if (recommended && !unavailable) KamChip("Recommended", tonal = true)
            Spacer(Modifier.weight(1f))
            Text(
                model.downloadLabel,
                style = KamTheme.type.mono,
                color = if (unavailable) colors.textTertiary else colors.textSecondary,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${model.parameterLabel} parameters",
                style = KamTheme.type.secondary,
                color = colors.textTertiary,
            )
            if (unavailable) {
                Spacer(Modifier.weight(1f))
                Text(
                    lockNote(model, fitsOnDisk),
                    style = KamTheme.type.mono,
                    color = colors.goldText,
                    fontWeight = FontWeight.W600,
                )
            }
        }
    }
}

/** Which of the two reasons a tier cannot be chosen, in the same short form. */
private fun lockNote(model: TierModel, fitsOnDisk: Boolean): String =
    if (!fitsOnDisk) "No room" else TierRecommendation.lockedNote(model.tier)

@Composable
private fun SlideFive(onSupport: () -> Unit, onFinish: () -> Unit) {
    val colors = KamTheme.colors
    SlideScaffold(
        eyebrow = OnboardingCopy.slide5.eyebrow,
        title = OnboardingCopy.slide5.title,
        body = {
            Staggered(2) {
                Text(
                    OnboardingCopy.slide5.body.orEmpty(),
                    style = KamTheme.type.bodyLarge,
                    color = colors.textSecondary,
                )
            }
            Spacer(Modifier.height(20.dp))
            Staggered(3) {
                Text(
                    OnboardingCopy.SUPPORT_LINE,
                    style = KamTheme.type.secondary,
                    color = colors.textTertiary,
                )
            }
        },
        footer = {
            Column {
                PrimaryButton(
                    OnboardingCopy.SUPPORT_BUTTON,
                    onClick = onSupport,
                    modifier = Modifier.fillMaxWidth(),
                    amber = true,
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    OnboardingCopy.slide5.button,
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

/**
 * The two optional additions, offered while the model downloads (#77).
 *
 * The insight this slide exists for: a model download is minutes, not seconds, so
 * the wait is the right place to put the two decisions that are genuinely
 * optional. Nobody is blocked, nothing starts on its own, and each card can be
 * declined without touching the other.
 *
 * The packs card deliberately does **not** offer a download. Packs are chosen by
 * topic and somebody five minutes into the app has no idea which topics they
 * want; pointing at where they live is more use than picking one for them. The
 * voice card does offer, because there is one sensible answer for a given phone
 * and choosing between transcription tiers is not a decision worth handing to
 * someone who has not used the app yet.
 */
@Composable
private fun SlideOptional(
    voiceLabel: String,
    voiceQueued: Boolean,
    onAddVoice: () -> Unit,
    onContinue: () -> Unit,
) {
    val colors = KamTheme.colors

    SlideScaffold(
        eyebrow = OnboardingCopy.slideOptional.eyebrow,
        title = OnboardingCopy.slideOptional.title,
        body = {
            Staggered(2) {
                Text(
                    OnboardingCopy.OPTIONAL_INTRO,
                    style = KamTheme.type.bodyLarge,
                    color = colors.textSecondary,
                )
            }
            Spacer(Modifier.height(20.dp))

            Staggered(3) {
                OptionalCard(
                    title = OnboardingCopy.VOICE_CARD_TITLE,
                    body = OnboardingCopy.VOICE_CARD_BODY,
                    size = voiceLabel,
                    action = if (voiceQueued) null else "Add it",
                    // Queued behind the model rather than run alongside it: the
                    // model is what makes the app work at all, and two large
                    // downloads at once make both slower.
                    note = if (voiceQueued) "Queued after the model" else null,
                    onAction = onAddVoice,
                )
            }
            Spacer(Modifier.height(11.dp))
            Staggered(4) {
                OptionalCard(
                    title = OnboardingCopy.PACKS_CARD_TITLE,
                    body = OnboardingCopy.PACKS_CARD_BODY,
                    size = "",
                    action = null,
                    note = OnboardingCopy.PACKS_CARD_ACTION + ": the Discover tab",
                    onAction = {},
                )
            }
            Spacer(Modifier.height(14.dp))
            Staggered(5) {
                Text(
                    OnboardingCopy.SETTINGS_LINE,
                    style = KamTheme.type.secondary,
                    color = colors.textTertiary,
                )
            }
        },
        footer = {
            PrimaryButton(
                OnboardingCopy.slideOptional.button,
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun OptionalCard(
    title: String,
    body: String,
    size: String,
    action: String?,
    note: String?,
    onAction: () -> Unit,
) {
    val colors = KamTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KamTheme.dimens.cardRadius))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(KamTheme.dimens.cardRadius))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = KamTheme.type.cardTitle,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (size.isNotBlank()) {
                Text(size, style = KamTheme.type.mono, color = colors.textTertiary)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(body, style = KamTheme.type.secondary, color = colors.textSecondary)
        if (action != null) {
            Spacer(Modifier.height(12.dp))
            SecondaryButton(action, onClick = onAction, modifier = Modifier.fillMaxWidth())
        }
        if (note != null) {
            Spacer(Modifier.height(8.dp))
            Text(note, style = KamTheme.type.secondary, color = colors.textTertiary)
        }
    }
}
