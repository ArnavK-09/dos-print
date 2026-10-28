package com.dosprint.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paper is split into what the printer reports and what it does not.
 *
 * A size the printer cannot load looks like it worked in a flat list, and the request is quietly
 * ignored. These cases pin the split so the UI can stay honest.
 */
class PaperChoicesTest {

    private fun capabilities(
        sizes: List<PaperSize>,
        unknown: Boolean = false,
    ) = PrinterCapabilities(printerId = "p1", supportedPaperSizes = sizes, unknown = unknown)

    @Test
    fun `a printer that reports sizes puts them first`() {
        val choices = PaperChoices.forPrinter(
            capabilities(listOf(PaperSize.A4, PaperSize.LETTER)),
            PaperSize.A4,
        )
        assertEquals(listOf(PaperSize.A4, PaperSize.LETTER), choices.supported)
        assertTrue(choices.isReliable)
    }

    @Test
    fun `reported sizes are not repeated in the other group`() {
        val choices = PaperChoices.forPrinter(capabilities(listOf(PaperSize.A4)), PaperSize.A4)
        assertFalse(choices.other.any { it.matches(PaperSize.A4) })
    }

    @Test
    fun `the two groups together cover every catalogue size`() {
        val choices = PaperChoices.forPrinter(capabilities(listOf(PaperSize.A4)), PaperSize.A4)
        assertEquals(PaperSize.ALL.size, choices.all.size)
    }

    @Test
    fun `a printer with unknown capabilities offers the common sizes and admits it`() {
        val choices = PaperChoices.forPrinter(
            capabilities(listOf(PaperSize.A4), unknown = true),
            PaperSize.A4,
        )
        assertFalse(choices.isReliable)
        assertTrue(choices.supported.isNotEmpty())
        assertTrue("nothing should be hidden when the printer will not say", choices.other.isEmpty())
    }

    @Test
    fun `a printer that reports no sizes is treated the same as unknown`() {
        val choices = PaperChoices.forPrinter(capabilities(emptyList()), PaperSize.A4)
        assertFalse(choices.isReliable)
        assertTrue(choices.all.isNotEmpty())
    }

    @Test
    fun `a size outside the reported list is kept so the selection cannot vanish`() {
        val exotic = PaperSize("photo_2x2", "2 x 2 in photo", 2000, 2000)
        val choices = PaperChoices.forPrinter(capabilities(listOf(PaperSize.A4)), exotic)
        assertTrue("the chosen size must stay selectable", choices.supported.any { it.id == exotic.id })
    }

    @Test
    fun `a chosen size matching a reported one is not duplicated`() {
        val choices = PaperChoices.forPrinter(capabilities(listOf(PaperSize.A4)), PaperSize.A4)
        assertEquals(1, choices.supported.count { it.matches(PaperSize.A4) })
    }

    @Test
    fun `degenerate reported sizes are discarded`() {
        val broken = PaperSize("broken", "Broken", 0, 0)
        val choices = PaperChoices.forPrinter(capabilities(listOf(broken)), PaperSize.A4)
        assertFalse(choices.supported.any { it.id == broken.id })
    }

    @Test
    fun `contains answers for both groups`() {
        val choices = PaperChoices.forPrinter(capabilities(listOf(PaperSize.A4)), PaperSize.A4)
        assertTrue(choices.contains(PaperSize.A4.id))
        assertTrue(choices.contains(choices.other.first().id))
        assertFalse(choices.contains("not_a_size"))
    }

    @Test
    fun `reported sizes keep the order the printer gave them`() {
        val order = listOf(PaperSize.LETTER, PaperSize.A4, PaperSize.A3)
        val choices = PaperChoices.forPrinter(capabilities(order), PaperSize.A4)
        assertEquals(order, choices.supported)
    }

    @Test
    fun `the list is never empty so the picker always has something to show`() {
        listOf(true, false).forEach { unknown ->
            val choices = PaperChoices.forPrinter(capabilities(listOf(PaperSize.A4), unknown), PaperSize.A4)
            assertFalse(choices.isEmpty)
        }
    }

    @Test
    fun `every offered size has usable dimensions`() {
        val choices = PaperChoices.forPrinter(capabilities(listOf(PaperSize.A4)), PaperSize.A4)
        choices.all.forEach { paper ->
            assertTrue(paper.label, paper.widthMils > 0)
            assertTrue(paper.label, paper.heightMils > 0)
        }
    }
}
