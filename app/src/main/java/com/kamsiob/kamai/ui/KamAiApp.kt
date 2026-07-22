package com.kamsiob.kamai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.ui.components.KamMark
import com.kamsiob.kamai.ui.theme.KamTheme

/**
 * Phase 0 shell. The real navigation, brand bar, and bottom navigation arrive in
 * Phase 1; this exists so the toolchain, the theme, the fonts, and the mark can
 * all be proven on a real device before anything is built on top of them.
 */
@Composable
fun KamAiApp() {
    val colors = KamTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .padding(KamTheme.dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        KamMark(size = 72.dp, breathing = true)
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Kam AI",
            style = KamTheme.type.screenTitle,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Everything happens on your phone.",
            style = KamTheme.type.body,
            color = colors.textSecondary,
        )
    }
}
