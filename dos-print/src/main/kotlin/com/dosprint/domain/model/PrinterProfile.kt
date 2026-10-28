package com.dosprint.domain.model

import androidx.compose.runtime.Immutable

@Immutable
enum class PrinterConnectionType(val label: String) {
    SYSTEM("Print service"),
    WIFI("Wi-Fi"),
    PORT_RAW("Raw port"),
    BLUETOOTH("Bluetooth"),
    USB("USB"),
    CLOUD("Cloud"),
    PDF("PDF file"),
}

@Immutable
enum class PrinterAvailability {
    ONLINE,
    BUSY,
    OFFLINE,
    NO_PAPER,
    ERROR,
    UNKNOWN,
}

@Immutable
data class PrinterProfile(
    val id: String,
    val displayName: String,
    val description: String,
    val connectionType: PrinterConnectionType,
    val serviceId: String? = null,
    val address: String? = null,
    val isUserAdded: Boolean = false,
    val isDefault: Boolean = false,
    val availability: PrinterAvailability = PrinterAvailability.UNKNOWN,
) {
    val isReachable: Boolean
        get() = availability != PrinterAvailability.OFFLINE && availability != PrinterAvailability.ERROR

    val systemPrinterId: String? get() = serviceId

    val isVirtual: Boolean get() = connectionType == PrinterConnectionType.PDF

    val acceptsAddress: Boolean
        get() = connectionType == PrinterConnectionType.WIFI ||
            connectionType == PrinterConnectionType.BLUETOOTH ||
            connectionType == PrinterConnectionType.USB ||
            connectionType == PrinterConnectionType.PORT_RAW

    companion object {
        val PdfDestination: PrinterProfile = PrinterProfile(
            id = PrinterDestinations.PdfId,
            displayName = PrinterDestinations.PdfName,
            description = PrinterDestinations.PdfDescription,
            connectionType = PrinterConnectionType.PDF,
            isUserAdded = false,
            availability = PrinterAvailability.ONLINE,
        )
    }

    fun matches(other: PrinterProfile): Boolean =
        id == other.id || (displayName.equals(other.displayName, ignoreCase = true) && connectionType == other.connectionType)

    fun withAvailability(value: PrinterAvailability): PrinterProfile = copy(availability = value)

    fun withDefault(value: Boolean): PrinterProfile = copy(isDefault = value)
}

@Immutable
data class PrinterCapabilities(
    val printerId: String,
    val supportedPaperSizes: List<PaperSize> = emptyList(),
    val supportedResolutions: List<PrintResolution> = listOf(PrintResolution.DRAFT, PrintResolution.STANDARD),
    val supportsColor: Boolean = false,
    val supportsGrayscale: Boolean = true,
    val supportsDuplexLongEdge: Boolean = false,
    val supportsDuplexShortEdge: Boolean = false,
    val maxCopies: Int = PrintSettings.MAX_COPIES,
    val maxSheetLayoutCount: Int = 1,
    val minMarginsMils: Int = 0,
    val supportsBorderless: Boolean = false,
    val unknown: Boolean = true,
) {
    val supportsDuplex: Boolean get() = supportsDuplexLongEdge || supportsDuplexShortEdge

    fun supports(paperSize: PaperSize): Boolean =
        supportedPaperSizes.any { it.matches(paperSize) }

    fun closestPaperSize(paperSize: PaperSize): PaperSize? {
        if (unknown || supportedPaperSizes.isEmpty()) {
            return null
        }
        return supportedPaperSizes.minByOrNull { candidate ->
            val widthDelta = kotlin.math.abs(candidate.widthMils - paperSize.widthMils)
            val heightDelta = kotlin.math.abs(candidate.heightMils - paperSize.heightMils)
            widthDelta + heightDelta
        }
    }

    fun supports(resolution: PrintResolution): Boolean =
        supportedResolutions.any { it.horizontalDpi == resolution.horizontalDpi }

    fun closestResolution(resolution: PrintResolution): PrintResolution {
        if (supportedResolutions.isEmpty()) {
            return resolution
        }
        return supportedResolutions.minByOrNull { kotlin.math.abs(it.horizontalDpi - resolution.horizontalDpi) } ?: resolution
    }

    fun supports(colorMode: ColorMode): Boolean = when (colorMode) {
        ColorMode.COLOR -> supportsColor
        ColorMode.GRAYSCALE -> supportsGrayscale || supportsColor
        ColorMode.MONOCHROME -> true
    }

    fun closestColorMode(colorMode: ColorMode): ColorMode = when {
        supports(colorMode) -> colorMode
        supportsColor -> ColorMode.COLOR
        supportsGrayscale && !supportsColor -> ColorMode.GRAYSCALE
        else -> ColorMode.MONOCHROME
    }

    fun supports(duplex: DuplexMode): Boolean = when (duplex) {
        DuplexMode.NONE -> true
        DuplexMode.LONG_EDGE -> supportsDuplexLongEdge
        DuplexMode.SHORT_EDGE -> supportsDuplexShortEdge
    }

    fun closestDuplex(duplex: DuplexMode): DuplexMode = when {
        supports(duplex) -> duplex
        supportsDuplexLongEdge -> DuplexMode.LONG_EDGE
        supportsDuplexShortEdge -> DuplexMode.SHORT_EDGE
        else -> DuplexMode.NONE
    }

    fun supportsSheetLayout(count: Int): Boolean = count <= maxSheetLayoutCount && count > 0

    fun maxSheetLayoutCountOrDefault(): Int =
        if (unknown) 1 else maxSheetLayoutCount.coerceIn(1, LayoutGrid.MAX_SAFE_SLOTS)

    companion object {
        val Unknown: PrinterCapabilities = PrinterCapabilities(printerId = "")
    }
}
