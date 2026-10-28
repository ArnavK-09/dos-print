package com.dosprint.domain.model

import androidx.compose.runtime.Immutable

object PrinterDestinations {
    const val PdfId = "destination_pdf"
    const val PdfName = "Save as PDF"
    const val PdfDescription = "Write the finished sheets to a PDF file"
    const val PdfUriScheme = "dosprint+pdf"
}

@Immutable
data class AddPrinterResult(
    val printer: PrinterProfile?,
    val error: AddPrinterError? = null,
)

@Immutable
sealed interface AddPrinterError {
    data object NameRequired : AddPrinterError

    data class InvalidName(val reason: NameProblem) : AddPrinterError

    data class AddressRequired(val connectionType: PrinterConnectionType) : AddPrinterError

    data class InvalidAddress(val reason: AddressProblem) : AddPrinterError

    data class DuplicateName(val existing: String) : AddPrinterError
}

@Immutable
enum class NameProblem {
    TOO_SHORT,
    TOO_LONG,
    CONTAINS_CONTROL_CHARACTERS,
}

@Immutable
enum class AddressProblem {
    NOT_A_HOSTNAME,
    INVALID_PORT,
    OUT_OF_RANGE,
}

@Immutable
object PrinterValidator {

    const val MinNameLength = 1
    const val MaxNameLength = 64
    const val MinHostLabelLength = 1
    const val MaxHostLength = 253
    const val MinPort = 1
    const val MaxPort = 65535

    fun validateName(raw: String): NameProblem? {
        val name = raw.trim()
        if (name.length < MinNameLength) {
            return NameProblem.TOO_SHORT
        }
        if (name.length > MaxNameLength) {
            return NameProblem.TOO_LONG
        }
        if (name.any { it.isISOControl() }) {
            return NameProblem.CONTAINS_CONTROL_CHARACTERS
        }
        return null
    }

    fun validateAddress(raw: String, connectionType: PrinterConnectionType): AddPrinterError? {
        if (!connectionType.requiresAddress) {
            return null
        }
        val address = raw.trim()
        if (address.isEmpty()) {
            return AddPrinterError.AddressRequired(connectionType)
        }
        return when (connectionType) {
            PrinterConnectionType.WIFI, PrinterConnectionType.PORT_RAW -> validateHost(address)
            PrinterConnectionType.BLUETOOTH -> validateBluetoothAddress(address)
            PrinterConnectionType.USB -> validateUsb(address)
            PrinterConnectionType.SYSTEM, PrinterConnectionType.CLOUD, PrinterConnectionType.PDF -> null
        }
    }

    fun validate(request: com.dosprint.ui.AddPrinterRequest, existingNames: List<String>): AddPrinterError? {
        val nameError = validateName(request.name)
        if (nameError != null) {
            return if (nameError == NameProblem.TOO_SHORT) {
                AddPrinterError.NameRequired
            } else {
                AddPrinterError.InvalidName(nameError)
            }
        }
        val trimmed = request.name.trim()
        val clash = existingNames.firstOrNull { it.equals(trimmed, ignoreCase = true) }
        if (clash != null) {
            return AddPrinterError.DuplicateName(clash)
        }
        return validateAddress(request.address, request.connectionType)
    }

    fun splitHostAndPort(raw: String): Pair<String, Int?> {
        val address = raw.trim()
        val separator = address.lastIndexOf(':')
        if (separator <= 0 || separator == address.length - 1) {
            return address to null
        }
        val host = address.substring(0, separator)
        val portText = address.substring(separator + 1)
        val port = portText.toIntOrNull() ?: return address to null
        return host to port
    }

    private fun validateHost(address: String): AddPrinterError? {
        val (host, port) = splitHostAndPort(address)
        val portProblem = if (port != null && (port < MinPort || port > MaxPort)) {
            AddressProblem.OUT_OF_RANGE
        } else {
            null
        }
        return when {
            portProblem != null -> AddPrinterError.InvalidAddress(portProblem)
            host.isEmpty() || host.length > MaxHostLength -> AddPrinterError.InvalidAddress(
                AddressProblem.NOT_A_HOSTNAME,
            )
            else -> validateHostLabels(host)
        }
    }

    private fun validateHostLabels(host: String): AddPrinterError? {
        val labels = host.split('.')
        val malformed = labels.isEmpty() ||
            labels.any { it.length < MinHostLabelLength || it.length > MaxHostLabelLength } ||
            labels.any { !hostLabelPattern.matches(it) }
        return if (malformed) {
            AddPrinterError.InvalidAddress(AddressProblem.NOT_A_HOSTNAME)
        } else {
            null
        }
    }

    private val hostLabelPattern = Regex("^[A-Za-z0-9_-]+$")

    private val usbDevicePattern = Regex("^[0-9A-Fa-f]{4}:[0-9A-Fa-f]{4}$")

    private fun validateBluetoothAddress(address: String): AddPrinterError? {
        val macPattern = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
        if (!macPattern.matches(address)) {
            return AddPrinterError.InvalidAddress(AddressProblem.NOT_A_HOSTNAME)
        }
        return null
    }

    private fun validateUsb(address: String): AddPrinterError? {
        val valid = usbDevicePattern.matches(address)
        return if (valid) {
            null
        } else {
            AddPrinterError.InvalidAddress(AddressProblem.NOT_A_HOSTNAME)
        }
    }

    private const val MaxHostLabelLength = 63
}

fun PrinterProfile.isPdfDestination(): Boolean = id == PrinterDestinations.PdfId

val PrinterConnectionType.requiresAddress: Boolean
    get() = this == PrinterConnectionType.WIFI ||
        this == PrinterConnectionType.PORT_RAW ||
        this == PrinterConnectionType.BLUETOOTH ||
        this == PrinterConnectionType.USB

val PdfDestinationCapabilities: PrinterCapabilities = PrinterCapabilities(
    printerId = PrinterDestinations.PdfId,
    supportedPaperSizes = PaperSize.ALL,
    supportedResolutions = PrintResolution.entries.toList(),
    supportsColor = true,
    supportsGrayscale = true,
    supportsDuplexLongEdge = true,
    supportsDuplexShortEdge = true,
    supportsBorderless = true,
    maxSheetLayoutCount = LayoutGrid.MAX_SAFE_SLOTS,
    unknown = false,
)
