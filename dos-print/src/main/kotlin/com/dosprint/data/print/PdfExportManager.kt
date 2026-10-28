package com.dosprint.data.print

import android.content.Context
import android.net.Uri
import android.print.pdf.PrintedPdfDocument
import com.dosprint.core.AppError
import com.dosprint.core.AppLog
import com.dosprint.core.Outcome
import com.dosprint.data.media.PageSource
import com.dosprint.data.media.PageSourceFactory
import com.dosprint.domain.model.DocumentKind
import com.dosprint.domain.model.PdfDestinationCapabilities
import com.dosprint.domain.model.PrintDocument
import com.dosprint.domain.model.PrintSettings
import com.dosprint.domain.model.PrintSettingsResolver
import java.io.IOException

class PdfExportManager(
    private val context: Context,
    private val coordinator: PrintJobCoordinator,
) {
    private val pageSourceFactory = PageSourceFactory(context)

    fun export(
        destination: Uri,
        documents: List<PrintDocument>,
        requestedSettings: PrintSettings,
        lowMemory: Boolean,
    ): Outcome<Unit> {
        if (documents.isEmpty()) {
            return Outcome.Failure(AppError.NoDocumentSelected)
        }
        val totalPages = documents.sumOf { it.detectedPageCount ?: 1 }
        val settings = PrintSettingsResolver.resolve(
            requestedSettings,
            PdfDestinationCapabilities,
            totalPages,
        )
        val imageMode = documents.all { it.format.kind == DocumentKind.IMAGE }
        val inputs = documents.map { document ->
            PrintDocumentInput(
                uri = document.uri,
                displayName = document.displayName,
                mimeType = document.mimeType,
                pageCountHint = document.detectedPageCount ?: 1,
            )
        }
        val plan = PrintPagePlanner.plan(inputs.map { it.pageCountHint }, settings, imageMode)
        if (plan.sheetCount == 0) {
            return Outcome.Failure(AppError.NoPrintablePages)
        }
        val attributes = coordinator.buildAttributes(settings, PdfDestinationCapabilities)
        val composer = PrintDocumentComposer(
            settings,
            coordinator.qualityFor(settings, lowMemory),
            imageMode,
        )
        val (widthPx, heightPx) = composer.mediaPixelSize(attributes)
        if (widthPx <= 0 || heightPx <= 0) {
            return Outcome.Failure(AppError.NoPrintablePages)
        }
        val sources = MutableList<PageSource?>(inputs.size) { null }
        var pdfDocument: PrintedPdfDocument? = null
        return try {
            inputs.forEachIndexed { index, input ->
                when (val outcome = pageSourceFactory.create(input.toPrintDocument())) {
                    is Outcome.Success -> sources[index] = outcome.value
                    is Outcome.Failure -> AppLog.warn("Cannot export ${input.displayName}: ${outcome.error}")
                }
            }
            if (sources.none { it != null }) {
                return Outcome.Failure(AppError.FileUnreadable)
            }
            val pdf = PrintedPdfDocument(context, attributes)
            pdfDocument = pdf
            val pageWidth = pdf.pageWidth
            val pageHeight = pdf.pageHeight
            if (pageWidth <= 0 || pageHeight <= 0) {
                return Outcome.Failure(AppError.NoPrintablePages)
            }
            val output = context.contentResolver.openOutputStream(destination, "w")
                ?: return Outcome.Failure(AppError.StorageUnavailable)
            output.use { stream ->
                var pdfPageNumber = 0
                for (sheet in plan.sheets) {
                    val entries = sheet.pages.mapNotNull { page ->
                        sources.getOrNull(page.documentIndex)?.let { it to page.pageIndex }
                    }
                    if (entries.isEmpty()) {
                        continue
                    }
                    val page = pdf.startPage(pdfPageNumber)
                    val canvas = page.canvas
                    canvas.save()
                    canvas.scale(pageWidth.toFloat() / widthPx, pageHeight.toFloat() / heightPx)
                    val composed = composer.composeSheet(canvas, widthPx, heightPx, entries)
                    canvas.restore()
                    pdfDocument.finishPage(page)
                    if (!composed) {
                        return Outcome.Failure(AppError.NoPrintablePages)
                    }
                    pdfPageNumber++
                }
                if (pdfPageNumber == 0) {
                    return Outcome.Failure(AppError.NoPrintablePages)
                }
                pdf.writeTo(stream)
                stream.flush()
            }
            Outcome.Success(Unit)
        } catch (error: IOException) {
            AppLog.error(AppLog.throwableTag("exportPdf", error))
            Outcome.Failure(AppError.Unknown(error))
        } catch (error: OutOfMemoryError) {
            AppLog.error("Out of memory while exporting PDF", error)
            Outcome.Failure(AppError.OutOfMemory)
        } catch (error: Exception) {
            AppLog.error(AppLog.throwableTag("exportPdf", error))
            Outcome.Failure(AppError.Unknown(error))
        } finally {
            pdfDocument?.close()
            sources.filterNotNull().forEach { source -> runCatching { source.close() } }
        }
    }

    private fun PrintDocumentInput.toPrintDocument(): PrintDocument = PrintDocument(
        uri = uri,
        displayName = displayName,
        sizeBytes = 0,
        mimeType = mimeType,
        format = com.dosprint.domain.model.DocumentFormat.fromMimeType(mimeType, displayName),
        addedAtMillis = 0,
    )
}
