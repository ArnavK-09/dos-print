package com.dosprint.core

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File

/**
 * Reads how the previous run ended and records it.
 *
 * An ANR is not an exception: nothing is thrown, so no handler inside the app ever runs and the log
 * ends mid-sentence. The system does keep its own record, and for an ANR it includes the stack of
 * every thread at the moment of the hang — which names the exact call the main thread was stuck in.
 * That is the single most useful fact about a "not responding" report, and the app was not reading
 * it at all.
 *
 * It runs once at startup and does nothing afterwards: no polling, no watchdog thread. Entries are
 * remembered by timestamp so each ending is reported once. Only available from Android 11.
 */
object ExitReasonReporter {

    private const val STATE_FILE = "last-exit-seen.txt"
    private const val MAX_EXITS = 8
    private const val MAX_TRACE_LINES = 120

    fun reportNew(context: Context, stateDirectory: File) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val appContext = context.applicationContext
        BackgroundIo.execute { collect(appContext, File(stateDirectory, STATE_FILE)) }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun collect(context: Context, stateFile: File) {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return
        val lastSeen = runCatching { stateFile.readText().trim().toLong() }.getOrDefault(0L)
        val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXITS)
        exits
            .filter { it.timestamp > lastSeen && isWorthReporting(it.reason) }
            .sortedBy { it.timestamp }
            .forEach { info ->
                ErrorReporter.reportExternal(
                    area = "previous run: ${label(info.reason)}",
                    severity = ErrorReporter.Severity.FATAL,
                    summary = describe(info.reason, info.description),
                    detail = traceOf(info),
                )
            }
        exits.maxOfOrNull { it.timestamp }?.let { newest ->
            runCatching { stateFile.writeText(newest.toString()) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun traceOf(info: ApplicationExitInfo): String = runCatching {
        info.traceInputStream?.bufferedReader()?.useLines { lines ->
            lines.take(MAX_TRACE_LINES).joinToString("\n")
        }
    }.getOrNull().orEmpty()

    fun isWorthReporting(reason: Int): Boolean = when (reason) {
        ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_LOW_MEMORY,
        -> true
        else -> false
    }

    fun label(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_ANR -> "not responding"
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "out of memory"
        else -> "exit $reason"
    }

    fun describe(reason: Int, description: String?): String {
        val detail = description?.takeIf { it.isNotBlank() }
        return if (detail == null) label(reason) else "${label(reason)}: $detail"
    }
}
