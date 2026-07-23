package com.kamsiob.kamai.files

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * On-device text extraction for the three formats that carry real parsing risk:
 * plain text, DOCX, and a PDF with a text layer. Proves the file-attachment
 * pipeline pulls the right text out on the phone.
 */
@RunWith(AndroidJUnit4::class)
class FileExtractorTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun readsPlainText() = runBlocking {
        val f = File(context.cacheDir, "note.txt").apply { writeText("The meeting is on Tuesday.") }
        val r = FileExtractor.extract(context, Uri.fromFile(f))
        assertTrue(r is FileExtractor.Result.Ok)
        assertTrue((r as FileExtractor.Result.Ok).text.contains("meeting is on Tuesday"))
    }

    @Test
    fun readsDocx() = runBlocking {
        val f = File(context.cacheDir, "doc.docx")
        ZipOutputStream(f.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(
                ("""<?xml version="1.0"?><w:document xmlns:w="x"><w:body>""" +
                    """<w:p><w:r><w:t>Falcon budget is 2.4 million.</w:t></w:r></w:p>""" +
                    """<w:p><w:r><w:t>Lead engineer is Dana.</w:t></w:r></w:p>""" +
                    """</w:body></w:document>""").toByteArray(),
            )
            zip.closeEntry()
        }
        val r = FileExtractor.extract(context, Uri.fromFile(f))
        assertTrue(r is FileExtractor.Result.Ok)
        val text = (r as FileExtractor.Result.Ok).text
        assertTrue(text.contains("Falcon budget is 2.4 million"))
        assertTrue(text.contains("Lead engineer is Dana"))
    }

    @Test
    fun readsPdfTextLayer() = runBlocking {
        PDFBoxResourceLoader.init(context)
        val f = File(context.cacheDir, "doc.pdf")
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(72f, 700f)
                cs.showText("The capital of Japan is Tokyo.")
                cs.endText()
            }
            doc.save(f)
        }
        val r = FileExtractor.extract(context, Uri.fromFile(f))
        assertTrue("expected text PDF to extract, got $r", r is FileExtractor.Result.Ok)
        assertTrue((r as FileExtractor.Result.Ok).text.contains("capital of Japan is Tokyo"))
    }

    @Test
    fun refusesUnsupported() = runBlocking {
        val f = File(context.cacheDir, "pic.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val r = FileExtractor.extract(context, Uri.fromFile(f))
        assertTrue(r is FileExtractor.Result.Error)
    }
}
