package com.dosprint.data.print

import com.dosprint.domain.model.PaperSize

data class PdfPageGeometry(
    val widthPoints: Int,
    val heightPoints: Int,
) {
    val widthInches: Float get() = widthPoints / POINTS_PER_INCH

    val heightInches: Float get() = heightPoints / POINTS_PER_INCH

    companion object {
        const val POINTS_PER_INCH = 72f
        const val MILS_PER_INCH = 1000f

        fun forMils(widthMils: Int, heightMils: Int): PdfPageGeometry = PdfPageGeometry(
            widthPoints = (widthMils / MILS_PER_INCH * POINTS_PER_INCH).toInt().coerceAtLeast(1),
            heightPoints = (heightMils / MILS_PER_INCH * POINTS_PER_INCH).toInt().coerceAtLeast(1),
        )

        fun forPaper(paper: PaperSize, landscape: Boolean = false): PdfPageGeometry {
            val oriented = paper.withOrientation(landscape)
            return forMils(oriented.widthMils, oriented.heightMils)
        }
    }
}
