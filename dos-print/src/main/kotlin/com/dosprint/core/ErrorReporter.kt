package com.dosprint.core

import java.io.File

/**
 * A single place every failure in the app is reported to, whether it was fatal or recovered.
 *
 * Failures used to be held in memory, so they were gone by the time the app reopened. Records are
 * now written to a bounded on-disk log. Ordinary reports are queued so an error path never blocks on
 * disk; a *fatal* report is written synchronously, because the process is about to be killed and a
 * queued write would simply never happen — which is exactly why crashes used to leave no record.
 *
 * A fatal report also leaves a marker behind. On the next launch [takePendingCrash] hands it to the
 * UI, so the user is told what happened rather than the app silently restarting.
 */
object ErrorReporter {

    private const val MAX_RECORDS = 200
    private const val FILE_NAME = "errors.log"
    private const val PENDING_FILE_NAME = "pending-crash.txt"

    private val fileLock = Any()

    @Volatile
    private var directory: File? = null

    @Volatile
    private var listeners: List<(ErrorRecord) -> Unit> = emptyList()

    fun initialize(directory: File) {
        if (!directory.exists()) {
            directory.mkdirs()
        }
        this.directory = directory
    }

    /** Records a failure that was handled and the app carried on from. */
    fun report(area: String, throwable: Throwable) {
        AppLog.error("[$area] ${throwable.javaClass.simpleName}: ${throwable.message}", throwable)
        record(ErrorRecord.of(area, Severity.RECOVERED, throwable), blocking = false)
    }

    /**
     * Records a failure that is about to end the process.
     *
     * Written synchronously and flushed, then marked pending so the next launch can report it.
     */
    fun reportFatal(area: String, throwable: Throwable) {
        val record = ErrorRecord.of(area, Severity.FATAL, throwable)
        record(record, blocking = true)
        writePending(record)
        FileLog.flush()
    }

    /** Records something observed from outside the app, such as how the previous run ended. */
    fun reportExternal(
        area: String,
        severity: Severity,
        summary: String,
        detail: String,
    ) {
        AppLog.error("[$area] $summary")
        record(
            ErrorRecord(
                area = area,
                severity = severity,
                summary = summary,
                detail = detail,
                timestampMillis = System.currentTimeMillis(),
            ),
            blocking = false,
        )
    }

    /**
     * Runs [block], recording any failure with [area] before letting it propagate.
     *
     * Wraps the boundaries of a flow so the area that failed is known even when the exception
     * carries no useful message of its own.
     */
    inline fun <T> guard(area: String, block: () -> T): T = try {
        block()
    } catch (throwable: Throwable) {
        report(area, throwable)
        throw throwable
    }

    fun addListener(listener: (ErrorRecord) -> Unit) {
        listeners = listeners + listener
    }

    fun recent(limit: Int = MAX_RECORDS): List<ErrorRecord> {
        val source = file(FILE_NAME) ?: return emptyList()
        return synchronized(fileLock) {
            runCatching {
                if (!source.exists()) {
                    emptyList()
                } else {
                    source.readLines()
                        .asReversed()
                        .filter { it.isNotBlank() }
                        .take(limit)
                        .mapNotNull { ErrorRecord.parse(it) }
                }
            }.getOrDefault(emptyList())
        }
    }

    fun count(): Int = recent().size

    /** The crash that ended the previous run, if one has not been shown yet. Returned once. */
    fun takePendingCrash(): ErrorRecord? {
        val marker = file(PENDING_FILE_NAME) ?: return null
        return synchronized(fileLock) {
            runCatching {
                if (!marker.exists()) {
                    null
                } else {
                    val parsed = ErrorRecord.parse(marker.readText().trim())
                    marker.delete()
                    parsed
                }
            }.getOrNull()
        }
    }

    fun clear() {
        synchronized(fileLock) {
            runCatching { file(FILE_NAME)?.delete() }
        }
        AppLog.info("Error log cleared")
    }

    private fun record(record: ErrorRecord, blocking: Boolean) {
        listeners.forEach { listener -> runCatching { listener(record) } }
        if (directory == null) return
        if (blocking) {
            append(record)
        } else {
            BackgroundIo.execute { append(record) }
        }
    }

