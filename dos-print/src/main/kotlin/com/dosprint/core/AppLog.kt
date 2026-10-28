package com.dosprint.core

import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

/**
 * The app-wide logger.
 *
 * Every call is recorded three ways: the platform log, the in-memory [LogBuffer] that the in-app
 * viewer reads, and the persisted file. The first two are cheap and happen on the calling thread;
 * the file write is queued, so logging from the main thread never touches the disk.
 *
 * Logging must never be the reason something fails, so the platform call is guarded: it is absent in
 * plain JVM tests, and a logging error is not worth a crash anywhere else either.
 */
object AppLog {
    private const val TAG = "DosPrint"

    val buffer = LogBuffer()

    fun verbose(message: String, throwable: Throwable? = null) {
        write(LogLevel.VERBOSE, message, throwable)
    }

    fun debug(message: String, throwable: Throwable? = null) {
        write(LogLevel.DEBUG, message, throwable)
    }

    fun info(message: String, throwable: Throwable? = null) {
        write(LogLevel.INFO, message, throwable)
    }

    fun warn(message: String, throwable: Throwable? = null) {
        write(LogLevel.WARN, message, throwable)
    }

    fun error(message: String, throwable: Throwable? = null) {
        write(LogLevel.ERROR, message, throwable)
    }

    fun error(message: String, throwable: Throwable, context: String) {
        write(LogLevel.ERROR, "$context: $message", throwable)
    }

    fun throwableTag(prefix: String, throwable: Throwable): String = "$prefix: ${throwable.javaClass.simpleName}: ${throwable.message}"

    fun snapshot(): List<LogEntry> = buffer.snapshot()

    fun clear() {
        buffer.clear()
    }

    private fun write(level: LogLevel, message: String, throwable: Throwable?) {
        platformLog(level, message, throwable)
        val entry = LogEntry(
            timestampMillis = System.currentTimeMillis(),
            level = level,
            message = message,
            stackTrace = throwable?.let(::stackTraceOf),
        )
        buffer.add(entry)
        FileLog.enqueue(entry)
    }

    private fun platformLog(level: LogLevel, message: String, throwable: Throwable?) {
        try {
            when (level) {
                LogLevel.VERBOSE -> Log.v(TAG, message, throwable)
                LogLevel.DEBUG -> Log.d(TAG, message, throwable)
                LogLevel.INFO -> Log.i(TAG, message, throwable)
                LogLevel.WARN -> Log.w(TAG, message, throwable)
                LogLevel.ERROR -> Log.e(TAG, message, throwable)
            }
        } catch (ignored: RuntimeException) {
            return
        }
    }

    private fun stackTraceOf(throwable: Throwable): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }
}
