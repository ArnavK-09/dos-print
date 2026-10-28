package com.dosprint.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class LayoutGrid(
    val columns: Int,
    val rows: Int,
    val marginFraction: Float = DefaultMarginFraction,
    val gutterFraction: Float = DefaultGutterFraction,
    val cellBorderFraction: Float = 0f,
) {
    init {
        require(columns >= 1 && rows >= 1) { "A layout needs at least one column and one row" }
    }

    val slotCount: Int get() = columns * rows

    /** True when the grid holds exactly one cell, so grid options have nothing to arrange. */
    val isSingleSlot: Boolean get() = columns * rows <= 1

    fun slotRect(index: Int, pageWidth: Float, pageHeight: Float): FloatArray {
        val outOfRange = index < 0 || index >= slotCount
        val degeneratePage = pageWidth <= 0f || pageHeight <= 0f
        if (outOfRange || degeneratePage) {
            return FloatArray(0)
        }
        val marginX = pageWidth * marginFraction
        val marginY = pageHeight * marginFraction
        val usableWidth = pageWidth - marginX * 2f
        val usableHeight = pageHeight - marginY * 2f
        val gutterX = usableWidth * gutterFraction
        val gutterY = usableHeight * gutterFraction
        val cellWidth = (usableWidth - gutterX * (columns - 1)) / columns
        val cellHeight = (usableHeight - gutterY * (rows - 1)) / rows
        val row = index / columns
        val column = index - row * columns
        val insetX = pageWidth * cellBorderFraction
        val insetY = pageHeight * cellBorderFraction
        val left = marginX + column * (cellWidth + gutterX) + insetX
        val top = marginY + row * (cellHeight + gutterY) + insetY
        val right = left + cellWidth - insetX * 2f
        val bottom = top + cellHeight - insetY * 2f
        return floatArrayOf(left, top, right, bottom)
    }

    fun allSlots(pageWidth: Float, pageHeight: Float): List<FloatArray> =
        (0 until slotCount).map { slotRect(it, pageWidth, pageHeight) }

    fun gridOf(slots: Int): LayoutGrid {
        if (slots <= 0) return this
        if (slots <= columns) return copy(columns = slots, rows = 1)
        var bestColumns = columns
        var bestScore = Float.MAX_VALUE
        for (candidate in 1..MAX_DIMENSION) {
            val rows = ((slots + candidate - 1) / candidate).coerceIn(1, MAX_DIMENSION)
            val covered = candidate * rows
            if (covered < slots) continue
            val delta = kotlin.math.abs(candidate.toFloat() / rows - layoutAspect)
            val score = delta * AspectWeight + (covered - slots) * WasteWeight
            if (score < bestScore) {
                bestScore = score
                bestColumns = candidate
            }
        }
        val rows = ((slots + bestColumns - 1) / bestColumns).coerceIn(1, MAX_DIMENSION)
        return copy(columns = bestColumns, rows = rows)
    }

    val layoutAspect: Float get() = columns.toFloat() / rows

    fun toSheetLayout(): SheetLayout = SheetLayout.fromGrid(this)

    companion object {
        /** One item occupying the whole sheet, edge to edge. */
        fun single(): LayoutGrid = LayoutGrid(
            columns = 1,
            rows = 1,
            marginFraction = 0f,
            gutterFraction = 0f,
        )

        const val DefaultMarginFraction = 0.045f
        const val DefaultGutterFraction = 0.035f
        private const val AspectWeight = 1f
        private const val MAX_DIMENSION = 6

        val Single = LayoutGrid(1, 1, marginFraction = 0f, gutterFraction = 0f)
        val Full = LayoutGrid(1, 1)

        fun forSlots(
            slots: Int,
            pageAspect: Float,
            marginFraction: Float = DefaultMarginFraction,
            gutterFraction: Float = DefaultGutterFraction,
        ): LayoutGrid {
            val safeSlots = slots.coerceIn(1, MAX_SAFE_SLOTS)
            val safeAspect = if (pageAspect.isFinite() && pageAspect > 0f) pageAspect else 1f
            var best = LayoutGrid(1, 1, marginFraction, gutterFraction)
            var bestScore = Float.MAX_VALUE
            for (columns in 1..MAX_DIMENSION) {
                val rows = ((safeSlots + columns - 1) / columns).coerceIn(1, MAX_DIMENSION)
                val covered = columns * rows
                if (covered < safeSlots) continue
                val cellAspect = safeAspect * columns / rows
                val waste = covered - safeSlots
                val score = kotlin.math.abs(1f - cellAspect) * AspectWeight + waste * WasteWeight
                if (score < bestScore) {
                    bestScore = score
                    best = LayoutGrid(columns, rows, marginFraction, gutterFraction)
                }
            }
            return best
        }

        private const val WasteWeight = 0.6f
        const val MAX_SAFE_SLOTS = 64
    }
}

