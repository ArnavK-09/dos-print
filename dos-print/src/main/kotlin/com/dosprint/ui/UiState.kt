package com.dosprint.ui

import androidx.compose.runtime.Immutable
import com.dosprint.domain.model.DocumentKind
import com.dosprint.domain.model.ImagePageSettings
import com.dosprint.domain.model.PaperChoices
import com.dosprint.domain.model.PaperSize
import com.dosprint.domain.model.PrintDocument
import com.dosprint.domain.model.PrintSettings
import com.dosprint.domain.model.PrinterCapabilities
import com.dosprint.domain.model.PrinterConnectionType
import com.dosprint.domain.model.PrinterProfile
import com.dosprint.domain.model.SelectionState
import com.dosprint.domain.model.SetupWarning
import com.dosprint.domain.model.SheetLayout
import com.dosprint.domain.model.UserPreferences

@Immutable
data class HomeUiState(
    val favoriteDocuments: List<PrintDocument> = emptyList(),
    val snackbarMessage: String? = null,
)

@Immutable
data class DocumentsUiState(
    val query: String = "",
    val filter: DocumentFilter = DocumentFilter.ALL,
    val documents: List<PrintDocument> = emptyList(),
    val selectedUris: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val snackbarMessage: String? = null,
    val errorMessage: String? = null,
    val failedUris: List<String> = emptyList(),
) {
    val visibleDocuments: List<PrintDocument>
        get() = documents
            .filter { document -> filter.accepts(document) }
            .filter { document ->
                query.isBlank() || document.displayName.contains(query, ignoreCase = true)
            }

    val selectedDocuments: List<PrintDocument>
        get() = documents.filter { it.uri in selectedUris }

    val canContinue: Boolean get() = selectedUris.isNotEmpty()

    val allVisibleSelected: Boolean
        get() = visibleDocuments.isNotEmpty() && visibleDocuments.all { it.uri in selectedUris }
}

enum class DocumentFilter {
    ALL,
    IMAGE,
    DOCUMENT,
    TEXT,
    ;

    fun accepts(document: PrintDocument): Boolean = when (this) {
        ALL -> true
        IMAGE -> document.format.kind == DocumentKind.IMAGE
        DOCUMENT ->
            document.format.kind == DocumentKind.PDF ||
                document.format.kind == DocumentKind.OFFICE_DOCUMENT
        TEXT ->
            document.format.kind == DocumentKind.TEXT ||
                document.format.kind == DocumentKind.MARKDOWN ||
                document.format.kind == DocumentKind.RICH_TEXT ||
                document.format.kind == DocumentKind.HTML
    }
}

@Immutable
data class AddPrinterRequest(
    val name: String = "",
    val connectionType: PrinterConnectionType = PrinterConnectionType.WIFI,
    val address: String = "",
) {
    val isValid: Boolean get() = name.isNotBlank()
}

@Immutable
data class PrintersUiState(
    val isRefreshing: Boolean = false,
    val printers: List<PrinterProfile> = emptyList(),
    val searchQuery: String = "",
    val defaultPrinterId: String? = null,
    val isPrintServiceAvailable: Boolean = true,
    val addPrinterRequest: AddPrinterRequest? = null,
    val addPrinterError: com.dosprint.domain.model.AddPrinterError? = null,
    val isSubmitting: Boolean = false,
    val renameTarget: PrinterProfile? = null,
    val renameText: String = "",
    val snackbarMessage: String? = null,
    val errorMessage: String? = null,
    val failedOperation: com.dosprint.core.FailedOperation? = null,
) {
    val visiblePrinters: List<PrinterProfile>
        get() = if (searchQuery.isBlank()) {
            printers
        } else {
            printers.filter {
                it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.description.contains(searchQuery, ignoreCase = true)
            }
        }

    val hasPrinters: Boolean get() = printers.isNotEmpty()
}

