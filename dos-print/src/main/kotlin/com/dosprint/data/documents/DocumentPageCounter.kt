package com.dosprint.data.documents

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.dosprint.core.AppLog
import com.dosprint.data.media.DocumentFormatDetector
import com.dosprint.domain.model.DocumentKind
import java.io.IOException

class DocumentPageCounter(
    private val context: Context,
) {
    fun pageCount(uri: String): Int? {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return null
        val format = DocumentFormatDetector.detect(context, parsed)
        return when (format.kind) {
            DocumentKind.PDF -> pdfPageCount(parsed)
            DocumentKind.IMAGE -> imagePageCount(parsed)
            DocumentKind.TEXT, DocumentKind.MARKDOWN -> textPageCount(parsed)
            else -> null
        }
    }

    private fun pdfPageCount(uri: Uri): Int? = openDescriptor(uri)?.use { descriptor ->
        try {
            PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
        } catch (error: SecurityException) {
            AppLog.warn(AppLog.throwableTag("pdfPageCount", error))
            null
        } catch (error: IOException) {
            AppLog.warn(AppLog.throwableTag("pdfPageCount", error))
            null
        } catch (error: IllegalArgumentException) {
            AppLog.warn("Not a readable PDF: $uri", error)
            null
        }
    }

    private fun imagePageCount(uri: Uri): Int? = openDescriptor(uri)?.use { descriptor ->
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            if (options.outWidth > 0 && options.outHeight > 0) 1 else null
        } catch (error: Exception) {
            AppLog.warn(AppLog.throwableTag("imagePageCount", error))
            null
        }
    }

    private fun textPageCount(uri: Uri): Int? = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val reader = stream.bufferedReader()
            val charactersPerPage = 2600
            var total = 0L
            var line: String? = reader.readLine()
            while (line != null && total < MAX_CHARACTERS_SCANNED) {
                total += line.length + 1
                line = reader.readLine()
            }
            ((total + charactersPerPage - 1) / charactersPerPage).toInt().coerceAtLeast(1)
        }
    } catch (error: Exception) {
        AppLog.warn(AppLog.throwableTag("textPageCount", error))
        null
    }

    private fun openDescriptor(uri: Uri): ParcelFileDescriptor? = try {
        context.contentResolver.openFileDescriptor(uri, "r")
    } catch (error: SecurityException) {
        AppLog.warn(AppLog.throwableTag("openFileDescriptor", error))
        null
    } catch (error: IOException) {
        AppLog.warn(AppLog.throwableTag("openFileDescriptor", error))
        null
    } catch (error: IllegalArgumentException) {
        AppLog.warn("Cannot open document: $uri", error)
        null
    }

    private companion object {
        const val MAX_CHARACTERS_SCANNED = 2_000_000L
    }
}
