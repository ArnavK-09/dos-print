package com.dosprint.data.print

import com.dosprint.domain.model.PrintSettings
import com.dosprint.domain.model.SheetLayout

data class PageRef(
    val documentIndex: Int,
    val pageIndex: Int,
    val logicalNumber: Int,
)

data class PrintSheet(
    val index: Int,
    val pages: List<PageRef>,
    val isPartial: Boolean,
)

data class PrintPagePlan(
    val sheets: List<PrintSheet>,
    val logicalPageCount: Int,
    val layout: SheetLayout,
    val pagesPerSheet: Int = layout.count,
    val copies: Int = 1,
    val rangePageCount: Int = logicalPageCount,
) {
    val sheetCount: Int get() = sheets.size

    val outputPageCount: Int get() = logicalPageCount * copies

    val isNUp: Boolean get() = pagesPerSheet > 1
}

object PrintPagePlanner {

    fun plan(
        documentPageCounts: List<Int>,
        settings: PrintSettings,
        imageMode: Boolean = false,
    ): PrintPagePlan {
        val perSheet = if (imageMode) {
            settings.imagePageSettings.slotsPerSheet
        } else {
            settings.pagesPerSheet
        }.coerceAtLeast(1)
        val layout = SheetLayout.fromCount(perSheet)
        val logicalPages = buildLogicalPages(documentPageCounts, settings)
        val requestedCopies = settings.copies.coerceIn(PrintSettings.MIN_COPIES, PrintSettings.MAX_COPIES)
        val sheetCopies = if (imageMode) 1 else requestedCopies
        val pageCopies = if (imageMode) requestedCopies else 1
        val sheetItems = if (imageMode && logicalPages.isNotEmpty()) {
            buildList(logicalPages.size * pageCopies) {
                logicalPages.forEach { page ->
                    repeat(pageCopies) { add(page) }
                }
            }
        } else {
            logicalPages
        }
        val baseSheets = ArrayList<PrintSheet>()
        var sheetIndex = 0
        var cursor = 0
        while (cursor < sheetItems.size) {
            val window = sheetItems.subList(cursor, minOf(cursor + perSheet, sheetItems.size))
            baseSheets += PrintSheet(
                index = sheetIndex,
                pages = window.toList(),
                isPartial = window.size < perSheet,
            )
            cursor += perSheet
            sheetIndex++
        }
        val sheets = ArrayList<PrintSheet>(baseSheets.size * sheetCopies)
        repeat(sheetCopies) {
            baseSheets.forEach { sheet ->
                sheets += sheet.copy(index = sheets.size)
            }
        }
        return PrintPagePlan(
            sheets = sheets,
            logicalPageCount = sheetItems.size,
            layout = layout,
            pagesPerSheet = perSheet,
            copies = sheetCopies,
            rangePageCount = logicalPages.size,
        )
    }

    fun buildLogicalPages(documentPageCounts: List<Int>, settings: PrintSettings): List<PageRef> {
        val pages = mutableListOf<PageRef>()
        var logical = 1
        for ((documentIndex, count) in documentPageCounts.withIndex()) {
            val available = count.coerceAtLeast(0)
            for (pageIndex in 0 until available) {
                pages += PageRef(
                    documentIndex = documentIndex,
                    pageIndex = pageIndex,
                    logicalNumber = logical,
                )
                logical++
            }
        }
        return applyUserRange(pages, settings)
    }

    fun requestedRanges(plan: PrintPagePlan, settings: PrintSettings): List<IntRange> {
        if (plan.sheetCount == 0) {
            return emptyList()
        }
        if (settings.pageRange.isAllPages) {
            return listOf(1..plan.sheetCount)
        }
        val segments = settings.pageRange.segmentsFor(plan.rangePageCount)
        val perSheet = plan.pagesPerSheet.coerceAtLeast(1)
        val baseSheetCount = (plan.sheets.size / plan.copies.coerceAtLeast(1))
        val ranges = segments.map { segment ->
            val firstSheet = (segment.start - 1) / perSheet + 1
            val lastSheet = (segment.end - 1) / perSheet + 1
            firstSheet..lastSheet
        }
        val baseRanges = ranges.filter { !it.isEmpty() }
        return (0 until plan.copies).flatMap { copyIndex ->
            val offset = copyIndex * baseSheetCount
            baseRanges.map { range -> (range.first + offset)..(range.last + offset) }
        }
    }

    private fun applyUserRange(pages: List<PageRef>, settings: PrintSettings): List<PageRef> {
        if (settings.pageRange.isAllPages || pages.isEmpty()) {
            return pages
        }
        val segments = settings.pageRange.segmentsFor(pages.size)
        if (segments.isEmpty()) {
            return emptyList()
        }
        val selected = pages.filter { page ->
            segments.any { segment -> page.logicalNumber in segment.start..segment.end }
        }
        return selected.mapIndexed { index, page -> page.copy(logicalNumber = index + 1) }
    }
}
