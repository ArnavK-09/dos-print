package com.dosprint.ui

import com.dosprint.domain.model.DocumentAvailability
import com.dosprint.domain.model.DocumentFormat
import com.dosprint.domain.model.PrintDocument
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The import path touches the content resolver, the document parser and permission persistence, and
 * every one of them can throw. These cases pin the rule that matters: one bad file must never take
 * the whole selection down with it.
 */
class DocumentImporterTest {

    private fun document(uri: String, pages: Int? = null, availability: DocumentAvailability = DocumentAvailability.AVAILABLE) =
        PrintDocument(
            uri = uri,
            displayName = uri.substringAfterLast('/'),
            sizeBytes = 1,
            mimeType = "application/pdf",
            format = DocumentFormat.fromExtension("pdf", "application/pdf"),
            addedAtMillis = 0,
            availability = availability,
            detectedPageCount = pages,
        )

    private fun importer(
        describe: suspend (String) -> PrintDocument?,
        pageCountOf: suspend (String) -> Int? = { null },
        persist: suspend (String) -> Unit = {},
        remember: suspend (PrintDocument) -> Unit = {},
        errors: MutableList<String> = mutableListOf(),
    ) = DocumentImporter(describe, pageCountOf, persist, remember).apply {
        onError = { uri, _ -> errors += uri }
    }

    @Test
    fun `every readable file is imported`() = runTest {
        val result = importer(describe = { document(it, pages = 3) }).import(listOf("a", "b", "c"))
        assertEquals(3, result.importedCount)
        assertEquals(0, result.failedCount)
        assertTrue(result.isCompleteSuccess)
    }

    @Test
    fun `one unreadable file does not lose the others`() = runTest {
        val result = importer(describe = { uri ->
            if (uri == "bad") null else document(uri)
        }).import(listOf("good", "bad", "alsoGood"))
        assertEquals(listOf("good", "alsoGood"), result.documents.map { it.uri })
        assertEquals(listOf("bad"), result.failedUris)
        assertTrue(result.isPartialFailure)
    }

    @Test
    fun `a file that throws while being described is counted as a failure`() = runTest {
        val result = importer(describe = { uri ->
            if (uri == "boom") throw SecurityException("revoked") else document(uri)
        }).import(listOf("a", "boom"))
        assertEquals(1, result.importedCount)
        assertEquals(listOf("boom"), result.failedUris)
    }

    @Test
    fun `a total failure is reported rather than looking like success`() = runTest {
        val result = importer(describe = { null }).import(listOf("a", "b"))
        assertEquals(0, result.importedCount)
        assertTrue(result.isTotalFailure)
        assertFalse(result.isCompleteSuccess)
    }

    @Test
    fun `a page count that throws leaves the document importable`() = runTest {
        val errors = mutableListOf<String>()
        val result = importer(
            describe = { document(it) },
            pageCountOf = { throw IllegalStateException("corrupt") },
            errors = errors,
        ).import(listOf("a"))
        assertEquals("a corrupt page count must not lose the file", 1, result.importedCount)
        assertEquals(0, result.failedCount)
        assertTrue(errors.contains("a"))
    }

    @Test
    fun `a failed permission persist does not lose the import`() = runTest {
        val result = importer(
            describe = { document(it) },
            persist = { throw SecurityException("read only") },
        ).import(listOf("a"))
        assertEquals(1, result.importedCount)
    }

    @Test
    fun `a failed recency write does not lose the import`() = runTest {
        val result = importer(
            describe = { document(it) },
            remember = { throw IllegalStateException("disk") },
        ).import(listOf("a"))
        assertEquals(1, result.importedCount)
    }

    @Test
    fun `a known page count is carried onto the document`() = runTest {
        val result = importer(describe = { document(it) }, pageCountOf = { 12 }).import(listOf("a"))
        assertEquals(12, result.documents.single().detectedPageCount)
    }

    @Test
    fun `an uncountable document keeps whatever count it already had`() = runTest {
        val result = importer(
            describe = { document(it, pages = 7) },
            pageCountOf = { throw IllegalStateException("no") },
        ).import(listOf("a"))
        assertEquals(7, result.documents.single().detectedPageCount)
    }

    @Test
    fun `an empty selection imports nothing and fails nothing`() = runTest {
        val result = importer(describe = { document(it) }).import(emptyList())
        assertEquals(0, result.importedCount)
        assertEquals(0, result.failedCount)
        assertFalse(result.isTotalFailure)
    }

    @Test
    fun `failures are reported once even when the same uri repeats`() = runTest {
        val result = importer(describe = { null }).import(listOf("a", "a"))
        assertEquals(listOf("a"), result.failedUris)
    }

    @Test
    fun `every failure reaches the error reporter`() = runTest {
        val errors = mutableListOf<String>()
        importer(describe = { if (it == "x") null else document(it) }, errors = errors)
            .import(listOf("x", "y"))
        assertTrue("the failure must be reported, not swallowed", errors.contains("x"))
    }

    @Test
    fun `an unavailable document is imported but flagged`() = runTest {
        val result = importer(
            describe = { document(it, availability = DocumentAvailability.MISSING) },
        ).import(listOf("a"))
        assertEquals(1, result.importedCount)
        assertEquals(DocumentAvailability.MISSING, result.documents.single().availability)
    }

    @Test
    fun `import order follows the selection order`() = runTest {
        val result = importer(describe = { document(it) }).import(listOf("c", "a", "b"))
        assertEquals(listOf("c", "a", "b"), result.documents.map { it.uri })
    }
}
