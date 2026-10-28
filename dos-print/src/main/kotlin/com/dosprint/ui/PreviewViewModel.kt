package com.dosprint.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dosprint.core.AppLog
import com.dosprint.core.Outcome
import com.dosprint.data.render.SheetPreviewRenderer
import com.dosprint.di.AppContainer
import com.dosprint.domain.model.PdfDestinationCapabilities
import com.dosprint.domain.model.PrintSettingsResolver
import com.dosprint.domain.model.PrinterProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PreviewViewModel(
    private val container: AppContainer,
    private val session: SessionStore,
    private val renderer: SheetPreviewRenderer,
) : ViewModel() {

    private val mutableState = MutableStateFlow(PreviewUiState())
    val state: StateFlow<PreviewUiState> = mutableState.asStateFlow()

    private var renderJob: Job? = null
    private var renderWidthPx: Int = 0

    init {
        observeSelection()
        defaultDestination()
    }

    private fun defaultDestination() {
        viewModelScope.launch {
            if (session.selection.value.printer == null) {
                session.setPrinter(PrinterProfile.PdfDestination, PdfDestinationCapabilities)
            }
        }
    }

    private fun observeSelection() {
        viewModelScope.launch {
            session.selection.collect { selection ->
                val totalPages = selection.documents.sumOf { it.detectedPageCount ?: 1 }
                val resolvedSettings = PrintSettingsResolver.resolve(
                    selection.settings,
                    selection.capabilities,
                    totalPages,
                )
                mutableState.update { current ->
                    val changed = current.documents != selection.documents ||
                        current.settings != resolvedSettings ||
                        current.capabilities != selection.capabilities
                    current.copy(
                        documents = selection.documents,
                        printer = selection.printer,
                        capabilities = selection.capabilities,
                        settings = resolvedSettings,
                        currentSheetIndex = if (changed) 0 else current.currentSheetIndex,
                        totalSheets = if (changed) 0 else current.totalSheets,
                        renderedSheets = if (changed) emptyList() else current.renderedSheets,
                    )
                }
                if (renderWidthPx > 0) {
                    render(renderWidthPx)
                }
            }
        }
    }

    fun onSurfaceWidth(widthPx: Int) {
        if (widthPx <= 0 || widthPx == renderWidthPx) {
            return
        }
        renderWidthPx = widthPx
        render(widthPx)
    }

    fun render(widthPx: Int = renderWidthPx) {
        renderSheet(mutableState.value.currentSheetIndex, widthPx)
    }

    private fun renderSheet(index: Int, widthPx: Int = renderWidthPx) {
        val snapshot = mutableState.value
        if (snapshot.documents.isEmpty() || widthPx <= 0) {
            return
        }
        renderJob?.cancel()
        mutableState.update {
            it.copy(isRendering = true, errorMessage = null, currentSheetIndex = index)
        }
        renderJob = viewModelScope.launch {
            val totalSheets = withContext(container.dispatchers.io) {
                renderer.sheetCountFor(snapshot.documents, snapshot.settings)
            }
            val sheetIndex = index.coerceIn(0, (totalSheets - 1).coerceAtLeast(0))
            mutableState.update { it.copy(totalSheets = totalSheets, currentSheetIndex = sheetIndex) }
            val outcome = try {
                withContext(container.dispatchers.io) {
                    renderer.renderSheets(
                        documents = snapshot.documents,
                        settings = snapshot.settings,
                        maxSheets = 1,
                        targetWidthPx = widthPx,
                        startSheetIndex = sheetIndex,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.warn(AppLog.throwableTag("previewSheet", error))
                Outcome.Failure(com.dosprint.core.AppError.Unknown(error))
            }
            when (outcome) {
                is Outcome.Success -> {
                    mutableState.update { state ->
                        state.copy(
                            isRendering = false,
                            renderedSheets = outcome.value.mapIndexed { index, bitmap ->
                                RenderedSheet(
                                    index = sheetIndex + index,
                                    image = bitmap,
                                    widthPx = bitmap.width,
                                    heightPx = bitmap.height,
                                )
                            },
                            currentSheetIndex = sheetIndex,
                            totalSheets = totalSheets,
                            errorMessage = null,
                        )
                    }
                }
                is Outcome.Failure -> {
                    AppLog.warn("Preview render failed: ${outcome.error}")
                    mutableState.update {
                        it.copy(isRendering = false, renderedSheets = emptyList(), errorMessage = PREVIEW_FAILED)
                    }
                }
            }
        }
    }

    fun dispatch(action: PreviewAction) {
        when (action) {
            PreviewAction.Render -> render()
            PreviewAction.NextSheet -> moveSheet(1)
            PreviewAction.PreviousSheet -> moveSheet(-1)
            is PreviewAction.GoToSheet -> renderSheet(action.index)
            PreviewAction.Print -> submitPrint()
            PreviewAction.BackToSetup -> Unit
            PreviewAction.OpenSetup -> Unit
            PreviewAction.DismissError -> mutableState.update { it.copy(errorMessage = null) }
        }
    }

    private fun moveSheet(delta: Int) {
        val current = mutableState.value
        renderSheet((current.currentSheetIndex + delta).coerceIn(0, (current.totalSheets - 1).coerceAtLeast(0)))
    }

    private fun submitPrint() {
        viewModelScope.launch {
            val printer = mutableState.value.printer ?: PrinterProfile.PdfDestination
            val documents = mutableState.value.documents
            if (documents.isEmpty()) {
                return@launch
            }
            val capabilities = when (val outcome = container.printerRepository.capabilitiesFor(printer.id)) {
                is Outcome.Success -> outcome.value
                is Outcome.Failure -> com.dosprint.domain.model.PrinterCapabilities.Unknown.copy(printerId = printer.id)
            }
            container.printRepository.submit(
                documents = documents,
                printer = printer,
                capabilities = capabilities,
                settings = mutableState.value.settings,
                jobId = java.util.UUID.randomUUID().toString(),
            )
        }
    }

    override fun onCleared() {
        renderJob?.cancel()
        mutableState.value.renderedSheets.forEach { sheet ->
            if (!sheet.image.isRecycled) {
                sheet.image.recycle()
            }
        }
        super.onCleared()
    }

    class Factory(
        private val container: AppContainer,
        private val session: SessionStore,
        private val renderer: SheetPreviewRenderer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PreviewViewModel(container, session, renderer) as T
    }

    private companion object {
        const val PREVIEW_FAILED = "preview_failed"
    }
}
