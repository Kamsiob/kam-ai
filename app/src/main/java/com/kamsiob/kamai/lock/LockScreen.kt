package com.kamsiob.kamai.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.ui.components.KamMark
import com.kamsiob.kamai.ui.components.PrimaryButton
import com.kamsiob.kamai.ui.components.TextActionButton
import com.kamsiob.kamai.ui.theme.KamTheme

/**
 * The lock screen, shown over everything when the app is locked. PART 3.
 *
 * Device mode shows the system biometric prompt straight away. Passphrase mode
 * takes the Kam AI passphrase, with an honest, non-nagging path to the wipe for
 * anyone who has forgotten it, because nobody should be permanently locked out
 * of a usable app.
 */
@Composable
fun LockScreen(
    mode: AppLock.Mode,
    error: String?,
    onSubmitPassphrase: (CharArray) -> Unit,
    onUseBiometric: () -> Unit,
    biometricAvailable: Boolean,
    onForgot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    var passphrase by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        KamMark(size = 64.dp, breathing = true)
        Spacer(Modifier.height(20.dp))
        Text("Kam AI is locked", style = KamTheme.type.sectionTitle, color = colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            when (mode) {
                AppLock.Mode.DEVICE -> "Unlock with your fingerprint, face, or phone code."
                AppLock.Mode.PASSPHRASE -> "Enter your Kam AI passphrase."
            },
            style = KamTheme.type.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )

        if (mode == AppLock.Mode.PASSPHRASE) {
            Spacer(Modifier.height(22.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                if (passphrase.isEmpty()) {
                    Text("Passphrase", style = KamTheme.type.body, color = colors.textTertiary)
                }
                BasicTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                    ),
                    textStyle = KamTheme.type.body.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error, style = KamTheme.type.secondary, color = colors.flagAmber, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                "Unlock",
                onClick = { onSubmitPassphrase(passphrase.toCharArray()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = passphrase.isNotEmpty(),
            )
            if (biometricAvailable) {
                Spacer(Modifier.height(6.dp))
                TextActionButton("Use biometric instead", onClick = onUseBiometric)
            }
            Spacer(Modifier.height(20.dp))
            TextActionButton("Forgot your passphrase?", onClick = onForgot)
        } else {
            if (error != null) {
                Spacer(Modifier.height(14.dp))
                Text(error, style = KamTheme.type.secondary, color = colors.flagAmber, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Unlock", onClick = onUseBiometric, modifier = Modifier.fillMaxWidth())
        }
    }
}
