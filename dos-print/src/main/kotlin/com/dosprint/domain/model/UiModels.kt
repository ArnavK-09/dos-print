package com.dosprint.domain.model

import androidx.compose.runtime.Immutable

@Immutable
enum class ThemeOption(val label: String) {
    SYSTEM("Follow system"),
    LIGHT("Light"),
    DARK("Dark"),
}

@Immutable
data class UserPreferences(
    val theme: ThemeOption = ThemeOption.SYSTEM,
    val useDynamicColor: Boolean = true,
    val increaseContrast: Boolean = false,
    val defaultQuality: PrintResolution = PrintResolution.STANDARD,
    val defaultColorMode: ColorMode = ColorMode.COLOR,
    val defaultCopies: Int = 1,
    val defaultPaperSizeId: String = PaperSize.A4.id,
    val notificationsEnabled: Boolean = true,
    val keepJobHistory: Boolean = true,
    val historyLimit: Int = 100,
    val backgroundMonitoring: Boolean = true,
    val lowMemoryMode: Boolean = false,
    val autoDiscoverPrinters: Boolean = true,
    val defaultPrinterId: String? = null,
    val advancedSetupEnabled: Boolean = false,
) {
    val defaultPaperSize: PaperSize get() = PaperSize.byId(defaultPaperSizeId) ?: PaperSize.A4

    fun defaultsForSettings(): PrintSettings = PrintSettings(
        paperSize = defaultPaperSize,
        colorMode = defaultColorMode,
        resolution = defaultQuality,
        copies = defaultCopies.coerceIn(PrintSettings.MIN_COPIES, PrintSettings.MAX_COPIES),
        advancedEnabled = advancedSetupEnabled,
    )

    companion object {
        const val MIN_HISTORY_LIMIT = 10
        const val MAX_HISTORY_LIMIT = 1000
    }
}

@Immutable
data class SetupWarning(
    val message: String,
    val suggestion: String? = null,
)

@Immutable
data class SelectionState(
    val documents: List<PrintDocument> = emptyList(),
    val printer: PrinterProfile? = null,
    val settings: PrintSettings = PrintSettings(),
    val capabilities: PrinterCapabilities = PrinterCapabilities.Unknown,
) {
    val documentCount: Int get() = documents.size

    val isEmpty: Boolean get() = documents.isEmpty()

    val firstDocument: PrintDocument? get() = documents.firstOrNull()
}
