package com.dosprint.data.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.dosprint.domain.model.DocumentFormat
import com.dosprint.domain.model.DocumentKind

object DocumentFormatDetector {
    fun detect(context: Context, uri: Uri): DocumentFormat {
        val declaredMime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val name = displayName(context, uri) ?: uri.lastPathSegment.orEmpty()
        val extension = name.substringAfterLast('.', "")
        val mime = declaredMime ?: guessMimeFromExtension(extension)
        val byMime = DocumentFormat.fromMimeType(mime, name)
        if (byMime.kind != DocumentKind.UNKNOWN) {
            return byMime
        }
        return DocumentFormat.fromExtension(extension, mime)
    }

    fun displayName(context: Context, uri: Uri): String? {
        if (uri.scheme == null || uri.scheme == "file") {
            return uri.path?.substringAfterLast('/')
        }
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    fun guessMimeFromExtension(extension: String): String {
        val normalized = extension.lowercase()
        if (normalized.isBlank()) {
            return "application/octet-stream"
        }
        return when (normalized) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "wbmp" -> "image/vnd.wap.wbmp"
            "tif", "tiff" -> "image/tiff"
            "ico" -> "image/x-icon"
            "txt", "log" -> "text/plain"
            "csv" -> "text/csv"
            "md", "markdown" -> "text/markdown"
            "html", "htm" -> "text/html"
            "rtf" -> "application/rtf"
            else -> "application/octet-stream"
        }
    }
}
