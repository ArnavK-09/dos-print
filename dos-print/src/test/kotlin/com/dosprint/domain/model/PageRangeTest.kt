package com.dosprint.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageRangeTest {

    @Test
    fun parsesSinglePage() {
        val result = PageRange.parse("3")
        assertTrue(result.isSuccess)
        assertEquals("3", result.getOrThrow().toDisplayString())
    }

    @Test
    fun parsesMixedRanges() {
        val range = PageRange.parse("1-3,5,8-10").getOrThrow()
        assertEquals("1-3, 5, 8-10", range.toDisplayString())
        assertEquals(7, range.selectedPageCount(100))
    }

    @Test
    fun rejectsReversedRange() {
        assertTrue(PageRange.parse("5-3").isFailure)
    }

    @Test
    fun rejectsZeroPage() {
        assertTrue(PageRange.parse("0").isFailure)
    }

    @Test
    fun rejectsGarbage() {
        assertTrue(PageRange.parse("abc").isFailure)
    }

    @Test
    fun rejectsEmptyInput() {
        assertTrue(PageRange.parse("").isFailure)
    }

    @Test
    fun rejectsBlankTokens() {
        assertTrue(PageRange.parse("1,,2").isSuccess)
    }

    @Test
    fun acceptsUnicodeDashes() {
        val range = PageRange.parse("1\u20133").getOrThrow()
        assertEquals("1-3", range.toDisplayString())
    }

    @Test
    fun clampsToDocumentLength() {
        val range = PageRange.parse("5-10").getOrThrow()
        val clamped = range.clampTo(7)
        assertEquals(3, clamped.selectedPageCount(7))
    }

    @Test
    fun clampsToEmptyWhenOutOfRange() {
        val range = PageRange.parse("20-30").getOrThrow()
        assertEquals(0, range.clampTo(7).selectedPageCount(7))
    }

    @Test
    fun mergesAdjacentRanges() {
        val range = PageRange.parse("1-3,4-6").getOrThrow()
        assertEquals("1-6", range.toDisplayString())
    }

    @Test
    fun allPagesSelectsEverything() {
        val range = PageRange.All
        assertTrue(range.isAllPages)
        assertEquals(10, range.selectedPageCount(10))
    }

    @Test
    fun firstPageOrNullReturnsLowest() {
        val range = PageRange.parse("5-7,2").getOrThrow()
        assertEquals(2, range.firstPageOrNull(10))
    }

    @Test
    fun ofCreatesSinglePageSegments() {
        val range = PageRange.of(3, 1, 2, 1)
        assertEquals("1-3", range.toDisplayString())
    }

    @Test
    fun segmentsAreValidForBoundsChecking() {
        val segment = PageSegment(2, 5)
        assertEquals(4, segment.count())
        assertTrue(segment.overlaps(PageSegment(5, 9)))
        assertEquals(PageSegment(2, 5), segment.clampTo(10))
    }
}
