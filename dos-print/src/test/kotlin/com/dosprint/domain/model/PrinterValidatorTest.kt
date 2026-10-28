package com.dosprint.domain.model

import com.dosprint.ui.AddPrinterRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterValidatorTest {

    @Test
    fun `a blank name is rejected as required`() {
        val error = PrinterValidator.validate(
            AddPrinterRequest(name = "   ", address = "printer.local"),
            emptyList(),
        )
        assertEquals(AddPrinterError.NameRequired, error)
    }

    @Test
    fun `an empty name is rejected as required`() {
        assertEquals(
            AddPrinterError.NameRequired,
            PrinterValidator.validate(AddPrinterRequest(name = ""), emptyList()),
        )
    }

    @Test
    fun `a realistic printer name is accepted`() {
        assertNull(
            PrinterValidator.validate(
                AddPrinterRequest(name = "Office Laser", address = "printer.local"),
                emptyList(),
            ),
        )
    }

    @Test
    fun `an over long name is rejected`() {
        val error = PrinterValidator.validate(
            AddPrinterRequest(name = "x".repeat(PrinterValidator.MaxNameLength + 1)),
            emptyList(),
        )
        assertEquals(AddPrinterError.InvalidName(NameProblem.TOO_LONG), error)
    }

    @Test
    fun `a name with control characters is rejected`() {
        val error = PrinterValidator.validate(
            AddPrinterRequest(name = "BadName"),
            emptyList(),
        )
        assertEquals(AddPrinterError.InvalidName(NameProblem.CONTAINS_CONTROL_CHARACTERS), error)
    }

    @Test
    fun `a duplicate name is rejected and reports the existing entry`() {
        val error = PrinterValidator.validate(
            AddPrinterRequest(name = "Office Laser", address = "printer.local"),
            listOf("office laser"),
        )
        assertTrue(error is AddPrinterError.DuplicateName)
        assertEquals("office laser", (error as AddPrinterError.DuplicateName).existing)
    }

    @Test
    fun `a wifi printer without an address is rejected`() {
        val error = PrinterValidator.validate(
            AddPrinterRequest(
                name = "Office",
                connectionType = PrinterConnectionType.WIFI,
                address = "",
            ),
            emptyList(),
        )
        assertEquals(AddPrinterError.AddressRequired(PrinterConnectionType.WIFI), error)
    }

    @Test
    fun `a hostname is accepted for wifi`() {
        assertNull(
            PrinterValidator.validateAddress("printer.local", PrinterConnectionType.WIFI),
        )
    }

    @Test
    fun `an ip address with a port is accepted`() {
        assertNull(
            PrinterValidator.validateAddress("192.168.1.20:9100", PrinterConnectionType.WIFI),
        )
    }

    @Test
    fun `a bare ip address is accepted`() {
        assertNull(
            PrinterValidator.validateAddress("192.168.1.20", PrinterConnectionType.WIFI),
        )
    }

    @Test
    fun `an out of range port is rejected`() {
        val error = PrinterValidator.validateAddress("printer.local:70000", PrinterConnectionType.WIFI)
        assertEquals(AddPrinterError.InvalidAddress(AddressProblem.OUT_OF_RANGE), error)
    }

    @Test
    fun `a port of zero is rejected`() {
        val error = PrinterValidator.validateAddress("printer.local:0", PrinterConnectionType.WIFI)
        assertEquals(AddPrinterError.InvalidAddress(AddressProblem.OUT_OF_RANGE), error)
    }

    @Test
    fun `an address with a space is rejected`() {
        val error = PrinterValidator.validateAddress("not a host", PrinterConnectionType.WIFI)
        assertEquals(AddPrinterError.InvalidAddress(AddressProblem.NOT_A_HOSTNAME), error)
    }

    @Test
    fun `an address with an empty label is rejected`() {
        assertNotNull(PrinterValidator.validateAddress("printer..local", PrinterConnectionType.WIFI))
    }

    @Test
    fun `a bluetooth address must be a mac`() {
        assertNull(PrinterValidator.validateAddress("00:11:22:33:44:55", PrinterConnectionType.BLUETOOTH))
        assertEquals(
            AddPrinterError.InvalidAddress(AddressProblem.NOT_A_HOSTNAME),
            PrinterValidator.validateAddress("00-11-22", PrinterConnectionType.BLUETOOTH),
        )
    }

    @Test
    fun `a usb address must be vid and pid`() {
        assertNull(PrinterValidator.validateAddress("04b8:0001", PrinterConnectionType.USB))
        assertNotNull(PrinterValidator.validateAddress("nonsense", PrinterConnectionType.USB))
    }

    @Test
    fun `cloud printers need no address`() {
        assertNull(PrinterValidator.validateAddress("", PrinterConnectionType.CLOUD))
    }

    @Test
    fun `host and port are split apart`() {
        val (host, port) = PrinterValidator.splitHostAndPort("printer.local:9100")
        assertEquals("printer.local", host)
        assertEquals(9100, port)
    }

    @Test
    fun `a host with no port yields a null port`() {
        val (host, port) = PrinterValidator.splitHostAndPort("printer.local")
        assertEquals("printer.local", host)
        assertNull(port)
    }

    @Test
    fun `a non numeric port leaves the whole string as the host`() {
        val (host, port) = PrinterValidator.splitHostAndPort("printer.local:abc")
        assertEquals("printer.local:abc", host)
        assertNull(port)
    }
}

class PrinterDestinationTest {

    @Test
    fun `the pdf destination is marked virtual`() {
        assertTrue(PrinterProfile.PdfDestination.isVirtual)
        assertTrue(PrinterProfile.PdfDestination.isPdfDestination())
        assertEquals(PrinterConnectionType.PDF, PrinterProfile.PdfDestination.connectionType)
    }

    @Test
    fun `a real printer is not virtual`() {
        val printer = PrinterProfile(
            id = "ipp://x",
            displayName = "Office",
            description = "",
            connectionType = PrinterConnectionType.WIFI,
        )
        assertTrue(!printer.isVirtual)
        assertTrue(!printer.isPdfDestination())
    }

    @Test
    fun `the pdf destination advertises full capabilities`() {
        val capabilities = PdfDestinationCapabilities
        assertTrue(!capabilities.unknown)
        assertTrue(capabilities.supportsColor)
        assertTrue(capabilities.supportsDuplex)
        assertTrue(capabilities.supportsBorderless)
        assertEquals(LayoutGrid.MAX_SAFE_SLOTS, capabilities.maxSheetLayoutCountOrDefault())
        assertTrue(PaperSize.ALL.size >= capabilities.supportedPaperSizes.size)
    }

    @Test
    fun `the pdf destination supports every paper size`() {
        PaperSize.ALL.forEach { paper ->
            assertTrue(PdfDestinationCapabilities.supports(paper))
        }
    }

    @Test
    fun `a virtual printer accepts no manual address`() {
        assertTrue(!PrinterProfile.PdfDestination.acceptsAddress)
        assertTrue(
            PrinterProfile(
                id = "a",
                displayName = "b",
                description = "",
                connectionType = PrinterConnectionType.WIFI,
            ).acceptsAddress,
        )
    }
}
