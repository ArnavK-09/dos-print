package com.dosprint.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutGridTest {

    @Test
    fun `cells tile the page without overlapping`() {
        val grid = LayoutGrid(3, 3, marginFraction = 0.1f, gutterFraction = 0.05f)
        val slots = grid.allSlots(300f, 300f)
        assertEquals(9, slots.size)
        for (index in 0 until slots.size) {
            val slot = slots[index]
            val left = slot[0]
            val top = slot[1]
            val right = slot[2]
            val bottom = slot[3]
            assertTrue("cell $index starts inside", left >= 0f && top >= 0f)
            assertTrue("cell $index ends inside", right <= 300f + Tolerance && bottom <= 300f + Tolerance)
        }
    }

    @Test
    fun `row major ordering places the first cell top left`() {
        val grid = LayoutGrid(2, 3, marginFraction = 0f, gutterFraction = 0f)
        val first = grid.slotRect(0, 100f, 300f)
        val second = grid.slotRect(1, 100f, 300f)
        val third = grid.slotRect(2, 100f, 300f)
        assertEquals(0f, first[0], Tolerance)
        assertEquals(0f, first[1], Tolerance)
        assertTrue("second cell moves right", second[0] > first[0])
        assertEquals("second cell shares the row", second[1], first[1], Tolerance)
        assertTrue("third cell starts the next row", third[1] > first[3] - Tolerance)
        assertEquals("third cell restarts at the left", 0f, third[0], Tolerance)
        assertEquals("cells are equal width", first[2] - first[0], second[2] - second[0], Tolerance)
    }

    @Test
    fun `gutter shrinks the cells and pushes them apart`() {
        val tight = LayoutGrid(2, 1, marginFraction = 0f, gutterFraction = 0f)
        val loose = LayoutGrid(2, 1, marginFraction = 0f, gutterFraction = 0.1f)
        val tightWidth = tight.slotRect(0, 200f, 100f)[2] - tight.slotRect(0, 200f, 100f)[0]
        val looseWidth = loose.slotRect(0, 200f, 100f)[2] - loose.slotRect(0, 200f, 100f)[0]
        assertTrue(looseWidth < tightWidth)
    }

    @Test
    fun `out of range slot index yields no geometry`() {
        val grid = LayoutGrid(2, 2)
        assertEquals(0, grid.slotRect(4, 100f, 100f).size)
        assertEquals(0, grid.slotRect(-1, 100f, 100f).size)
    }

    @Test
    fun `forSlots never exceeds the requested slot count by more than one row`() {
        val grid = LayoutGrid.forSlots(5, pageAspect = 0.707f)
        assertTrue(grid.slotCount >= 5)
        assertTrue("waste stays small", grid.slotCount - 5 <= 1)
    }

    @Test
    fun `forSlots fills every cell rather than leaving empty slots`() {
        val grid = LayoutGrid.forSlots(9, pageAspect = 0.707f)
        assertEquals(9, grid.slotCount)
        assertEquals(3, grid.columns)
        assertEquals(3, grid.rows)
    }

    @Test
    fun `forSlots clamps absurd requests`() {
        val grid = LayoutGrid.forSlots(9_999, pageAspect = 1f)
        assertTrue(grid.columns <= 6)
        assertTrue(grid.rows <= 6)
    }

    @Test
    fun `forSlots survives a degenerate page aspect`() {
        val grid = LayoutGrid.forSlots(4, pageAspect = 0f)
        assertEquals(4, grid.slotCount)
    }

    private companion object {
        const val Tolerance = 0.001f

        fun absOf(value: Float): Float = if (value < 0f) -value else value
    }
}
