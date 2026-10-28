package com.dosprint.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.dosprint.domain.model.DocumentKind
import com.dosprint.domain.model.PrinterConnectionType

fun DocumentKind.icon(): ImageVector = when (this) {
    DocumentKind.PDF -> DosPrintIcons.Printer
    DocumentKind.IMAGE -> DosPrintIcons.Image
    DocumentKind.TEXT, DocumentKind.MARKDOWN, DocumentKind.RICH_TEXT, DocumentKind.HTML -> DosPrintIcons.Text
    DocumentKind.OFFICE_DOCUMENT -> DosPrintIcons.Document
    DocumentKind.POSTSCRIPT -> DosPrintIcons.Printer
    DocumentKind.UNKNOWN -> Icons.Outlined.Info
}

fun PrinterConnectionType.icon(): ImageVector = when (this) {
    PrinterConnectionType.SYSTEM -> DosPrintIcons.Printer
    PrinterConnectionType.WIFI -> DosPrintIcons.Wifi
    PrinterConnectionType.BLUETOOTH -> DosPrintIcons.Bluetooth
    PrinterConnectionType.USB -> DosPrintIcons.Usb
    PrinterConnectionType.PORT_RAW -> DosPrintIcons.Usb
    PrinterConnectionType.CLOUD -> DosPrintIcons.Cloud
    PrinterConnectionType.PDF -> DosPrintIcons.Pdf
}

fun PrinterConnectionType.isLocalOnly(): Boolean =
    this == PrinterConnectionType.BLUETOOTH || this == PrinterConnectionType.USB || this == PrinterConnectionType.PORT_RAW

@Composable
fun DocumentIcon(kind: DocumentKind, description: String?, modifier: Modifier = Modifier) {
    Icon(
        imageVector = kind.icon(),
        contentDescription = description,
        modifier = modifier,
    )
}
