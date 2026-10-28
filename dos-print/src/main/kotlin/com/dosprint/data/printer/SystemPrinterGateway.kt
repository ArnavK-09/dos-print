package com.dosprint.data.printer

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintJob
import android.print.PrintJobInfo
import android.print.PrintManager
import com.dosprint.core.AppLog
import com.dosprint.core.DispatcherProvider
import com.dosprint.core.Outcome
import com.dosprint.core.runCatchingOutcome
import com.dosprint.domain.model.PaperSize
import com.dosprint.domain.model.PrintResolution
import com.dosprint.domain.model.PrinterCapabilities
import com.dosprint.domain.model.PrinterConnectionType
import com.dosprint.domain.model.PrinterProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class SystemPrinterGateway(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) {
    private val printManager: PrintManager? by lazy {
        runCatching { context.getSystemService(PrintManager::class.java) }
            .onFailure { error -> AppLog.warn(AppLog.throwableTag("printManager", error)) }
            .getOrNull()
    }

    fun isAvailable(): Boolean = printManager != null

    fun currentJobs(): List<PrintJob> {
        val manager = printManager ?: return emptyList()
        return runCatching { manager.getPrintJobs() }
            .onFailure { error -> AppLog.warn(AppLog.throwableTag("getPrintJobs", error)) }
            .getOrNull()
            .orEmpty()
    }

    fun activeJobs(): Flow<List<PrintJob>> = flow { emit(currentJobs()) }.flowOn(dispatchers.io)

    fun openPrintSettings() {
        launchIntent(Intent(ACTION_PRINT_SETTINGS))
    }

    fun openPrintServiceInstaller() {
        launchIntent(Intent(ACTION_ADD_PRINT_SERVICE))
    }

    fun printerIdOf(job: PrintJob): String =
        runCatching { job.info.printerId?.localId }.getOrNull().orEmpty()

    fun labelOf(job: PrintJob): String =
        runCatching { job.info.label }.getOrNull().orEmpty()

    fun stateOf(job: PrintJob): Int = runCatching { job.info.state }.getOrDefault(PrintJobInfo.STATE_FAILED)

    fun attributesOf(job: PrintJob): PrintAttributes? = runCatching { job.info.attributes }.getOrNull()

    fun capabilitiesFromAttributes(attributes: PrintAttributes, printerId: String): PrinterCapabilities =
        capabilitiesOf(listOf(attributes), printerId)

    fun discoverCapabilities(printJobs: List<PrintJob>): Map<String, PrinterCapabilities> =
        printJobs
            .mapNotNull { job ->
                val attributes = attributesOf(job) ?: return@mapNotNull null
                val printerId = printerIdOf(job)
                if (printerId.isBlank()) {
                    null
                } else {
                    printerId to capabilitiesFromAttributes(attributes, printerId)
                }
            }
            .toMap()

    fun probeAvailability(): Outcome<Unit> = runCatchingOutcome {
        printManager ?: throw PrinterServiceUnavailableException()
    }

    private fun capabilitiesOf(attributes: List<PrintAttributes>, printerId: String): PrinterCapabilities {
        val sizes = LinkedHashMap<String, PaperSize>()
        val resolutions = LinkedHashSet<PrintResolution>()
        var supportsColor = false
        var supportsDuplexLong = false
        var supportsDuplexShort = false
        var minMargins = 0
        for (current in attributes) {
            current.mediaSize?.let { media ->
                val size = PaperSize.fromPrinter(media.id, media.id, media.widthMils, media.heightMils)
                sizes[size.id] = size
            }
            current.resolution?.let { resolution ->
                resolutions += PrintResolution.closestTo(resolution.horizontalDpi)
            }
            if (current.colorMode == PrintAttributes.COLOR_MODE_COLOR) {
                supportsColor = true
            }
            when (current.duplexMode) {
                PrintAttributes.DUPLEX_MODE_LONG_EDGE -> supportsDuplexLong = true
                PrintAttributes.DUPLEX_MODE_SHORT_EDGE -> supportsDuplexShort = true
            }
            current.minMargins?.let { margins ->
                minMargins = maxOf(minMargins, margins.leftMils, margins.topMils)
            }
        }
        return PrinterCapabilities(
            printerId = printerId,
            supportedPaperSizes = sizes.values.toList(),
            supportedResolutions = resolutions.toList().ifEmpty { listOf(PrintResolution.STANDARD) },
            supportsColor = supportsColor,
            supportsGrayscale = true,
            supportsDuplexLongEdge = supportsDuplexLong,
            supportsDuplexShortEdge = supportsDuplexShort,
            maxCopies = PrintSettingsMaxCopies,
            maxSheetLayoutCount = MaxSheetLayoutCount,
            minMarginsMils = minMargins,
            supportsBorderless = minMargins == 0,
            unknown = sizes.isEmpty(),
        )
    }

    private fun launchIntent(intent: Intent) {
        val withFlags = intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        runCatching { context.startActivity(withFlags) }
            .onFailure { error -> AppLog.warn(AppLog.throwableTag("launchPrintSettings", error)) }
    }

    private companion object {
        const val ACTION_PRINT_SETTINGS = "android.settings.ACTION_PRINT_SETTINGS"
        const val ACTION_ADD_PRINT_SERVICE = "android.print.service.action.ADD_PRINT_SERVICE"
        const val PrintSettingsMaxCopies = 99
        const val MaxSheetLayoutCount = 9
    }
}

class PrinterServiceUnavailableException : IllegalStateException("No print service available on this device")

internal fun PrinterProfile.isSystemProfile(): Boolean = connectionType == PrinterConnectionType.SYSTEM

internal fun PrinterProfile.duplicateOf(other: PrinterProfile): Boolean =
    id == other.id || displayName.equals(other.displayName, ignoreCase = true)
