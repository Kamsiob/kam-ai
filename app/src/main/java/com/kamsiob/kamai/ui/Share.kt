package com.kamsiob.kamai.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.kamsiob.kamai.data.MessageEntity
import com.kamsiob.kamai.data.Role
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

    /** A whole conversation as clean, readable plain text. */
    fun renderThread(title: String?, messages: List<MessageEntity>): String = buildString {
        appendLine(title ?: "Kam AI conversation")
        appendLine()
        messages.forEach { m ->
            append(if (m.role == Role.USER) "You: " else "Kam AI: ")
            appendLine(m.content.trim())
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

    private fun renderThreadMarkdown(title: String?, messages: List<MessageEntity>): String =
        buildString {
            appendLine("# ${title ?: "Kam AI conversation"}")
            appendLine()
            messages.forEach { m ->
                appendLine("**${if (m.role == Role.USER) "You" else "Kam AI"}**")
                appendLine()
                appendLine(m.content.trim())
                appendLine()
            }
        }.trim()

    private fun Intent.addNewTaskIfNeeded(context: Context): Intent {
        if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return this
    }
}
