package com.dosprint.domain.model

object PrintSettingsResolver {
    fun resolve(
        settings: PrintSettings,
        capabilities: PrinterCapabilities,
        totalPages: Int? = null,
    ): PrintSettings {
        val clamped = settings.withClampedValues()
        val maxLayout = capabilities.maxSheetLayoutCountOrDefault()
        return clamped.copy(
            paperSize = capabilities.closestPaperSize(clamped.paperSize) ?: clamped.paperSize,
            colorMode = capabilities.closestColorMode(clamped.colorMode),
            duplex = capabilities.closestDuplex(clamped.duplex),
            copies = clamped.copies.coerceIn(PrintSettings.MIN_COPIES, capabilities.maxCopies.coerceAtLeast(1)),
            pagesPerSheet = clamped.pagesPerSheet.coerceAtMost(maxLayout),
            borderless = clamped.borderless && capabilities.supportsBorderless,
            pageRange = totalPages?.let { clamped.pageRange.clampTo(it.coerceAtLeast(1)) } ?: clamped.pageRange,
        )
    }
}
