package com.kamsiob.kamai.assist

import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.SolidColor
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
    val transcribing by vm.transcribing.collectAsStateWithLifecycle()
    val voiceAvailable by vm.voiceAvailable.collectAsStateWithLifecycle()
    val defaultToVoice by vm.defaultToVoice.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    var field by remember { mutableStateOf("") }
    var flagged by remember { mutableStateOf(false) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    // Open in the input the user chose: text brings up the keyboard focused;
    // voice leaves the microphone prominent, one tap away (item 18). Never grab
    // the keyboard when voice is the default.
    LaunchedEffect(Unit) {
        if (!(defaultToVoice && voiceAvailable)) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val micPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) vm.startRecording() }

    // The scrim: tapping outside the panel dismisses the overlay.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(colors.background)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .imePadding()
                .padding(horizontal = 18.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.kamsiob.kamai.ui.components.KamMark(size = 22.dp)
                Spacer(Modifier.width(8.dp))
                Text("Kam AI", style = KamTheme.type.sectionTitle, color = colors.textPrimary)
            }
            Spacer(Modifier.height(12.dp))

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
                    Text(
                        answer.ifEmpty { "Thinking..." },
                        style = KamTheme.type.body,
                        color = colors.textPrimary,
                    )
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
                                .semantics { contentDescription = if (flagged) "Flagged" else "Flag this" },
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
                                tint = if (recording) colors.onAccent else colors.textSecondary,
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
            .background(if (surface) colors.flagAmber else colors.accent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { content() }
}
