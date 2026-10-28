package com.dosprint.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The in-app log is the only thing a person holding just a phone can read, so it has to keep the
 * recent past, stay bounded, and survive being written from many threads at once.
 */
class LogBufferTest {

    private fun entry(message: String, level: LogLevel = LogLevel.INFO) =
        LogEntry(timestampMillis = 0L, level = level, message = message)

    @Test
    fun `entries come back oldest first`() {
        val buffer = LogBuffer(10)
        listOf("a", "b", "c").forEach { buffer.add(entry(it)) }
        assertEquals(listOf("a", "b", "c"), buffer.snapshot().map { it.message })
    }

    @Test
    fun `the buffer is bounded and drops the oldest entry first`() {
        val buffer = LogBuffer(3)
        (1..5).forEach { buffer.add(entry("m$it")) }
        assertEquals(listOf("m3", "m4", "m5"), buffer.snapshot().map { it.message })
        assertEquals(3, buffer.size())
    }

    @Test
    fun `a snapshot is a copy that later writes cannot change`() {
        val buffer = LogBuffer(10)
        buffer.add(entry("first"))
        val snapshot = buffer.snapshot()
        buffer.add(entry("second"))
        assertEquals(1, snapshot.size)
    }

    @Test
    fun `clear empties the buffer`() {
        val buffer = LogBuffer(10)
        buffer.add(entry("x"))
        buffer.clear()
        assertEquals(0, buffer.size())
        assertTrue(buffer.snapshot().isEmpty())
    }

    @Test
    fun `an empty buffer snapshots to an empty list`() {
        assertTrue(LogBuffer(5).snapshot().isEmpty())
    }

    @Test
    fun `a zero capacity is rejected rather than silently dropping everything`() {
        val failure = runCatching { LogBuffer(0) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `concurrent writers never exceed the capacity or lose consistency`() {
        val buffer = LogBuffer(100)
        val pool = Executors.newFixedThreadPool(8)
        val done = CountDownLatch(8)
        repeat(8) { worker ->
            pool.execute {
                repeat(500) { index -> buffer.add(entry("w$worker-$index")) }
                done.countDown()
            }
        }
        assertTrue("writers did not finish", done.await(10, TimeUnit.SECONDS))
        pool.shutdownNow()
        assertEquals(100, buffer.size())
        assertEquals(100, buffer.snapshot().size)
    }

    @Test
    fun `the global logger records what it is given`() {
        AppLog.clear()
        AppLog.info("hello from a test")
        AppLog.warn("something odd")
        val messages = AppLog.snapshot().map { it.message }
        assertTrue(messages.contains("hello from a test"))
        assertTrue(messages.contains("something odd"))
    }

    @Test
    fun `an exception attached to a log entry is kept with it`() {
        AppLog.clear()
        AppLog.error("boom", IllegalStateException("kaput"))
        val entry = AppLog.snapshot().single { it.message == "boom" }
        assertTrue(entry.stackTrace.orEmpty().contains("kaput"))
        assertEquals(LogLevel.ERROR, entry.level)
    }

    @Test
    fun `an entry without an exception has no stack trace`() {
        AppLog.clear()
        AppLog.info("plain")
        assertEquals(null, AppLog.snapshot().single { it.message == "plain" }.stackTrace)
    }

    @Test
    fun `logging never throws even with no platform log available`() {
        val failure = runCatching {
            LogLevel.entries.forEach { level ->
                when (level) {
                    LogLevel.VERBOSE -> AppLog.verbose("v")
                    LogLevel.DEBUG -> AppLog.debug("d")
                    LogLevel.INFO -> AppLog.info("i")
                    LogLevel.WARN -> AppLog.warn("w")
                    LogLevel.ERROR -> AppLog.error("e")
                }
            }
        }.exceptionOrNull()
        assertEquals(null, failure)
    }

    @Test
    fun `levels are ordered so a minimum filter works`() {
        assertTrue(LogLevel.ERROR > LogLevel.WARN)
        assertTrue(LogLevel.WARN > LogLevel.INFO)
        assertTrue(LogLevel.INFO > LogLevel.DEBUG)
        assertFalse(LogLevel.DEBUG >= LogLevel.INFO)
    }
}
