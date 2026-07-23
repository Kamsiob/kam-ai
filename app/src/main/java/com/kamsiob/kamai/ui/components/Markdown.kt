package com.kamsiob.kamai.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.ui.theme.KamTheme

/**
 * A small, dependency-free Markdown renderer for assistant text, styled entirely
 * from the design system so formatted output looks like it belongs in the app
 * rather than a web page pasted in.
 *
 * It covers the subset a chat model actually produces: headings, bold and italic,
 * inline code, fenced code blocks, bullet and numbered lists, block quotes, a
 * horizontal rule, and paragraph breaks. It is deliberately tolerant of the
 * half-finished Markdown that appears mid-stream (an unclosed ** or ``` renders
 * as plain text rather than breaking), so it can render a response as it arrives.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = KamTheme.colors.textPrimary,
) {
    val blocks = remember(text) { parseMarkdown(text) }
    val colors = KamTheme.colors
    val type = KamTheme.type

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    inline(block.text, colors.surfaceSecondary, colors.textPrimary, type.mono.fontFamily),
                    style = when (block.level) {
                        1 -> type.sectionTitle
                        2 -> type.cardTitle
                        else -> type.bodyEmphasis
                    },
                    color = color,
                )

                is MdBlock.Paragraph -> Text(
                    inline(block.text, colors.surfaceSecondary, colors.textPrimary, type.mono.fontFamily),
                    style = type.body,
                    color = color,
                )

                is MdBlock.Bullet -> block.items.forEach { item ->
                    Row(Modifier.fillMaxWidth()) {
                        Text("•", style = type.body, color = colors.textTertiary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            inline(item, colors.surfaceSecondary, colors.textPrimary, type.mono.fontFamily),
                            style = type.body, color = color, modifier = Modifier.weight(1f),
                        )
                    }
                }

                is MdBlock.Numbered -> block.items.forEachIndexed { i, item ->
                    Row(Modifier.fillMaxWidth()) {
                        Text("${block.start + i}.", style = type.body, color = colors.textTertiary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            inline(item, colors.surfaceSecondary, colors.textPrimary, type.mono.fontFamily),
                            style = type.body, color = color, modifier = Modifier.weight(1f),
                        )
                    }
                }

                is MdBlock.Code -> Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceSecondary)
                        .padding(12.dp),
                ) {
                    Text(
                        block.code,
                        style = type.mono,
                        color = colors.textPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        softWrap = false,
                    )
                }

                is MdBlock.Quote -> Row(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.border),
                    ) { Text(" ", style = type.body) }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        inline(block.text, colors.surfaceSecondary, colors.textPrimary, type.mono.fontFamily),
                        style = type.body, color = colors.textSecondary, modifier = Modifier.weight(1f),
                    )
                }

                MdBlock.Divider -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(colors.border)
                        .height(1.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Parsing. Pure functions, unit-tested in MarkdownParseTest.
// ---------------------------------------------------------------------------

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val items: List<String>) : MdBlock
    data class Numbered(val items: List<String>, val start: Int) : MdBlock
    data class Code(val code: String, val lang: String?) : MdBlock
    data class Quote(val text: String) : MdBlock
    data object Divider : MdBlock
}

private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
private val BULLET = Regex("^\\s*[-*+]\\s+(.*)$")
private val NUMBERED = Regex("^\\s*(\\d+)[.)]\\s+(.*)$")
private val RULE = Regex("^\\s*([-*_])\\1{2,}\\s*$")

/** Splits Markdown into block-level pieces, tolerant of unfinished input. */
fun parseMarkdown(text: String): List<MdBlock> {
    val lines = text.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<MdBlock>()
    val para = StringBuilder()

    fun flushPara() {
        if (para.isNotBlank()) blocks += MdBlock.Paragraph(para.toString().trim())
        para.setLength(0)
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]

        // Fenced code block: everything until the closing fence, or end of text.
        val fence = line.trimStart()
        if (fence.startsWith("```")) {
            flushPara()
            val lang = fence.removePrefix("```").trim().ifBlank { null }
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                if (code.isNotEmpty()) code.append('\n')
                code.append(lines[i])
                i++
            }
            i++ // consume closing fence (harmless if it was the end)
            blocks += MdBlock.Code(code.toString(), lang)
            continue
        }

        when {
            line.isBlank() -> flushPara()

            RULE.matches(line) -> { flushPara(); blocks += MdBlock.Divider }

            HEADING.matches(line) -> {
                flushPara()
                val m = HEADING.find(line)!!
                blocks += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2].trim())
            }

            BULLET.matches(line) -> {
                flushPara()
                val items = mutableListOf<String>()
                while (i < lines.size && BULLET.matches(lines[i])) {
                    items += BULLET.find(lines[i])!!.groupValues[1].trim()
                    i++
                }
                blocks += MdBlock.Bullet(items)
                continue
            }

            NUMBERED.matches(line) -> {
                flushPara()
                val start = NUMBERED.find(line)!!.groupValues[1].toIntOrNull() ?: 1
                val items = mutableListOf<String>()
                while (i < lines.size && NUMBERED.matches(lines[i])) {
                    items += NUMBERED.find(lines[i])!!.groupValues[2].trim()
                    i++
                }
                blocks += MdBlock.Numbered(items, start)
                continue
            }

            line.trimStart().startsWith(">") -> {
                flushPara()
                val quote = StringBuilder()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    if (quote.isNotEmpty()) quote.append('\n')
                    quote.append(lines[i].trimStart().removePrefix(">").trimStart())
                    i++
                }
                blocks += MdBlock.Quote(quote.toString().trim())
                continue
            }

            else -> {
                if (para.isNotEmpty()) para.append('\n')
                para.append(line.trim())
            }
        }
        i++
    }
    flushPara()
    return blocks
}

/**
 * Inline spans: **bold**, *italic*, and `code`. Underscores are left literal so
 * snake_case and file_names are never mangled. An unterminated marker renders as
 * plain text, which is what keeps mid-stream output readable.
 */
private fun inline(
    text: String,
    codeBg: Color,
    codeColor: Color,
    monoFamily: androidx.compose.ui.text.font.FontFamily?,
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.W700)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else { append("**"); i += 2 }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontFamily = monoFamily, background = codeBg, color = codeColor)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append('`'); i++ }
            }
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else { append('*'); i++ }
            }
            else -> { append(text[i]); i++ }
        }
    }
}
