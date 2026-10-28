package com.dosprint.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The error log is the only way to find out what went wrong after the fact, so a record has to
 * survive a round trip through the on-disk format intact, including the stack trace.
 */
class ErrorRecordTest {

    @Test
    fun `a record survives a round trip`() {
        val original = ErrorRecord.of("submit", ErrorReporter.Severity.FATAL, IllegalStateException("boom"))
        val parsed = ErrorRecord.parse(original.serialize())
        assertNotNull(parsed)
        assertEquals(original.area, parsed?.area)
        assertEquals(original.severity, parsed?.severity)
        assertEquals(original.timestampMillis, parsed?.timestampMillis)
    }

    @Test
    fun `a multiline stack trace survives the round trip`() {
        val error = runCatching { error("nested") }.exceptionOrNull() ?: IllegalStateException("x")
        val parsed = ErrorRecord.parse(ErrorRecord.of("render", ErrorReporter.Severity.RECOVERED, error).serialize())
        assertTrue("the stack trace must not be lost", parsed?.detail?.contains("nested") == true)
        assertTrue("the frames must survive", parsed?.detail?.contains("ErrorRecordTest") == true)
    }

    @Test
    fun `the summary names the exception type and its message`() {
        val record = ErrorRecord.of("import", ErrorReporter.Severity.RECOVERED, IllegalArgumentException("bad uri"))
        assertTrue(record.summary.startsWith("IllegalArgumentException"))
        assertTrue(record.summary.contains("bad uri"))
    }

    @Test
    fun `an exception with no message still summarises`() {
        val record = ErrorRecord.of("discover", ErrorReporter.Severity.RECOVERED, RuntimeException())
        assertTrue(record.summary.startsWith("RuntimeException"))
    }

    @Test
    fun `a serialised record occupies exactly one line`() {
        val record = ErrorRecord.of("preview", ErrorReporter.Severity.RECOVERED, IllegalStateException("line1\nline2"))
        val lines = record.serialize().split("\n")
        assertEquals("a newline would break the line oriented log", 1, lines.size)
    }

    @Test
    fun `a separator inside a field cannot silently split the record`() {
        val record = ErrorRecord.of("a\u001fb", ErrorReporter.Severity.RECOVERED, RuntimeException("x"))
        assertNull(
            "a record with an injected separator must be rejected, not misparsed",
            ErrorRecord.parse(record.serialize()),
        )
    }

    @Test
    fun `garbage is rejected rather than throwing`() {
        assertNull(ErrorRecord.parse(""))
        assertNull(ErrorRecord.parse("not a record"))
        assertNull(ErrorRecord.parse("ab"))
    }

    @Test
    fun `a record with a non numeric timestamp is rejected`() {
        assertNull(ErrorRecord.parse("notanumberRECOVEREDareasummary"))
    }

    @Test
    fun `non ascii text survives the encoding`() {
        val record = ErrorRecord.of("printer", ErrorReporter.Severity.FATAL, IllegalStateException("café — 漢字"))
        val parsed = ErrorRecord.parse(record.serialize())
        assertTrue(parsed?.summary?.contains("café") == true)
    }

    @Test
    fun `the detail is capped so one failure cannot fill the log`() {
        val deep = generateSequence(RuntimeException("root") as Throwable) { cause ->
            IllegalStateException("wrap", cause)
        }.take(120).last()
        val record = ErrorRecord.of("render", ErrorReporter.Severity.FATAL, deep)
        assertTrue("the detail must be bounded", record.detail.lineSequence().count() <= 80)
    }

    @Test
    fun `severity distinguishes a crash from a handled failure`() {
        assertEquals(
            ErrorReporter.Severity.FATAL,
            ErrorRecord.of("a", ErrorReporter.Severity.FATAL, RuntimeException()).severity,
        )
        assertEquals(
            ErrorReporter.Severity.RECOVERED,
            ErrorRecord.of("a", ErrorReporter.Severity.RECOVERED, RuntimeException()).severity,
        )
    }

    @Test
    fun `guard rethrows so the failure is never swallowed`() {
        val thrown = runCatching {
            ErrorReporter.guard("flow") { throw IllegalStateException("nope") }
        }.exceptionOrNull()
        assertTrue(
            "guard must not swallow a failure",
            thrown is IllegalStateException,
        )
    }

    @Test
    fun `guard returns the value of a successful block`() {
        assertEquals(7, ErrorReporter.guard("flow") { 7 })
    }
}
