package com.dosprint.data.print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.print.PrintAttributes
import com.dosprint.core.AppLog
import com.dosprint.data.media.ContentGeometry
import com.dosprint.data.media.PageSource
import com.dosprint.data.media.RenderBox
import com.dosprint.domain.model.ColorMode
import com.dosprint.domain.model.ImagePageSettings
import com.dosprint.domain.model.ImageScaling
import com.dosprint.domain.model.PhotoSizePreset
import com.dosprint.domain.model.PrintSettings
import com.dosprint.domain.model.WatermarkStyle
import kotlin.math.min
import kotlin.math.roundToInt

class PrintDocumentComposer(
    private val settings: PrintSettings,
    private val quality: PrintQualityProfile,
    private val imageMode: Boolean = false,
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val photoBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
    }

    fun composeSheet(
        canvas: Canvas,
        mediaWidthPx: Int,
        mediaHeightPx: Int,
        sources: List<Pair<PageSource, Int>>,
    ): Boolean {
        if (sources.isEmpty()) {
            return false
        }
        if (mediaWidthPx <= 0 || mediaHeightPx <= 0 || sources.isEmpty()) {
            return false
        }
        canvas.drawColor(Color.WHITE)
        val slots = slotRects(mediaWidthPx, mediaHeightPx, sources.size)
        var drawn = false
        for ((index, entry) in sources.withIndex()) {
            val slot = slots.getOrNull(index) ?: break
            val (source, pageIndex) = entry
            val drawable = renderSlot(source, pageIndex, slot) ?: continue
            if (imageMode) {
                drawImageIntoSlot(canvas, drawable, slot, settings.imagePageSettings, mediaWidthPx)
            } else {
                drawIntoSlot(canvas, drawable, slot)
            }
            recycleIfOwned(drawable, source, slot)
            drawn = true
        }
        drawWatermark(canvas, mediaWidthPx, mediaHeightPx)
        return drawn
    }

    private fun slotRects(mediaWidthPx: Int, mediaHeightPx: Int, itemCount: Int): List<RectF> {
        if (imageMode) {
            return imageSlotRects(mediaWidthPx, mediaHeightPx, itemCount)
        }
        val grid = settings.layoutGrid
        val pageWidth = mediaWidthPx.toFloat()
        val pageHeight = mediaHeightPx.toFloat()
        return grid.allSlots(pageWidth, pageHeight).map { slot ->
            RectF(
                slot[0],
                slot[1],
                slot[2],
                slot[3],
            )
        }
    }

    private fun imageSlotRects(mediaWidthPx: Int, mediaHeightPx: Int, itemCount: Int): List<RectF> {
        val page = settings.imagePageSettings
        val printGrid = settings.layoutGrid
        val paper = settings.effectivePaper
        val grid = page.layoutGridFor(
            pageAspect = settings.sheetAspect,
            pageWidthInches = paper.widthInches,
            pageHeightInches = paper.heightInches,
            marginFraction = printGrid.marginFraction,
            cellBorderFraction = settings.cellBorderFraction,
        ).copy(
            marginFraction = printGrid.marginFraction,
            cellBorderFraction = settings.cellBorderFraction,
        )
        val safeCount = itemCount.coerceIn(1, page.slotsPerSheet)
        val pageWidth = mediaWidthPx.toFloat()
        val pageHeight = mediaHeightPx.toFloat()
        if (page.photoSize != PhotoSizePreset.AUTO) {
            return fixedPhotoSlotRects(
                page = page,
                grid = grid,
                itemCount = safeCount,
                pageWidth = pageWidth,
                pageHeight = pageHeight,
            )
        }
        val bounds = grid.allSlots(pageWidth, pageHeight).map { slot ->
            RectF(slot[0], slot[1], slot[2], slot[3])
        }
        val indexes = when {
            safeCount >= bounds.size -> bounds.indices.toList()
            !page.compactGroup && safeCount > 1 -> {
                bounds.indices.map { index -> (index * (bounds.lastIndex.toFloat() / (safeCount - 1))).roundToInt() }
                    .distinct()
                    .take(safeCount)
            }
            else -> bounds.indices.take(safeCount)
        }
        val selected = indexes.mapNotNull(bounds::getOrNull)
        val shouldPositionGroup = selected.isNotEmpty() &&
            page.usesGrid &&
            page.compactGroup &&
            safeCount < bounds.size
        if (!shouldPositionGroup) {
            return selected
        }
        val group = RectF(selected.first())
        selected.drop(1).forEach(group::union)
        val gridBounds = RectF(bounds.first())
        bounds.drop(1).forEach(gridBounds::union)
        val anchorX = page.position.column / 2f
        val anchorY = page.position.row / 2f
        val targetX = gridBounds.left + gridBounds.width() * anchorX
        val targetY = gridBounds.top + gridBounds.height() * anchorY
        val offsetX = targetX - (group.left + group.width() * anchorX)
        val offsetY = targetY - (group.top + group.height() * anchorY)
        return selected.map { slot -> RectF(slot).apply { offset(offsetX, offsetY) } }
    }

    private fun fixedPhotoSlotRects(
        page: ImagePageSettings,
        grid: com.dosprint.domain.model.LayoutGrid,
        itemCount: Int,
        pageWidth: Float,
        pageHeight: Float,
    ): List<RectF> {
        val paper = settings.effectivePaper
        if (!page.fitsPhotoCount(
                count = itemCount,
                pageAspect = settings.sheetAspect,
                pageWidthInches = paper.widthInches,
                pageHeightInches = paper.heightInches,
                marginFraction = grid.marginFraction,
                cellBorderFraction = settings.cellBorderFraction,
            )
        ) {
            return emptyList()
        }
        val photoWidth = page.photoWidthInches * pageWidth / paper.widthInches
        val photoHeight = page.photoHeightInches * pageHeight / paper.heightInches
        val marginX = pageWidth * grid.marginFraction
        val marginY = pageHeight * grid.marginFraction
        val printableBounds = RectF(
            marginX,
            marginY,
            pageWidth - marginX,
            pageHeight - marginY,
        )
        if (page.usesGrid && page.compactGroup) {
            val compactPage = page.withImagesPerPage(itemCount, settings.sheetAspect)
            val compactGrid = compactPage.layoutGridFor(
                pageAspect = settings.sheetAspect,
                pageWidthInches = paper.widthInches,
                pageHeightInches = paper.heightInches,
                marginFraction = grid.marginFraction,
            )
            val columns = compactGrid.columns
            val rows = (itemCount + columns - 1) / columns
            val gapX = printableBounds.width() * compactGrid.gutterFraction
            val gapY = printableBounds.height() * compactGrid.gutterFraction
            val groupWidth = columns * photoWidth + (columns - 1) * gapX
            val groupHeight = rows * photoHeight + (rows - 1) * gapY
            val groupLeft = printableBounds.left + (printableBounds.width() - groupWidth) * page.position.column / 2f
            val groupTop = printableBounds.top + (printableBounds.height() - groupHeight) * page.position.row / 2f
            return (0 until itemCount).map { index ->
                val row = index / columns
                val column = index % columns
                val left = groupLeft + column * (photoWidth + gapX)
                val top = groupTop + row * (photoHeight + gapY)
                RectF(left, top, left + photoWidth, top + photoHeight)
            }
        }
        if (!page.usesGrid) {
            val left = printableBounds.left + (printableBounds.width() - photoWidth) * page.position.column / 2f
            val top = printableBounds.top + (printableBounds.height() - photoHeight) * page.position.row / 2f
            return listOf(RectF(left, top, left + photoWidth, top + photoHeight))
        }
        val cells = grid.allSlots(pageWidth, pageHeight).map { RectF(it[0], it[1], it[2], it[3]) }
        val indexes = when {
            itemCount >= cells.size -> cells.indices.toList()
            itemCount > 1 ->
                cells.indices
                    .map { index -> (index * (cells.lastIndex.toFloat() / (itemCount - 1))).roundToInt() }
                    .distinct()
                    .take(itemCount)
            else -> cells.indices.take(itemCount)
        }
        return indexes.mapNotNull(cells::getOrNull).map { cell ->
            val left = cell.left + (cell.width() - photoWidth) * page.position.column / 2f
            val top = cell.top + (cell.height() - photoHeight) * page.position.row / 2f
            RectF(left, top, left + photoWidth, top + photoHeight)
        }
    }

    private fun renderSlot(source: PageSource, pageIndex: Int, slot: RectF): Bitmap? {
        val rotation = if (imageMode) settings.imagePageSettings.rotation else settings.rotation
        val boxWidth = if (rotation.swapsDimensions) slot.height() else slot.width()
        val boxHeight = if (rotation.swapsDimensions) slot.width() else slot.height()
        val box = RenderBox(boxWidth.toInt().coerceAtLeast(1), boxHeight.toInt().coerceAtLeast(1))
        val bitmap = source.renderPage(pageIndex, box.widthPx, box.heightPx) ?: return null
        if (settings.colorMode == ColorMode.COLOR) {
            return bitmap
        }
        val converted = com.dosprint.data.media.BitmapTransforms.toGrayscaleIfRequested(bitmap, settings.colorMode)
        return converted
    }

    private fun drawIntoSlot(canvas: Canvas, bitmap: Bitmap, slot: RectF) {
        val rotation = settings.rotation
        val targetWidth = if (rotation.swapsDimensions) slot.height() else slot.width()
        val targetHeight = if (rotation.swapsDimensions) slot.width() else slot.height()
        val target = RectF(
            slot.centerX() - targetWidth / 2f,
            slot.centerY() - targetHeight / 2f,
            slot.centerX() + targetWidth / 2f,
            slot.centerY() + targetHeight / 2f,
        )
        val placement = ContentGeometry.placement(
            sourceWidth = bitmap.width.toFloat(),
            sourceHeight = bitmap.height.toFloat(),
            target = target,
            scaling = settings.scaling,
            scalePercent = settings.scalePercent,
        )
        canvas.save()
        canvas.clipRect(slot)
        canvas.rotate(rotation.degrees.toFloat(), slot.centerX(), slot.centerY())
        paint.alpha = 255
        canvas.drawBitmap(bitmap, null, placement, paint)
        canvas.restore()
    }

    private fun drawImageIntoSlot(
        canvas: Canvas,
        bitmap: Bitmap,
        slot: RectF,
        page: ImagePageSettings,
        mediaWidthPx: Int,
    ) {
        val rotation = page.rotation
        val targetWidth = (if (rotation.swapsDimensions) slot.height() else slot.width()).coerceAtLeast(1f)
        val targetHeight = (if (rotation.swapsDimensions) slot.width() else slot.height()).coerceAtLeast(1f)
        val target = RectF(
            slot.centerX() - targetWidth / 2f,
            slot.centerY() - targetHeight / 2f,
            slot.centerX() + targetWidth / 2f,
            slot.centerY() + targetHeight / 2f,
        )
        val scale = when (page.scaling) {
            ImageScaling.CROP_TO_FIT -> maxOf(targetWidth / bitmap.width, targetHeight / bitmap.height)
            ImageScaling.SCALE_TO_FIT -> minOf(targetWidth / bitmap.width, targetHeight / bitmap.height)
            ImageScaling.STRETCH_TO_FIT -> 1f
        }
        val drawWidth = if (page.scaling == ImageScaling.STRETCH_TO_FIT) targetWidth else bitmap.width * scale
        val drawHeight = if (page.scaling == ImageScaling.STRETCH_TO_FIT) targetHeight else bitmap.height * scale
        val anchorX = if (page.photoSize == PhotoSizePreset.AUTO) page.position.column / 2f else 0.5f
        val anchorY = if (page.photoSize == PhotoSizePreset.AUTO) page.position.row / 2f else 0.5f
        val placement = RectF(
            target.left + (target.width() - drawWidth) * anchorX,
            target.top + (target.height() - drawHeight) * anchorY,
            target.left + (target.width() - drawWidth) * anchorX + drawWidth,
            target.top + (target.height() - drawHeight) * anchorY + drawHeight,
        )
        canvas.save()
        canvas.clipRect(slot)
        canvas.rotate(rotation.degrees.toFloat(), slot.centerX(), slot.centerY())
        paint.alpha = 255
        canvas.drawBitmap(bitmap, null, placement, paint)
        if (page.border.widthPoints > 0f) {
            val pageWidthPoints = settings.effectivePaper.widthInches * POINTS_PER_INCH
            val strokeWidth = page.border.widthPoints * mediaWidthPx / pageWidthPoints.coerceAtLeast(1f)
            val outline = when {
                page.photoSize != PhotoSizePreset.AUTO -> target
                page.scaling == ImageScaling.SCALE_TO_FIT -> placement
                else -> target
            }
            val halfStroke = strokeWidth / 2f
            val border = RectF(outline).apply { inset(halfStroke, halfStroke) }
            photoBorderPaint.strokeWidth = strokeWidth
            canvas.drawRect(border, photoBorderPaint)
        }
        canvas.restore()
    }

    private fun recycleIfOwned(bitmap: Bitmap, source: PageSource, slot: RectF) {
        if (!quality.recycleIntermediateBitmaps && source.pageCount > 1) {
            return
        }
        if (bitmap.width == slot.width().toInt() && bitmap.height == slot.height().toInt()) {
            return
        }
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    private fun drawWatermark(canvas: Canvas, widthPx: Int, heightPx: Int) {
        val text = settings.watermark.text(settings.watermarkText)
        if (settings.watermark == WatermarkStyle.NONE || text.isBlank()) {
            return
        }
        val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WATERMARK_COLOR
            textSize = min(widthPx, heightPx) * WATERMARK_TEXT_FRACTION
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val baseline = heightPx / 2f - (watermarkPaint.descent() + watermarkPaint.ascent()) / 2f
        canvas.save()
        canvas.rotate(WATERMARK_ROTATION_DEGREES, widthPx / 2f, heightPx / 2f)
        canvas.drawText(text, widthPx / 2f, baseline, watermarkPaint)
        canvas.restore()
    }

    fun mediaPixelSize(attributes: PrintAttributes): Pair<Int, Int> {
        val size = attributes.mediaSize ?: PrintAttributes.MediaSize.UNKNOWN_PORTRAIT
        val resolution = attributes.resolution
            ?: PrintAttributes.Resolution("default", "Default", DEFAULT_DPI, DEFAULT_DPI)
        val width = quality.toPixels(size.widthMils, resolution.horizontalDpi)
        val height = quality.toPixels(size.heightMils, resolution.verticalDpi)
        return width.coerceIn(1, MAX_MEDIA_EDGE_PX) to height.coerceIn(1, MAX_MEDIA_EDGE_PX)
    }

    private companion object {
        const val MINIMUM_MARGIN_FRACTION = 0.04f
        const val WATERMARK_TEXT_FRACTION = 0.14f
        const val WATERMARK_ROTATION_DEGREES = -35f
        const val WATERMARK_COLOR = 0x24A0A0A0
        const val MAX_MEDIA_EDGE_PX = 10000
        const val DEFAULT_DPI = 300
        const val POINTS_PER_INCH = 72f
        const val MILS_PER_INCH = 1000f
    }
}

