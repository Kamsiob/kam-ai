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
    onFinish: () -> Unit,
    onSupport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    val pager = rememberPagerState(pageCount = { OnboardingCopy.SLIDE_COUNT })
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
                3 -> SlideFour(
                    totalRamGb = totalRamGb,
                    tiers = tiers,
                    downloadProgress = downloadProgress,
                    onDownload = onDownload,
                    onContinue = advance,
                )
                4 -> SlideFive(onSupport = onSupport, onFinish = onFinish)
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
    onDownload: (TierModel) -> Unit,
    onContinue: () -> Unit,
) {
    val colors = KamTheme.colors
    val recommended = TierRecommendation.recommended(totalRamGb)
    var chosen by remember(recommended) { mutableStateOf(recommended) }

    SlideScaffold(
        eyebrow = OnboardingCopy.slide4.eyebrow,
        title = OnboardingCopy.slide4.title,
        body = {
            Staggered(2) {
                Text(
                    TierRecommendation.explain(totalRamGb),
                    style = KamTheme.type.bodyLarge,
                    color = colors.textSecondary,
                )
            }
            Spacer(Modifier.height(20.dp))
            tiers.forEachIndexed { i, model ->
                Staggered(3 + i) {
                    TierCard(
                        model = model,
                        locked = TierRecommendation.isLocked(model.tier, totalRamGb),
                        recommended = model.tier == recommended,
                        selected = model.tier == chosen,
                        onSelect = { chosen = model.tier },
                    )
                }
                Spacer(Modifier.height(11.dp))
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
            Text(
                "${(progress * 100).toInt()}% downloaded",
                style = KamTheme.type.mono,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun TierCard(
    model: TierModel,
    locked: Boolean,
    recommended: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = KamTheme.colors
    val shape = RoundedCornerShape(KamTheme.dimens.cardRadius)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected && !locked) colors.tonalFill else colors.surface)
            .border(
                width = if (selected && !locked) 2.dp else 1.dp,
                color = if (selected && !locked) colors.accent else colors.border,
                shape = shape,
            )
            .then(if (locked) Modifier else Modifier.clickable(onClick = onSelect))
            .padding(16.dp)
            .semantics {
                contentDescription = buildString {
                    append(model.tier.displayName)
                    append(", ").append(model.downloadLabel)
                    if (recommended) append(", recommended")
                    // Colour is never the only carrier of meaning: the locked
                    // reason is spoken as well as shown.
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
            Spacer(Modifier.width(9.dp))
            if (recommended && !locked) KamChip("Recommended", tonal = true)
            Spacer(Modifier.weight(1f))
            Text(
                model.downloadLabel,
                style = KamTheme.type.mono,
                color = if (locked) colors.textTertiary else colors.textSecondary,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${model.parameterLabel} parameters",
                style = KamTheme.type.secondary,
                color = colors.textTertiary,
            )
            if (locked) {
                Spacer(Modifier.weight(1f))
                Text(
                    TierRecommendation.lockedNote(model.tier),
                    style = KamTheme.type.mono,
                    color = colors.flagAmber,
                    fontWeight = FontWeight.W600,
                )
            }
        }
    }
}

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