@Immutable
enum class PageOrder(val label: String) {
    NORMAL("Normal"),
    REVERSE("Reverse page order"),
    BOOK_FOLD("Book fold"),
    ;

    fun reversesOrder(): Boolean = this == REVERSE

    fun foldsSheets(): Boolean = this == BOOK_FOLD

    fun requiredSides(): Int = if (foldsSheets()) 2 else 1
}

@Immutable
enum class PrintSides(val label: String) {
    ONE_SIDED("Print on one side"),
    BOTH_SIDES_LONG_EDGE("Two-sided, flip on long edge"),
    BOTH_SIDES_SHORT_EDGE("Two-sided, flip on short edge"),
    ;

    fun isDuplex(): Boolean = this != ONE_SIDED
}

@Immutable
enum class SheetsPerBooklet(val label: String, val pageCount: Int?) {
    AUTO("Auto", null),
    ONE("1 sheet", 1),
    TWO("2 sheets", 2),
    FOUR("4 sheets", 4),
    SIX("6 sheets", 6),
    EIGHT("8 sheets", 8),
    TWELVE("12 sheets", 12),
    SIXTEEN("16 sheets", 16),
    TWENTY("20 sheets", 20),
    ;

    fun logicalPages(): Int? = pageCount?.times(PAGES_PER_SHEET_BOOKLET)
}

private const val PAGES_PER_SHEET_BOOKLET = 4

@Immutable
enum class LayoutCategory(val label: String) {
    STANDARD("Page layout"),
    PHOTO("Photo"),
    LABELS("Labels and cards"),
    BOOKLET("Booklets"),
    DOCUMENTS("Everyday documents"),
}

@Immutable
data class LayoutPreset(
    val id: String,
    val label: String,
    val description: String,
    val category: LayoutCategory,
    val grid: LayoutGrid,
    val paperId: String,
    val borderless: Boolean,
    val pageOrder: PageOrder = PageOrder.NORMAL,
    val sides: PrintSides = PrintSides.ONE_SIDED,
) {
    fun appliesTo(settings: PrintSettings): PrintSettings = settings.copy(
        paperSize = PaperSize.byId(paperId) ?: settings.paperSize,
        pagesPerSheet = grid.slotCount,
        borderless = borderless,
        pageOrder = pageOrder,
        sides = sides,
        marginPreset = if (borderless) MarginPreset.NONE else settings.marginPreset,
    )
}

@Immutable
object LayoutPresetCatalog {

