package com.kamsiob.kamai.integrations

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.kamsiob.kamai.MainActivity
import com.kamsiob.kamai.ui.components.PrimaryButton
import com.kamsiob.kamai.ui.components.SecondaryButton
import com.kamsiob.kamai.ui.theme.KamTheme

/**
 * Handles text arriving from other apps: the text-selection "Ask Kam AI" action
 * (ACTION_PROCESS_TEXT, which needs no permission) and the share sheet
 * (ACTION_SEND, text/plain). Shows a lightweight sheet with two quick actions and
 * hands the text into the full app. Nothing about the text leaves the phone.
 */
class TextIntakeActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        com.kamsiob.kamai.ui.theme.Appearance.init(this)
        super.onCreate(savedInstanceState)

        val text = extractText(intent)
        if (text.isNullOrBlank()) { finish(); return }

        setContent {
            KamTheme {
                IntakeSheet(
                    text = text,
                    onChat = { open(text, Intake.Target.CHAT) },
                    onWorkbench = { open(text, Intake.Target.WORKBENCH) },
                    onDismiss = { finish() },
                )
            }
        }
    }

    private fun extractText(intent: Intent?): String? = when (intent?.action) {
        Intent.ACTION_PROCESS_TEXT ->
            intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        Intent.ACTION_SEND ->
            intent.getStringExtra(Intent.EXTRA_TEXT)
        else -> null
    }

    private fun open(text: String, target: Intake.Target) {
        Intake.request(text, target)
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }
}

@Composable
private fun IntakeSheet(
    text: String,
    onChat: () -> Unit,
    onWorkbench: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = KamTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
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
                .padding(20.dp),
        ) {
            Text("Kam AI", style = KamTheme.type.sectionTitle, color = colors.textPrimary)
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface)
                    .padding(14.dp),
            ) {
                Text(text, style = KamTheme.type.body, color = colors.textPrimary)
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Ask about this", onClick = onChat, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Rework in Workbench", onClick = onWorkbench, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
    }
}
