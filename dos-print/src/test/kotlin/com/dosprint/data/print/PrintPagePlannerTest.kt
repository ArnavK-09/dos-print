package com.dosprint.data.print

import com.dosprint.domain.model.PrintSettings
import com.dosprint.domain.model.SheetLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintPagePlannerTest {

    private fun settings(
        pagesPerSheet: Int = 1,
        range: com.dosprint.domain.model.PageRange = com.dosprint.domain.model.PageRange.All,
    ) = PrintSettings(pagesPerSheet = pagesPerSheet, pageRange = range)

    @Test
    fun plansOneSheetPerPageByDefault() {
        val plan = PrintPagePlanner.plan(listOf(4), settings())
        assertEquals(4, plan.sheetCount)
        assertEquals(4, plan.logicalPageCount)
        assertTrue(plan.sheets.none { it.isPartial })
    }

    @Test
    fun plansPagesInDocumentOrder() {
        val plan = PrintPagePlanner.plan(listOf(2, 3), settings())
        assertEquals(5, plan.sheetCount)
        assertEquals(0, plan.sheets[0].pages[0].documentIndex)
        assertEquals(0, plan.sheets[0].pages[0].pageIndex)
        assertEquals(1, plan.sheets[2].pages[0].documentIndex)
        assertEquals(0, plan.sheets[2].pages[0].pageIndex)
        assertEquals(1, plan.sheets[4].pages[0].documentIndex)
        assertEquals(2, plan.sheets[4].pages[0].pageIndex)
    }

    @Test
    fun plansNUpSheets() {
        val plan = PrintPagePlanner.plan(listOf(10), settings(pagesPerSheet = 4))
        assertEquals(3, plan.sheetCount)
        assertEquals(4, plan.sheets[0].pages.size)
        assertEquals(4, plan.sheets[1].pages.size)
        assertEquals(2, plan.sheets[2].pages.size)
        assertTrue(plan.sheets[2].isPartial)
    }

    @Test
    fun appliesPageRangeToLogicalPages() {
        val range = com.dosprint.domain.model.PageRange.parse("2-4").getOrThrow()
        val plan = PrintPagePlanner.plan(listOf(10), settings(range = range))
        assertEquals(3, plan.logicalPageCount)
        assertEquals(3, plan.sheetCount)
        assertEquals(1, plan.sheets[0].pages[0].pageIndex)
    }

    @Test
    fun appliesPageRangeAcrossMultipleDocuments() {
        val range = com.dosprint.domain.model.PageRange.parse("3-4").getOrThrow()
        val plan = PrintPagePlanner.plan(listOf(2, 2), settings(range = range))
        assertEquals(2, plan.logicalPageCount)
        assertEquals(1, plan.sheets[0].pages[0].documentIndex)
        assertEquals(0, plan.sheets[0].pages[0].pageIndex)
    }

    @Test
    fun emptyDocumentProducesNoSheets() {
        val plan = PrintPagePlanner.plan(listOf(0), settings())
        assertEquals(0, plan.sheetCount)
    }

    @Test
    fun emptySelectionProducesNoSheets() {
        val range = com.dosprint.domain.model.PageRange.parse("50-60").getOrThrow()
        val plan = PrintPagePlanner.plan(listOf(5), settings(range = range))
        assertEquals(0, plan.sheetCount)
    }

    @Test
    fun requestedRangesCoverAllSheetsForAllPages() {
        val plan = PrintPagePlanner.plan(listOf(3), settings())
        val ranges = PrintPagePlanner.requestedRanges(plan, settings())
        assertEquals(listOf(1..3), ranges)
    }

    @Test
    fun requestedRangesMapNUpPagesToSheets() {
        val nup = settings(pagesPerSheet = 4)
        val plan = PrintPagePlanner.plan(listOf(10), nup)
        val ranges = PrintPagePlanner.requestedRanges(plan, nup)
        assertEquals(listOf(1..3), ranges)
    }

    @Test
    fun sheetLayoutIsReportedInPlan() {
        val plan = PrintPagePlanner.plan(listOf(4), settings(pagesPerSheet = 2))
        assertEquals(SheetLayout.TWO_PORTRAIT, plan.layout)
        assertTrue(plan.isNUp)
    }
}