    val all: List<LayoutPreset> = listOf(
        LayoutPreset(
            id = "single",
            label = "1 page per sheet",
            description = "One full-size page on the front of every sheet.",
            category = LayoutCategory.STANDARD,
            grid = LayoutGrid.Single,
            paperId = PaperSize.A4.id,
            borderless = false,
        ),
        LayoutPreset(
            id = "two_portrait",
            label = "2 pages per sheet",
            description = "Two pages stacked down the sheet, portrait flow.",
            category = LayoutCategory.STANDARD,
            grid = LayoutGrid(1, 2),
            paperId = PaperSize.A4.id,
            borderless = false,
        ),
        LayoutPreset(
            id = "two_wide",
            label = "2 pages per sheet, wide",
            description = "Two pages side by side across the sheet.",
            category = LayoutCategory.STANDARD,
            grid = LayoutGrid(2, 1),
            paperId = PaperSize.A4.id,
            borderless = false,
        ),
        LayoutPreset(
            id = "four",
            label = "4 pages per sheet",
            description = "A two by two grid of pages.",
            category = LayoutCategory.STANDARD,
            grid = LayoutGrid(2, 2),
            paperId = PaperSize.A4.id,
            borderless = false,
        ),
        LayoutPreset(
            id = "six",
            label = "6 pages per sheet",
            description = "A two by three grid of pages.",
            category = LayoutCategory.STANDARD,
            grid = LayoutGrid(2, 3),
            paperId = PaperSize.A4.id,
            borderless = false,
        ),
        LayoutPreset(
            id = "nine",
            label = "9 pages per sheet",
            description = "A three by three grid of pages.",
            category = LayoutCategory.STANDARD,
            grid = LayoutGrid(3, 3),
            paperId = PaperSize.A4.id,
            borderless = false,
        ),
        LayoutPreset(
            id = "sixteen",
            label = "16 pages per sheet",
            description = "A four by four grid for dense reference printing.",
            category = LayoutCategory.STANDARD,
            grid = LayoutGrid(4, 4),
            paperId = PaperSize.A4.id,
            borderless = false,
        ),
        LayoutPreset(
            id = "book_fold",
            label = "Book fold",
            description = "Two pages per side, reordered and imposed for folding.",
            category = LayoutCategory.BOOKLET,
            grid = LayoutGrid(2, 1),
            paperId = PaperSize.A5.id,
            borderless = false,
            pageOrder = PageOrder.BOOK_FOLD,
            sides = PrintSides.BOTH_SIDES_LONG_EDGE,
        ),
        LayoutPreset(
            id = "photo_4x6",
            label = "4 x 6 photo",
            description = "Full-bleed borderless 4 x 6 in prints.",
            category = LayoutCategory.PHOTO,
            grid = LayoutGrid(1, 1, marginFraction = 0f),
            paperId = PaperSize.PHOTO_4X6.id,
            borderless = true,
        ),
        LayoutPreset(
            id = "photo_5x7",
            label = "5 x 7 photo",
            description = "Full-bleed borderless 5 x 7 in prints.",
            category = LayoutCategory.PHOTO,
            grid = LayoutGrid(1, 1, marginFraction = 0f),
            paperId = PaperSize.PHOTO_5X7.id,
            borderless = true,
        ),
        LayoutPreset(
            id = "photo_10x15",
            label = "10 x 15 cm photo",
            description = "Full-bleed borderless 10 x 15 cm prints.",
            category = LayoutCategory.PHOTO,
            grid = LayoutGrid(1, 1, marginFraction = 0f),
            paperId = PaperSize.PHOTO_10X15.id,
            borderless = true,
        ),
        LayoutPreset(
            id = "photo_contact_sheet",
            label = "Contact sheet 3 x 3",
            description = "Nine photos on one sheet with even gutters.",
            category = LayoutCategory.PHOTO,
            grid = LayoutGrid(3, 3, gutterFraction = 0.045f),
            paperId = PaperSize.PHOTO_4X6.id,
            borderless = true,
        ),
        LayoutPreset(
            id = "photo_2x2",
            label = "2 x 2 in photo",
            description = "Four 2 x 2 in photos on a 4 x 6 in sheet.",
            category = LayoutCategory.PHOTO,
            grid = LayoutGrid(2, 2, gutterFraction = 0.05f),
            paperId = PaperSize.PHOTO_4X6.id,
            borderless = true,
        ),
        LayoutPreset(
            id = "card_standard",
            label = "Business cards",
            description = "Ten 3.5 x 2 in cards per Letter sheet with cut guides.",
            category = LayoutCategory.LABELS,
            grid = LayoutGrid(2, 5, gutterFraction = 0.08f, cellBorderFraction = 0.004f),
            paperId = PaperSize.LETTER.id,
            borderless = false,
        ),
        LayoutPreset(
            id = "card_ten",
            label = "10 cards per sheet",
            description = "Ten 2 x 3.5 in cards on an A4 sheet.",
            category = LayoutCategory.LABELS,
            grid = LayoutGrid(2, 5, gutterFraction = 0.08f, cellBorderFraction = 0.004f),
            paperId = PaperSize.A4.id,
            borderless = false,
        ),
        LayoutPreset(
            id = "label_letter_21",
            label = "21 address labels",
            description = "Twenty-one 2.625 x 1 in labels on a Letter sheet.",
            category = LayoutCategory.LABELS,
            grid = LayoutGrid(3, 7, gutterFraction = 0.03f, cellBorderFraction = 0.003f),
            paperId = PaperSize.LETTER.id,
            borderless = false,
        ),
        LayoutPreset(
            id = "label_a4_24",
            label = "24 address labels",
            description = "Twenty-four 2.625 x 1 in labels on an A4 sheet.",
            category = LayoutCategory.LABELS,
            grid = LayoutGrid(3, 8, gutterFraction = 0.02f, cellBorderFraction = 0.003f),
            paperId = PaperSize.A4.id,
            borderless = false,
        ),
        LayoutPreset(
            id = "label_cd",
            label = "CD labels",
            description = "Two disc labels across the top of a Letter sheet.",
            category = LayoutCategory.LABELS,
            grid = LayoutGrid(2, 1, gutterFraction = 0.04f, marginFraction = 0.06f),
            paperId = PaperSize.LETTER.id,
            borderless = false,
        ),
        LayoutPreset(
            id = "doc_envelope_dl",
            label = "DL envelopes",
            description = "Two DL envelopes laid out on an A4 sheet for test printing.",
            category = LayoutCategory.DOCUMENTS,
            grid = LayoutGrid(1, 2, gutterFraction = 0.02f),
            paperId = PaperSize.A4.id,
            borderless = false,
        ),
        LayoutPreset(
            id = "doc_flyer_half",
            label = "Half-sheet flyer",
            description = "Two A5 flyers per A4 sheet, trimmed down the middle.",
            category = LayoutCategory.DOCUMENTS,
            grid = LayoutGrid(2, 1, gutterFraction = 0.02f, cellBorderFraction = 0.002f),
            paperId = PaperSize.A4.id,
            borderless = false,
        ),
        LayoutPreset(
            id = "doc_flyer_quarter",
            label = "Quarter-sheet flyer",
            description = "Four A6 flyers per A4 sheet with trim guides.",
            category = LayoutCategory.DOCUMENTS,
            grid = LayoutGrid(2, 2, gutterFraction = 0.025f, cellBorderFraction = 0.002f),
            paperId = PaperSize.A4.id,
            borderless = false,
        ),
        LayoutPreset(
            id = "doc_poster",
            label = "11 x 17 poster",
            description = "Tabloid pages scaled to fit the sheet.",
            category = LayoutCategory.DOCUMENTS,
            grid = LayoutGrid.Single,
            paperId = PaperSize.TABLOID.id,
            borderless = false,
        ),
    )

    fun byCategory(): List<Pair<LayoutCategory, List<LayoutPreset>>> =
        LayoutCategory.entries.map { category -> category to all.filter { it.category == category } }

    fun byId(id: String): LayoutPreset? = all.firstOrNull { it.id == id }

    fun forSlotCount(slots: Int): LayoutPreset? = all.firstOrNull { it.grid.slotCount == slots && it.category == LayoutCategory.STANDARD }
}