@Immutable
data class SetupUiState(
    val selection: SelectionState = SelectionState(),
    val resolvedSettings: PrintSettings = PrintSettings(),
    val availablePaperSizes: List<PaperSize> = PaperSize.COMMON,
    val paperChoices: PaperChoices = PaperChoices.forPrinter(
        PrinterCapabilities(printerId = ""),
        PaperSize.A4,
    ),
    val totalPages: Int = 0,
    val selectedPages: Int = 0,
    val warnings: List<SetupWarning> = emptyList(),
    val pageRangeInput: String = "",
    val pageRangeError: String? = null,
    val isSubmitting: Boolean = false,
    val snackbarMessage: String? = null,
    val submitError: String? = null,
    val previewError: String? = null,
    val failedOperation: com.dosprint.core.FailedOperation? = null,
    val livePreview: LivePreviewState = LivePreviewState(),
    val mode: PrintMode = PrintMode.DOCUMENTS,
) {
    val settings: PrintSettings get() = selection.settings

    /** True when the photo layout options should be shown instead of the document ones. */
    val isImageMode: Boolean get() = mode.isImages

    val imagePage: ImagePageSettings get() = settings.imagePageSettings

    val printer: PrinterProfile? get() = selection.printer

    val documents: List<PrintDocument> get() = selection.documents

    val capabilities: PrinterCapabilities get() = selection.capabilities

    val canPrint: Boolean
        get() = documents.isNotEmpty() && !isSubmitting && pageRangeError == null

    val estimatedSheets: Int get() = livePreview.sheetCount

    val sheetLayout: SheetLayout get() = settings.sheetLayout

    val supportsDuplex: Boolean get() = capabilities.supportsDuplex

    val supportsColor: Boolean get() = capabilities.supportsColor

    val supportsBorderless: Boolean get() = capabilities.supportsBorderless

    val maxSheetLayoutCount: Int get() = capabilities.maxSheetLayoutCountOrDefault()

    val hasMultipleDocuments: Boolean get() = documents.size > 1
}

@Immutable
data class PreviewUiState(
    val documents: List<PrintDocument> = emptyList(),
    val printer: PrinterProfile? = null,
    val capabilities: PrinterCapabilities = PrinterCapabilities.Unknown,
    val settings: PrintSettings = PrintSettings(),
    val totalSheets: Int = 0,
    val currentSheetIndex: Int = 0,
    val renderedSheets: List<RenderedSheet> = emptyList(),
    val isRendering: Boolean = false,
    val errorMessage: String? = null,
    val snackbarMessage: String? = null,
    val failedOperation: com.dosprint.core.FailedOperation? = null,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
) {
    val currentSheet: RenderedSheet? get() = renderedSheets.firstOrNull { it.index == currentSheetIndex }

    val hasSheets: Boolean get() = renderedSheets.isNotEmpty()

    val isFirstSheet: Boolean get() = currentSheetIndex == 0

    val isLastSheet: Boolean get() = currentSheetIndex >= totalSheets - 1

    val canPrint: Boolean get() = documents.isNotEmpty() && hasSheets
}

@Immutable
class RenderedSheet(
    val index: Int,
    val image: android.graphics.Bitmap,
    val widthPx: Int,
    val heightPx: Int,
) {
    val aspectRatio: Float
        get() = if (heightPx == 0) 1f else widthPx.toFloat() / heightPx
}

@Immutable
data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val printerCount: Int = 0,
    val versionName: String = "",
    val isNotificationPermissionGranted: Boolean = true,
    val notificationRequestVisible: Boolean = false,
    val snackbarMessage: String? = null,
)

@Immutable
data class LivePreviewState(
    val isRendering: Boolean = false,
    val sheetIndex: Int = 0,
    val sheetCount: Int = 0,
    val aspectRatio: Float = 1f,
    val image: android.graphics.Bitmap? = null,
    val errorMessage: String? = null,
    val hasContent: Boolean = false,
) {
    val hasImage: Boolean get() = image != null
}
