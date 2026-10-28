package com.dosprint.core

import android.util.Log

/**
 * Records a fatal exception, then hands it on so the process ends the way the system expects.
 *
 * This deliberately does as little as possible. It previously relaunched the launcher activity from
 * inside the handler while the process was being torn down, which leaves the system with a task that
 * is starting in a process it is simultaneously killing — the usual route to a "not responding"
 * dialog instead of an ordinary crash. It also queued its report on a background thread that the
 * kill then discarded, so nothing was ever recorded.
 *
 * Now the record is written synchronously, which is safe because nothing else is left to run, and
 * the user hears about it on the *next* launch through [ErrorReporter.takePendingCrash].
 */
class CrashHandler(
    private val store: CrashStore,
    private val delegate: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            AppLog.error("Fatal exception on thread ${thread.name}", throwable)
            store.record(throwable)
            ErrorReporter.reportFatal("crash on ${thread.name}", throwable)
        } catch (secondary: Throwable) {
            Log.e(TAG, AppLog.throwableTag("crashHandler", secondary), secondary)
        } finally {
            delegate?.uncaughtException(thread, throwable)
        }
    }

    private companion object {
        const val TAG = "DosPrint"
    }
}
