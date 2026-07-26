package com.kamsiob.kamai.assist

import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kamsiob.kamai.MainActivity
import com.kamsiob.kamai.ui.components.PrimaryButton
import com.kamsiob.kamai.ui.components.SecondaryButton
import com.kamsiob.kamai.ui.theme.KamTheme

/**
 * The quick overlay. Opened by the assistant gesture (long-press power) when Kam
 * AI is the default digital assistant. A minimal panel over whatever the user was
 * doing: ask by text or voice, get a short answer, flag it with one tap, or hand
 * the exchange off into the full app. It answers entirely on-device, so it works
 * with no network like the rest of Kam AI.
 */
class OverlayActivity : FragmentActivity() {

    private val vm: OverlayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        com.kamsiob.kamai.ui.theme.Appearance.init(this)
        super.onCreate(savedInstanceState)
        setContent {
            KamTheme {
                OverlayPanel(
                    vm = vm,
                    onClose = { finish() },
                    onHandoff = {
                        vm.handoff {
                            startActivity(
                                Intent(this, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                            finish()
                        }
                    },
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        vm.cancelRecording()
        vm.stop()
    }
}

@Composable
private fun OverlayPanel(
    vm: OverlayViewModel,
    onClose: () -> Unit,
    onHandoff: () -> Unit,
) {
    val colors = KamTheme.colors
    val question by vm.question.collectAsStateWithLifecycle()
    val answer by vm.answer.collectAsStateWithLifecycle()
    val streaming by vm.streaming.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle()
    val recordedSeconds by vm.recordedSeconds.collectAsStateWithLifecycle()
    val transcribing by vm.transcribing.collectAsStateWithLifecycle()
    val voiceAvailable by vm.voiceAvailable.collectAsStateWithLifecycle()
    val openWithVoice by vm.openWithVoice.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    var field by remember { mutableStateOf("") }
    var flagged by remember { mutableStateOf(false) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    // The panel arrives on the expressive spring, the one signature moment here,
    // rising into place rather than snapping. Collapses to instant under reduced
    // motion. See DESIGN.md section 6, "the sheet arriving".
    val reduced = com.kamsiob.kamai.ui.theme.reducedMotion()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val context = androidx.compose.ui.platform.LocalContext.current
    val micPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) vm.startRecording() }

    // Open in the input the user chose.
    //
    // Keyed on the setting rather than on Unit, and it waits for a non-null
    // value, because both underlying flows start false while the database is
    // still being read. Deciding on first composition meant always deciding
    // "text", so the voice-first setting did nothing at all (#46). The `opened`
    // latch keeps this to one decision per overlay, so a setting changing while
    // the panel is up cannot grab focus or the microphone out from under
    // somebody mid-sentence.
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(openWithVoice) {
        val voiceFirst = openWithVoice ?: return@LaunchedEffect
        if (opened) return@LaunchedEffect
        opened = true
        if (voiceFirst) {
            // Voice first means listening, not merely available. Stopping is the
            // same button, which turns into Stop, and the field is right there to
            // type into instead.
            val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            // Asked plainly rather than silently falling back to the keyboard,
            // which is what the old behaviour amounted to.
            if (granted) vm.startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            runCatching { focusRequester.requestFocus() }
        }
    }

    // Drag state for the handle (#47). An Animatable rather than a plain float so
    // a drag that does not reach either threshold can spring back rather than
    // snapping, which is what makes the panel feel attached to the finger.
    val dragY = remember { androidx.compose.animation.core.Animatable(0f) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    // How far up the panel may travel, and how far commits either way. Up is the
    // shorter throw because expanding is the useful outcome and dismissing is
    // already one tap on the scrim away.
    // Hoisted, because the spec helper is composable and the gesture callbacks
    // below are not.
    val settleSpec = com.kamsiob.kamai.ui.theme.standardSpec<Float>()
    val expandTravel = with(density) { 160.dp.toPx() }
    val commitUp = with(density) { 56.dp.toPx() }
    val commitDown = with(density) { 96.dp.toPx() }

    // The scrim: a faint dim so the panel reads as lifted over whatever was
    // behind it, and tapping it dismisses the overlay.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
      AnimatedVisibility(
          visible = visible,
          enter = if (reduced) {
              fadeIn(tween(0))
          } else {
              slideInVertically(com.kamsiob.kamai.ui.theme.expressiveSpec<IntOffset>()) { it } +
                  fadeIn(com.kamsiob.kamai.ui.theme.standardSpec())
          },
      ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, dragY.value.roundToInt()) }
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(colors.background)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .imePadding()
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            // The grabber, which now does something. It looked draggable and was
            // decorative, which is issue #47: an affordance that promises and then
            // refuses is worse than no affordance.
            //
            // Dragging up expands the exchange into the full app, landing in that
            // conversation with its content intact, so a quick question becomes a
            // real one without retyping. Dragging down dismisses. Both follow the
            // finger and settle on release. Tapping expands, since doing nothing
            // was the whole complaint.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 12.dp)
                    // A larger touch target than the 34x4 line it draws, so the
                    // gesture is reachable without hunting for a hairline.
                    .size(width = 96.dp, height = 28.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, delta ->
                                change.consume()
                                // Upward is unbounded enough to feel free, downward
                                // is not resisted at all, so the two directions read
                                // differently under the finger.
                                scope.launch {
                                    dragY.snapTo((dragY.value + delta).coerceAtLeast(-expandTravel))
                                }
                            },
                            onDragEnd = {
                                when {
                                    dragY.value <= -commitUp -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onHandoff()
                                    }
                                    dragY.value >= commitDown -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onClose()
                                    }
                                    // Short of either, it goes back where it was.
                                    else -> scope.launch {
                                        dragY.animateTo(0f, settleSpec)
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    dragY.animateTo(0f, settleSpec)
                                }
                            },
                        )
                    }
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onHandoff()
                        },
                    )
                    .semantics { contentDescription = "Expand into the full app" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .size(width = 34.dp, height = 4.dp)
                        .background(colors.textTertiary.copy(alpha = 0.4f)),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The mark breathes while an answer is being written, its
                // status-indicator behaviour from DESIGN.md section 2.
                com.kamsiob.kamai.ui.components.KamMark(size = 22.dp, breathing = streaming)
                Spacer(Modifier.width(8.dp))
                Text("Kam AI", style = KamTheme.type.sectionTitle, color = colors.textPrimary)
                Spacer(Modifier.width(8.dp))
                // The on device tag from DESIGN.md section 7: quiet, mono, a fact
                // about where the thinking happens.
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.tonalFill)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text("on device", style = KamTheme.type.mono, color = colors.tonalText)
                }
            }
            Spacer(Modifier.height(12.dp))

            // Listening, said unmistakably.
            //
            // The only sign the microphone was live used to be the small round
            // button changing fill and glyph, on a panel over somebody else's
            // screen. That is not enough for a surface whose whole point is that
            // you opened it by holding a button and started talking (owner
            // feedback).
            if (recording) {
                ListeningBar(seconds = recordedSeconds)
                Spacer(Modifier.height(12.dp))
            } else if (transcribing) {
                TranscribingBar()
                Spacer(Modifier.height(12.dp))
            }

            // What it heard, given back before the answer.
            //
            // Speaking used to transcribe and ask in one go, and the transcript
            // went into a view-model field the panel never read, so the answer
            // arrived with no sign of what the question had been. On a small
            // model that mishears, being able to see "you asked X" is the
            // difference between a wrong answer and a wrong question.
            if (question.isNotBlank() && (answer.isNotEmpty() || streaming)) {
                // One line of ordinary prose, not a label beside a quote.
                //
                // This started as a mono "You said" tag next to the text. Mono is
                // the app's voice for facts about the machine, and putting the
                // user's own words next to a robotic label got it backwards; the
                // two type sizes also refused to sit on the same line, so the tag
                // floated above the sentence it belonged to (owner feedback).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.tonalFill)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        "You said: $question",
                        style = KamTheme.type.secondary,
                        color = colors.tonalText,
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            // Answer / status
            if (answer.isNotEmpty() || streaming) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
                        .padding(14.dp),
                ) {
                    // Markdown, rendered, like the chat (#91). This was a plain
                    // Text, so an answer that used a list showed the user its
                    // syntax. The overlay gets short answers, but "short" and
                    // "never a list" are different claims.
                    if (answer.isEmpty()) {
                        Text(
                            "Thinking...",
                            style = KamTheme.type.body,
                            color = colors.textPrimary,
                        )
                    } else {
                        com.kamsiob.kamai.ui.components.MarkdownText(
                            text = answer,
                            color = colors.textPrimary,
                        )
                    }
                }
                if (answer.isNotEmpty() && !streaming) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // One-tap flag, no extra UI.
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (flagged) colors.flagAmber else colors.surface)
                                .border(1.dp, colors.border, CircleShape)
                                .clickable(enabled = !flagged) { vm.flag { flagged = true } }
                                .semantics { contentDescription = if (flagged) "Bookmarked for follow-up" else "Bookmark for follow-up" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.BookmarkBorder,
                                contentDescription = null,
                                tint = if (flagged) colors.onAccent else colors.textSecondary,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        SecondaryButton(
                            "Copy",
                            onClick = { clipboard.setText(AnnotatedString(answer)) },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        PrimaryButton(
                            "Open Kam AI",
                            onClick = onHandoff,
                            modifier = Modifier.weight(1.4f),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            notice?.let {
                Text(it, style = KamTheme.type.secondary, color = colors.textSecondary)
                Spacer(Modifier.height(10.dp))
            }

            // Input
            Row(verticalAlignment = Alignment.Bottom) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(22.dp))
                        .padding(horizontal = 15.dp, vertical = 12.dp),
                ) {
                    if (field.isEmpty()) {
                        Text(
                            when {
                                recording -> "Listening..."
                                transcribing -> "Turning your voice into text..."
                                else -> "Ask something"
                            },
                            style = KamTheme.type.body,
                            color = colors.textTertiary,
                        )
                    }
                    BasicTextField(
                        value = field,
                        onValueChange = { field = it; vm.setQuestion(it) },
                        // Locked while an answer is being written, so nothing is
                        // typed into a state the assistant is not ready for. The
                        // Ask button becomes Stop so the run can still be cancelled.
                        enabled = !recording && !transcribing && !streaming,
                        textStyle = KamTheme.type.body.copy(color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    )
                }
                Spacer(Modifier.width(8.dp))

                if (voiceAvailable && field.isBlank() && !streaming) {
                    RoundBtn(
                        surface = recording,
                        desc = if (recording) "Stop recording" else "Voice",
                        onClick = {
                            if (recording) {
                                vm.stopAndTranscribe()
                            } else {
                                val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                                    PackageManager.PERMISSION_GRANTED
                                if (granted) vm.startRecording()
                                else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    ) {
                        if (transcribing) {
                            CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp, color = colors.accent)
                        } else {
                            Icon(
                                if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                contentDescription = null,
                                tint = if (recording) colors.tonalText else colors.textSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }

                RoundBtn(
                    surface = false,
                    desc = if (streaming) "Stop" else "Ask",
                    onClick = {
                        if (streaming) vm.stop()
                        else if (field.isNotBlank()) vm.ask(field)
                    },
                ) {
                    Icon(
                        if (streaming) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                        contentDescription = null,
                        tint = colors.onAccent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
      }
    }
}

@Composable
private fun RoundBtn(
    surface: Boolean,
    desc: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = KamTheme.colors
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            // Listening is drawn in the tonal fill, not the reserved gold (#61)
            // and not the accent either: the accent is the Ask button sitting
            // next to it, and two identical circles a thumb apart is how you tap
            // send when you meant stop. Tonal is the app's own "active but not
            // the primary action" weight, used by chips and user bubbles.
            .background(if (surface) colors.tonalFill else colors.accent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * The "I am listening" state, made unmissable.
 *
 * The assistant panel opens over whatever the user was doing, often because they
 * held the power button and started talking straight away. Before this the only
 * sign the microphone was live was a small round button swapping its glyph and
 * fill, which is far too quiet for the one moment where being wrong costs the
 * user a whole sentence (owner feedback).
 *
 * Three pulsing bars rather than a spinner: a spinner means "wait", and this
 * means "go on, I can hear you". The elapsed count is there so a long thought
 * visibly registers as still being captured.
 */
@Composable
private fun ListeningBar(seconds: Int) {
    val colors = KamTheme.colors
    val reduced = com.kamsiob.kamai.ui.theme.reducedMotion()
    val transition = rememberInfiniteTransition(label = "listening")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.tonalFill)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "Listening. Tap stop when you are done."
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            repeat(3) { index ->
                // Each bar runs on its own offset so they read as a level meter
                // rather than three things blinking together.
                val animated by transition.animateFloat(
                    initialValue = 6f,
                    targetValue = 18f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 520, delayMillis = index * 130),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "bar$index",
                )
                // Reduced motion gets three still bars rather than none, so the
                // state still reads as a level meter.
                val height = if (reduced) 12.dp else animated.dp
                Box(
                    Modifier
                        .padding(end = 4.dp)
                        .size(width = 4.dp, height = height)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.tonalText),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "Listening",
            style = KamTheme.type.label,
            color = colors.tonalText,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "${seconds}s",
            style = KamTheme.type.mono,
            color = colors.tonalText,
        )
    }
}

/** The gap between speaking and the answer, which used to be silent. */
@Composable
private fun TranscribingBar() {
    val colors = KamTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.accent)
        Spacer(Modifier.width(12.dp))
        Text(
            "Turning your voice into text",
            style = KamTheme.type.label,
            color = colors.textSecondary,
        )
    }
}
