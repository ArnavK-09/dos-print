package com.dosprint.domain.model

import android.graphics.pdf.PdfRenderer
import android.print.PrintAttributes
import androidx.compose.runtime.Immutable

@Immutable
enum class PrintResolution(val label: String, val horizontalDpi: Int, val verticalDpi: Int) {
    DRAFT("Draft", 150, 150),
    STANDARD("Standard", 300, 300),
    HIGH("High", 600, 600),
    BEST("Best", 1200, 1200),
    ;

    fun toPrintResolution(): PrintAttributes.Resolution =
        PrintAttributes.Resolution("res-${name.lowercase()}", label, horizontalDpi, verticalDpi)

    fun next(): PrintResolution = when (this) {
        DRAFT -> STANDARD
        STANDARD -> HIGH
        HIGH -> BEST
        BEST -> BEST
    }

    fun previous(): PrintResolution = when (this) {
        DRAFT -> DRAFT
        STANDARD -> DRAFT
        HIGH -> STANDARD
        BEST -> HIGH
    }

    companion object {
        fun closestTo(dpi: Int): PrintResolution = when {
            dpi <= 200 -> DRAFT
            dpi <= 400 -> STANDARD
            dpi <= 900 -> HIGH
            else -> BEST
        }
    }
}

@Immutable
enum class ColorMode(val label: String) {
    COLOR("Color"),
    GRAYSCALE("Grayscale"),
    MONOCHROME("Black and white"),
    ;

    fun toPrintColorMode(): Int = when (this) {
        COLOR, GRAYSCALE -> PrintAttributes.COLOR_MODE_COLOR
        MONOCHROME -> PrintAttributes.COLOR_MODE_MONOCHROME
    }

    fun desaturatesContent(): Boolean = this != COLOR

    companion object {
        fun fromPrintConstant(constant: Int): ColorMode = when (constant) {
            PrintAttributes.COLOR_MODE_COLOR -> COLOR
            else -> MONOCHROME
        }
    }
}

@Immutable
enum class ScalingMode(val label: String) {
    FIT_TO_PAGE("Fit to page"),
    FILL_PAGE("Fill page"),
    ACTUAL_SIZE("Actual size"),
    ;

    fun multipliesContent(): Boolean = this == FILL_PAGE

    fun fitsContent(): Boolean = this == FIT_TO_PAGE
}

@Immutable
enum class DuplexMode(val label: String) {
    NONE("Single-sided"),
    LONG_EDGE("Long edge"),
    SHORT_EDGE("Short edge"),
    ;

    fun toPrintDuplexMode(): Int = when (this) {
        NONE -> PrintAttributes.DUPLEX_MODE_NONE
        LONG_EDGE -> PrintAttributes.DUPLEX_MODE_LONG_EDGE
        SHORT_EDGE -> PrintAttributes.DUPLEX_MODE_SHORT_EDGE
    }

    fun isDuplex(): Boolean = this != NONE

    companion object {
        fun fromPrintConstant(constant: Int): DuplexMode = when (constant) {
            PrintAttributes.DUPLEX_MODE_LONG_EDGE -> LONG_EDGE
            PrintAttributes.DUPLEX_MODE_SHORT_EDGE -> SHORT_EDGE
            else -> NONE
        }
    }
}

@Immutable
enum class MarginPreset(val label: String) {
    PRINTER_DEFAULT("Printer default"),
    MINIMUM("Minimum"),
    NONE("No margins"),
    ;

    fun toMargins(printerMargins: PrintAttributes.Margins?): PrintAttributes.Margins = when (this) {
        PRINTER_DEFAULT -> printerMargins ?: PrintAttributes.Margins.NO_MARGINS
        MINIMUM -> PrintAttributes.Margins(MINIMUM_MARGIN_MILS, MINIMUM_MARGIN_MILS, MINIMUM_MARGIN_MILS, MINIMUM_MARGIN_MILS)
        NONE -> PrintAttributes.Margins.NO_MARGINS
    }

    private companion object {
        const val MINIMUM_MARGIN_MILS = 1000
    }
}

@Immutable
enum class PageRotation(val label: String, val degrees: Int) {
    NONE("None", 0),
    CLOCKWISE_90("90°", 90),
    UPSIDE_DOWN("180°", 180),
    COUNTER_CLOCKWISE_90("270°", 270),
    ;

    val swapsDimensions: Boolean get() = degrees == 90 || degrees == 270

    fun next(): PageRotation = when (this) {
        NONE -> CLOCKWISE_90
        CLOCKWISE_90 -> UPSIDE_DOWN
        UPSIDE_DOWN -> COUNTER_CLOCKWISE_90
        COUNTER_CLOCKWISE_90 -> NONE
    }
}

@Immutable
enum class WatermarkStyle(val label: String) {
    NONE("None"),
    TEXT("Custom"),
    ;

    fun text(customText: String): String = when (this) {
        NONE -> ""
        TEXT -> customText
    }
}

@Immutable
data class PageGeometry(
    val contentWidthMils: Int,
    val contentHeightMils: Int,
) {
    fun aspectRatio(): Float =
        if (contentHeightMils == 0) DEFAULT_ASPECT_RATIO else contentWidthMils.toFloat() / contentHeightMils

    companion object {
        private const val DEFAULT_ASPECT_RATIO = 0.707f

        fun fromPdfPage(widthPoints: Int, heightPoints: Int): PageGeometry {
            val widthMils = (widthPoints * PaperSize.POINTS_PER_INCH) / PaperSize.MILLIS_PER_INCH
            val heightMils = (heightPoints * PaperSize.POINTS_PER_INCH) / PaperSize.MILLIS_PER_INCH
            return PageGeometry(
                contentWidthMils = widthMils.coerceAtLeast(1),
                contentHeightMils = heightMils.coerceAtLeast(1),
            )
        }

        fun fromPdfPage(page: PdfRenderer.Page): PageGeometry = fromPdfPage(page.width, page.height)
    }
}
