package com.dosprint.data.print

import android.print.PrintJob
import com.dosprint.core.AppLog
import com.dosprint.core.DispatcherProvider
import com.dosprint.domain.repository.PrintRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PrintJobMonitor(
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
    private val jobSource: () -> List<PrintJob>,
) {
    private var repositoryRef: PrintRepository? = null
    private var monitoringJob: Job? = null
    private val knownStates = HashMap<String, Int>()

    fun register(repository: PrintRepository) {
        repositoryRef = repository
        startPolling()
    }

    fun unregister() {
        repositoryRef = null
        stopPolling()
        knownStates.clear()
    }

    fun refreshNow() {
        pollOnce()
    }

    fun trackedJobCount(): Int = knownStates.size

    /**
     * Starts watching for state changes.
     *
     * Only starts if something might actually change: an armed job, or one the framework still
     * reports as in flight from a previous run. Polling forever would wake the CPU every
     * [POLL_INTERVAL_MILLIS] for the lifetime of the process for no benefit.
     */
    private fun startPolling() {
        if (monitoringJob?.isActive == true) {
            return
        }
        if (!isWatching()) {
            return
        }
        monitoringJob = scope.launch(dispatchers.io) {
            while (isActive) {
                pollOnce()
                if (!isWatching()) {
                    break
                }
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    private fun stopPolling() {
        monitoringJob?.cancel()
        monitoringJob = null
    }

    /** Called when a job is submitted, so its state changes are picked up. */
    fun arm() {
        startPolling()
    }

    private fun isWatching(): Boolean {
        if (armed.get()) {
            return true
        }
        if (knownStates.isEmpty()) {
            return false
        }
        return jobSource().any { it.info.state.isInFlightState() }
    }

    private val armed = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun pollOnce() {
        val repository = repositoryRef ?: return
        val jobs = try {
            jobSource()
        } catch (error: Exception) {
            AppLog.warn(AppLog.throwableTag("jobSource", error))
            return
        }
        val activeKeys = HashSet<String>(jobs.size)
        for (job in jobs) {
            val key = keyOf(job)
            if (key.isBlank()) {
                continue
            }
            activeKeys += key
            val state = stateOf(job)
            if (knownStates[key] != state) {
                knownStates[key] = state
                repository.onSystemJobUpdate(job)
            }
        }
        knownStates.keys.retainAll(activeKeys)
        if (activeKeys.isEmpty()) {
            armed.set(false)
        }
    }

    private fun Int.isInFlightState(): Boolean = when (this) {
        android.print.PrintJobInfo.STATE_CREATED,
        android.print.PrintJobInfo.STATE_QUEUED,
        android.print.PrintJobInfo.STATE_STARTED,
        android.print.PrintJobInfo.STATE_BLOCKED,
        -> true
        else -> false
    }

    private fun keyOf(job: PrintJob): String = runCatching {
        val info = job.info
        "${info.printerId?.localId.orEmpty()}:${info.creationTime}:${info.label.orEmpty()}"
    }.getOrDefault("")

    private fun stateOf(job: PrintJob): Int = runCatching { job.info.state }
        .getOrDefault(android.print.PrintJobInfo.STATE_FAILED)

    private companion object {
        const val POLL_INTERVAL_MILLIS = 1500L
    }
}

internal fun PrintJob.isCancelledState(): Boolean = try {
    isCancelled
} catch (error: Exception) {
    AppLog.warn(AppLog.throwableTag("isCancelled", error))
    false
}

internal fun PrintJob.isCompletedState(): Boolean = runCatching { isCompleted }.getOrDefault(false)

internal fun PrintJob.isFailedState(): Boolean = runCatching { isFailed }.getOrDefault(false)

internal fun PrintJob.isBlockedState(): Boolean = runCatching { isBlocked }.getOrDefault(false)

internal fun PrintJob.cancelSafely(): Boolean = try {
    cancel()
    true
} catch (error: Exception) {
    AppLog.warn(AppLog.throwableTag("cancel", error))
    false
}
