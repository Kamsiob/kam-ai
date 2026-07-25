package com.kamsiob.kamai.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.kamsiob.kamai.data.MessageEntity
import com.kamsiob.kamai.data.Role
import com.kamsiob.kamai.ui.components.markdownToPlainText
import java.io.File

/**
 * Sharing and exporting, all through the native Android share sheet. PART 5B.
 * Nothing here routes through a backend; it hands off to the OS.
 */
object Share {

    /** Sends plain text to the system share sheet. */
    fun text(context: Context, body: String, subject: String? = null) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
            if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        context.startActivity(Intent.createChooser(intent, "Share").addNewTaskIfNeeded(context))
    }

    /**
     * A whole conversation as clean, readable plain text.
     *
     * Three roles, not two. A SYSTEM entry is a mode-change notice or the
     * Discover continue-in-open-chat note: something that happened *to* the
     * conversation, not something anybody said. Branching only on USER, as this
     * did, exported those as though the assistant had said them, which put words
     * in its mouth that no model produced (issue #41).
     *
     * They are not dropped either. The four-mode update requires an export to
     * show where the mode changed, so they are rendered as what they are: a note
     * on its own line, set off by brackets, with no speaker.
     */
    fun renderThread(title: String?, messages: List<MessageEntity>): String = buildString {
        appendLine(title ?: "Kam AI conversation")
        appendLine()
        messages.forEach { m ->
            when (m.role) {
                Role.SYSTEM -> appendLine("[ ${m.content.trim()} ]")
                // Left exactly as typed. Only the assistant writes Markdown, and
                // running a user's own words through a Markdown stripper would
                // quietly eat their asterisks.
                Role.USER -> appendLine("You: ${m.content.trim()}")
                // The user picked plain text over Markdown and got Markdown
                // anyway, which made the choice between the two formats mean
                // nothing except the file extension.
                else -> appendLine("Kam AI: ${markdownToPlainText(cleaned(m.content)).trim()}")
            }
            appendLine()
        }
    }.trim()

    /**
     * Writes a thread to a file and opens the share sheet on it, so the user can
     * save it or send it anywhere. Markdown or plain text.
     */
    fun exportThread(
        context: Context,
        title: String?,
        messages: List<MessageEntity>,
        asMarkdown: Boolean,
    ) {
        val safeTitle = (title ?: "conversation").replace(Regex("[^A-Za-z0-9 _-]"), "").trim()
            .ifBlank { "conversation" }.take(40)
        val ext = if (asMarkdown) "md" else "txt"
        val body = if (asMarkdown) renderThreadMarkdown(title, messages)
        else renderThread(title, messages)

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "$safeTitle.$ext")
        file.writeText(body)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (asMarkdown) "text/markdown" else "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export").addNewTaskIfNeeded(context))
    }

    /** The same three roles as [renderThread]; a SYSTEM notice becomes an aside. */
    internal fun renderThreadMarkdown(title: String?, messages: List<MessageEntity>): String =
        buildString {
            appendLine("# ${title ?: "Kam AI conversation"}")
            appendLine()
            messages.forEach { m ->
                if (m.role == Role.SYSTEM) {
                    appendLine("_${m.content.trim()}_")
                } else {
                    appendLine("**${if (m.role == Role.USER) "You" else "Kam AI"}**")
                    appendLine()
                    appendLine(if (m.role == Role.USER) m.content.trim() else cleaned(m.content))
                }
                appendLine()
            }
        }.trim()

    /**
     * The name an exported file should carry.
     *
     * The conversation's own title when it has one. Falling back to the first
     * thing anybody actually said, skipping SYSTEM notices: a conversation whose
     * mode was switched at the top used to export as a file named after the
     * mode-change notice (issue #41).
     */
    fun exportName(title: String?, messages: List<MessageEntity>): String? =
        title?.takeIf { it.isNotBlank() }
            ?: messages.firstOrNull { it.role != Role.SYSTEM }?.content?.trim()?.take(40)

    /**
     * Assistant text with stray template markers taken out (#59), so an export
     * does not carry a marker out of the app and into somebody's notes, where it
     * is even harder to explain than it is in a bubble.
     */
    private fun cleaned(content: String): String =
        com.kamsiob.kamai.llm.PromptBuilder.withoutControlTokens(content).trim()

    private fun Intent.addNewTaskIfNeeded(context: Context): Intent {
        if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return this
    }
}
