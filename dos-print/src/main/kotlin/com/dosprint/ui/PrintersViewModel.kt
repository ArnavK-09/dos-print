package com.dosprint.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dosprint.core.AppLog
import com.dosprint.core.UiMessages
import com.dosprint.data.printer.manualPrinterId
import com.dosprint.di.AppContainer
import com.dosprint.domain.model.PrinterProfile
import com.dosprint.domain.model.PrinterValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PrintersViewModel(
    private val container: AppContainer,
    private val session: SessionStore,
) : ViewModel() {

    private val appContext: Context = container.applicationContext

    private val mutableState = MutableStateFlow(PrintersUiState())
    val state: StateFlow<PrintersUiState> = mutableState.asStateFlow()

    init {
        observePrinters()
        viewModelScope.launch {
            val available = container.printerRepository.isPrintServiceAvailable()
            mutableState.update { it.copy(isPrintServiceAvailable = available) }
        }
    }

    private fun observePrinters() {
        viewModelScope.launch {
            container.printerRepository.printers.collect { printers ->
                mutableState.update { it.copy(printers = printers, isRefreshing = false) }
            }
        }
    }

    fun acceptAction(action: PrintersAction) {
        dispatch(action)
    }

    fun dispatch(action: PrintersAction) {
        when (action) {
            PrintersAction.Refresh -> refresh()
            is PrintersAction.Search -> search(action.query)
            is PrintersAction.Select -> select(action.printer)
            is PrintersAction.SetDefault -> setDefault(action.printerId)
            is PrintersAction.Remove -> remove(action.printerId)
            PrintersAction.OpenAddDialog -> openAddDialog()
            PrintersAction.DismissAddDialog -> dismissAddDialog()
            is PrintersAction.UpdateAddName -> updateAddName(action.name)
            is PrintersAction.UpdateAddConnection -> updateAddConnection(action.type)
            is PrintersAction.UpdateAddAddress -> updateAddAddress(action.address)
            PrintersAction.ConfirmAdd -> confirmAdd()
            is PrintersAction.StartRename -> startRename(action.printer)
            is PrintersAction.UpdateRenameText -> updateRenameText(action.text)
            PrintersAction.ConfirmRename -> confirmRename()
            PrintersAction.DismissRename -> dismissRename()
            PrintersAction.OpenSystemSettings -> openSystemSettings()
            PrintersAction.DismissError -> dismissError()
            PrintersAction.ClearSnackbar -> clearSnackbar()
        }
    }

    fun refresh() {
        mutableState.update { it.copy(isRefreshing = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                container.printerRepository.refreshPrinters()
                mutableState.update { it.copy(isRefreshing = false) }
            } catch (error: Exception) {
                AppLog.warn(AppLog.throwableTag("refreshPrinters", error))
                mutableState.update { it.copy(isRefreshing = false, errorMessage = DISCOVERY_FAILED) }
            }
        }
    }

    fun search(query: String) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun select(printer: PrinterProfile) {
        viewModelScope.launch {
            val capabilities = when (val outcome = container.printerRepository.capabilitiesFor(printer.id)) {
                is com.dosprint.core.Outcome.Success -> outcome.value
                is com.dosprint.core.Outcome.Failure -> {
                    mutableState.update { it.copy(errorMessage = PRINTER_UNRESOLVED) }
                    com.dosprint.domain.model.PrinterCapabilities.Unknown.copy(printerId = printer.id)
                }
            }
            session.setPrinter(printer, capabilities)
            mutableState.update { it.copy(snackbarMessage = PRINTER_SELECTED) }
        }
    }

    fun setDefault(printerId: String) {
        viewModelScope.launch {
            container.printerRepository.setDefaultPrinter(printerId)
            container.preferencesRepository.setDefaultPrinterId(printerId)
            mutableState.update { it.copy(defaultPrinterId = printerId, snackbarMessage = DEFAULT_SET) }
        }
    }

    fun remove(printerId: String) {
        viewModelScope.launch {
            container.printerRepository.removePrinter(printerId)
            mutableState.update { it.copy(snackbarMessage = PRINTER_REMOVED) }
        }
    }

    fun openAddDialog() {
        mutableState.update {
            it.copy(
                addPrinterRequest = AddPrinterRequest(),
                addPrinterError = null,
                errorMessage = null,
                failedOperation = null,
            )
        }
    }

    fun dismissAddDialog() {
        mutableState.update { it.copy(addPrinterRequest = null, addPrinterError = null) }
    }

    fun updateAddName(name: String) {
        mutableState.update {
            it.copy(addPrinterRequest = it.addPrinterRequest?.copy(name = name), addPrinterError = null)
        }
    }

    fun updateAddConnection(type: com.dosprint.domain.model.PrinterConnectionType) {
        mutableState.update {
            it.copy(
                addPrinterRequest = it.addPrinterRequest?.copy(connectionType = type, address = ""),
                addPrinterError = null,
            )
        }
    }

    fun updateAddAddress(address: String) {
        mutableState.update {
            it.copy(addPrinterRequest = it.addPrinterRequest?.copy(address = address), addPrinterError = null)
        }
    }

    fun confirmAdd() {
        val request = mutableState.value.addPrinterRequest ?: return
        val existingNames = mutableState.value.printers.map { it.displayName }
        val failure = PrinterValidator.validate(request, existingNames)
        if (failure != null) {
            mutableState.update { it.copy(addPrinterError = failure) }
            return
        }
        mutableState.update { it.copy(addPrinterRequest = null, addPrinterError = null, isSubmitting = true) }
        viewModelScope.launch {
            val trimmedAddress = request.address.trim().ifBlank { null }
            val profile = PrinterProfile(
                id = manualPrinterId(request.name, request.address),
                displayName = request.name.trim(),
                description = trimmedAddress ?: request.connectionType.label,
                connectionType = request.connectionType,
                serviceId = null,
                address = trimmedAddress,
                isUserAdded = true,
                isDefault = false,
            )
            runCatching { container.printerRepository.upsertManualPrinter(profile) }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = ADD_FAILED,
                            failedOperation = UiMessages.failedOperation("add-printer", error),
                        )
                    }
                    return@launch
                }
            mutableState.update {
                it.copy(isSubmitting = false, snackbarMessage = PRINTER_ADDED, failedOperation = null)
            }
        }
    }

    fun addDialogErrorText(): String? = mutableState.value.addPrinterError?.let { error ->
        UiMessages.addPrinterErrorText(appContext, error)
    }

    fun startRename(printer: PrinterProfile) {
        mutableState.update { it.copy(renameTarget = printer, renameText = printer.displayName) }
    }

    fun updateRenameText(text: String) {
        mutableState.update { it.copy(renameText = text) }
    }

    fun confirmRename() {
        val target = mutableState.value.renameTarget ?: return
        val text = mutableState.value.renameText
        viewModelScope.launch {
            container.printerRepository.renamePrinter(target.id, text)
            mutableState.update { it.copy(renameTarget = null, snackbarMessage = PRINTER_RENAMED) }
        }
    }

    fun dismissRename() {
        mutableState.update { it.copy(renameTarget = null, renameText = "") }
    }

    fun openSystemSettings() {
        container.printerRepository.openSystemPrinterSettings()
    }

    fun dismissError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    fun clearSnackbar() {
        mutableState.update { it.copy(snackbarMessage = null) }
    }

    class Factory(
        private val container: AppContainer,
        private val session: SessionStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PrintersViewModel(container, session) as T
    }

    internal companion object {
        const val DISCOVERY_FAILED = "discovery_failed"
        const val PRINTER_SELECTED = "printer_selected"
        const val ADD_FAILED = "printer_add_failed"
        const val PRINTER_ADDED = "printer_added"
        const val PRINTER_REMOVED = "printer_removed"
        const val PRINTER_RENAMED = "printer_renamed"
        const val PRINTER_UNRESOLVED = "printer_unresolved"
        const val DEFAULT_SET = "default_set"
        const val NAME_REQUIRED = "name_required"
    }
}
