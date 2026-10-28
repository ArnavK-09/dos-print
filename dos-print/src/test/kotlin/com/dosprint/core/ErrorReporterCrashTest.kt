package com.dosprint.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A crash used to leave no trace: the report was queued on a background thread and the process was
 * killed before it ran. These cases pin the guarantee that matters — by the time a fatal report
 * returns, it is on disk, and the next launch can retrieve it exactly once.
 */
class ErrorReporterCrashTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun fresh() = ErrorReporter.initialize(folder.newFolder())

    @Test
    fun `a fatal report is on disk by the time the call returns`() {
        fresh()
        ErrorReporter.reportFatal("crash on main", IllegalStateException("boom"))
        val saved = ErrorReporter.recent()
        assertEquals("the write must not be deferred", 1, saved.size)
        assertEquals("crash on main", saved.single().area)
        assertEquals(ErrorReporter.Severity.FATAL, saved.single().severity)
    }

    @Test
    fun `the next launch can retrieve the crash that ended the last one`() {
        fresh()
        ErrorReporter.reportFatal("crash on main", IllegalStateException("boom"))
        val pending = ErrorReporter.takePendingCrash()
        assertNotNull(pending)
        assertTrue(pending?.summary.orEmpty().contains("boom"))
    }

    @Test
    fun `a pending crash is handed over only once`() {
        fresh()
        ErrorReporter.reportFatal("crash", IllegalStateException("once"))
        assertNotNull(ErrorReporter.takePendingCrash())
        assertNull("showing the same crash twice would nag", ErrorReporter.takePendingCrash())
    }

    @Test
    fun `there is nothing pending after a clean run`() {
        fresh()
        assertNull(ErrorReporter.takePendingCrash())
    }

    @Test
    fun `retrieving the pending crash does not erase it from the log`() {
        fresh()
        ErrorReporter.reportFatal("crash", IllegalStateException("kept"))
        ErrorReporter.takePendingCrash()
        assertEquals(1, ErrorReporter.recent().size)
    }

    @Test
    fun `the stack trace survives the trip through the pending marker`() {
        fresh()
        ErrorReporter.reportFatal("crash", IllegalStateException("traced"))
        val pending = ErrorReporter.takePendingCrash()
        assertTrue(pending?.detail.orEmpty().contains("ErrorReporterCrashTest"))
    }

    @Test
    fun `an external report such as an ANR is recorded with its trace`() {
        fresh()
        ErrorReporter.reportExternal(
            area = "previous run: not responding",
            severity = ErrorReporter.Severity.FATAL,
            summary = "not responding: input dispatching timed out",
            detail = "\"main\" prio=5\n  at android.os.MessageQueue.nativePollOnce",
        )
        Thread.sleep(300)
        val record = ErrorReporter.recent().firstOrNull { it.area.startsWith("previous run") }
        assertNotNull("an ANR must be recorded", record)
        assertTrue(record?.detail.orEmpty().contains("nativePollOnce"))
    }

    @Test
    fun `an external report with no trace is still stored`() {
        fresh()
        ErrorReporter.reportExternal("previous run: crash", ErrorReporter.Severity.FATAL, "crash", "")
        Thread.sleep(300)
        assertEquals("crash", ErrorReporter.recent().single().summary)
    }

    @Test
    fun `reporting before initialisation does not throw`() {
        val failure = runCatching {
            ErrorReporter.report("early", IllegalStateException("too soon"))
        }.exceptionOrNull()
        assertNull(failure)
    }

    @Test
    fun `the log is bounded so a crash loop cannot fill the disk`() {
        fresh()
        repeat(260) { ErrorReporter.reportFatal("crash $it", IllegalStateException("n$it")) }
        assertTrue(ErrorReporter.recent(1000).size <= 200)
    }

    @Test
    fun `clearing removes the saved errors`() {
        fresh()
        ErrorReporter.reportFatal("crash", IllegalStateException("x"))
        ErrorReporter.clear()
        assertTrue(ErrorReporter.recent().isEmpty())
    }
}
