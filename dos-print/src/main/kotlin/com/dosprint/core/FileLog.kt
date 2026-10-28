package com.dosprint.core

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

/**
 * Persists the log so it survives the process that wrote it.
 *
 * Two earlier defects are why this looks the way it does. It used to write synchronously on the
 * calling thread while holding a lock, so a log call on the main thread could stall the UI for the
 * length of a disk write. And it silently discarded every entry that had no exception attached, so
 * the file contained almost nothing and offered no help diagnosing a hang.
 *
 * Entries are now queued and written by [BackgroundIo] in batches. Only INFO and above are kept on
 * disk, since debug chatter would rotate the useful history out of the size cap. On a fatal path
 * [flush] writes whatever is queued before the process is killed.
 */
object FileLog {
    private const val MAX_FILE_BYTES = 512 * 1024
    private const val MAX_ROTATED_FILES = 3
    private const val LOCK_TIMEOUT_MILLIS = 2000L
    private const val PRIMARY_FILE_NAME = "dos-print.log"

    private val lock = ReentrantLock()
    private val pending = ConcurrentLinkedQueue<LogEntry>()
    private val drainScheduled = AtomicBoolean(false)
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var logDirectory: File? = null

    fun initialize(directory: File) {
        logDirectory = directory
        if (!directory.exists()) {
            directory.mkdirs()
        }
        AppLog.info("File log initialised at ${directory.absolutePath}")
    }

    /** Queues [entry] for writing. Never touches the disk on the calling thread. */
    fun enqueue(entry: LogEntry) {
        if (entry.level < LogLevel.INFO || logDirectory == null) {
            return
        }
        pending.add(entry)
        scheduleDrain()
    }

    /** Writes everything queued, on the calling thread. For use immediately before a crash ends the process. */
    fun flush() {
        drain()
    }

    fun recentLines(logDirectory: File, limit: Int = 200): List<String> {
        val file = latestLogFile(logDirectory) ?: return emptyList()
        return try {
            file.readLines().asReversed().take(limit).asReversed()
        } catch (error: java.io.IOException) {
            AppLog.warn(AppLog.throwableTag("readLog", error))
            emptyList()
        }
    }

    fun directory(): File? = logDirectory

    fun deleteAll(logDirectory: File) {
        logDirectory.listFiles()?.forEach { file ->
            if (file.isFile && file.delete()) {
                AppLog.debug("Deleted log file ${file.name}")
            }
        }
    }

    private fun scheduleDrain() {
        if (!drainScheduled.compareAndSet(false, true)) {
            return
        }
        BackgroundIo.execute {
            try {
                drain()
            } finally {
                drainScheduled.set(false)
            }
            if (pending.isNotEmpty()) {
                scheduleDrain()
            }
        }
    }

    private fun drain() {
        val directory = logDirectory ?: return
        if (pending.isEmpty()) return
        if (!lock.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            return
        }
        try {
            val batch = StringBuilder()
            while (true) {
                val entry = pending.poll() ?: break
                batch.append(format(entry)).append('\n')
            }
            if (batch.isNotEmpty()) {
                rotateIfNeeded(directory)
                directory.resolve(PRIMARY_FILE_NAME).appendText(batch.toString())
            }
        } catch (error: java.io.IOException) {
            android.util.Log.w("DosPrint", "log write failed", error)
        } finally {
            lock.unlock()
        }
    }

    private fun format(entry: LogEntry): String =
        LogFormatter.line(entry) { timestampFormat.format(Date(it)) }

    private fun rotateIfNeeded(directory: File) {
        val primary = directory.resolve(PRIMARY_FILE_NAME)
        if (!primary.exists() || primary.length() < MAX_FILE_BYTES) {
            return
        }
        for (index in MAX_ROTATED_FILES downTo 2) {
            val source = directory.resolve(rotatedName(index))
            if (source.exists()) {
                source.delete()
            }
            if (index > 2) {
                source.renameTo(directory.resolve(rotatedName(index - 1)))
            }
        }
        val newest = directory.resolve(rotatedName(1))
        newest.delete()
        primary.renameTo(newest)
    }

    private fun rotatedName(index: Int): String = "dos-print-$index.log"

    private fun latestLogFile(directory: File): File? {
        val primary = directory.resolve(PRIMARY_FILE_NAME)
        return when {
            primary.exists() -> primary
            directory.resolve(rotatedName(1)).exists() -> directory.resolve(rotatedName(1))
            else -> null
        }
    }
}
