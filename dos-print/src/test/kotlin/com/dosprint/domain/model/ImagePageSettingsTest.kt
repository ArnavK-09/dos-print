package com.dosprint.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The photo flow's options, checked against the reference app's documented defaults.
 *
 * Photo layout is entirely automatic, so these values decide the output directly. The defaults are
 * pinned because they are what a user sees before touching anything, and a silent default change
 * would alter every printed sheet.
 */
class ImagePageSettingsTest {

    @Test
    fun `defaults match the reference app`() {
        val settings = ImagePageSettings()
        assertEquals(ImageScaling.CROP_TO_FIT, settings.scaling)
        assertEquals(ImagesPerPage.MULTIPLE, settings.perPage)
        assertEquals(ImageGap.NORMAL, settings.gap)
        assertEquals(3, settings.columns)
        assertEquals(2, settings.rows)
        assertEquals(PagePosition.CENTER, settings.position)
        assertTrue(settings.compactGroup)
        assertFalse("the reference app leaves Restrict to grid off by default", settings.restrictToGrid)
    }

    @Test
    fun `single mode collapses the grid to one slot`() {
        val single = ImagePageSettings().withPerPage(ImagesPerPage.SINGLE)
        assertEquals(1, single.columns)
        assertEquals(1, single.rows)
        assertEquals(1, single.slotsPerSheet)
    }

    @Test
    fun `switching to multiple keeps the grid the user chose`() {
        val single = ImagePageSettings().withPerPage(ImagesPerPage.SINGLE)
        val back = single.withPerPage(ImagesPerPage.MULTIPLE)
        assertEquals(ImagesPerPage.MULTIPLE, back.perPage)
        assertTrue(back.slotsPerSheet >= 1)
    }

    @Test
    fun `a grid never reports fewer than one slot`() {
        val empty = ImagePageSettings(columns = 0, rows = 0)
        assertEquals(1, empty.slotsPerSheet)
    }

    @Test
    fun `each gap preset maps to a wider gap than the last`() {
        val fractions = ImageGap.entries.map { it.fraction }
        assertEquals("gaps must be strictly increasing", fractions.size, fractions.distinct().size)
        assertTrue(fractions.zipWithNext().all { (a, b) -> b > a })
        assertEquals(0f, ImageGap.NONE.fraction, 0f)
    }

    @Test
    fun `no gaps removes the gutter entirely`() {
        assertEquals(0f, ImagePageSettings(gap = ImageGap.NONE).effectiveGrid.gutterFraction, 0f)
    }

    @Test
    fun `every gap preset is reflected in the grid`() {
        ImageGap.entries.forEach { gap ->
            val grid = ImagePageSettings(gap = gap).effectiveGrid
            if (gap == ImageGap.NONE) {
                assertEquals(0f, grid.gutterFraction, 0f)
            } else {
                assertEquals(gap.fraction, grid.gutterFraction, 0f)
            }
        }
    }

    @Test
    fun `only crop and stretch fill the slot exactly`() {
        assertTrue(ImageScaling.CROP_TO_FIT.fillsSlot)
        assertTrue(ImageScaling.STRETCH_TO_FIT.fillsSlot)
        assertFalse("scale to fit must leave whitespace", ImageScaling.SCALE_TO_FIT.fillsSlot)
    }

    @Test
    fun `every position is one of the nine the reference app offers`() {
        assertEquals(9, PagePosition.entries.size)
        assertEquals("Center", PagePosition.CENTER.label)
    }

    @Test
    fun `positions span three columns and three rows`() {
        assertEquals(3, PagePosition.entries.map { it.column }.distinct().size)
        assertEquals(3, PagePosition.entries.map { it.row }.distinct().size)
    }

    @Test
    fun `each position is a distinct place on the sheet`() {
        val cells = PagePosition.entries.map { it.column to it.row }
        assertEquals(cells.size, cells.distinct().size)
    }

    @Test
    fun `centre is the middle of the three by three arrangement`() {
        assertEquals(1, PagePosition.CENTER.column)
        assertEquals(1, PagePosition.CENTER.row)
    }

    @Test
    fun `single mode ignores the grid entirely`() {
        val settings = ImagePageSettings(perPage = ImagesPerPage.SINGLE, columns = 3, rows = 2)
        assertEquals(1, settings.effectiveGrid.columns)
        assertEquals(1, settings.effectiveGrid.rows)
        assertFalse(settings.usesGrid)
    }

    @Test
    fun `the effective grid never has a negative dimension`() {
        ImageGap.entries.forEach { gap ->
            val grid = ImagePageSettings(gap = gap).effectiveGrid
            assertTrue(grid.columns >= 1)
            assertTrue(grid.rows >= 1)
            assertTrue(grid.gutterFraction >= 0f)
            assertTrue(grid.marginFraction >= 0f)
        }
    }

    @Test
    fun `a grid fills its cells`() {
        val settings = ImagePageSettings(columns = 2, rows = 3)
        assertEquals(6, settings.effectiveGrid.slotCount)
    }

    @Test
    fun `restricting to the grid discards the image aspect`() {
        val grid = ImagePageSettings(columns = 2, rows = 2, restrictToGrid = true).effectiveGrid
        val widths = (0 until grid.slotCount).map { grid.slotRect(it, 1000f, 1000f).width() }
        val heights = (0 until grid.slotCount).map { grid.slotRect(it, 1000f, 1000f).height() }
        assertEquals("every cell must be the same width", 1, widths.map { it.toInt() }.distinct().size)
        assertEquals("every cell must be the same height", 1, heights.map { it.toInt() }.distinct().size)
    }

    @Test
    fun `slots never overlap`() {
        val grid = ImagePageSettings(columns = 3, rows = 3, gap = ImageGap.NORMAL).effectiveGrid
        val slots = grid.allSlots(1000f, 1000f)
        for (i in slots.indices) {
            for (j in i + 1 until slots.size) {
                val a = slots[i]
                val b = slots[j]
                val overlaps = a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3]
                assertFalse("slots $i and $j overlap", overlaps)
            }
        }
    }

    @Test
    fun `every slot stays inside the sheet`() {
        val grid = ImagePageSettings(columns = 3, rows = 2, gap = ImageGap.WIDE).effectiveGrid
        grid.allSlots(1000f, 1000f).forEach { slot ->
            assertTrue(slot[0] >= -Tolerance)
            assertTrue(slot[1] >= -Tolerance)
            assertTrue(slot[2] <= 1000f + Tolerance)
            assertTrue(slot[3] <= 1000f + Tolerance)
        }
    }

    private fun FloatArray.width() = this[2] - this[0]

    private fun FloatArray.height() = this[3] - this[1]

    private companion object {
        const val Tolerance = 0.01f
    }
}
