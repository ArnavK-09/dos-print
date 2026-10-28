package com.dosprint.ui

import com.dosprint.domain.model.ColorMode
import com.dosprint.domain.model.DuplexMode
import com.dosprint.domain.model.MarginPreset
import com.dosprint.domain.model.PageOrder
import com.dosprint.domain.model.PageRotation
import com.dosprint.domain.model.PaperSize
import com.dosprint.domain.model.PrintDocument
import com.dosprint.domain.model.PrintResolution
import com.dosprint.domain.model.PrintSettings
import com.dosprint.domain.model.PrintSides
import com.dosprint.domain.model.PrinterConnectionType
import com.dosprint.domain.model.PrinterProfile
import com.dosprint.domain.model.ScalingMode
import com.dosprint.domain.model.SheetLayout
import com.dosprint.domain.model.SheetsPerBooklet
import com.dosprint.domain.model.ThemeOption
import com.dosprint.domain.model.WatermarkStyle

sealed interface HomeAction {
    data object Refresh : HomeAction

    /** Opens the system picker for documents. */
    data object PickDocuments : HomeAction

    /** Opens the system picker for images. */
    data object PickImages : HomeAction

    data class ToggleFavorite(val uri: String, val favorite: Boolean) : HomeAction

    data class DocumentClicked(val document: PrintDocument) : HomeAction

    data object OpenPrinters : HomeAction

    data object OpenDocuments : HomeAction

    data object ClearSnackbar : HomeAction
}

sealed interface DocumentsAction {
    data object BrowseFiles : DocumentsAction

    data object LoadRecents : DocumentsAction

    data class Search(val query: String) : DocumentsAction

    data class Filter(val filter: DocumentFilter) : DocumentsAction

    data class ToggleSelection(val uri: String) : DocumentsAction

    data object SelectAllVisible : DocumentsAction

    data object ClearSelection : DocumentsAction

    data class ToggleFavorite(val uri: String) : DocumentsAction

    data object Continue : DocumentsAction

    data object ClearSnackbar : DocumentsAction

    data object ClearError : DocumentsAction

    data object OpenLogs : DocumentsAction
}

sealed interface PrintersAction {
    data object Refresh : PrintersAction

    data class Search(val query: String) : PrintersAction

    data class Select(val printer: PrinterProfile) : PrintersAction

    data class SetDefault(val printerId: String) : PrintersAction

    data class Remove(val printerId: String) : PrintersAction

    data object OpenAddDialog : PrintersAction

    data object DismissAddDialog : PrintersAction

    data class UpdateAddName(val name: String) : PrintersAction

    data class UpdateAddConnection(val type: PrinterConnectionType) : PrintersAction

    data class UpdateAddAddress(val address: String) : PrintersAction

    data object ConfirmAdd : PrintersAction

    data class StartRename(val printer: PrinterProfile) : PrintersAction

    data class UpdateRenameText(val text: String) : PrintersAction

    data object ConfirmRename : PrintersAction

    data object DismissRename : PrintersAction

    data object OpenSystemSettings : PrintersAction

    data object DismissError : PrintersAction

    data object ClearSnackbar : PrintersAction
}

sealed interface SetupAction {
    data class SelectPrinter(val printer: PrinterProfile) : SetupAction

    data class SelectPaperSize(val paperSize: PaperSize) : SetupAction

    data class ToggleLandscape(val landscape: Boolean) : SetupAction

    data class SelectColorMode(val mode: ColorMode) : SetupAction

    data class SelectResolution(val resolution: PrintResolution) : SetupAction

    data class SelectScaling(val scaling: ScalingMode) : SetupAction

    data class SelectMarginPreset(val preset: MarginPreset) : SetupAction

    data class SelectPageSelection(val mode: com.dosprint.domain.model.PageSelectionMode) : SetupAction

    data class SetPageSegments(val segments: List<com.dosprint.domain.model.PageSegment>) : SetupAction

