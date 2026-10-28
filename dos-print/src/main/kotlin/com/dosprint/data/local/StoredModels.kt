package com.dosprint.data.local

import kotlinx.serialization.Serializable

@Serializable
data class CrashRecord(
    val summary: String,
    val stackTrace: String,
    val timestampMillis: Long,
)

@Serializable
data class StoredDocument(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val extension: String,
    val formatKind: String,
    val addedAtMillis: Long,
    val lastPrintedAtMillis: Long? = null,
    val isFavorite: Boolean = false,
    val detectedPageCount: Int? = null,
)

@Serializable
data class StoredPrinter(
    val id: String,
    val displayName: String,
    val description: String,
    val connectionType: String,
    val serviceId: String? = null,
    val address: String? = null,
    val isUserAdded: Boolean = false,
    val isDefault: Boolean = false,
    val availability: String = "UNKNOWN",
)

@Serializable
data class StoredPreset(
    val id: String,
    val name: String,
    val encodedSettings: String,
    val createdAtMillis: Long,
    val lastUsedAtMillis: Long? = null,
    val usageCount: Int = 0,
)

@Serializable
data class StoredJob(
    val id: String,
    val documentUris: List<String>,
    val documentNames: List<String>,
    val printerId: String,
    val printerName: String,
    val encodedSettings: String,
    val status: String,
    val submittedAtMillis: Long,
    val updatedAtMillis: Long,
    val systemJobKey: String? = null,
    val totalPages: Int = 0,
    val printedPages: Int = 0,
    val estimatedSheets: Int = 0,
    val errorType: String? = null,
    val errorDetail: String? = null,
)

@Serializable
data class HistoryFile(
    val jobs: List<StoredJob> = emptyList(),
)

@Serializable
data class PresetFile(
    val presets: List<StoredPreset> = emptyList(),
)

@Serializable
data class PrinterFile(
    val printers: List<StoredPrinter> = emptyList(),
)

@Serializable
data class DocumentFile(
    val documents: List<StoredDocument> = emptyList(),
)
