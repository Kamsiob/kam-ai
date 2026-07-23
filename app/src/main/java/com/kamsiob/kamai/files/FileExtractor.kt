package com.kamsiob.kamai.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipInputStream

/**
 * Pulls the text out of an attached file, entirely on the phone. Handles plain
 * text, Markdown, a PDF that has a real text layer, and DOCX. Everything else is
 * refused with a plain reason rather than a bad extraction: images need a vision
 * model, spreadsheets do not read well as text, and a scanned PDF is just images
 * of text and needs OCR, none of which this version does.
 */
object FileExtractor {

    sealed interface Result {
        data class Ok(val name: String, val text: String) : Result
        data class Error(val message: String) : Result
    }

    private var pdfInit = false

    suspend fun extract(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        val name = displayName(context, uri) ?: "the file"
        val lower = name.lowercase()
        val mime = context.contentResolver.getType(uri).orEmpty()

        try {
            when {
                lower.endsWith(".txt") || lower.endsWith(".md") ||
                    mime.startsWith("text/") ->
                    text(context, uri, name)

                lower.endsWith(".pdf") || mime == "application/pdf" ->
                    pdf(context, uri, name)

                lower.endsWith(".docx") ||
                    mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                    docx(context, uri, name)

                lower.endsWith(".doc") ->
                    Result.Error("Old Word .doc files are not supported. Save it as .docx or a PDF with text.")

                mime.startsWith("image/") ->
                    Result.Error("Kam AI cannot read images. Reading a picture of text needs a different kind of model.")

                lower.endsWith(".csv") || lower.endsWith(".xlsx") || mime.contains("sheet") ->
                    Result.Error("Spreadsheets are not supported. Small models handle tables poorly.")

                else ->
                    Result.Error("That file type is not supported. Try a text file, a Markdown file, a PDF with real text, or a DOCX.")
            }
        } catch (e: Exception) {
            Result.Error("That file could not be read. It may be damaged.")
        }
    }

    private fun text(context: Context, uri: Uri, name: String): Result {
        val content = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        }.orEmpty()
        return if (content.isBlank()) Result.Error("That file is empty.")
        else Result.Ok(name, content.trim())
    }

    private fun pdf(context: Context, uri: Uri, name: String): Result {
        if (!pdfInit) { PDFBoxResourceLoader.init(context.applicationContext); pdfInit = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { doc ->
                val text = PDFTextStripper().getText(doc).trim()
                return if (text.isBlank()) {
                    Result.Error("This PDF has no text to read. It may be a scan, which needs OCR that Kam AI does not do.")
                } else {
                    Result.Ok(name, text)
                }
            }
        }
        return Result.Error("That PDF could not be opened.")
    }

    private fun docx(context: Context, uri: Uri, name: String): Result {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val xml = zip.readBytes().toString(Charsets.UTF_8)
                        return Result.Ok(name, docxText(xml))
                    }
                    entry = zip.nextEntry
                }
            }
        }
        return Result.Error("That DOCX file could not be read.")
    }

    /** Pulls readable text from the DOCX body XML, keeping paragraph and line
     *  breaks as newlines and text runs in document order. */
    private fun docxText(xml: String): String {
        val normalized = xml
            .replace(Regex("</w:p>"), "\n")
            .replace(Regex("<w:br[^>]*/>"), "\n")
        val out = StringBuilder()
        Regex("<w:t[^>]*>(.*?)</w:t>|\n", RegexOption.DOT_MATCHES_ALL)
            .findAll(normalized)
            .forEach { m ->
                if (m.value == "\n") out.append('\n') else out.append(unescape(m.groupValues[1]))
            }
        return out.toString().replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun unescape(s: String) = s
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")

    private fun displayName(context: Context, uri: Uri): String? {
        val fromProvider = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        // content:// documents give a display name; file:// and others fall back
        // to the last path segment so the extension is still visible.
        return fromProvider ?: uri.lastPathSegment?.substringAfterLast('/')
    }
}
