package com.kamsiob.kamai.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.ui.onboarding.OnboardingCopy
import com.kamsiob.kamai.ui.theme.KamTheme

/**
 * The one-time explanation of the mode control (#93).
 *
 * The control is how a conversation starts, and it reads as a filter or a
 * switcher, so it is not obvious that tapping a segment creates something. That
 * is a problem of learning a control once, not a problem the interface has
 * forever, so it is answered once and never again rather than with a permanent
 * label. The area it sits in is also full: the control and the navigation bar
 * are already there, and nothing may be added to it.
 *
 * The control itself stays lit while everything behind is dimmed, so the
 * connection between the explanation and the thing it explains cannot be
 * mistaken. That is why the caller draws the control a second time inside this
 * overlay rather than dimming the whole screen and describing it in words.
 *
 * The copy is [OnboardingCopy.slide3Modes], the same list onboarding shows, used
 * rather than repeated so the two cannot drift apart.
 */
@Composable
fun ModeBarHintCard(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KamTheme.dimens.cardRadius))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(KamTheme.dimens.cardRadius))
            .padding(18.dp),
    ) {
        Text(
            "This row starts a chat",
            style = KamTheme.type.cardTitle,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Each one opens a new conversation with a different job. You can " +
                "switch at any time once you are in it.",
            style = KamTheme.type.secondary,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(12.dp))

        OnboardingCopy.slide3Modes.forEach { (name, what) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    name,
                    style = KamTheme.type.secondary,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.W600,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    what,
                    style = KamTheme.type.secondary,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextActionButton("Got it", onClick = onDismiss)
        }
    }
}

/**
 * The dim behind the card.
 *
 * Tapping it dismisses, because reaching for the button is not the only way
 * somebody says they are done with an explanation, and an overlay that can only
 * be dismissed one way reads as a demand rather than an offer.
 */
@Composable
fun ModeBarHintScrim(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(interactionSource = interaction, indication = null, onClick = onDismiss)
            .semantics { contentDescription = "Dismiss the explanation" },
    )
}

/**
 * Entrance animation, skipped entirely when the system asks for reduced motion.
 *
 * A spring is the right feel for something arriving to be looked at, and it is
 * exactly the kind of movement that makes some people ill. Respecting the
 * setting means no spring rather than a smaller one.
 */
@Composable
fun ModeBarHintEntrance(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    val reduced = reduceMotion()
    AnimatedVisibility(
        visible = visible,
        enter = if (reduced) {
            fadeIn(tween(90))
        } else {
            fadeIn(tween(160)) + slideInVertically(
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            ) { full: Int -> full / 3 }
        },
    ) {
        content()
    }
}

/** True when the system animation scale is off, which is how Android reports it. */
@Composable
private fun reduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}
