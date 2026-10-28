package com.dosprint.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exports go over binder, which fails above about a megabyte, so size handling is not optional. */
class LogFormatterTest {

    private fun entry(message: String, level: LogLevel = LogLevel.INFO, stack: String? = null) =
        LogEntry(1L, level, message, stack)

    private val clock: (Long) -> String = { "T$it" }

    @Test
    fun `a line carries time level and message`() {
        assertEquals("T1 I hello", LogFormatter.line(entry("hello"), clock))
    }

    @Test
    fun `each level has its own letter`() {
        val letters = LogLevel.entries.map { LogFormatter.line(entry("m", it), clock).split(' ')[1] }
        assertEquals(listOf("V", "D", "I", "W", "E"), letters)
    }

    @Test
    fun `a stack trace follows its message on later lines`() {
        val line = LogFormatter.line(entry("failed", LogLevel.ERROR, "java.lang.Boom\n\tat x"), clock)
        assertTrue(line.startsWith("T1 E failed\n"))
        assertTrue(line.contains("java.lang.Boom"))
    }

    @Test
    fun `trailing blank lines from a trace are trimmed`() {
        val line = LogFormatter.line(entry("failed", LogLevel.ERROR, "trace\n\n\n"), clock)
        assertFalse(line.endsWith("\n"))
    }

    @Test
    fun `a small export is returned whole`() {
        assertEquals("a\nb", LogFormatter.export(listOf("a", "b")))
    }

    @Test
    fun `an empty export is empty`() {
        assertEquals("", LogFormatter.export(emptyList()))
    }

    @Test
    fun `an oversized export is cut from the front and keeps the newest lines`() {
        val lines = (1..40_000).map { "line-$it" }
        val exported = LogFormatter.export(lines)
        assertTrue("export must be bounded", exported.length < 160_000)
        assertTrue("the newest line must survive", exported.endsWith("line-40000"))
        assertFalse("the oldest line must be dropped", exported.contains("line-1\n"))
        assertTrue(exported.startsWith("[earlier output omitted]"))
    }

    @Test
    fun `an oversized export never starts on a half line`() {
        val lines = (1..40_000).map { "line-$it" }
        val body = LogFormatter.export(lines).removePrefix("[earlier output omitted]\n")
        assertTrue(body.startsWith("line-"))
    }
}
