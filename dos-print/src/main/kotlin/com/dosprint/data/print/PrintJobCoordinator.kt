package com.dosprint.data.print

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import com.dosprint.core.AppLog
import com.dosprint.core.Outcome
import com.dosprint.core.runCatchingOutcome
import com.dosprint.data.media.PageSource
import com.dosprint.data.media.PageSourceFactory
import com.dosprint.domain.model.DocumentFormat
import com.dosprint.domain.model.PrintDocument
import com.dosprint.domain.model.PrintJobStatus
import com.dosprint.domain.model.PrintSettings
import com.dosprint.domain.model.PrinterCapabilities
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PrintJobCoordinator(
    private val context: Context,
    private val state: PrintJobState,
) {
    private val pageSourceFactory = PageSourceFactory(context)
    private val writeExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, WRITE_THREAD_NAME).apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }

    fun qualityFor(settings: PrintSettings, lowMemory: Boolean): PrintQualityProfile {
        val base = PrintQualityProfile.forDpi(settings.resolution.horizontalDpi)
        return if (lowMemory) base.lowMemory() else base
    }

    fun createAdapter(
        jobId: String,
        documents: List<PrintDocumentInput>,
        settings: PrintSettings,
        lowMemory: Boolean,
    ): DosPrintDocumentAdapter = DosPrintDocumentAdapter(
        context = context,
        jobId = jobId,
        documents = documents,
        settings = settings,
        quality = qualityFor(settings, lowMemory),
        state = state,
        sourceFactory = { input -> openSource(input) },
        writeExecutor = writeExecutor,
    )

    fun buildAttributes(settings: PrintSettings, capabilities: PrinterCapabilities): PrintAttributes {
        val paper = PrintAttributeSanity.resolvedPaper(settings, capabilities).withOrientation(settings.landscape)
        return PrintAttributes.Builder()
            .setMediaSize(
                PrintAttributes.MediaSize(
                    paper.id,
                    paper.label,
                    paper.widthMils,
                    paper.heightMils,
                ),
            )
            .setResolution(settings.resolution.toPrintResolution())
            .setColorMode(PrintAttributeSanity.resolvedColorMode(settings, capabilities).toPrintColorMode())
            .setDuplexMode(capabilities.closestDuplex(settings.duplex).toPrintDuplexMode())
            .setMinMargins(settings.marginPreset.toMargins(PrintAttributes.Margins.NO_MARGINS))
            .build()
    }

    fun buildJobName(documents: List<PrintDocumentInput>): String = when (documents.size) {
        0 -> DEFAULT_JOB_NAME
        1 -> documents.first().displayName
        2 -> "${documents[0].displayName} +1 more"
        else -> "Dos Print (${documents.size} files)"
    }

    fun printManager(): PrintManager? = runCatching { context.getSystemService(PrintManager::class.java) }
        .onFailure { error -> AppLog.warn(AppLog.throwableTag("printManager", error)) }
        .getOrNull()

    fun submitJob(
        jobName: String,
        adapter: DosPrintDocumentAdapter,
        attributes: PrintAttributes,
    ): Outcome<android.print.PrintJob> = when (val manager = printManager()) {
        null -> Outcome.Failure(com.dosprint.core.AppError.NoPrintService)
        else -> runCatchingOutcome { manager.print(jobName, adapter, attributes) }
    }

    fun shutdown() {
        writeExecutor.shutdown()
        try {
            if (!writeExecutor.awaitTermination(SHUTDOWN_GRACE_SECONDS, TimeUnit.SECONDS)) {
                AppLog.warn("Write executor did not terminate in time; forcing shutdown")
                writeExecutor.shutdownNow()
            }
        } catch (error: InterruptedException) {
            AppLog.warn(AppLog.throwableTag("executorShutdown", error))
            writeExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    private fun openSource(input: PrintDocumentInput): Outcome<PageSource> = pageSourceFactory.create(
        PrintDocument(
            uri = input.uri,
            displayName = input.displayName,
            sizeBytes = 0,
            mimeType = input.mimeType,
            format = DocumentFormat.fromMimeType(input.mimeType, input.displayName),
            addedAtMillis = 0,
        ),
    )

    private companion object {
        const val WRITE_THREAD_NAME = "dos-print-writer"
        const val SHUTDOWN_GRACE_SECONDS = 5L
        const val DEFAULT_JOB_NAME = "Dos Print"
    }
}

internal object PrintAttributeSanity {
    fun resolvedColorMode(settings: PrintSettings, capabilities: PrinterCapabilities) =
        capabilities.closestColorMode(settings.colorMode)

    fun resolvedPaper(settings: PrintSettings, capabilities: PrinterCapabilities) =
        if (capabilities.unknown || capabilities.supportedPaperSizes.isEmpty()) {
            settings.paperSize
        } else {
            capabilities.closestPaperSize(settings.paperSize) ?: settings.paperSize
        }

    fun normalizedCopies(settings: PrintSettings, capabilities: PrinterCapabilities): Int =
        settings.copies.coerceIn(1, capabilities.maxCopies.coerceAtLeast(1))
}

internal object PrintJobStateBridge {
    fun statusFor(state: Int): PrintJobStatus = when (state) {
        android.print.PrintJobInfo.STATE_CREATED -> PrintJobStatus.PREPARING
        android.print.PrintJobInfo.STATE_QUEUED -> PrintJobStatus.QUEUED
        android.print.PrintJobInfo.STATE_STARTED -> PrintJobStatus.PRINTING
        android.print.PrintJobInfo.STATE_BLOCKED -> PrintJobStatus.BLOCKED
        android.print.PrintJobInfo.STATE_COMPLETED -> PrintJobStatus.COMPLETED
        android.print.PrintJobInfo.STATE_CANCELED -> PrintJobStatus.CANCELLED
        else -> PrintJobStatus.FAILED
    }

    fun isTerminal(state: Int): Boolean = statusFor(state).isTerminal
}
