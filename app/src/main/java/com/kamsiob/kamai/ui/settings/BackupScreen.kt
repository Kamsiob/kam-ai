package com.kamsiob.kamai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.ui.components.Eyebrow
import com.kamsiob.kamai.ui.components.PrimaryButton
import com.kamsiob.kamai.ui.components.SecondaryButton
import com.kamsiob.kamai.ui.theme.KamTheme

/**
 * Backup and restore. Export writes everything into one encrypted file the user
 * saves wherever they like; import brings it back on any phone, merging or
 * replacing. The large model and pack files are not in the backup; the app offers
 * to re-download them after a restore.
 */
@Composable
fun BackupScreen(
    onExport: (passphrase: String) -> Unit,
    onImport: (passphrase: String, replace: Boolean) -> Unit,
    busy: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    var exportPass by remember { mutableStateOf("") }
    var importPass by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KamTheme.dimens.screenPadding),
    ) {
        Text("Backup and restore", style = KamTheme.type.screenTitle, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Move everything to a new phone, or keep a copy somewhere safe. The backup is " +
                "one file, locked with a passphrase you choose. Your models and packs are not " +
                "in it, since they are large downloads; the app offers to fetch them again after.",
            style = KamTheme.type.body,
            color = colors.textSecondary,
        )

        Spacer(Modifier.height(24.dp))
        Eyebrow("Export")
        Spacer(Modifier.height(8.dp))
        Text(
            "Set a passphrase. There is no way to open the backup without it, and no way to " +
                "recover it, which is what keeps the file private.",
            style = KamTheme.type.secondary,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(10.dp))
        PassField(exportPass, "Passphrase", colors) { exportPass = it }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            "Export backup",
            onClick = { onExport(exportPass) },
            enabled = !busy && exportPass.length >= 6,
            modifier = Modifier.fillMaxWidth(),
        )
        if (exportPass.isNotEmpty() && exportPass.length < 6) {
            Spacer(Modifier.height(6.dp))
            Text("Use at least six characters.", style = KamTheme.type.secondary, color = colors.textTertiary)
        }

        Spacer(Modifier.height(30.dp))
        Eyebrow("Restore")
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick a backup file and enter its passphrase. Merge keeps what is already here and " +
                "adds the backup; replace clears this phone first.",
            style = KamTheme.type.secondary,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(10.dp))
        PassField(importPass, "Backup passphrase", colors) { importPass = it }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModeChip("Merge", !replace, colors) { replace = false }
            Spacer(Modifier.width(8.dp))
            ModeChip("Replace", replace, colors) { replace = true }
        }
        Spacer(Modifier.height(12.dp))
        SecondaryButton(
            "Choose a backup file",
            onClick = { onImport(importPass, replace) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun PassField(value: String, hint: String, colors: com.kamsiob.kamai.ui.theme.KamColors, onChange: (String) -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        if (value.isEmpty()) Text(hint, style = KamTheme.type.body, color = colors.textTertiary)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = KamTheme.type.body.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, colors: com.kamsiob.kamai.ui.theme.KamColors, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) colors.tonalFill else colors.surfaceSecondary)
            .border(1.dp, if (selected) colors.accent else colors.border, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(label, style = KamTheme.type.label, color = if (selected) colors.tonalText else colors.textSecondary)
    }
}
