package com.dosprint.data.print

import android.print.PrintJob
import com.dosprint.core.AppError
import com.dosprint.core.DispatcherProvider
import com.dosprint.core.Outcome
import com.dosprint.domain.model.DocumentAvailability
import com.dosprint.domain.model.PrintDocument
import com.dosprint.domain.model.PrintJobStatus
import com.dosprint.domain.model.PrintSettings
import com.dosprint.domain.model.PrintSettingsResolver
import com.dosprint.domain.model.PrinterCapabilities
import com.dosprint.domain.model.PrinterProfile
import com.dosprint.domain.repository.HistoryRepository
import com.dosprint.domain.repository.PrintRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.dosprint.domain.model.PrintJob as DomainPrintJob

class PrintRepositoryImpl(
    private val dispatchers: DispatcherProvider,
    private val historyRepository: HistoryRepository,
    private val coordinator: PrintJobCoordinator,
    private val scope: CoroutineScope,
    private val monitor: PrintJobMonitor,
    private val documentTouchProvider: suspend (List<String>) -> Unit,
    private val lowMemoryProvider: () -> Boolean,
) : PrintRepository {

    private val trackedJobs = ConcurrentHashMap<String, TrackedJob>()
    private val activeJobsState = MutableStateFlow<List<DomainPrintJob>>(emptyList())
    private val eventsFlow = MutableSharedFlow<DomainPrintJob>(extraBufferCapacity = EVENT_BUFFER)
    private val submitMutex = Mutex()

    override val activeJobs: StateFlow<List<DomainPrintJob>> = activeJobsState.asStateFlow()

    override val events: Flow<DomainPrintJob> = eventsFlow.asSharedFlow()

    override suspend fun submit(
        documents: List<PrintDocument>,
        printer: PrinterProfile,
        capabilities: PrinterCapabilities,
        settings: PrintSettings,
        jobId: String,
    ): Outcome<DomainPrintJob> = submitMutex.withLock {
        monitor.arm()
        if (documents.isEmpty()) {
            return@withLock Outcome.Failure(AppError.NoDocumentSelected)
        }
        val unsupported = documents.firstOrNull { !it.format.isPrintable }
        if (unsupported != null) {
            return@withLock Outcome.Failure(AppError.UnsupportedFormat(unsupported.format.extension))
        }
        val unavailable = documents.firstOrNull { it.availability != DocumentAvailability.AVAILABLE }
        if (unavailable != null) {
            return@withLock Outcome.Failure(unavailable.errorForAvailability() ?: AppError.FileUnreadable)
        }
        val pageCount = documents.sumOf { it.detectedPageCount ?: 1 }.coerceAtLeast(1)
        val resolved = resolveSettings(settings, capabilities, pageCount)
        val imageMode = documents.all { it.format.kind == com.dosprint.domain.model.DocumentKind.IMAGE }
        val plan = PrintPagePlanner.plan(
            documents.map { it.detectedPageCount ?: 1 },
            resolved,
            imageMode,
        )
        val totalPages = plan.outputPageCount.coerceAtLeast(1)
        val id = jobId.ifBlank { UUID.randomUUID().toString() }
        val prepared = DomainPrintJob(
            id = id,
            documentUris = documents.map { it.uri },
            documentNames = documents.map { it.displayName },
            printerId = printer.id,
            printerName = printer.displayName,
            settings = resolved,
            status = PrintJobStatus.PREPARING,
            submittedAtMillis = System.currentTimeMillis(),
            updatedAtMillis = System.currentTimeMillis(),
            totalPages = totalPages,
            estimatedSheets = plan.sheetCount,
        )
        historyRepository.upsert(prepared)
        publish(prepared)

        val inputs = documents.map { document ->
            PrintDocumentInput(
                uri = document.uri,
                displayName = document.displayName,
                mimeType = document.mimeType,
                pageCountHint = document.detectedPageCount ?: 1,
            )
        }
        val adapter = coordinator.createAdapter(
            jobId = id,
            documents = inputs,
            settings = resolved,
            lowMemory = lowMemoryProvider(),
        )
        val attributes = coordinator.buildAttributes(resolved, capabilities)
        val submission = coordinator.submitJob(coordinator.buildJobName(inputs), adapter, attributes)
        return@withLock when (submission) {
            is Outcome.Failure -> {
                val failed = prepared.withStatus(PrintJobStatus.FAILED, submission.error)
                historyRepository.upsert(failed)
                publish(failed)
                submission
            }
            is Outcome.Success -> {
                val systemJob = submission.value
                val queued = prepared.copy(
                    status = PrintJobStatus.QUEUED,
                    systemJobKey = systemJob?.let(::keyOf),
                    updatedAtMillis = System.currentTimeMillis(),
                )
                track(queued, systemJob)
                historyRepository.upsert(queued)
                publish(queued)
                documentTouchProvider(documents.map { it.uri })
                Outcome.Success(queued)
            }
        }
    }

    override suspend fun cancelJob(jobId: String): Outcome<Unit> = withContext(dispatchers.default) {
        val tracked = trackedJobs[jobId] ?: return@withContext Outcome.Failure(AppError.PrinterUnavailable)
        val systemJob = tracked.systemJob ?: return@withContext Outcome.Failure(AppError.PrinterUnavailable)
        if (!systemJob.cancelSafely()) {
            return@withContext Outcome.Failure(AppError.Unknown())
        }
        val updated = tracked.job.withStatus(PrintJobStatus.CANCELLED)
        historyRepository.upsert(updated)
        trackedJobs.remove(jobId)
        publish(updated)
        Outcome.Success(Unit)
    }

    override suspend fun resumeMonitoring(): Outcome<Unit> {
        monitor.refreshNow()
        return Outcome.Success(Unit)
    }

    override suspend fun reconcile(): List<DomainPrintJob> = withContext(dispatchers.io) {
        val active = historyRepository.jobsSnapshot().filter { it.status.isActive }
        if (active.isEmpty()) {
            return@withContext emptyList()
        }
        active.forEach { job ->
            val refreshed = job.copy(updatedAtMillis = System.currentTimeMillis())
            historyRepository.upsert(refreshed)
            publish(refreshed)
        }
        refreshActiveJobs()
        active
    }

    override fun onSystemJobUpdate(systemJob: PrintJob) {
        val key = keyOf(systemJob)
        if (key.isBlank()) {
            return
        }
        val entry = trackedJobs.entries.firstOrNull { it.value.systemKey == key }
        if (entry == null) {
            return
        }
        val status = PrintJobStateBridge.statusFor(stateOf(systemJob))
        val updated = entry.value.job.withStatus(status)
        entry.value.job = updated
        publish(updated)
        scope.launch { historyRepository.upsert(updated) }
        if (status.isTerminal) {
            trackedJobs.remove(entry.key)
            refreshActiveJobs()
        }
    }

    fun release() {
        trackedJobs.clear()
        coordinator.shutdown()
    }

    private fun track(job: DomainPrintJob, systemJob: PrintJob?) {
        trackedJobs[job.id] = TrackedJob(job = job, systemJob = systemJob, systemKey = job.systemJobKey.orEmpty())
        refreshActiveJobs()
    }

    private fun publish(job: DomainPrintJob) {
        eventsFlow.tryEmit(job)
        refreshActiveJobs()
    }

    private fun refreshActiveJobs() {
        val active = trackedJobs.values
            .map { it.job }
            .filter { it.status.isActive }
            .sortedBy { it.submittedAtMillis }
        activeJobsState.value = active
    }

    private fun resolveSettings(
        settings: PrintSettings,
        capabilities: PrinterCapabilities,
        totalPages: Int,
    ): PrintSettings = PrintSettingsResolver.resolve(settings, capabilities, totalPages)

    private class TrackedJob(
        var job: DomainPrintJob,
        val systemJob: PrintJob?,
        val systemKey: String,
    )

    private companion object {
        const val EVENT_BUFFER = 32

        fun keyOf(job: PrintJob): String = runCatching {
            val info = job.info
            "${info.printerId?.localId.orEmpty()}:${info.creationTime}:${info.label.orEmpty()}"
        }.getOrDefault("")

        fun stateOf(job: PrintJob): Int = runCatching { job.info.state }
            .getOrDefault(android.print.PrintJobInfo.STATE_FAILED)
    }
}
