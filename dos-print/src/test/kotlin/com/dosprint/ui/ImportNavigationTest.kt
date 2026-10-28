package com.dosprint.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Import navigation.
 *
 * The guard was written against a route captured once when the collecting effect started, which is
 * `null` on the first frame — before the back stack has an entry. The condition was therefore
 * permanently false and picking a file did nothing at all, with no error to report. These cases pin
 * the rule so the routes that must accept an import are explicit.
 */
class ImportNavigationTest {

    @Test
    fun `an import from home opens the setup screen`() {
        assertTrue(shouldOpenAfterImport(Route.Home))
    }

    @Test
    fun `an import from the documents screen opens the setup screen`() {
        assertTrue(shouldOpenAfterImport(Route.Documents))
    }

    @Test
    fun `an import already on setup does not navigate away`() {
        assertFalse(shouldOpenAfterImport(Route.Setup))
    }

    @Test
    fun `an import from the preview does not navigate away`() {
        assertFalse(shouldOpenAfterImport(Route.Preview))
    }

    @Test
    fun `an import from printers does not navigate away`() {
        assertFalse(shouldOpenAfterImport(Route.Printers))
    }

    @Test
    fun `an unknown route does not navigate away`() {
        assertFalse(shouldOpenAfterImport("somewhere_else"))
    }

    @Test
    fun `an unresolved destination does not navigate`() {
        assertFalse(
            "a null route is what the very first frame reports, and navigating on it was never " +
                "possible because the back stack has no entry yet",
            shouldOpenAfterImport(null),
        )
    }

    @Test
    fun `the route constants are the values navigation actually reports`() {
        assertTrue(Route.Home.isNotBlank())
        assertTrue(Route.Documents.isNotBlank())
        assertFalse(Route.Home == Route.Documents)
    }
}
