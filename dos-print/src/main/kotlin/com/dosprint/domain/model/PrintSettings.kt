package com.dosprint.domain.model

import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlin.math.roundToInt

@Immutable
data class PrintSettings(
    val paperSize: PaperSize = PaperSize.A4,
    val landscape: Boolean = false,
    val colorMode: ColorMode = ColorMode.COLOR,
    val resolution: PrintResolution = PrintResolution.STANDARD,
    val copies: Int = 1,
    val pageRange: PageRange = PageRange.All,
    val scaling: ScalingMode = ScalingMode.FIT_TO_PAGE,
    val scalePercent: Int = 100,
    val duplex: DuplexMode = DuplexMode.NONE,
    val collate: Boolean = true,
    val rotation: PageRotation = PageRotation.NONE,
    val marginPreset: MarginPreset = MarginPreset.MINIMUM,
    val pagesPerSheet: Int = 1,
    val watermark: WatermarkStyle = WatermarkStyle.NONE,
    val watermarkText: String = "",
    val borderless: Boolean = false,
    val advancedEnabled: Boolean = false,
    val pageOrder: PageOrder = PageOrder.NORMAL,
    val sides: PrintSides = PrintSides.ONE_SIDED,
    val sheetsPerBooklet: SheetsPerBooklet = SheetsPerBooklet.AUTO,
    val gridGutterFraction: Float = LayoutGrid.DefaultGutterFraction,
    val gridMarginFraction: Float = LayoutGrid.DefaultMarginFraction,
    val cellBorderFraction: Float = 0f,
    val pageSelection: PageSelectionMode = PageSelectionMode.ALL,
    val pageSegments: List<PageSegment> = emptyList(),
    val reversePageOrder: Boolean = false,
    val position: ContentPosition = ContentPosition.CENTER,
    val pageScaling: PageScaling = PageScaling.SCALE_TO_FIT,
    val margins: MarginOption = MarginOption.BY_PRINTABLE_AREA,
    val manualDuplex: ManualDuplex = ManualDuplex.NONE,
    val imagePageSettings: ImagePageSettings = ImagePageSettings(),
) {
    /** The page numbers this job prints, one-based, in ascending order. */
    val resolvedPages: ResolvedPages
        get() = ResolvedPages.resolve(pageSelection, pageSegments, totalPagesHint, reversePageOrder)

    /** Known page count, when it has been resolved; zero means "not known yet". */
    val totalPagesHint: Int get() = pageSegments.maxOfOrNull { it.end } ?: 0

    val sheetLayout: SheetLayout get() = SheetLayout.fromCount(pagesPerSheet)

    val effectivePaper: PaperSize get() = paperSize.withOrientation(landscape)

    val effectiveDuplex: DuplexMode
        get() = when (sides) {
            PrintSides.ONE_SIDED -> DuplexMode.NONE
            PrintSides.BOTH_SIDES_LONG_EDGE -> DuplexMode.LONG_EDGE
            PrintSides.BOTH_SIDES_SHORT_EDGE -> DuplexMode.SHORT_EDGE
        }

    val layoutGrid: LayoutGrid
        get() = if (borderless || marginPreset == MarginPreset.NONE) {
            LayoutGrid.forSlots(
                slots = pagesPerSheet,
                pageAspect = sheetAspect,
                marginFraction = 0f,
                gutterFraction = gridGutterFraction,
            ).copy(cellBorderFraction = cellBorderFraction)
        } else {
            LayoutGrid.forSlots(
                slots = pagesPerSheet,
                pageAspect = sheetAspect,
                marginFraction = gridMarginFraction,
                gutterFraction = gridGutterFraction,
            ).copy(cellBorderFraction = cellBorderFraction)
        }

    val sheetAspect: Float
        get() {
            val paper = effectivePaper
            if (paper.heightMils <= 0) return 1f
            return paper.widthMils.toFloat() / paper.heightMils
        }

    fun withClampedValues(): PrintSettings {
        val clampedImagePage = imagePageSettings.withImagesPerPage(imagePageSettings.imagesPerPage, sheetAspect)
        val effectiveMargin = if (borderless || marginPreset == MarginPreset.NONE) {
            0f
        } else {
            gridMarginFraction.coerceIn(0f, 0.2f)
        }
        val paper = effectivePaper
        val fittingCount = if (clampedImagePage.photoSize == PhotoSizePreset.AUTO) {
            null
        } else {
            (clampedImagePage.imagesPerPage downTo ImagePageSettings.MIN_IMAGES_PER_PAGE)
                .firstOrNull { count ->
                    clampedImagePage.fitsPhotoCount(
                        count = count,
                        pageAspect = sheetAspect,
                        pageWidthInches = paper.widthInches,
                        pageHeightInches = paper.heightInches,
                        marginFraction = effectiveMargin,
                        cellBorderFraction = cellBorderFraction,
                    )
                }
        }
        val safeImagePage = if (fittingCount == null) {
            clampedImagePage
        } else {
            clampedImagePage.withImagesPerPage(fittingCount, sheetAspect)
        }
        return copy(
            copies = copies.coerceIn(MIN_COPIES, MAX_COPIES),
            scalePercent = scalePercent.coerceIn(MIN_SCALE_PERCENT, MAX_SCALE_PERCENT),
            pagesPerSheet = pagesPerSheet.coerceIn(1, LayoutGrid.MAX_SAFE_SLOTS),
            gridGutterFraction = gridGutterFraction.coerceIn(0f, 0.25f),
            gridMarginFraction = gridMarginFraction.coerceIn(0f, 0.2f),
            cellBorderFraction = cellBorderFraction.coerceIn(0f, 0.02f),
            imagePageSettings = safeImagePage,
        )
    }

    fun sheetCount(selectedPages: Int, pagesPerSheet: Int = this.pagesPerSheet): Int {
        val perSheet = pagesPerSheet.coerceAtLeast(1)
        val laidOutPages = (selectedPages + perSheet - 1) / perSheet
        return (laidOutPages * copies.coerceAtLeast(1)).coerceAtLeast(if (selectedPages == 0) 0 else 1)
    }

    fun summaryLabel(): String {
        val parts = ArrayList<String>(6)
        parts += effectivePaper.label
        if (pagesPerSheet > 1) parts += "$pagesPerSheet-up"
        if (colorMode != ColorMode.COLOR) parts += colorMode.label
        parts += if (copies > 1) "x$copies" else "1 copy"
        if (sides.isDuplex()) parts += sides.label.substringAfter("Two-sided, ")
        if (pageOrder != PageOrder.NORMAL) parts += pageOrder.label
        if (borderless) parts += "Borderless"
        return parts.joinToString(" · ")
    }

    companion object {
        const val MIN_COPIES = 1
        const val MAX_COPIES = 99
        const val MIN_SCALE_PERCENT = 10
        const val MAX_SCALE_PERCENT = 400
        fun samePaper(first: PaperSize, second: PaperSize, toleranceMils: Int = 750): Boolean =
            abs(first.widthMils - second.widthMils) <= toleranceMils &&
                abs(first.heightMils - second.heightMils) <= toleranceMils
    }
}

