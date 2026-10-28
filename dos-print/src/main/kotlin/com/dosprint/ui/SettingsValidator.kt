package com.dosprint.ui

import android.content.Context
import com.dosprint.R
import com.dosprint.domain.model.DuplexMode
import com.dosprint.domain.model.PageRange
import com.dosprint.domain.model.PrintSettings
import com.dosprint.domain.model.PrintSettingsResolver
import com.dosprint.domain.model.PrinterCapabilities
import com.dosprint.domain.model.SetupWarning
import com.dosprint.domain.model.SheetLayout

data class SettingsValidation(
    val settings: PrintSettings,
    val warnings: List<SetupWarning>,
    val pageRangeError: String? = null,
) {
    val canPrint: Boolean get() = pageRangeError == null
}

object SettingsValidator {
    fun validate(
        context: Context,
        requested: PrintSettings,
        capabilities: PrinterCapabilities,
        totalPages: Int,
    ): SettingsValidation {
        val resolved = PrintSettingsResolver.resolve(requested, capabilities, totalPages)
        val warnings = mutableListOf<SetupWarning>()

        if (!capabilities.unknown && capabilities.supportedPaperSizes.isNotEmpty()) {
            if (!resolved.paperSize.matches(requested.paperSize)) {
                warnings += SetupWarning(
                    message = context.getString(R.string.setup_unsupported_size, requested.paperSize.label, resolved.paperSize.label),
                    suggestion = context.getString(R.string.setup_paper_size),
                )
            }
        }

        if (resolved.duplex != requested.duplex && requested.duplex != NoDuplex) {
            warnings += SetupWarning(
                message = context.getString(R.string.setup_unsupported_duplex),
                suggestion = resolved.duplex.label,
            )
        }

        if (resolved.colorMode != requested.colorMode) {
            warnings += SetupWarning(
                message = context.getString(R.string.setup_unsupported_color),
                suggestion = resolved.colorMode.label,
            )
        }

        if (requested.borderless && !capabilities.unknown && !capabilities.supportsBorderless) {
            warnings += SetupWarning(message = context.getString(R.string.setup_borderless_unsupported))
        }

        if (resolved.pagesPerSheet != requested.pagesPerSheet) {
            val fallback = SheetLayout.fromCount(resolved.pagesPerSheet)
            warnings += SetupWarning(
                message = context.getString(
                    R.string.setup_unsupported_layout,
                    requested.pagesPerSheet.toString(),
                    fallback.label,
                ),
            )
        }

        val rangeError = if (!resolved.pageRange.isAllPages && selectedPageCount(resolved, totalPages) <= 0) {
            context.getString(R.string.setup_invalid_range_empty)
        } else {
            null
        }

        return SettingsValidation(
            settings = resolved,
            warnings = warnings,
            pageRangeError = rangeError,
        )
    }

    fun selectedPageCount(settings: PrintSettings, totalPages: Int): Int {
        if (totalPages <= 0) {
            return 0
        }
        return settings.pageRange.selectedPageCount(totalPages)
    }

    fun estimatedSheets(settings: PrintSettings, totalPages: Int): Int =
        settings.sheetCount(selectedPageCount(settings, totalPages))

    fun parsePageRange(
        context: Context,
        input: String,
        totalPages: Int,
    ): Pair<PageRange, String?> {
        if (input.isBlank()) {
            return PageRange.All to context.getString(R.string.setup_invalid_range_empty)
        }
        val parsed = PageRange.parse(input, totalPages.coerceAtLeast(1))
        val range = parsed.getOrNull()
            ?: return PageRange.All to context.getString(R.string.setup_invalid_range)
        if (selectedPageCount(PrintSettings(pageRange = range), totalPages) <= 0) {
            return PageRange.All to context.getString(R.string.error_zero_pages)
        }
        return range to null
    }

    private val NoDuplex = DuplexMode.NONE
}
