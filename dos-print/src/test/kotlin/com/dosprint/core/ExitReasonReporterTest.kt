package com.dosprint.core

import android.app.ApplicationExitInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only endings that indicate a defect are reported; a user swiping the app away is not one. */
class ExitReasonReporterTest {

    @Test
    fun `an ANR is worth reporting`() {
        assertTrue(ExitReasonReporter.isWorthReporting(ApplicationExitInfo.REASON_ANR))
    }

    @Test
    fun `crashes and memory kills are worth reporting`() {
        assertTrue(ExitReasonReporter.isWorthReporting(ApplicationExitInfo.REASON_CRASH))
        assertTrue(ExitReasonReporter.isWorthReporting(ApplicationExitInfo.REASON_CRASH_NATIVE))
        assertTrue(ExitReasonReporter.isWorthReporting(ApplicationExitInfo.REASON_LOW_MEMORY))
    }

    @Test
    fun `an unrelated exit reason is ignored`() {
        assertFalse(ExitReasonReporter.isWorthReporting(-1))
    }

    @Test
    fun `an ANR reads as not responding`() {
        assertEquals("not responding", ExitReasonReporter.label(ApplicationExitInfo.REASON_ANR))
    }

    @Test
    fun `a description is appended when the system supplies one`() {
        val text = ExitReasonReporter.describe(ApplicationExitInfo.REASON_ANR, "input timed out")
        assertEquals("not responding: input timed out", text)
    }

    @Test
    fun `a blank description adds nothing`() {
        assertEquals("crash", ExitReasonReporter.describe(ApplicationExitInfo.REASON_CRASH, "  "))
        assertEquals("crash", ExitReasonReporter.describe(ApplicationExitInfo.REASON_CRASH, null))
    }

    @Test
    fun `an unknown reason still produces a label`() {
        assertTrue(ExitReasonReporter.label(-99).isNotBlank())
    }
}
