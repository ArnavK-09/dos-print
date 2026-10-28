package com.dosprint.ui

import com.dosprint.core.AppLog
import com.dosprint.domain.model.DocumentAvailability
import com.dosprint.domain.model.PrintDocument

/**
 * Turns a list of picked URIs into documents, surviving whatever any single one does.
 *
 * This exists as its own unit because the import path is where the app is most fragile: every step
 * touches the platform (content resolver, document parser, permission persist) and every one of
 * them can throw. It previously ran inline with no error handling and the navigation signal was the
 * last statement, so one unreadable file aborted the whole import silently — no preview, no error,
 * and a crash from the escaping exception.
 *
 * Three rules, each from a bug:
 * - **One bad file cannot fail the batch.** Failures are collected, not propagated.
 * - **A page count is optional.** Probing it parses the document, which is the most likely thing to
 *   throw; a document whose count is unknown is still perfectly printable.
 * - **Permission and recency are best effort.** A revoked write permission must not discard an
 *   import that otherwise worked.
 */
internal class DocumentImporter(
    private val describe: suspend (String) -> PrintDocument?,
    private val pageCountOf: suspend (String) -> Int?,
    private val persistPermission: suspend (String) -> Unit = {},
    private val remember: suspend (PrintDocument) -> Unit = {},
    /** Overridable so a test can assert on what was reported without touching disk. */
    var onError: (String, Throwable) -> Unit = { _, _ -> },
) {

    data class Result(
        val documents: List<PrintDocument>,
        val failedUris: List<String>,
    ) {
        val importedCount: Int get() = documents.size

        val failedCount: Int get() = failedUris.size

        val isTotalFailure: Boolean get() = documents.isEmpty() && failedUris.isNotEmpty()

        val isPartialFailure: Boolean get() = failedUris.isNotEmpty() && documents.isNotEmpty()

        val isCompleteSuccess: Boolean get() = failedUris.isEmpty() && documents.isNotEmpty()

        /** Uris that were accepted but could not be opened again. */
        fun unavailableIn(uris: List<String>): List<String> =
            uris.filter { raw -> documents.none { it.uri == raw && it.availability != DocumentAvailability.AVAILABLE } }
    }

    suspend fun import(uris: List<String>): Result {
        val documents = ArrayList<PrintDocument>(uris.size)
        val failures = ArrayList<String>()
        AppLog.info("import: ${uris.size} file(s) to read")
        uris.forEach { raw ->
            AppLog.info("import: reading ${shorten(raw)}")
            val parsed = try {
                describe(raw)
            } catch (error: Throwable) {
                onError(raw, error)
                null
            }
            if (parsed == null) {
                failures += raw
                onError(raw, IllegalStateException("unreadable: $raw"))
                return@forEach
            }
            // Keep the count the document already carried when the probe fails; overwriting it with
            // null would lose information the repository had already established.
            AppLog.info("import: counting pages of ${described0Name(parsed)}")
            val probed = pageCountOrNull(raw)
            val described = parsed.copy(detectedPageCount = probed ?: parsed.detectedPageCount)
            AppLog.info("import: ready ${described.displayName} pages=${described.detectedPageCount ?: "unknown"}")
            documents += described
            // Best effort, and deliberately outside the guarded block above.
            runCatching { persistPermission(raw) }.onFailure { onError(raw, it) }
            runCatching { remember(described) }.onFailure { onError(raw, it) }
        }
        AppLog.info("import: done ok=${documents.size} failed=${failures.distinct().size}")
        return Result(documents = documents, failedUris = failures.distinct())
    }

    private fun described0Name(document: PrintDocument): String = document.displayName

    private fun shorten(uri: String): String = if (uri.length <= MAX_LOGGED_URI) uri else uri.takeLast(MAX_LOGGED_URI)

    private suspend fun pageCountOrNull(uri: String): Int? =
        try {
            pageCountOf(uri)
        } catch (error: Throwable) {
            onError(uri, error)
            null
        }
}

private const val MAX_LOGGED_URI = 80
