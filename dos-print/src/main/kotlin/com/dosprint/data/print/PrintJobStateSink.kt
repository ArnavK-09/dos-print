package com.dosprint.data.print

import com.dosprint.core.AppError
import com.dosprint.core.AppLog
import com.dosprint.domain.model.PrintDocument
import com.dosprint.domain.model.PrintJobStatus
import com.dosprint.domain.repository.HistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.dosprint.domain.model.PrintJob as DomainPrintJob

class PrintJobStateSink(
    private val historyRepository: HistoryRepository,
    private val scope: CoroutineScope,
) : PrintJobState {

    @Volatile
    private var trackedJob: DomainPrintJob? = null

    override fun onLayoutResolved(jobId: String, plan: PrintPagePlan) {
        trackedJob = trackedJob?.copy(
            totalPages = plan.outputPageCount,
            estimatedSheets = plan.sheetCount,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    override fun onSheetWritten(jobId: String, sheetIndex: Int, pageCount: Int) {
        AppLog.debug("Job $jobId wrote sheet $sheetIndex with $pageCount pages")
    }

    override fun onProgress(jobId: String, pagesWritten: Int, totalPages: Int) {
        val current = trackedJob ?: return
        val updated = current.copy(
            printedPages = pagesWritten,
            totalPages = if (totalPages > 0) totalPages else current.totalPages,
            status = PrintJobStatus.PRINTING,
            updatedAtMillis = System.currentTimeMillis(),
        )
        trackedJob = updated
        scope.launch { historyRepository.upsert(updated) }
    }

    override fun onWriteCompleted(jobId: String, pagesWritten: Int) {
        val current = trackedJob ?: return
        val completed = current.copy(
            printedPages = maxOf(pagesWritten, current.printedPages),
            status = PrintJobStatus.COMPLETED,
            updatedAtMillis = System.currentTimeMillis(),
        )
        trackedJob = completed
        scope.launch { historyRepository.upsert(completed) }
    }

    override fun onWriteFailed(jobId: String, error: AppError) {
        val current = trackedJob ?: return
        val failed = current.withStatus(PrintJobStatus.FAILED, error)
        trackedJob = failed
        scope.launch { historyRepository.upsert(failed) }
    }

    override fun onJobFinished(jobId: String) {
        val current = trackedJob ?: return
        if (current.status.isTerminal) {
            return
        }
        val finished = current.withStatus(PrintJobStatus.COMPLETED)
        trackedJob = finished
        scope.launch { historyRepository.upsert(finished) }
    }

    fun bind(job: DomainPrintJob) {
        trackedJob = job
    }

    fun current(): DomainPrintJob? = trackedJob
}

internal fun List<PrintDocument>.totalPageCount(): Int = sumOf { it.detectedPageCount ?: 1 }
