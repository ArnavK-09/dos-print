package com.dosprint.domain.model

import androidx.compose.runtime.Immutable

/**
 * Which pages a document job prints.
 *
 * The reference app offers All, Odd, Even, Selected and Range, plus a reverse-order toggle. Range
 * alone is not enough: printing only the odd sides of a duplex job is a common real need, and
 * modelling it explicitly beats asking a user to type "1, 3, 5, 7".
 */
enum class PageSelectionMode(val label: String) {
    ALL("All"),
    ODD("Odd"),
    EVEN("Even"),
    RANGE("Range"),
    ;

    val isRange: Boolean get() = this == RANGE

    /** Odd and Even need at least two pages to mean anything. */
    fun needsMultiplePages(totalPages: Int): Boolean = totalPages > 1
}

/**
 * Margin presets, reproduced from the reference app's geometry.
 *
 * The reference app insets by multiples of `min(pageWidth, pageHeight) / 36`, which keeps the
 * margin visually the same relative size on A4 and on a business card — something a fixed
 * millimetre value cannot do.
 */
enum class MarginOption(val label: String, val units: Int) {
    NONE("No margins", 0),
    BY_PRINTABLE_AREA("By printable area", -1),
    NARROW("Narrow margins", 1),
    NORMAL("Normal margins", 2),
    WIDE("Wide margins", 3),
    ;

    /** The inset in mils for a sheet of the given size, or null when the printer decides. */
    fun insetMils(paper: PaperSize): Int? {
        if (units < 0) return null
        val shortEdge = minOf(paper.widthMils, paper.heightMils)
        return units * shortEdge / MARGIN_UNITS
    }

    val isPrintableArea: Boolean get() = units < 0

    companion object {
        /** The reference app divides the short edge by this to derive one margin unit. */
        const val MARGIN_UNITS = 36
    }
}

/**
 * Where content sits on the sheet.
 *
 * Nine positions, centred by default, matching the reference app. Applies to a single image on a
 * sheet and to a single page scaled onto a sheet; a full grid overrides it, because a grid already
 * decides where things go.
 */
enum class ContentPosition(val label: String, val column: Int, val row: Int) {
    TOP_LEFT("Top left", 0, 0),
    TOP_MIDDLE("Top middle", 1, 0),
    TOP_RIGHT("Top right", 2, 0),
    MIDDLE_LEFT("Middle left", 0, 1),
    CENTER("Center", 1, 1),
    MIDDLE_RIGHT("Middle right", 2, 1),
    BOTTOM_LEFT("Bottom left", 0, 2),
    BOTTOM_MIDDLE("Bottom middle", 1, 2),
    BOTTOM_RIGHT("Bottom right", 2, 2),
}

/**
 * How a page is scaled onto the sheet.
 *
 * Distinct from photo scaling: this is about a document page and the printable area, so "shrink to
 * fit" is its own mode — it scales down only when the page is too large, which is what stops a
 * small page being blown up to fill the sheet.
 */
enum class PageScaling(val label: String) {
    ACTUAL_SIZE("Actual size"),
    SCALE_TO_FIT("Scale to fit"),
    SHRINK_TO_FIT("Shrink to fit"),
    CUSTOM("Custom scaling"),
    ;

    /** Only Custom uses [PrintSettings.scalePercent]; the rest are computed from the page. */
    val usesCustomPercent: Boolean get() = this == CUSTOM
}

/**
 * Manual duplex, for printers and drivers that cannot do it themselves.
 *
 * The reference app splits the sheets by index and reorders the back sides, appending a blank when
 * the counts differ. That is what "print front sides, turn the stack over, print the back sides"
 * needs on hardware with no duplex unit.
 */
enum class ManualDuplex(val label: String) {
    NONE("Automatic"),
    FRONT_SIDES("Front sides only"),
    BACK_SIDES("Back sides only"),
    BACK_SIDES_REVERSED("Back sides in reverse order"),
    ;

    /** Splits [count] sheets into the indices this pass should print. */
    fun select(indices: List<Int>): List<Int> = when (this) {
        NONE -> indices
        FRONT_SIDES -> indices.filterIndexed { position, _ -> position % 2 == 0 }
        BACK_SIDES -> indices.filterIndexed { position, _ -> position % 2 == 1 }
        BACK_SIDES_REVERSED -> indices.filterIndexed { position, _ -> position % 2 == 1 }.asReversed()
    }

    /** A blank sheet is appended so the backs are not printed onto the wrong side. */
    fun needsBlankSheet(sheetCount: Int): Boolean = this != NONE && sheetCount % 2 != 0
}

/** The resolved page numbers for a job, in the order they will be printed. */
@Immutable
data class ResolvedPages(
    val numbers: List<Int>,
    val reverse: Boolean,
    val needsBlankSheet: Boolean = false,
) {
    val count: Int get() = numbers.size

    val isEmpty: Boolean get() = numbers.isEmpty()

    fun inPrintOrder(): List<Int> = if (reverse) numbers.asReversed() else numbers

    companion object {
        /**
         * Resolves a selection against the real page count.
         *
         * The list is sorted and de-duplicated first, because a range like "1, 3-5, 8" can overlap
         * itself and printing a page twice is never what the user meant.
         */
        fun resolve(
            mode: PageSelectionMode,
            segments: List<PageSegment>,
            totalPages: Int,
            reverse: Boolean,
        ): ResolvedPages {
            if (totalPages < 1) return ResolvedPages(emptyList(), reverse)
            val all = (1..totalPages).toList()
            val base = when (mode) {
                PageSelectionMode.ALL -> all
                PageSelectionMode.ODD -> all.filter { it % 2 == 1 }
                PageSelectionMode.EVEN -> all.filter { it % 2 == 0 }
                PageSelectionMode.RANGE ->
                    segments
                        .mapNotNull { it.clampTo(totalPages) }
                        .flatMap { segment -> segment.start..segment.end }
                        .distinct()
                        .sorted()
            }
            return ResolvedPages(base, reverse)
        }
    }
}
