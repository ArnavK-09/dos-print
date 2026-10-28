package com.dosprint.data.render

import android.content.Context
import android.graphics.Bitmap
import com.dosprint.core.AppError
import com.dosprint.core.AppLog
import com.dosprint.core.Outcome
import com.dosprint.data.media.PageSource
import com.dosprint.data.media.PageSourceFactory
import com.dosprint.data.print.PrintDocumentComposer
import com.dosprint.data.print.PrintDocumentInput
import com.dosprint.data.print.PrintPagePlanner
import com.dosprint.data.print.PrintQualityProfile
import com.dosprint.domain.model.DocumentKind
import com.dosprint.domain.model.PrintDocument
import com.dosprint.domain.model.PrintResolution
import com.dosprint.domain.model.PrintSettings
import com.dosprint.domain.model.SheetLayout
import kotlin.math.roundToInt

class SheetPreviewRenderer(
    private val context: Context,
) {
    private val pageSourceFactory = PageSourceFactory(context)

    fun renderSheets(
        documents: List<PrintDocument>,
        settings: PrintSettings,
        maxSheets: Int,
        targetWidthPx: Int,
        startSheetIndex: Int = 0,
    ): Outcome<List<Bitmap>> {
        if (documents.isEmpty()) {
            return Outcome.Failure(AppError.NoDocumentSelected)
        }
        if (maxSheets <= 0 || targetWidthPx <= 0) {
            return Outcome.Success(emptyList())
        }
        val inputs = documents.map { document ->
            PrintDocumentInput(
                uri = document.uri,
                displayName = document.displayName,
                mimeType = document.mimeType,
                pageCountHint = document.detectedPageCount ?: 1,
            )
        }
        val imageMode = documents.isNotEmpty() && documents.all { it.format.kind == DocumentKind.IMAGE }
        val plan = PrintPagePlanner.plan(inputs.map { it.pageCountHint }, settings, imageMode)
        val orderedSheets = plan.sheets
            .sortedBy { it.index }
            .let { sheets ->
                if (settings.pageOrder.reversesOrder()) sheets.sortedByDescending { it.index } else sheets
            }
        val sheets = orderedSheets.drop(startSheetIndex.coerceAtLeast(0)).take(maxSheets)
        if (sheets.isEmpty()) {
            return Outcome.Failure(AppError.NoPrintablePages)
        }
        val previewSettings = settings.copy(
            resolution = PrintResolution.DRAFT,
        )
        val quality = PrintQualityProfile.forDpi(PreviewDpi)
        val composer = PrintDocumentComposer(previewSettings, quality, imageMode)
        val sources = ArrayList<PageSource>(inputs.size)
        val rendered = ArrayList<Bitmap>(sheets.size)
        return try {
            for (document in documents) {
                when (val outcome = pageSourceFactory.create(document)) {
                    is Outcome.Success -> sources += outcome.value
                    is Outcome.Failure -> {
                        AppLog.warn("Preview skipped ${document.displayName}: ${outcome.error}")
                    }
                }
            }
            if (sources.isEmpty()) {
                return Outcome.Failure(AppError.FileUnreadable)
            }
            val sheetHeight = sheetHeightFor(settings, targetWidthPx)
            for (sheet in sheets) {
                val entries = sheet.pages.mapNotNull { page ->
                    sources.getOrNull(page.documentIndex)?.let { it to page.pageIndex }
                }
                if (entries.isEmpty()) {
                    continue
                }
                renderOne(composer, entries, targetWidthPx, sheetHeight)?.let(rendered::add)
            }
            if (rendered.isEmpty()) {
                Outcome.Failure(AppError.NoPrintablePages)
            } else {
                Outcome.Success(rendered)
            }
        } catch (error: OutOfMemoryError) {
            rendered.forEach { it.recycleSafely() }
            AppLog.error("Out of memory while rendering preview", error)
            Outcome.Failure(AppError.OutOfMemory)
        } catch (error: Exception) {
            rendered.forEach { it.recycleSafely() }
            AppLog.error(AppLog.throwableTag("renderSheets", error))
            Outcome.Failure(AppError.Unknown(error))
        } finally {
            sources.forEach { source -> runCatching { source.close() } }
        }
    }

    fun sheetCountFor(documents: List<PrintDocument>, settings: PrintSettings): Int {
        val imageMode = documents.isNotEmpty() && documents.all { it.format.kind == DocumentKind.IMAGE }
        return PrintPagePlanner.plan(documents.map { it.detectedPageCount ?: 1 }, settings, imageMode).sheetCount
    }

    fun sheetAspectFor(settings: PrintSettings): Float = settings.sheetAspect

    private fun sheetHeightFor(settings: PrintSettings, targetWidthPx: Int): Int {
        val aspect = settings.sheetAspect
        if (!aspect.isFinite() || aspect <= 0f) {
            return targetWidthPx
        }
        val height = (targetWidthPx / aspect).roundToInt()
        return height.coerceIn(MinSheetEdgePx, MaxSheetEdgePx)
    }

    private fun renderOne(
        composer: PrintDocumentComposer,
        entries: List<Pair<PageSource, Int>>,
        widthPx: Int,
        heightPx: Int,
    ): Bitmap? = try {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        if (composer.composeSheet(android.graphics.Canvas(bitmap), widthPx, heightPx, entries)) {
            bitmap
        } else {
            bitmap.recycleSafely()
            null
        }
    } catch (error: OutOfMemoryError) {
        AppLog.error("Out of memory rendering preview sheet", error)
        null
    }

    private fun Bitmap.recycleSafely() {
        if (!isRecycled) {
            recycle()
        }
    }

    companion object {
        const val PreviewDpi = 150
        const val MinSheetEdgePx = 160
        const val MaxSheetEdgePx = 4000
    }
}

internal fun SheetLayout.slotCountFor(): Int = count

internal fun Bitmap.byteCountOrZero(): Long = try {
    allocationByteCount.toLong()
} catch (error: Exception) {
    AppLog.debug(AppLog.throwableTag("allocationByteCount", error))
    width.toLong() * height.toLong() * BytesPerPixel
}

private const val BytesPerPixel = 4L
