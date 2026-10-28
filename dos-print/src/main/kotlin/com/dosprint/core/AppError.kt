package com.dosprint.core

import android.content.Context
import com.dosprint.R

sealed class AppError {
    open val cause: Throwable? = null

    object NoPrintService : AppError()

    object PrinterUnavailable : AppError()

    data class PrinterUnresolved(val printerName: String) : AppError()

    object NoDocumentSelected : AppError()

    object NoPrinterSelected : AppError()

    object FileMissing : AppError()

    object FileUnreadable : AppError()

    data class FileTooLarge(val sizeBytes: Long, val limitBytes: Long) : AppError()

    data class UnsupportedFormat(val extension: String?) : AppError()

    object CorruptDocument : AppError()

    object EncryptedDocument : AppError()

    object NoPrintablePages : AppError()

    object OutOfMemory : AppError()

    data class InvalidPageRange(val input: String) : AppError()

    object PermissionDenied : AppError()

    object DiskFull : AppError()

    object StorageUnavailable : AppError()

    data class PrintFailed(val reason: String, override val cause: Throwable? = null) : AppError()

    object NotificationsBlocked : AppError()

    data class Unknown(override val cause: Throwable? = null) : AppError()

    fun message(context: Context): String = when (this) {
        is NoPrintService -> context.getString(R.string.error_no_print_service)
        is PrinterUnavailable -> context.getString(R.string.error_printer_unavailable)
        is PrinterUnresolved -> context.getString(R.string.error_printer_unavailable)
        is NoDocumentSelected -> context.getString(R.string.error_no_document_selected)
        is NoPrinterSelected -> context.getString(R.string.error_no_printer_selected)
        is FileMissing -> context.getString(R.string.error_file_missing)
        is FileUnreadable -> context.getString(R.string.error_file_unreadable)
        is FileTooLarge -> context.getString(R.string.error_file_too_large, ByteFormatter.format(sizeBytes))
        is UnsupportedFormat -> context.getString(
            R.string.error_unsupported_format,
            extension?.uppercase() ?: context.getString(R.string.error_generic),
        )
        is CorruptDocument -> context.getString(R.string.error_corrupt_document)
        is EncryptedDocument -> context.getString(R.string.error_encrypted_document)
        is NoPrintablePages -> context.getString(R.string.error_no_pages)
        is OutOfMemory -> context.getString(R.string.error_out_of_memory)
        is InvalidPageRange -> context.getString(R.string.error_invalid_range)
        is PermissionDenied -> context.getString(R.string.error_permission_denied)
        is DiskFull -> context.getString(R.string.error_disk_full)
        is StorageUnavailable -> context.getString(R.string.error_storage_unavailable)
        is PrintFailed -> context.getString(R.string.error_print_failed, reason)
        is NotificationsBlocked -> context.getString(R.string.error_notification_blocked)
        is Unknown -> context.getString(R.string.error_generic)
    }

    fun isRetryable(): Boolean = when (this) {
        is PrinterUnavailable, is PrintFailed, is OutOfMemory, is Unknown -> true
        else -> false
    }
}

object ByteFormatter {
    fun format(bytes: Long): String {
        if (bytes < 1024) {
            return "$bytes B"
        }
        val units = listOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble() / 1024
        var index = 0
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index++
        }
        val rounded = if (value >= 100) {
            String.format(java.util.Locale.US, "%.0f", value)
        } else {
            String.format(java.util.Locale.US, "%.1f", value)
        }
        return "$rounded ${units[index]}"
    }
}
