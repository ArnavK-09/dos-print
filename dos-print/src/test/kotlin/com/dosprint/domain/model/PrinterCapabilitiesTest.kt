package com.dosprint.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterCapabilitiesTest {

    private val a4 = PaperSize.A4
    private val letter = PaperSize.LETTER
    private val a3 = PaperSize.A3

    private fun capabilities(
        paper: List<PaperSize> = listOf(a4, letter),
        color: Boolean = true,
        grayscale: Boolean = true,
        longEdge: Boolean = false,
        shortEdge: Boolean = false,
        copies: Int = 99,
        layouts: Int = 1,
        borderless: Boolean = false,
        unknown: Boolean = false,
    ) = PrinterCapabilities(
        printerId = "printer",
        supportedPaperSizes = paper,
        supportedResolutions = listOf(PrintResolution.DRAFT, PrintResolution.STANDARD),
        supportsColor = color,
        supportsGrayscale = grayscale,
        supportsDuplexLongEdge = longEdge,
        supportsDuplexShortEdge = shortEdge,
        maxCopies = copies,
        maxSheetLayoutCount = layouts,
        supportsBorderless = borderless,
        unknown = unknown,
    )

    @Test
    fun supportsExactPaperSize() {
        assertTrue(capabilities().supports(a4))
    }

    @Test
    fun doesNotSupportAbsentPaperSize() {
        assertFalse(capabilities().supports(a3))
    }

    @Test
    fun closestPaperSizePrefersNearestDimension() {
        val closest = capabilities().closestPaperSize(a3)
        assertEquals(a4, closest)
    }

    @Test
    fun closestPaperSizeIsNullWhenUnknown() {
        assertEquals(null, capabilities(unknown = true).closestPaperSize(a4))
    }

    @Test
    fun supportsColorWhenAdvertised() {
        assertTrue(capabilities(color = true).supports(ColorMode.COLOR))
    }

    @Test
    fun doesNotSupportColorWhenMonochromeOnly() {
        assertFalse(capabilities(color = false).supports(ColorMode.COLOR))
    }

    @Test
    fun closestColorModeFallsBackToGrayscaleWhenColorAbsent() {
        assertEquals(
            ColorMode.GRAYSCALE,
            capabilities(color = false, grayscale = true).closestColorMode(ColorMode.COLOR),
        )
    }

    @Test
    fun closestColorModeFallsBackToMonoWhenOnlyMonoSupported() {
        assertEquals(
            ColorMode.MONOCHROME,
            capabilities(color = false, grayscale = false).closestColorMode(ColorMode.COLOR),
        )
    }

    @Test
    fun closestColorModePrefersColorWhenAvailable() {
        assertEquals(ColorMode.COLOR, capabilities(color = true).closestColorMode(ColorMode.COLOR))
    }

    @Test
    fun closestDuplexFallsBackToNone() {
        assertEquals(DuplexMode.NONE, capabilities().closestDuplex(DuplexMode.LONG_EDGE))
    }

    @Test
    fun closestDuplexPrefersLongEdge() {
        val resolved = capabilities(longEdge = true).closestDuplex(DuplexMode.LONG_EDGE)
        assertEquals(DuplexMode.LONG_EDGE, resolved)
    }

    @Test
    fun supportsDuplexReportsEitherEdge() {
        assertFalse(capabilities().supportsDuplex)
        assertTrue(capabilities(shortEdge = true).supportsDuplex)
    }

    @Test
    fun supportsSheetLayoutRespectsMaximum() {
        assertFalse(capabilities(layouts = 1).supportsSheetLayout(4))
        assertTrue(capabilities(layouts = 4).supportsSheetLayout(4))
        assertFalse(capabilities(layouts = 4).supportsSheetLayout(0))
    }

    @Test
    fun maxSheetLayoutCountIsClampedWhenUnknown() {
        assertEquals(1, capabilities(unknown = true, layouts = 99).maxSheetLayoutCountOrDefault())
    }

    @Test
    fun closestResolutionPicksNearestDpi() {
        val resolved = capabilities().closestResolution(PrintResolution.HIGH)
        assertEquals(PrintResolution.STANDARD, resolved)
    }
}
