package com.dosprint.domain.model

import androidx.compose.runtime.Immutable
import com.dosprint.core.AppError

@Immutable
enum class PrintJobStatus {
    QUEUED,
    PREPARING,
    BLOCKED,
    PRINTING,
    COMPLETED,
    CANCELLED,
    FAILED,
    ;

    val isTerminal: Boolean get() = this == COMPLETED || this == CANCELLED || this == FAILED

    val isActive: Boolean get() = !isTerminal

    companion object {
        val Initial: PrintJobStatus = PREPARING
    }
}

@Immutable
data class PrintJob(
    val id: String,
    val documentUris: List<String>,
    val documentNames: List<String>,
    val printerId: String,
    val printerName: String,
    val settings: PrintSettings,
    val status: PrintJobStatus,
    val submittedAtMillis: Long,
    val updatedAtMillis: Long,
    val systemJobKey: String? = null,
    val totalPages: Int = 0,
    val printedPages: Int = 0,
    val estimatedSheets: Int = 0,
    val error: AppError? = null,
    val queuedForMillis: Long = 0L,
) {
    val primaryDocumentName: String
        get() = documentNames.firstOrNull() ?: "Document"

    val documentCount: Int get() = documentUris.size

    val progressFraction: Float
        get() = if (totalPages <= 0) 0f else (printedPages.toFloat() / totalPages).coerceIn(0f, 1f)

    val isBatch: Boolean get() = documentUris.size > 1

    fun withStatus(status: PrintJobStatus, error: AppError? = null): PrintJob =
        copy(status = status, error = error, updatedAtMillis = System.currentTimeMillis())
}

@Immutable
data class PrintPreset(
    val id: String,
    val name: String,
    val settings: PrintSettings,
    val createdAtMillis: Long,
    val lastUsedAtMillis: Long? = null,
    val usageCount: Int = 0,
) {
    fun markedUsed(): PrintPreset = copy(
        lastUsedAtMillis = System.currentTimeMillis(),
        usageCount = usageCount + 1,
    )
}
