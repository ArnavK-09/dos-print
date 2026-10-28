package com.dosprint.data.history

import com.dosprint.core.AppError
import com.dosprint.core.AppLog
import com.dosprint.data.local.HistoryFile
import com.dosprint.data.local.JsonFileStore
import com.dosprint.data.local.StoredJob
import com.dosprint.data.preferences.PrintSettingsCodec
import com.dosprint.domain.model.PrintJob
import com.dosprint.domain.model.PrintJobStatus
import com.dosprint.domain.model.PrintSettings
import com.dosprint.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class HistoryRepositoryImpl(
    private val store: JsonFileStore<HistoryFile>,
    private val historyLimitProvider: () -> Int,
    private val keepHistoryProvider: () -> Boolean,
) : HistoryRepository {

    override val jobs: Flow<List<PrintJob>> = store.value.map { file ->
        file.jobs.map { it.toJob() }.sortedByDescending { it.submittedAtMillis }
    }

    override suspend fun load() {
        store.load()
    }

    override suspend fun upsert(job: PrintJob) {
        store.update { file ->
            val stored = job.toStored()
            val existing = file.jobs.filterNot { it.id == job.id }
            val retained = if (job.status.isTerminal && !keepHistoryProvider()) {
                existing.filterNot { it.id == job.id }.filter { !it.status.isTerminal() }
            } else {
                existing
            }
            val merged = (listOf(stored) + retained)
                .sortedByDescending { it.submittedAtMillis }
                .take(historyLimitProvider().coerceAtLeast(MIN_HISTORY_ENTRIES))
            file.copy(jobs = merged)
        }
    }

    override suspend fun delete(jobId: String) {
        store.update { file -> file.copy(jobs = file.jobs.filterNot { it.id == jobId }) }
    }

    override suspend fun clear() {
        store.update { file -> file.copy(jobs = file.jobs.filter { !it.status.isTerminal() }) }
    }

    override suspend fun find(jobId: String): PrintJob? =
        store.value.value.jobs.firstOrNull { it.id == jobId }?.toJob()

    override suspend fun jobsSnapshot(): List<PrintJob> =
        store.value.value.jobs.map { it.toJob() }.sortedByDescending { it.submittedAtMillis }

    private fun PrintJob.toStored(): StoredJob = StoredJob(
        id = id,
        documentUris = documentUris,
        documentNames = documentNames,
        printerId = printerId,
        printerName = printerName,
        encodedSettings = PrintSettingsCodec.encode(settings),
        status = status.name,
        submittedAtMillis = submittedAtMillis,
        updatedAtMillis = updatedAtMillis,
        systemJobKey = systemJobKey,
        totalPages = totalPages,
        printedPages = printedPages,
        estimatedSheets = estimatedSheets,
        errorType = error?.let { errorKey(it) },
        errorDetail = error?.cause?.message,
    )

    private fun StoredJob.toJob(): PrintJob {
        val decodedStatus = runCatching { PrintJobStatus.valueOf(status) }
            .onFailure { AppLog.warn("Unknown stored job status '$status' for job $id") }
            .getOrDefault(PrintJobStatus.FAILED)
        return PrintJob(
            id = id,
            documentUris = documentUris,
            documentNames = documentNames,
            printerId = printerId,
            printerName = printerName,
            settings = PrintSettingsCodec.decode(encodedSettings) ?: PrintSettings(),
            status = decodedStatus,
            submittedAtMillis = submittedAtMillis,
            updatedAtMillis = updatedAtMillis,
            systemJobKey = systemJobKey,
            totalPages = totalPages,
            printedPages = printedPages,
            estimatedSheets = estimatedSheets,
            error = errorType?.let { restoreError(it) },
        )
    }

    private fun errorKey(error: AppError): String = error::class.simpleName ?: UNKNOWN_ERROR_KEY

    private fun restoreError(key: String): AppError = when (key) {
        "NoPrintService" -> AppError.NoPrintService
        "PrinterUnavailable" -> AppError.PrinterUnavailable
        "NoDocumentSelected" -> AppError.NoDocumentSelected
        "NoPrinterSelected" -> AppError.NoPrinterSelected
        "FileMissing" -> AppError.FileMissing
        "FileUnreadable" -> AppError.FileUnreadable
        "CorruptDocument" -> AppError.CorruptDocument
        "EncryptedDocument" -> AppError.EncryptedDocument
        "NoPrintablePages" -> AppError.NoPrintablePages
        "OutOfMemory" -> AppError.OutOfMemory
        "PermissionDenied" -> AppError.PermissionDenied
        "DiskFull" -> AppError.DiskFull
        else -> AppError.Unknown()
    }

    private fun String.isTerminal(): Boolean = runCatching { PrintJobStatus.valueOf(this).isTerminal }
        .getOrDefault(true)

    private companion object {
        const val MIN_HISTORY_ENTRIES = 10
        const val UNKNOWN_ERROR_KEY = "Unknown"
    }
}

internal fun newJobId(): String = UUID.randomUUID().toString()

internal fun PrintJob.defaultSettings(): PrintSettings = settings