data class PrintQualityProfile(
    val maxMediaEdgePx: Int,
    val maxBitmapPixels: Long,
    val recycleIntermediateBitmaps: Boolean,
    val maxConcurrentDocuments: Int,
) {
    fun toPixels(mils: Int, dpi: Int): Int {
        val milsPerInch = 1000f
        val safeDpi = dpi.coerceAtLeast(MIN_DPI)
        val pixels = (mils.toFloat() / milsPerInch * safeDpi).toInt()
        val budgetScale = minOf(
            1f,
            kotlin.math.sqrt(maxBitmapPixels.toDouble() / (pixels.toLong() * pixels.coerceAtLeast(1)).coerceAtLeast(1)).toFloat(),
        )
        return (pixels * budgetScale).toInt().coerceIn(1, maxMediaEdgePx)
    }

    fun lowMemory(): PrintQualityProfile = copy(
        maxMediaEdgePx = minOf(maxMediaEdgePx, LOW_MEMORY_MAX_EDGE_PX),
        maxBitmapPixels = minOf(maxBitmapPixels, LOW_MEMORY_MAX_PIXELS),
        recycleIntermediateBitmaps = true,
        maxConcurrentDocuments = 1,
    )

    companion object {
        const val MIN_DPI = 72

        fun forDpi(dpi: Int): PrintQualityProfile = when {
            dpi <= DRAFT_DPI_CEILING -> Draft
            dpi <= STANDARD_DPI_CEILING -> Standard
            dpi <= HIGH_DPI_CEILING -> High
            else -> Best
        }

        private const val DRAFT_DPI_CEILING = 200
        private const val STANDARD_DPI_CEILING = 400
        private const val HIGH_DPI_CEILING = 900
        private const val LOW_MEMORY_MAX_EDGE_PX = 2000
        private const val LOW_MEMORY_MAX_PIXELS = 4_000_000L

        val Draft = PrintQualityProfile(
            maxMediaEdgePx = 2400,
            maxBitmapPixels = 8_000_000L,
            recycleIntermediateBitmaps = true,
            maxConcurrentDocuments = 1,
        )

        val Standard = PrintQualityProfile(
            maxMediaEdgePx = 4800,
            maxBitmapPixels = 16_000_000L,
            recycleIntermediateBitmaps = true,
            maxConcurrentDocuments = 2,
        )

        val High = PrintQualityProfile(
            maxMediaEdgePx = 8000,
            maxBitmapPixels = 32_000_000L,
            recycleIntermediateBitmaps = false,
            maxConcurrentDocuments = 2,
        )

        val Best = PrintQualityProfile(
            maxMediaEdgePx = 10000,
            maxBitmapPixels = 48_000_000L,
            recycleIntermediateBitmaps = false,
            maxConcurrentDocuments = 1,
        )

        val MemorySaver = PrintQualityProfile(
            maxMediaEdgePx = 1800,
            maxBitmapPixels = 3_000_000L,
            recycleIntermediateBitmaps = true,
            maxConcurrentDocuments = 1,
        )
    }
}

internal fun logCompositionFailure(reason: String) {
    AppLog.warn("Sheet composition failed: $reason")
}
