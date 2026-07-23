package com.kamsiob.kamai.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.ui.components.Eyebrow
import com.kamsiob.kamai.ui.components.KamCard
import com.kamsiob.kamai.ui.components.PrimaryButton
import com.kamsiob.kamai.ui.components.SecondaryButton
import com.kamsiob.kamai.ui.components.SettingsGroup
import com.kamsiob.kamai.ui.components.SettingsRow
import com.kamsiob.kamai.ui.components.SettingsToggleRow
import com.kamsiob.kamai.ui.theme.KamTheme

/**
 * Sets up, changes, or removes the app lock. PART 3. The two strengths are
 * presented honestly, with the tradeoff spelled out at the moment of choosing.
 */
@Composable
fun LockSettingsScreen(
    enabled: Boolean,
    mode: AppLock.Mode,
    biometricEnabled: Boolean,
    biometricAvailable: Boolean,
    onEnableDevice: () -> Unit,
    onEnablePassphrase: (CharArray) -> Unit,
    onDisable: () -> Unit,
    onBiometricToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    var choosing by remember { mutableStateOf(false) }
    var passphraseMode by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KamTheme.dimens.screenPadding),
    ) {
        Text("App lock", style = KamTheme.type.screenTitle, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "A lock on Kam AI itself, separate from your phone's lock. Off by default. " +
                "Your conversations are already encrypted on the device; this stops " +
                "someone who has your unlocked phone from opening the app.",
            style = KamTheme.type.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(20.dp))

        if (enabled) {
            SettingsGroup("On") {
                SettingsRow(
                    title = "Lock is on",
                    subtitle = when (mode) {
                        AppLock.Mode.DEVICE -> "Unlocks with your phone's fingerprint, face, or code"
                        AppLock.Mode.PASSPHRASE -> "Unlocks with your Kam AI passphrase"
                    },
                    showDivider = mode == AppLock.Mode.DEVICE,
                )
                SettingsRow(
                    title = "Turn off the lock",
                    destructive = true,
                    onClick = onDisable,
                    showDivider = false,
                )
            }
            return@Column
        }

        if (!choosing) {
            PrimaryButton("Set up a lock", onClick = { choosing = true }, modifier = Modifier.fillMaxWidth())
            return@Column
        }

        // Choosing a strength.
        StrengthCard(
            title = "Use my phone's lock",
            recommended = true,
            body = "Unlock Kam AI with the same fingerprint, face, or code you use for " +
                "the phone. Convenient, and you can never be locked out. Slightly weaker " +
                "against someone who already knows your phone code.",
            onClick = {
                if (biometricAvailable) {
                    onEnableDevice()
                } else {
                    error = "Set a screen lock on your phone first, in the phone's Settings."
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        StrengthCard(
            title = "Use a separate passphrase",
            recommended = false,
            body = "A passphrase only for Kam AI. Stronger: it locks the data itself, so " +
                "even someone with your unlocked phone cannot open it. There is no way to " +
                "recover it. If you forget it, the only option is to erase and start fresh.",
            onClick = { passphraseMode = true },
        )

        if (passphraseMode) {
            Spacer(Modifier.height(18.dp))
            Eyebrow("Choose a passphrase")
            Spacer(Modifier.height(8.dp))
            SecretField("Passphrase", passphrase) { passphrase = it }
            Spacer(Modifier.height(10.dp))
            SecretField("Type it again", confirm) { confirm = it }
            Spacer(Modifier.height(10.dp))
            Text(
                "Save this somewhere safe. It cannot be recovered, and nobody, including " +
                    "us, can reset it for you.",
                style = KamTheme.type.secondary,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                "Turn on the lock",
                onClick = {
                    when {
                        passphrase.length < 4 -> error = "Use at least four characters."
                        passphrase != confirm -> error = "The two passphrases do not match."
                        else -> onEnablePassphrase(passphrase.toCharArray())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = passphrase.isNotEmpty() && confirm.isNotEmpty(),
            )
        }

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error.orEmpty(), style = KamTheme.type.secondary, color = colors.flagAmber)
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun StrengthCard(
    title: String,
    recommended: Boolean,
    body: String,
    onClick: () -> Unit,
) {
    val colors = KamTheme.colors
    KamCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = KamTheme.type.cardTitle, color = colors.textPrimary)
                if (recommended) {
                    Spacer(Modifier.width(8.dp))
                    com.kamsiob.kamai.ui.components.KamChip("Simplest", tonal = true)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(body, style = KamTheme.type.body, color = colors.textSecondary)
        }
    }
}

@Composable
private fun SecretField(placeholder: String, value: String, onChange: (String) -> Unit) {
    val colors = KamTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = KamTheme.type.body, color = colors.textTertiary)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            textStyle = KamTheme.type.body.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