@Immutable
enum class SheetLayout(val count: Int, val columns: Int, val rows: Int, val label: String) {
    SINGLE(1, 1, 1, "1-up"),
    TWO_PORTRAIT(2, 1, 2, "2-up"),
    TWO_LANDSCAPE(2, 2, 1, "2-up wide"),
    FOUR(4, 2, 2, "4-up"),
    SIX(6, 2, 3, "6-up"),
    NINE(9, 3, 3, "9-up"),
    TWELVE(12, 3, 4, "12-up"),
    SIXTEEN(16, 4, 4, "16-up"),
    ;

    fun next(): SheetLayout = when (this) {
        SINGLE -> TWO_PORTRAIT
        TWO_PORTRAIT -> TWO_LANDSCAPE
        TWO_LANDSCAPE -> FOUR
        FOUR -> SIX
        SIX -> NINE
        NINE -> TWELVE
        TWELVE -> SIXTEEN
        SIXTEEN -> SINGLE
    }

    companion object {
        val Standard: List<SheetLayout> = listOf(SINGLE, TWO_PORTRAIT, TWO_LANDSCAPE, FOUR, SIX, NINE)

        fun fromCount(count: Int): SheetLayout = entries.firstOrNull { it.count == count } ?: SINGLE

        fun fromGrid(grid: LayoutGrid): SheetLayout = when {
            grid.columns == 1 && grid.rows == 1 -> SINGLE
            grid.columns == 2 && grid.rows == 1 -> TWO_LANDSCAPE
            grid.columns == 1 && grid.rows == 2 -> TWO_PORTRAIT
            else -> fromCount(grid.slotCount)
        }

        fun selectable(counts: List<Int>): List<SheetLayout> {
            val supported = counts.toSet()
            val chosen = LinkedHashSet<Int>()
            return entries.filter { it.count in supported && chosen.add(it.count) }
        }

        fun gridFor(count: Int, pageAspect: Float): LayoutGrid =
            LayoutGrid.forSlots(count, pageAspect).copy(
                columns = fromCount(count).columns.coerceAtLeast(1),
                rows = fromCount(count).rows.coerceAtLeast(1),
            )
    }
}

internal fun Float.roundToTenths(): Float = (this * 10f).roundToInt() / 10f
