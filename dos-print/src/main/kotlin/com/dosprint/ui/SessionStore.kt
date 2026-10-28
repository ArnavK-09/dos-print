package com.dosprint.ui

import com.dosprint.domain.model.PrintDocument
import com.dosprint.domain.model.PrintSettings
import com.dosprint.domain.model.PrinterCapabilities
import com.dosprint.domain.model.PrinterProfile
import com.dosprint.domain.model.SelectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SessionStore {
    private val selectionState = MutableStateFlow(SelectionState())

    val selection: StateFlow<SelectionState> = selectionState.asStateFlow()

    fun setDocuments(documents: List<PrintDocument>) {
        selectionState.update { it.copy(documents = documents) }
    }

    fun setPrinter(printer: PrinterProfile, capabilities: PrinterCapabilities) {
        selectionState.update {
            it.copy(
                printer = printer,
                capabilities = capabilities,
                settings = it.settings.copy(paperSize = capabilities.closestPaperSize(it.settings.paperSize) ?: it.settings.paperSize),
            )
        }
    }

    fun updateSettings(transform: (PrintSettings) -> PrintSettings) {
        selectionState.update { it.copy(settings = transform(it.settings).withClampedValues()) }
    }

    fun setSettings(settings: PrintSettings) {
        selectionState.update { it.copy(settings = settings.withClampedValues()) }
    }

    fun clear() {
        selectionState.value = SelectionState()
    }

    fun clearDocuments() {
        selectionState.update { it.copy(documents = emptyList()) }
    }
}