    data class ToggleReversePageOrder(val reverse: Boolean) : SetupAction

    data class SelectPosition(val position: com.dosprint.domain.model.ContentPosition) : SetupAction

    data class SelectPageScaling(val scaling: com.dosprint.domain.model.PageScaling) : SetupAction

    data class SetScalePercent(val percent: Int) : SetupAction

    data class SelectMargins(val margins: com.dosprint.domain.model.MarginOption) : SetupAction

    data class SelectManualDuplex(val duplex: com.dosprint.domain.model.ManualDuplex) : SetupAction

    data class SelectDuplex(val duplex: DuplexMode) : SetupAction

    data class ToggleCollate(val collate: Boolean) : SetupAction

    data class SetCopies(val copies: Int) : SetupAction

    data class SelectSheetLayout(val layout: SheetLayout) : SetupAction

    data class SelectPagesPerSheet(val count: Int) : SetupAction

    data class SelectPageOrder(val order: com.dosprint.domain.model.PageOrder) : SetupAction

    data class SelectSides(val sides: com.dosprint.domain.model.PrintSides) : SetupAction

    data class SelectSheetsPerBooklet(val sheets: com.dosprint.domain.model.SheetsPerBooklet) : SetupAction

    data class SetGridGutter(val fraction: Float) : SetupAction

    data class SetGridMargin(val fraction: Float) : SetupAction

    data class SetCellBorder(val fraction: Float) : SetupAction

    data class ApplyLayoutPreset(val preset: com.dosprint.domain.model.LayoutPreset) : SetupAction

    data class ToggleAllPages(val allPages: Boolean) : SetupAction

    data class PageRangeInputChanged(val input: String) : SetupAction

    data object ApplyPageRange : SetupAction

    data object ClearPreviewError : SetupAction

    data object RetryPreview : SetupAction

    data object OpenLogs : SetupAction

    data class SetWatermark(val style: WatermarkStyle) : SetupAction

    data class SetWatermarkText(val text: String) : SetupAction

    data class ToggleBorderless(val borderless: Boolean) : SetupAction

    data class Rotate(val rotation: PageRotation) : SetupAction

    data object OpenPreview : SetupAction

    data object Print : SetupAction

    data object ClearSnackbar : SetupAction

    data object DismissSubmitError : SetupAction
}

sealed interface PreviewAction {
    data object Render : PreviewAction

    data object NextSheet : PreviewAction

    data object PreviousSheet : PreviewAction

    data class GoToSheet(val index: Int) : PreviewAction

    data object Print : PreviewAction

    data object BackToSetup : PreviewAction

    data object OpenSetup : PreviewAction

    data object DismissError : PreviewAction
}

sealed interface SettingsAction {
    data class SelectTheme(val theme: ThemeOption) : SettingsAction

    data class ToggleDynamicColor(val enabled: Boolean) : SettingsAction

    data class ToggleContrast(val enabled: Boolean) : SettingsAction

    data class SelectDefaultQuality(val resolution: PrintResolution) : SettingsAction

    data class SelectDefaultColorMode(val mode: ColorMode) : SettingsAction

    data class SetDefaultCopies(val copies: Int) : SettingsAction

    data class ToggleNotifications(val enabled: Boolean) : SettingsAction

    data class ToggleKeepHistory(val enabled: Boolean) : SettingsAction

    data class SetHistoryLimit(val limit: Int) : SettingsAction

    data class ToggleMonitoring(val enabled: Boolean) : SettingsAction

    data class ToggleLowMemory(val enabled: Boolean) : SettingsAction

    data class ToggleAutoDiscover(val enabled: Boolean) : SettingsAction

    data class SetDefaultPrinter(val printerId: String?) : SettingsAction

    data object ClearPreviewCache : SettingsAction

    data object RequestNotificationsPermission : SettingsAction

    data object ClearSnackbar : SettingsAction
}

internal fun PrintSettings.sanitized(): PrintSettings = withClampedValues()
