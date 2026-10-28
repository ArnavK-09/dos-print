package com.dosprint.data.print

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.pdf.PrintedPdfDocument
import com.dosprint.core.AppError
import com.dosprint.core.AppLog
import com.dosprint.core.Outcome
import com.dosprint.data.media.PageSource
import com.dosprint.domain.model.DocumentFormat
import com.dosprint.domain.model.DocumentKind
import com.dosprint.domain.model.PrintSettings
import java.io.IOException
import java.util.concurrent.ExecutorService

data class PrintDocumentInput(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val pageCountHint: Int = 1,
) {
    val isImage: Boolean get() = DocumentFormat.fromMimeType(mimeType, displayName).kind == DocumentKind.IMAGE
}

interface PrintJobState {
    fun onLayoutResolved(jobId: String, plan: PrintPagePlan)

    fun onSheetWritten(jobId: String, sheetIndex: Int, pageCount: Int)

    fun onProgress(jobId: String, pagesWritten: Int, totalPages: Int)

    fun onWriteCompleted(jobId: String, pagesWritten: Int)

    fun onWriteFailed(jobId: String, error: AppError)

    fun onJobFinished(jobId: String)
}

class DosPrintDocumentAdapter(
    private val context: Context,
    private val jobId: String,
    private val documents: List<PrintDocumentInput>,
    private val settings: PrintSettings,
    private val quality: PrintQualityProfile,
    private val state: PrintJobState,
    private val sourceFactory: (PrintDocumentInput) -> Outcome<PageSource>,
    private val writeExecutor: ExecutorService,
) : PrintDocumentAdapter() {

    private var resolvedAttributes: PrintAttributes? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (documents.isEmpty()) {
            callback.onLayoutFailed(REASON_NO_DOCUMENTS)
            return
        }
        if (newAttributes == null) {
            callback.onLayoutFailed(REASON_NO_ATTRIBUTES)
            return
        }
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val imageMode = documents.isNotEmpty() && documents.all { it.isImage }
        val plan = PrintPagePlanner.plan(documents.map { it.pageCountHint.coerceAtLeast(1) }, settings, imageMode)
        if (plan.sheetCount == 0) {
            callback.onLayoutFailed(REASON_EMPTY_SELECTION)
            return
        }
        val info = PrintDocumentInfo.Builder(documentLabel())
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(plan.sheetCount)
            .build()
        resolvedAttributes = newAttributes
        state.onLayoutResolved(jobId, plan)
        callback.onLayoutFinished(info, newAttributes != oldAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        val attributes = resolvedAttributes
        if (attributes == null) {
            callback.onWriteFailed(REASON_NO_LAYOUT)
            return
        }
        writeExecutor.execute {
            val result = writePages(pages, destination, cancellationSignal, attributes)
            when (result) {
                is WriteResult.Completed -> callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                is WriteResult.Cancelled -> callback.onWriteCancelled()
                is WriteResult.Failed -> callback.onWriteFailed(result.reason)
            }
        }
    }

    override fun onFinish() {
        state.onJobFinished(jobId)
    }

    private fun writePages(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        attributes: PrintAttributes,
    ): WriteResult {
        val imageMode = documents.isNotEmpty() && documents.all { it.isImage }
        val composer = PrintDocumentComposer(settings, quality, imageMode)
        val (widthPx, heightPx) = composer.mediaPixelSize(attributes)
        val plan = PrintPagePlanner.plan(documents.map { it.pageCountHint.coerceAtLeast(1) }, settings, imageMode)
        val requestedSheets = resolveRequestedSheets(pages, plan.sheetCount)
        if (widthPx <= 0 || heightPx <= 0 || requestedSheets.isEmpty()) {
            val reason = if (widthPx <= 0 || heightPx <= 0) REASON_MEDIA_SIZE else REASON_NO_SHEETS
            return failWith(reason, AppError.NoPrintablePages)
        }
        val sources = ArrayList<PageSource>(documents.size)
        var pagesWritten = 0
        var pdfPagesWritten = 0
        return try {
            for (document in documents) {
                openSource(document)?.let(sources::add)
            }
            if (sources.isEmpty()) {
                return failWith(REASON_NO_SOURCES, AppError.FileUnreadable)
            }
            val pdfDocument = PrintedPdfDocument(context, attributes)
            val pageWidthPoints = pdfDocument.pageWidth
            val pageHeightPoints = pdfDocument.pageHeight
            if (pageWidthPoints <= 0 || pageHeightPoints <= 0) {
                pdfDocument.close()
                return failWith(REASON_MEDIA_SIZE, AppError.NoPrintablePages)
            }
            ParcelFileDescriptor.AutoCloseOutputStream(destination).use { output ->
                try {
                    for (sheetIndex in requestedSheets) {
                        if (cancellationSignal?.isCanceled == true) {
                            state.onWriteFailed(jobId, AppError.Unknown())
                            return WriteResult.Cancelled
                        }
                        val entries = plan.sheets.getOrNull(sheetIndex)?.pages
                            ?.mapNotNull { page ->
                                sources.getOrNull(page.documentIndex)?.let { it to page.pageIndex }
                            }
                            .orEmpty()
                        if (entries.isEmpty()) {
                            continue
                        }
                        val page = pdfDocument.startPage(pdfPagesWritten)
                        val canvas = page.canvas
                        canvas.save()
                        canvas.scale(
                            pageWidthPoints.toFloat() / widthPx,
                            pageHeightPoints.toFloat() / heightPx,
                        )
                        val composed = composer.composeSheet(canvas, widthPx, heightPx, entries)
                        canvas.restore()
                        pdfDocument.finishPage(page)
                        if (!composed) {
                            return failWith(REASON_COMPOSE_FAILED, AppError.NoPrintablePages)
                        }
                        pdfPagesWritten += 1
                        pagesWritten += entries.size
                        state.onSheetWritten(jobId, sheetIndex, entries.size)
                        state.onProgress(jobId, pagesWritten, plan.outputPageCount)
                    }
                    if (pdfPagesWritten == 0) {
                        return failWith(REASON_NO_SHEETS, AppError.NoPrintablePages)
                    }
                    pdfDocument.writeTo(output)
                    output.flush()
                } finally {
                    pdfDocument.close()
                }
            }
            state.onWriteCompleted(jobId, pagesWritten)
            WriteResult.Completed(pagesWritten)
        } catch (error: IOException) {
            AppLog.error(AppLog.throwableTag("writePages", error))
            state.onWriteFailed(jobId, AppError.Unknown(error))
            WriteResult.Failed(REASON_IO)
        } catch (error: Exception) {
            AppLog.error(AppLog.throwableTag("writePages", error))
            state.onWriteFailed(jobId, AppError.Unknown(error))
            WriteResult.Failed(REASON_IO)
        } catch (error: OutOfMemoryError) {
            AppLog.error("Out of memory while writing print pages", error)
            state.onWriteFailed(jobId, AppError.OutOfMemory)
            WriteResult.Failed(REASON_MEMORY)
        } finally {
            sources.forEach { source -> runCatching { source.close() } }
        }
    }

    private fun failWith(reason: String, error: AppError): WriteResult {
        state.onWriteFailed(jobId, error)
        return WriteResult.Failed(reason)
    }

    private fun resolveRequestedSheets(pages: Array<out PageRange>, sheetCount: Int): List<Int> {
        val result = sortedSetOf<Int>()
        for (pageRange in pages) {
            val start = pageRange.start.coerceAtLeast(1)
            val end = pageRange.end.coerceIn(start, sheetCount)
            for (sheet in start..end) {
                result += sheet - 1
            }
        }
        return result.toList()
    }

    private fun openSource(document: PrintDocumentInput): PageSource? =
        when (val outcome = sourceFactory(document)) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> {
                AppLog.warn("Cannot render ${document.displayName}: ${outcome.error}")
                null
            }
        }

    private fun documentLabel(): String = when (documents.size) {
        1 -> documents.first().displayName
        else -> "Dos Print (${documents.size} files)"
    }

    private sealed interface WriteResult {
        data class Completed(val pagesWritten: Int) : WriteResult

        object Cancelled : WriteResult

        data class Failed(val reason: String) : WriteResult
    }

    private companion object {
        const val REASON_NO_DOCUMENTS = "No documents to print"
        const val REASON_NO_ATTRIBUTES = "The print system supplied no page settings"
        const val REASON_NO_LAYOUT = "Print layout was not established"
        const val REASON_EMPTY_SELECTION = "The selected page range produced no pages"
        const val REASON_NO_SHEETS = "No sheets matched the requested pages"
        const val REASON_NO_SOURCES = "No document could be opened for printing"
        const val REASON_COMPOSE_FAILED = "A sheet could not be composed"
        const val REASON_MEDIA_SIZE = "The media size could not be resolved"
        const val REASON_IO = "Writing the print data failed"
        const val REASON_MEMORY = "Not enough memory to render the document"
    }
}

internal object NoOpPrintJobState : PrintJobState {
    override fun onLayoutResolved(jobId: String, plan: PrintPagePlan) = Unit

    override fun onSheetWritten(jobId: String, sheetIndex: Int, pageCount: Int) = Unit

    override fun onProgress(jobId: String, pagesWritten: Int, totalPages: Int) = Unit

    override fun onWriteCompleted(jobId: String, pagesWritten: Int) = Unit

    override fun onWriteFailed(jobId: String, error: AppError) = Unit

    override fun onJobFinished(jobId: String) = Unit
}
