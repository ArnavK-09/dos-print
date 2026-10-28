package com.dosprint.domain.model

import androidx.compose.runtime.Immutable
import com.dosprint.core.AppError
import java.util.Locale

enum class DocumentKind {
    PDF,
    IMAGE,
    TEXT,
    RICH_TEXT,
    MARKDOWN,
    HTML,
    OFFICE_DOCUMENT,
    POSTSCRIPT,
    UNKNOWN,
}

@Immutable
data class DocumentFormat(
    val kind: DocumentKind,
    val extension: String,
    val mimeType: String,
) {
    val isPrintable: Boolean get() = kind != DocumentKind.UNKNOWN

    val supportsNativePreview: Boolean
        get() = kind == DocumentKind.PDF ||
            kind == DocumentKind.IMAGE ||
            kind == DocumentKind.TEXT ||
            kind == DocumentKind.MARKDOWN

    companion object {
        val Unknown = DocumentFormat(DocumentKind.UNKNOWN, "", "application/octet-stream")

        fun fromExtension(extension: String, mimeType: String): DocumentFormat {
            val normalizedExtension = extension.lowercase(Locale.US).removePrefix(".")
            val normalizedMime = mimeType.lowercase(Locale.US)
            val kind = kindForExtension(normalizedExtension) ?: kindForMime(normalizedMime) ?: DocumentKind.UNKNOWN
            return DocumentFormat(kind, normalizedExtension, normalizedMime)
        }

        fun fromMimeType(mimeType: String, fileName: String): DocumentFormat {
            val extension = fileName.substringAfterLast('.', "").lowercase(Locale.US)
            return fromExtension(extension, mimeType)
        }

        private fun kindForExtension(extension: String): DocumentKind? = when (extension) {
            "pdf" -> DocumentKind.PDF
            "png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif", "wbmp", "ico", "tif", "tiff", "avif", "jfif" -> DocumentKind.IMAGE
            "txt", "log", "text", "ini", "cfg", "csv" -> DocumentKind.TEXT
            "md", "markdown" -> DocumentKind.MARKDOWN
            "rtf" -> DocumentKind.RICH_TEXT
            "htm", "html", "xhtml" -> DocumentKind.HTML
            "doc", "docx", "odt", "rtfd", "pages" -> DocumentKind.OFFICE_DOCUMENT
            "xls", "xlsx", "ods", "csvz", "numbers" -> DocumentKind.OFFICE_DOCUMENT
            "ppt", "pptx", "odp", "key" -> DocumentKind.OFFICE_DOCUMENT
            "ps", "eps", "prn" -> DocumentKind.POSTSCRIPT
            else -> null
        }

        private fun kindForMime(mimeType: String): DocumentKind? {
            val baseType = mimeType.substringBefore('/')
            return when {
                mimeType == "application/pdf" -> DocumentKind.PDF
                baseType == "image" -> DocumentKind.IMAGE
                mimeType == "text/markdown" || mimeType == "text/x-markdown" -> DocumentKind.MARKDOWN
                mimeType == "text/html" || mimeType == "application/xhtml+xml" -> DocumentKind.HTML
                mimeType == "application/rtf" || mimeType == "text/rtf" -> DocumentKind.RICH_TEXT
                baseType == "text" -> DocumentKind.TEXT
                mimeType.startsWith("application/vnd.openxmlformats") ||
                    mimeType.startsWith("application/vnd.ms-") ||
                    mimeType.startsWith("application/vnd.oasis.opendocument") -> DocumentKind.OFFICE_DOCUMENT
                mimeType == "application/postscript" || mimeType == "application/x-postscript" -> DocumentKind.POSTSCRIPT
                else -> null
            }
        }
    }
}

@Immutable
enum class DocumentAvailability {
    AVAILABLE,
    MISSING,
    UNREADABLE,
}

@Immutable
data class PrintDocument(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val format: DocumentFormat,
    val addedAtMillis: Long,
    val lastPrintedAtMillis: Long? = null,
    val isFavorite: Boolean = false,
    val availability: DocumentAvailability = DocumentAvailability.AVAILABLE,
    val detectedPageCount: Int? = null,
) {
    val key: String get() = uri

    /** One line describing the document well enough to pick it out of a list. */
    fun summaryLine(): String = buildString {
        append(displayName)
        val pages = detectedPageCount
        if (pages != null && pages > 0) {
            append(" · ")
            append(pages)
            append(if (pages == 1) " page" else " pages")
        }
        if (sizeBytes > 0) {
            append(" · ")
            append(sizeBytes / 1024)
            append(" KB")
        }
    }

    fun errorForAvailability(): AppError? = when (availability) {
        DocumentAvailability.AVAILABLE -> null
        DocumentAvailability.MISSING -> AppError.FileMissing
        DocumentAvailability.UNREADABLE -> AppError.FileUnreadable
    }

    companion object {
        const val MAX_SUPPORTED_BYTES = 512L * 1024 * 1024
        const val MAX_DOCUMENTS_PER_BATCH = 50
    }
}