    private fun append(record: ErrorRecord) {
        val target = file(FILE_NAME) ?: return
        synchronized(fileLock) {
            runCatching {
                val existing = if (target.exists()) target.readLines() else emptyList()
                val kept = (existing + record.serialize()).takeLast(MAX_RECORDS)
                target.writeText(kept.joinToString(System.lineSeparator()))
            }
        }
    }

    private fun writePending(record: ErrorRecord) {
        val marker = file(PENDING_FILE_NAME) ?: return
        synchronized(fileLock) {
            runCatching { marker.writeText(record.serialize()) }
        }
    }

    private fun file(name: String): File? = directory?.let { File(it, name) }

    enum class Severity {
        RECOVERED,
        FATAL,
    }
}

/** One recorded failure. */
data class ErrorRecord(
    val area: String,
    val severity: ErrorReporter.Severity,
    val summary: String,
    val detail: String,
    val timestampMillis: Long,
) {
    /**
     * Serialised as one line so the log stays greppable, with the stack trace hex encoded so its
     * newlines cannot break the format.
     */
    fun serialize(): String = listOf(
        timestampMillis.toString(),
        severity.name,
        area.singleLine(),
        summary.singleLine(),
        detail.encode(),
    ).joinToString(SEPARATOR)

    companion object {
        private const val SEPARATOR = "\u001f"
        private const val MAX_DETAIL_LINES = 80
        private const val FIELD_COUNT = 5
        private const val HEX = "0123456789abcdef"

        fun of(area: String, severity: ErrorReporter.Severity, throwable: Throwable) = ErrorRecord(
            area = area,
            severity = severity,
            summary = throwable.javaClass.simpleName + ": " + throwable.message.orEmpty(),
            detail = throwable.stackTraceToString()
                .lineSequence()
                .take(MAX_DETAIL_LINES)
                .joinToString("\n"),
            timestampMillis = System.currentTimeMillis(),
        )

        /**
         * Returns null for anything that is not exactly one well-formed record.
         *
         * The field count is checked rather than merely a minimum: a value with an injected
         * separator splits into extra fields, and accepting it would silently yield a record with
         * the wrong area and a truncated stack trace, which is worse than reporting it as unreadable.
         */
        fun parse(line: String): ErrorRecord? {
            val parts = line.split(SEPARATOR)
            if (parts.size != FIELD_COUNT) return null
            val severity = runCatching { ErrorReporter.Severity.valueOf(parts[1]) }.getOrNull() ?: return null
            val timestamp = parts[0].toLongOrNull() ?: return null
            val detail = parts[4].decode() ?: return null
            return ErrorRecord(
                area = parts[2],
                severity = severity,
                summary = parts[3],
                detail = detail,
                timestampMillis = timestamp,
            )
        }

        /** Hex rather than Base64, so a record can be built and parsed without Android. */
        private fun String.encode(): String {
            if (isEmpty()) return EMPTY_DETAIL
            return buildString(length * 2) {
                this@encode.toByteArray(Charsets.UTF_8).forEach { byte ->
                    val code = byte.toInt() and 0xFF
                    append(HEX[code ushr 4])
                    append(HEX[code and 0x0F])
                }
            }
        }

        /** Returns null for anything that is not valid hex, so a corrupt record is never trusted. */
        private fun String.decode(): String? {
            if (this == EMPTY_DETAIL) return ""
            if (isEmpty() || length % 2 != 0) return null
            val bytes = ByteArray(length / 2)
            for (index in bytes.indices) {
                val high = Character.digit(this[index * 2], 16)
                val low = Character.digit(this[index * 2 + 1], 16)
                if (high < 0 || low < 0) return null
                bytes[index] = ((high shl 4) or low).toByte()
            }
            return String(bytes, Charsets.UTF_8)
        }

        private fun String.singleLine(): String = replace('\n', ' ').replace('\r', ' ')

        /** An empty detail cannot be told apart from a truncated record, so it gets an explicit marker. */
        private const val EMPTY_DETAIL = "-"
    }
}
