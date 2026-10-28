package com.dosprint.core

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Runs small pieces of disk work off the calling thread, without leaving a thread behind.
 *
 * A single-thread executor created up front keeps a thread alive for the whole life of the process
 * even when there is nothing to do. This one has no core threads at all: a thread exists only while
 * work is queued and exits a few seconds after the queue empties, so an idle app owns no background
 * thread. Tasks run one at a time, in order, which is what log and error writes need.
 */
internal object BackgroundIo {

    private const val IDLE_SECONDS = 3L
    private const val THREAD_NAME = "dosprint-io"

    private val executor = ThreadPoolExecutor(
        0,
        1,
        IDLE_SECONDS,
        TimeUnit.SECONDS,
        LinkedBlockingQueue(),
        ThreadFactory { runnable ->
            Thread(runnable, THREAD_NAME).apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        },
    )

    /** Queues [task]. A rejected task is dropped: losing a log line must never fail the caller. */
    fun execute(task: () -> Unit) {
        try {
            executor.execute {
                try {
                    task()
                } catch (ignored: Throwable) {
                    android.util.Log.w(THREAD_NAME, "background task failed", ignored)
                }
            }
        } catch (ignored: RejectedExecutionException) {
            return
        }
    }
}
