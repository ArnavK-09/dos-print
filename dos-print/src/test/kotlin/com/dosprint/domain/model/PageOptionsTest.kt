package com.dosprint.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The document flow's page options, checked against the reference app's behaviour.
 *
 * Page numbering here is one-based and inclusive, matching what a user types in a range box, so an
 * off-by-one here prints the wrong pages with no visible error.
 */
class PageOptionsTest {

    @Test
    fun `all selects every page`() {
        assertEquals(listOf(1, 2, 3, 4, 5), resolve(PageSelectionMode.ALL, 5))
    }

    @Test
    fun `odd selects only odd pages`() {
        assertEquals(listOf(1, 3, 5), resolve(PageSelectionMode.ODD, 5))
    }

    @Test
    fun `even selects only even pages`() {
        assertEquals(listOf(2, 4), resolve(PageSelectionMode.EVEN, 5))
    }

    @Test
    fun `odd and even together cover every page exactly once`() {
        val odd = resolve(PageSelectionMode.ODD, 6)
        val even = resolve(PageSelectionMode.EVEN, 6)
        assertEquals((1..6).toList(), (odd + even).sorted())
    }

    @Test
    fun `a range is inclusive at both ends`() {
        assertEquals(listOf(2, 3, 4), resolve(PageSelectionMode.RANGE, 5, listOf(PageSegment(2, 4))))
    }

    @Test
    fun `a range past the end is clamped rather than rejected`() {
        assertEquals(listOf(3, 4, 5), resolve(PageSelectionMode.RANGE, 5, listOf(PageSegment(3, 9))))
    }

    @Test
    fun `a range starting past the end selects nothing`() {
        assertTrue(resolvePages(PageSelectionMode.RANGE, 3, listOf(PageSegment(7, 9))).isEmpty)
    }

    @Test
    fun `overlapping ranges never print a page twice`() {
        assertEquals(
            listOf(1, 2, 3, 4),
            resolve(PageSelectionMode.RANGE, 4, listOf(PageSegment(1, 3), PageSegment(2, 4))),
        )
    }

    @Test
    fun `ranges are printed in ascending order however they were typed`() {
        assertEquals(
            listOf(1, 2, 5, 6),
            resolve(PageSelectionMode.RANGE, 6, listOf(PageSegment(5, 6), PageSegment(1, 2))),
        )
    }

    @Test
    fun `reverse order prints the same pages the other way round`() {
        val forward = resolvePages(PageSelectionMode.ALL, 4)
        val reversed = forward.copy(reverse = true)
        assertEquals(forward.numbers, reversed.numbers)
        assertEquals(listOf(4, 3, 2, 1), reversed.inPrintOrder())
    }

    @Test
    fun `a document with no pages resolves to nothing rather than throwing`() {
        assertTrue(resolvePages(PageSelectionMode.ALL, 0).isEmpty)
        assertTrue(resolvePages(PageSelectionMode.RANGE, 0, listOf(PageSegment(1, 3))).isEmpty)
    }

    @Test
    fun `odd and even need more than one page to be useful`() {
        assertFalse(PageSelectionMode.ODD.needsMultiplePages(1))
        assertTrue(PageSelectionMode.ODD.needsMultiplePages(2))
        assertFalse(PageSelectionMode.ALL.needsMultiplePages(1))
    }

    @Test
    fun `margin presets scale with the paper so they look the same on any size`() {
        val a4 = PaperSize.A4
        val card = PaperSize.PHOTO_4X6
        listOf(MarginOption.NARROW, MarginOption.NORMAL, MarginOption.WIDE).forEach { preset ->
            assertTrue(
                "${preset.label} must be smaller on a card than on A4",
                (preset.insetMils(card) ?: 0) < (preset.insetMils(a4) ?: 0),
            )
        }
    }

    @Test
    fun `margin presets increase in width`() {
        val insets = listOf(MarginOption.NARROW, MarginOption.NORMAL, MarginOption.WIDE)
            .mapNotNull { it.insetMils(PaperSize.A4) }
        assertEquals(3, insets.size)
        assertTrue(insets.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun `no margins means no inset and printable area defers to the printer`() {
        assertEquals(0, MarginOption.NONE.insetMils(PaperSize.A4))
        assertNull(MarginOption.BY_PRINTABLE_AREA.insetMils(PaperSize.A4))
        assertTrue(MarginOption.BY_PRINTABLE_AREA.isPrintableArea)
    }

    @Test
    fun `the five margin presets the reference app offers are present`() {
        assertEquals(5, MarginOption.entries.size)
    }

    @Test
    fun `positions are the nine the reference app offers with centre as default`() {
        assertEquals(9, ContentPosition.entries.size)
        assertEquals(ContentPosition.CENTER, ContentPosition.entries.firstOrNull { it.column == 1 && it.row == 1 })
        assertEquals(3, ContentPosition.entries.map { it.column }.distinct().size)
        assertEquals(3, ContentPosition.entries.map { it.row }.distinct().size)
    }

    @Test
    fun `each position is a distinct place`() {
        val cells = ContentPosition.entries.map { it.column to it.row }
        assertEquals(9, cells.distinct().size)
    }

    @Test
    fun `page scaling offers the four modes the reference app does`() {
        assertEquals(4, PageScaling.entries.size)
        assertTrue(PageScaling.ACTUAL_SIZE in PageScaling.entries)
        assertTrue(PageScaling.entries.single { it.usesCustomPercent } == PageScaling.CUSTOM)
    }

    @Test
    fun `manual duplex splits front and back sheets by index`() {
        val sheets = listOf(0, 1, 2, 3, 4)
        assertEquals(listOf(0, 2, 4), ManualDuplex.FRONT_SIDES.select(sheets))
        assertEquals(listOf(1, 3), ManualDuplex.BACK_SIDES.select(sheets))
    }

    @Test
    fun `reversed back sides come out in reverse order`() {
        val sheets = listOf(0, 1, 2, 3, 4)
        assertEquals(listOf(3, 1), ManualDuplex.BACK_SIDES_REVERSED.select(sheets))
    }

    @Test
    fun `automatic duplex keeps every sheet`() {
        val sheets = listOf(0, 1, 2, 3)
        assertEquals(sheets, ManualDuplex.NONE.select(sheets))
    }

    @Test
    fun `an odd sheet count needs a blank so the backs land correctly`() {
        assertTrue(ManualDuplex.BACK_SIDES.needsBlankSheet(3))
        assertFalse(ManualDuplex.BACK_SIDES.needsBlankSheet(4))
        assertFalse("automatic duplex never needs a blank", ManualDuplex.NONE.needsBlankSheet(3))
    }

    @Test
    fun `front and back splits never overlap`() {
        val sheets = (0 until 8).toList()
        val front = ManualDuplex.FRONT_SIDES.select(sheets)
        val back = ManualDuplex.BACK_SIDES.select(sheets)
        assertTrue(front.intersect(back.toSet()).isEmpty())
        assertEquals(sheets.size, front.size + back.size)
    }

    private fun resolve(
        mode: PageSelectionMode,
        total: Int,
        segments: List<PageSegment> = emptyList(),
    ): List<Int> = ResolvedPages.resolve(mode, segments, total, reverse = false).numbers

    private fun resolvePages(
        mode: PageSelectionMode,
        total: Int,
        segments: List<PageSegment> = emptyList(),
    ) = ResolvedPages.resolve(mode, segments, total, reverse = false)
}
