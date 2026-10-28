package com.dosprint.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentFormatTest {

    @Test
    fun detectsPdfByExtension() {
        val format = DocumentFormat.fromExtension("pdf", "application/octet-stream")
        assertEquals(DocumentKind.PDF, format.kind)
        assertTrue(format.isPrintable)
    }

    @Test
    fun detectsImagesByExtension() {
        val kinds = listOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic")
        kinds.forEach { extension ->
            assertEquals(
                DocumentKind.IMAGE,
                DocumentFormat.fromExtension(extension, "application/octet-stream").kind,
            )
        }
    }

    @Test
    fun detectsOfficeDocumentsByExtension() {
        val extensions = listOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods")
        extensions.forEach { extension ->
            assertEquals(
                DocumentKind.OFFICE_DOCUMENT,
                DocumentFormat.fromExtension(extension, "application/octet-stream").kind,
            )
        }
    }

    @Test
    fun detectsMimeTypeWhenExtensionIsUnknown() {
        val format = DocumentFormat.fromMimeType("application/pdf", "report")
        assertEquals(DocumentKind.PDF, format.kind)
    }

    @Test
    fun detectsTextByMimeType() {
        assertEquals(DocumentKind.TEXT, DocumentFormat.fromMimeType("text/plain", "notes").kind)
    }

    @Test
    fun unknownFormatIsNotPrintable() {
        val format = DocumentFormat.fromExtension("bin", "application/octet-stream")
        assertEquals(DocumentKind.UNKNOWN, format.kind)
        assertFalse(format.isPrintable)
    }

    @Test
    fun normalizesExtensionCase() {
        assertEquals("pdf", DocumentFormat.fromExtension("PDF", "").extension)
    }

    @Test
    fun pdfSupportsNativePreview() {
        assertTrue(DocumentFormat.fromExtension("pdf", "").supportsNativePreview)
    }

    @Test
    fun officeDocumentHasNoNativePreview() {
        assertFalse(DocumentFormat.fromExtension("docx", "").supportsNativePreview)
    }
}
