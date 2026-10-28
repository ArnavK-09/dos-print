package com.dosprint.data.print

import com.dosprint.domain.model.PaperSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageGeometryTest {

    @Test
    fun `a4 is declared as points not pixels`() {
        val geometry = PdfPageGeometry.forPaper(PaperSize.A4)
        assertEquals(595.0f, geometry.widthPoints.toFloat(), 1f)
        assertEquals(842.0f, geometry.heightPoints.toFloat(), 1f)
    }

    @Test
    fun `a4 measures eight and a quarter by eleven and two thirds inches`() {
        val geometry = PdfPageGeometry.forPaper(PaperSize.A4)
        assertEquals(8.27f, geometry.widthInches, 0.02f)
        assertEquals(11.69f, geometry.heightInches, 0.02f)
    }

    @Test
    fun `letter is declared as points not pixels`() {
        val geometry = PdfPageGeometry.forPaper(PaperSize.LETTER)
        assertEquals(612.0f, geometry.widthPoints.toFloat(), 1f)
        assertEquals(792.0f, geometry.heightPoints.toFloat(), 1f)
    }

    @Test
    fun `a4 and letter differ so choosing a size actually changes the output`() {
        val a4 = PdfPageGeometry.forPaper(PaperSize.A4)
        val letter = PdfPageGeometry.forPaper(PaperSize.LETTER)
        assertTrue(a4.widthPoints != letter.widthPoints)
        assertTrue(a4.heightPoints != letter.heightPoints)
    }

    @Test
    fun `photo sizes are declared in points`() {
        val geometry = PdfPageGeometry.forPaper(PaperSize.PHOTO_4X6)
        assertEquals(288.0f, geometry.widthPoints.toFloat(), 2f)
        assertEquals(432.0f, geometry.heightPoints.toFloat(), 2f)
    }

    @Test
    fun `landscape swaps the page dimensions`() {
        val portrait = PdfPageGeometry.forPaper(PaperSize.A4, landscape = false)
        val landscape = PdfPageGeometry.forPaper(PaperSize.A4, landscape = true)
        assertEquals(portrait.heightPoints.toFloat(), landscape.widthPoints.toFloat(), 1f)
        assertEquals(portrait.widthPoints.toFloat(), landscape.heightPoints.toFloat(), 1f)
    }

    @Test
    fun `the aspect ratio survives the conversion`() {
        val paper = PaperSize.A4
        val geometry = PdfPageGeometry.forPaper(paper)
        val paperAspect = paper.widthMils.toFloat() / paper.heightMils
        val pointAspect = geometry.widthPoints.toFloat() / geometry.heightPoints
        assertEquals(paperAspect, pointAspect, 0.005f)
    }

    @Test
    fun `every catalogue size produces a valid page`() {
        PaperSize.ALL.forEach { paper ->
            val geometry = PdfPageGeometry.forPaper(paper)
            assertTrue("${paper.label} width", geometry.widthPoints >= 100)
            assertTrue("${paper.label} height", geometry.heightPoints >= 100)
        }
    }

    @Test
    fun `a degenerate size never produces a zero page`() {
        val geometry = PdfPageGeometry.forMils(0, 0)
        assertEquals(1, geometry.widthPoints)
        assertEquals(1, geometry.heightPoints)
    }

    @Test
    fun `mils convert at seventy two points per inch`() {
        val geometry = PdfPageGeometry.forMils(1000, 1000)
        assertEquals(72, geometry.widthPoints)
        assertEquals(72, geometry.heightPoints)
    }
}
