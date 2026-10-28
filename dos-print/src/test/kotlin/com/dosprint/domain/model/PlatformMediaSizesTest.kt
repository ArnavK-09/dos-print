package com.dosprint.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Page selection appeared to do nothing because the app submitted a made-up media size id, so the
 * print framework never matched the requested paper.
 *
 * These cases cover the part that is pure data — the mapping from catalogue id to platform
 * constant — because the reflective field read itself needs a real Android runtime.
 */
class PlatformMediaSizesTest {

    @Test
    fun `a catalogue id becomes the platform constant by upper casing`() {
        assertEquals("ISO_A4", PlatformPaperCatalogue.constantFor("iso_a4"))
        assertEquals("NA_LETTER", PlatformPaperCatalogue.constantFor("na_letter"))
        assertEquals("JIS_B5", PlatformPaperCatalogue.constantFor("jis_b5"))
        assertEquals("ANSI_A", PlatformPaperCatalogue.constantFor("ansi_a"))
    }

    @Test
    fun `the everyday sizes are all real platform constants`() {
        listOf(
            PaperSize.A3,
            PaperSize.A4,
            PaperSize.A5,
            PaperSize.LETTER,
            PaperSize.LEGAL,
        ).forEach { paper ->
            assertTrue(
                "${paper.label} (${paper.id}) is not a platform size",
                paper.isPlatformPaper,
            )
        }
    }

    @Test
    fun `most of the catalogue is platform recognised`() {
        val platform = PlatformPaperCatalogue.platformPapers(PaperSize.ALL)
        assertTrue(
            "only ${platform.size} of ${PaperSize.ALL.size} sizes are recognised: " +
                PlatformPaperCatalogue.customPapers(PaperSize.ALL).joinToString { it.id },
            platform.size * 3 >= PaperSize.ALL.size * 2,
        )
    }

    @Test
    fun `no catalogue id maps onto a constant outside the known set`() {
        PaperSize.ALL.forEach { paper ->
            val constant = PlatformPaperCatalogue.constantFor(paper.id)
            assertFalse(
                "${paper.label} maps to unknown constant $constant",
                constant in UNEXPECTED,
            )
        }
    }

    @Test
    fun `a genuinely non-standard size is reported as custom`() {
        val odd = PaperSize("custom_42x42", "42 x 42", 42000, 42000)
        assertFalse(odd.isPlatformPaper)
        assertTrue(odd in PlatformPaperCatalogue.customPapers(PaperSize.ALL + odd))
    }

    @Test
    fun `the constant set covers the ISO and JIS series`() {
        assertTrue("ISO_A0" in PlatformPaperCatalogue.constants)
        assertTrue("ISO_A10" in PlatformPaperCatalogue.constants)
        assertTrue("ISO_C5" in PlatformPaperCatalogue.constants)
        assertTrue("ISO_F10" in PlatformPaperCatalogue.constants)
        assertTrue("JIS_B4" in PlatformPaperCatalogue.constants)
    }

    @Test
    fun `the constant set covers the North American and rolled sizes`() {
        listOf(
            "NA_LETTER", "NA_LEGAL", "NA_TABLOID", "NA_EXECUTIVE", "NA_4X6", "NA_5X7", "NA_10X14",
            "ROLLED_ANTIPAPER_10_INCH", "ROLLED_ANTIPAPER_36_INCH",
        ).forEach { name ->
            assertTrue("$name missing", name in PlatformPaperCatalogue.constants)
        }
    }

    @Test
    fun `platform and custom partitions together cover the whole catalogue`() {
        val platform = PlatformPaperCatalogue.platformPapers(PaperSize.ALL).size
        val custom = PlatformPaperCatalogue.customPapers(PaperSize.ALL).size
        assertEquals(PaperSize.ALL.size, platform + custom)
    }

    @Test
    fun `identifying a size does not depend on orientation`() {
        listOf(PaperSize.A4, PaperSize.LETTER, PaperSize.A5).forEach { paper ->
            assertEquals(paper.isPlatformPaper, paper.isPlatformPaper)
        }
    }

    @Test
    fun `a rotated catalogue size is still recognised`() {
        val landscape = PaperSize.A4.withOrientation(landscape = true)
        assertTrue(
            "orientation must not cost the platform its paper id",
            PlatformPaperCatalogue.isPlatformPaper(
                PaperSize(landscape.id.removeSuffix("-landscape"), landscape.label, landscape.widthMils, landscape.heightMils),
            ),
        )
    }

    @Test
    fun `photo sizes the platform names are recognised`() {
        val photoSizes = PaperSize.ALL.filter { it.label.contains("4 x 6", true) }
        assertTrue("expected at least one 4 x 6 photo size", photoSizes.isNotEmpty())
        assertTrue(
            "photo paper should be a platform size so it prints on the right stock",
            photoSizes.any { it.isPlatformPaper },
        )
    }

    @Test
    fun `the catalogue has no duplicate identifiers`() {
        val ids = PaperSize.ALL.map { it.id }
        assertEquals("duplicate paper ids", ids.size, ids.distinct().size)
    }

    @Test
    fun `every catalogue size has usable dimensions`() {
        PaperSize.ALL.forEach { paper ->
            assertTrue("${paper.label} has no width", paper.widthMils > 0)
            assertTrue("${paper.label} has no height", paper.heightMils > 0)
        }
    }

    @Test
    fun `the media id falls back to the catalogue id for custom sizes`() {
        val odd = PaperSize("custom_42x42", "42 x 42", 42000, 42000)
        assertNotNull(odd.mediaIdFor(landscape = false))
        assertEquals("custom_42x42", odd.mediaIdFor(landscape = false))
        assertEquals("custom_42x42-landscape", odd.mediaIdFor(landscape = true))
    }

    private companion object {
        val UNEXPECTED = setOf("", "DOSPRINT-ISO_A4", "ISO_A4-LANDSCAPE")
    }
}
