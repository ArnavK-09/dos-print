package com.dosprint.core

/** One line of the in-app log. */
data class LogEntry(
    val timestampMillis: Long,
    val level: LogLevel,
    val message: String,
    val stackTrace: String? = null,
)

/**
 * A bounded, thread-safe history of everything the app logged this session.
 *
 * The platform log is invisible to someone holding a phone with no computer attached, and the old
 * file log only ever recorded entries that carried an exception, so ordinary events — a file being
 * picked, a preview starting, a navigation — left no trace at all. Keeping the recent history in
 * memory means the in-app log viewer can show what led up to a problem, at the cost of a fixed
 * ceiling on memory: once [capacity] is reached the oldest entry is dropped for each new one.
 */
class LogBuffer(private val capacity: Int = DEFAULT_CAPACITY) {

    private val entries = ArrayDeque<LogEntry>(capacity.coerceAtLeast(1))

    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    @Synchronized
    fun add(entry: LogEntry) {
        if (entries.size >= capacity) {
            entries.removeFirst()
        }
        entries.addLast(entry)
    }

    /** A copy, oldest first, so a caller can iterate without holding the lock. */
    @Synchronized
    fun snapshot(): List<LogEntry> = entries.toList()

    @Synchronized
    fun clear() {
        entries.clear()
    }

    @Synchronized
    fun size(): Int = entries.size

    companion object {
        const val DEFAULT_CAPACITY = 1500
    }
}

/** Turns log entries into the text shown, copied and shared. */
object LogFormatter {

    private const val MAX_EXPORT_CHARS = 150_000

    fun line(entry: LogEntry, clock: (Long) -> String): String = buildString {
        append(clock(entry.timestampMillis))
        append(' ')
        append(entry.level.symbol)
        append(' ')
        append(entry.message)
        entry.stackTrace?.let {
            append('\n')
            append(it.trimEnd())
        }
    }

    /**
     * Joins [lines] for export, keeping the newest.
     *
     * Clipboard and share intents travel over binder, which fails outright above roughly a
     * megabyte, so an oversized export is cut from the front rather than risking a crash at the
     * moment someone is trying to report one.
     */
    fun export(lines: List<String>): String {
        val joined = lines.joinToString("\n")
        if (joined.length <= MAX_EXPORT_CHARS) return joined
        val tail = joined.takeLast(MAX_EXPORT_CHARS)
        val firstBreak = tail.indexOf('\n')
        return "[earlier output omitted]\n" + if (firstBreak >= 0) tail.substring(firstBreak + 1) else tail
    }
}

private val LogLevel.symbol: Char
    get() = when (this) {
        LogLevel.VERBOSE -> 'V'
        LogLevel.DEBUG -> 'D'
        LogLevel.INFO -> 'I'
        LogLevel.WARN -> 'W'
        LogLevel.ERROR -> 'E'
    }
