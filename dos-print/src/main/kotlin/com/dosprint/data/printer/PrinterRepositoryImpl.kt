package com.dosprint.data.printer

import com.dosprint.core.AppError
import com.dosprint.core.AppLog
import com.dosprint.core.Outcome
import com.dosprint.data.local.JsonFileStore
import com.dosprint.data.local.PrinterFile
import com.dosprint.data.local.StoredPrinter
import com.dosprint.domain.model.PdfDestinationCapabilities
import com.dosprint.domain.model.PrinterAvailability
import com.dosprint.domain.model.PrinterCapabilities
import com.dosprint.domain.model.PrinterConnectionType
import com.dosprint.domain.model.PrinterDestinations
import com.dosprint.domain.model.PrinterProfile
import com.dosprint.domain.repository.PreferencesRepository
import com.dosprint.domain.repository.PrinterRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PrinterRepositoryImpl(
    private val gateway: SystemPrinterGateway,
    private val discovery: MdnsDiscovery,
    private val preferencesRepository: PreferencesRepository,
    private val store: JsonFileStore<PrinterFile>,
) : PrinterRepository {

    private val cached = MutableStateFlow<List<PrinterProfile>>(emptyList())
    private val learnedCapabilities = MutableStateFlow<Map<String, PrinterCapabilities>>(emptyMap())
    private val refreshMutex = Mutex()

    override val printers: StateFlow<List<PrinterProfile>> = cached.asStateFlow()

    override val defaultPrinter: Flow<PrinterProfile?> = preferencesRepository.preferences.map { preferences ->
        val all = cached.value
        all.firstOrNull { it.id == preferences.defaultPrinterId } ?: all.firstOrNull { it.isDefault }
    }

    override suspend fun load() {
        val stored = store.load()
        cached.value = stored.printers.map { it.toProfile() }.withPdfDestination()
    }

    override suspend fun refreshPrinters(): List<PrinterProfile> = refreshMutex.withLock {
        val discovered = withContext(Dispatchers.IO) {
            discovery.discover(SCAN_DURATION_MILLIS)
        }
        val learned = try {
            gateway.discoverCapabilities(gateway.currentJobs())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLog.warn(AppLog.throwableTag("learnCapabilities", error))
            emptyMap()
        }
        learnedCapabilities.value = mergeCapabilities(learnedCapabilities.value, learned)
        val manual = cached.value.filter { it.isUserAdded }
        val merged = LinkedHashMap<String, PrinterProfile>()
        (manual + discovered).forEach { profile ->
            val existing = merged[profile.id]
            merged[profile.id] = if (existing == null) {
                profile
            } else {
                existing.copy(
                    isUserAdded = true,
                    displayName = existing.displayName.ifBlank { profile.displayName },
                )
            }
        }
        val withDefault = merged.values.toList().withPdfDestination()
        cached.value = withDefault
        persist(withDefault.filterNot { it.isVirtual })
        withDefault
    }

    override suspend fun findPrinter(printerId: String): PrinterProfile? {
        cached.value.firstOrNull { it.id == printerId }?.let { return it }
        return cached.value.firstOrNull { it.displayName == printerId }
    }

    override suspend fun upsertManualPrinter(printer: PrinterProfile) {
        cached.update { current ->
            (current.filterNot { it.id == printer.id } + printer.copy(isUserAdded = true))
                .withPdfDestination()
        }
        persist(cached.value.filterNot { it.isVirtual })
    }

    override suspend fun removePrinter(printerId: String) {
        if (printerId == PrinterDestinations.PdfId) {
            return
        }
        cached.update { current -> current.filterNot { it.id == printerId }.withPdfDestination() }
        persist(cached.value)
        if (currentDefaultId() == printerId) {
            preferencesRepository.setDefaultPrinterId(null)
        }
    }

    override suspend fun renamePrinter(printerId: String, displayName: String) {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty()) {
            AppLog.warn("Ignoring rename to a blank name")
            return
        }
        cached.update { current ->
            current.map { if (it.id == printerId) it.copy(displayName = trimmed) else it }
        }
        persist(cached.value)
    }

    override suspend fun setDefaultPrinter(printerId: String) {
        if (printerId == PrinterDestinations.PdfId) {
            preferencesRepository.setDefaultPrinterId(printerId)
            cached.update { current -> current.map { it.copy(isDefault = it.id == printerId) } }
            return
        }
        if (cached.value.none { it.id == printerId }) {
            AppLog.warn("Cannot default unknown printer $printerId")
            return
        }
        cached.update { current ->
            current.map { it.copy(isDefault = it.id == printerId) }
        }
        persist(cached.value.filterNot { it.isVirtual })
        preferencesRepository.setDefaultPrinterId(printerId)
    }

    override suspend fun capabilitiesFor(printerId: String): Outcome<PrinterCapabilities> {
        val learned = learnedCapabilities.value[printerId]
        if (learned != null) {
            return Outcome.Success(learned)
        }
        val printer = findPrinter(printerId)
        if (printer == null) {
            return Outcome.Failure(AppError.PrinterUnresolved(printerId))
        }
        if (printer.isVirtual) {
            return Outcome.Success(PdfDestinationCapabilities)
        }
        return Outcome.Success(PrinterCapabilities.Unknown.copy(printerId = printer.id))
    }

    override suspend fun isPrintServiceAvailable(): Boolean = gateway.isAvailable()

    override fun openSystemPrinterSettings() {
        if (gateway.isAvailable()) {
            gateway.openPrintSettings()
        } else {
            gateway.openPrintServiceInstaller()
        }
    }

    private suspend fun currentDefaultId(): String? =
        preferencesRepository.preferences.first().defaultPrinterId

    private fun mergeCapabilities(
        existing: Map<String, PrinterCapabilities>,
        discovered: Map<String, PrinterCapabilities>,
    ): Map<String, PrinterCapabilities> {
        if (discovered.isEmpty()) {
            return existing
        }
        val merged = LinkedHashMap(existing)
        for ((key, value) in discovered) {
            val previous = merged[key]
            merged[key] = if (previous == null) {
                value
            } else {
                previous.copy(
                    supportedPaperSizes = (previous.supportedPaperSizes + value.supportedPaperSizes)
                        .distinctBy { it.id },
                    supportedResolutions = (previous.supportedResolutions + value.supportedResolutions).distinct(),
                    supportsColor = previous.supportsColor || value.supportsColor,
                    supportsGrayscale = previous.supportsGrayscale || value.supportsGrayscale,
                    supportsDuplexLongEdge = previous.supportsDuplexLongEdge || value.supportsDuplexLongEdge,
                    supportsDuplexShortEdge = previous.supportsDuplexShortEdge || value.supportsDuplexShortEdge,
                    minMarginsMils = minOf(previous.minMarginsMils, value.minMarginsMils),
                    unknown = false,
                )
            }
        }
        return merged
    }

    private suspend fun persist(list: List<PrinterProfile>) {
        store.update { file ->
            file.copy(printers = list.map { it.toStored() })
        }
    }

    private fun List<PrinterProfile>.withPdfDestination(): List<PrinterProfile> {
        if (any { it.isVirtual }) {
            return this
        }
        val withoutDefault = map { it.copy(isDefault = false) }
        return withoutDefault + PrinterProfile.PdfDestination.copy(isDefault = withoutDefault.isEmpty())
    }

    private companion object {
        const val SCAN_DURATION_MILLIS = 6_000L
    }
}

private fun StoredPrinter.toProfile(): PrinterProfile = PrinterProfile(
    id = id,
    displayName = displayName,
    description = description,
    connectionType = runCatching { PrinterConnectionType.valueOf(connectionType) }
        .getOrDefault(PrinterConnectionType.WIFI),
    serviceId = serviceId,
    address = address,
    isUserAdded = isUserAdded,
    isDefault = isDefault,
    availability = runCatching {
        com.dosprint.domain.model.PrinterAvailability.valueOf(availability)
    }.getOrDefault(com.dosprint.domain.model.PrinterAvailability.UNKNOWN),
)

private fun PrinterProfile.toStored(): StoredPrinter = StoredPrinter(
    id = id,
    displayName = displayName,
    description = description,
    connectionType = connectionType.name,
    serviceId = serviceId,
    address = address,
    isUserAdded = isUserAdded,
    isDefault = isDefault,
    availability = availability.name,
)

internal fun manualPrinterId(name: String, address: String?): String =
    "manual:${name.trim()}@${address?.trim().orEmpty()}"
