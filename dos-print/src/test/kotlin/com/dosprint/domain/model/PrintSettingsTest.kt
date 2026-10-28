package com.dosprint.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintSettingsTest {

    @Test
    fun defaultsAreSafe() {
        val settings = PrintSettings()
        assertEquals(1, settings.copies)
        assertEquals(1, settings.pagesPerSheet)
        assertEquals(ScalingMode.FIT_TO_PAGE, settings.scaling)
        assertEquals(DuplexMode.NONE, settings.duplex)
        assertTrue(settings.pageRange.isAllPages)
    }

    @Test
    fun clampedValuesEnforcesBounds() {
        val settings = PrintSettings(copies = 500, scalePercent = 5_000, pagesPerSheet = 42_000)
        val clamped = settings.withClampedValues()
        assertEquals(PrintSettings.MAX_COPIES, clamped.copies)
        assertEquals(PrintSettings.MAX_SCALE_PERCENT, clamped.scalePercent)
        assertEquals(LayoutGrid.MAX_SAFE_SLOTS, clamped.pagesPerSheet)
    }

    @Test
    fun clampedValuesEnforcesLowerBounds() {
        val settings = PrintSettings(copies = 0, scalePercent = 1, pagesPerSheet = 0)
        val clamped = settings.withClampedValues()
        assertEquals(PrintSettings.MIN_COPIES, clamped.copies)
        assertEquals(PrintSettings.MIN_SCALE_PERCENT, clamped.scalePercent)
        assertEquals(1, clamped.pagesPerSheet)
    }

    @Test
    fun sheetCountSinglePagePerSheet() {
        val settings = PrintSettings(copies = 1, pagesPerSheet = 1)
        assertEquals(10, settings.sheetCount(10))
    }

    @Test
    fun sheetCountRespectsNUp() {
        val settings = PrintSettings(copies = 1, pagesPerSheet = 4)
        assertEquals(3, settings.sheetCount(10))
    }

    @Test
    fun sheetCountRoundsUpPartialSheet() {
        val settings = PrintSettings(copies = 1, pagesPerSheet = 4)
        assertEquals(1, settings.sheetCount(1))
    }

    @Test
    fun sheetCountMultipliesCopies() {
        val settings = PrintSettings(copies = 3, pagesPerSheet = 2)
        assertEquals(6, settings.sheetCount(4))
    }

    @Test
    fun sheetCountIsZeroForNoPages() {
        val settings = PrintSettings(copies = 2, pagesPerSheet = 2)
        assertEquals(0, settings.sheetCount(0))
    }

    @Test
    fun sheetLayoutMapsCount() {
        assertEquals(SheetLayout.FOUR, SheetLayout.fromCount(4))
        assertEquals(SheetLayout.SINGLE, SheetLayout.fromCount(3))
    }

    @Test
    fun sheetLayoutSelectionRespectsSupportedCounts() {
        val selectable = SheetLayout.selectable(listOf(1, 2, 4))
        assertEquals(listOf(1, 2, 4), selectable.map { it.count })
    }
}
