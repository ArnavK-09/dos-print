package com.dosprint.domain.repository

import com.dosprint.core.Outcome
import com.dosprint.domain.model.PrintDocument
import com.dosprint.domain.model.PrintJob
import com.dosprint.domain.model.PrintSettings
import com.dosprint.domain.model.PrinterCapabilities
import com.dosprint.domain.model.PrinterProfile
import kotlinx.coroutines.flow.Flow

interface PrinterRepository {
    val printers: Flow<List<PrinterProfile>>

    val defaultPrinter: Flow<PrinterProfile?>

    suspend fun load()

    suspend fun refreshPrinters(): List<PrinterProfile>

    suspend fun findPrinter(printerId: String): PrinterProfile?

    suspend fun upsertManualPrinter(printer: PrinterProfile)

    suspend fun removePrinter(printerId: String)

    suspend fun renamePrinter(printerId: String, displayName: String)

    suspend fun setDefaultPrinter(printerId: String)

    suspend fun capabilitiesFor(printerId: String): Outcome<PrinterCapabilities>

    suspend fun isPrintServiceAvailable(): Boolean

    fun openSystemPrinterSettings()
}

interface PrintRepository {
    val activeJobs: Flow<List<PrintJob>>

    val events: Flow<PrintJob>

    fun onSystemJobUpdate(systemJob: android.print.PrintJob)

    suspend fun submit(
        documents: List<PrintDocument>,
        printer: PrinterProfile,
        capabilities: PrinterCapabilities,
        settings: PrintSettings,
        jobId: String,
    ): Outcome<PrintJob>

    suspend fun cancelJob(jobId: String): Outcome<Unit>

    suspend fun resumeMonitoring(): Outcome<Unit>

    suspend fun reconcile(): List<PrintJob>
}
