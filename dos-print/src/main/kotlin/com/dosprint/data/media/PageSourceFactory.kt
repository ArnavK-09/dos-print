package com.dosprint.data.media

import android.content.Context
import android.net.Uri
import com.dosprint.core.AppError
import com.dosprint.core.AppLog
import com.dosprint.core.Outcome
import com.dosprint.core.runCatchingOutcome
import com.dosprint.domain.model.DocumentKind
import com.dosprint.domain.model.PrintDocument

class PageSourceFactory(
    private val context: Context,
) {
    fun create(document: PrintDocument): Outcome<PageSource> {
        val uri = runCatching { Uri.parse(document.uri) }.getOrNull()
            ?: return Outcome.Failure(AppError.FileUnreadable)
        return when (document.format.kind) {
            DocumentKind.PDF -> createPdf(uri)
            DocumentKind.IMAGE -> createImage(uri)
            DocumentKind.TEXT -> createText(uri, renderMarkdown = false)
            DocumentKind.MARKDOWN -> createText(uri, renderMarkdown = true)
            DocumentKind.HTML, DocumentKind.RICH_TEXT -> createText(uri, renderMarkdown = false)
            DocumentKind.OFFICE_DOCUMENT -> createOffice(uri, document)
            DocumentKind.POSTSCRIPT -> Outcome.Failure(AppError.UnsupportedFormat(document.format.extension))
            DocumentKind.UNKNOWN -> Outcome.Failure(AppError.UnsupportedFormat(document.format.extension))
        }
    }

    private fun createPdf(uri: Uri): Outcome<PageSource> {
        val source = PdfPageSource(context, uri)
        if (!source.open()) {
            source.close()
            return Outcome.Failure(AppError.CorruptDocument)
        }
        return Outcome.Success(source)
    }

    private fun createImage(uri: Uri): Outcome<PageSource> = runCatchingOutcome {
        ImagePageSource(context, uri)
    }

    private fun createText(uri: Uri, renderMarkdown: Boolean): Outcome<PageSource> = runCatchingOutcome {
        TextPageSource(context, uri, renderMarkdown)
    }

    private fun createOffice(uri: Uri, document: PrintDocument): Outcome<PageSource> = runCatchingOutcome {
        AppLog.info("Attempting platform rendering for ${document.format.extension} document")
        OfficePageSource(context, uri, document)
    }
}

internal fun Throwable?.toAppError(): AppError = when (this) {
    is java.io.FileNotFoundException -> AppError.FileMissing
    is SecurityException -> AppError.PermissionDenied
    is OutOfMemoryError -> AppError.OutOfMemory
    else -> AppError.Unknown(this)
}
