package com.dosprint.ui

import androidx.compose.runtime.Immutable

@Immutable
data class ScreenStates(
    val home: HomeUiState = HomeUiState(),
    val documents: DocumentsUiState = DocumentsUiState(),
    val printers: PrintersUiState = PrintersUiState(),
    val setup: SetupUiState = SetupUiState(),
    val preview: PreviewUiState = PreviewUiState(),
    val settings: SettingsUiState = SettingsUiState(),
)

class ScreenCallbacks(
    val actions: ScreenActions,
    val launchers: ScreenLaunchers,
)

class ScreenActions(
    val onHome: (HomeAction) -> Unit,
    val onDocuments: (DocumentsAction) -> Unit,
    val onPrinters: (PrintersAction) -> Unit,
    val onSetup: (SetupAction) -> Unit,
    val onPreview: (PreviewAction) -> Unit,
    val onImagePageChange: (com.dosprint.domain.model.ImagePageSettings) -> Unit,
    val onSettings: (SettingsAction) -> Unit,
)

class ScreenLaunchers(
    val onPickFiles: () -> Unit,
    val onPickImages: () -> Unit,
    val onPickMedia: () -> Unit,
    val onSavePdf: (List<com.dosprint.domain.model.PrintDocument>, com.dosprint.domain.model.PrintSettings) -> Unit,
    val onRequestNotificationPermission: () -> Unit,
    val onSetupSurfaceWidth: (Int) -> Unit,
    val onPreviewSurfaceWidth: (Int) -> Unit,
)
