package com.kamsiob.kamai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import com.kamsiob.kamai.ui.theme.KamTheme

/**
 * Wraps selectable text so that highlighting any part of it surfaces the app's
 * own copy, follow-up, and share actions for exactly what was selected, in one
 * coherent menu. PART 5 and PART 5B.
 *
 * Rather than fight the system text-selection popup, this replaces it for this
 * content with a small themed menu carrying the three actions. The selected text
 * is captured by having each custom action first run the platform copy, which
 * puts the selection on the clipboard, then reading it back. That keeps the
 * selection handles and drag behaviour exactly as the system provides them, and
 * only the floating menu changes.
 *
 * **None of this currently runs. See issue #64.** `SelectionContainer` no longer
 * consults `LocalTextToolbar`, so the toolbar installed below is never asked for
 * a menu and the platform's own toolbar appears instead: selecting part of an
 * answer offers Copy, Select all and Read aloud, and never Follow up or Share.
 * The code reads as though it works, compiles, and has always been wrong on the
 * device. Kept rather than deleted because the actions and their wiring are
 * still what is wanted; only the mechanism for showing them has to change.
 */
@Composable
fun SelectionActions(
    onCopy: (String) -> Unit,
    onFollowUp: (String) -> Unit,
    onShare: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val clipboard = LocalClipboardManager.current

    var menu by remember { mutableStateOf<MenuState?>(null) }

    val toolbar = remember(clipboard) {
        object : TextToolbar {
            override var status: TextToolbarStatus = TextToolbarStatus.Hidden
                private set

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?,
            ) {
                status = TextToolbarStatus.Shown
                menu = MenuState(rect, onCopyRequested)
            }

            /**
             * The same thing, for the overload that carries autofill.
             *
             * `TextToolbar` grew a six-argument `showMenu` alongside the
             * five-argument one, with a default implementation. Overriding both
             * costs nothing and is correct for any caller that still goes
             * through this interface.
             *
             * It is **not** what fixes issue #64, though. Neither override
             * fires: `SelectionContainer` in this version of Compose does not
             * consult `LocalTextToolbar` at all, so the whole class below is
             * dead and the platform toolbar is what actually appears. See #64
             * for the evidence and the API that replaces this.
             */
            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?,
                onAutofillRequested: (() -> Unit)?,
            ) {
                status = TextToolbarStatus.Shown
                menu = MenuState(rect, onCopyRequested)
            }

            override fun hide() {
                status = TextToolbarStatus.Hidden
                menu = null
            }
        }
    }

    // Reads whatever the platform copy just placed on the clipboard.
    fun selectedText(copyRequested: (() -> Unit)?): String {
        copyRequested?.invoke()
        return clipboard.getText()?.text.orEmpty()
    }

    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        SelectionContainer(content = content)
    }

    menu?.let { state ->
        val colors = KamTheme.colors
        Popup(
            popupPositionProvider = anchorAbove(state.rect),
            onDismissRequest = { menu = null },
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(12.dp)),
            ) {
                MenuItem("Copy") {
                    onCopy(selectedText(state.onCopyRequested)); menu = null
                }
                MenuItem("Follow up") {
                    onFollowUp(selectedText(state.onCopyRequested)); menu = null
                }
                MenuItem("Share") {
                    onShare(selectedText(state.onCopyRequested)); menu = null
                }
            }
        }
    }
}

private data class MenuState(val rect: Rect, val onCopyRequested: (() -> Unit)?)

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = KamTheme.type.label,
        color = KamTheme.colors.accent,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    )
}

/** Positions the little menu just above the selection. */
private fun anchorAbove(rect: Rect): PopupPositionProvider = object : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize,
    ): IntOffset {
        val x = (rect.center.x - popupContentSize.width / 2f).toInt()
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (rect.top - popupContentSize.height - 8).toInt().coerceAtLeast(0)
        return IntOffset(x, y)
    }
}
