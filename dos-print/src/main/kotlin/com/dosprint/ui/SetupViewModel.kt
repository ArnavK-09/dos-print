package com.dosprint.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dosprint.core.AppLog
import com.dosprint.core.ErrorReporter
import com.dosprint.core.Outcome
import com.dosprint.di.AppContainer
import com.dosprint.domain.model.ImagePageSettings
import com.dosprint.domain.model.PageRange
import com.dosprint.domain.model.PaperChoices
import com.dosprint.domain.model.PdfDestinationCapabilities
import com.dosprint.domain.model.PrintDocument
import com.dosprint.domain.model.PrintSettings
import com.dosprint.domain.model.PrintSettingsResolver
import com.dosprint.domain.model.PrinterProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class SetupViewModel(
    private val container: AppContainer,
    private val session: SessionStore,
    private val dispatchers: com.dosprint.core.DispatcherProvider = com.dosprint.core.DefaultDispatchers,
) : ViewModel() {

    private val mutableState = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = mutableState.asStateFlow()

    private val appContext = container.applicationContext

    init {
        observeSelection()
        loadDefaults()
    }

    private var previewJob: Job? = null
    private var previewWidthPx = 0

    /**
     * Keeps a live sheet render next to the settings so the result of every change is visible
     * without navigating away. Rendering is debounced and re-triggered only when something that
     * actually affects the output changes.
     */
    /** Switches between the document and photo flows, keeping the shared print settings. */
    fun selectMode(mode: PrintMode) {
        mutableState.update { current ->
            val resolved = PrintSettingsResolver.resolve(current.settings, current.capabilities, current.totalPages)
            current.copy(
                mode = mode,
                livePreview = current.livePreview.copy(
                    sheetCount = container.sheetPreviewRenderer.sheetCountFor(current.documents, resolved),
                ),
            )
        }
        schedulePreview()
    }

    fun onPreviewSurface(widthPx: Int) {
        if (widthPx <= 0) {
            return
        }
        if (previewWidthPx == widthPx) {
            return
        }
        previewWidthPx = widthPx
        renderPreview()
    }

    /**
     * Renders the first sheet off the main thread and publishes it.
     *
     * Three things this must get right, each learned from a bug:
     *
     * - **It cannot run on the main thread.** Parsing a document and rasterising a sheet is heavy;
     *   doing it inline froze the UI.
     * - **It cannot throw.** An escaping exception kills the coroutine silently, so the spinner
     *   never clears and no error is ever shown. Every failure is caught, reported and surfaced.
     * - **It must not recycle the outgoing bitmap.** Compose may still be drawing it, and drawing a
     *   recycled bitmap crashes the process. Dropping the reference lets the collector take it.
     *
     * State is read *inside* the coroutine rather than captured, so a render that starts after a
     * settings change renders the new settings.
     */
    private fun renderPreview() {
        // The measured width can legitimately be zero before the panel has laid out, and returning
        // there meant a job with documents showed nothing at all with no explanation. Fall back to a
        // sensible preview width so a render is always attempted once there is something to render.
        val width = previewWidthPx.takeIf { it > 0 } ?: FallbackPreviewWidth
        previewJob?.cancel()
        mutableState.update {
            it.copy(livePreview = it.livePreview.copy(isRendering = true, errorMessage = null))
        }
        previewJob = viewModelScope.launch {
            delay(PreviewDebounceMillis)
            val snapshot = mutableState.value
            if (snapshot.documents.isEmpty()) {
                mutableState.update {
                    it.copy(
                        previewError = NO_DOCUMENTS,
                        livePreview = LivePreviewState(sheetCount = it.livePreview.sheetCount),
                    )
                }
                return@launch
            }
            val renderSettings = PrintSettingsResolver.resolve(
                snapshot.settings,
                snapshot.capabilities,
                snapshot.totalPages,
            )
            AppLog.info("preview: render start docs=${snapshot.documents.size} width=$width")
            val startedAt = System.currentTimeMillis()
            val outcome = runCatching {
                withContext(dispatchers.io) {
                    container.sheetPreviewRenderer.renderSheets(
                        documents = snapshot.documents,
                        settings = renderSettings,
                        maxSheets = PreviewSheetLimit,
                        targetWidthPx = width,
                    )
                }
            }.onFailure { ErrorReporter.report(AREA_PREVIEW_RENDER, it) }.getOrNull()
            AppLog.info(
                "preview: render " + when (outcome) {
                    null -> "threw"
                    is Outcome.Failure -> "failed"
                    is Outcome.Success -> "ok sheets=${outcome.value.size}"
                } + " in ${System.currentTimeMillis() - startedAt} ms",
            )
            mutableState.update { state ->
                when (outcome) {
                    null -> state.copy(
                        livePreview = state.livePreview.copy(isRendering = false, errorMessage = PREVIEW_FAILED),
                        previewError = PREVIEW_FAILED,
                    )
                    is Outcome.Failure -> state.copy(
                        livePreview = state.livePreview.copy(
                            isRendering = false,
                            errorMessage = PREVIEW_FAILED,
                        ),
                        previewError = PREVIEW_FAILED,
                    )
                    is Outcome.Success -> state.copy(
                        previewError = null,
                        livePreview = LivePreviewState(
                            isRendering = false,
                            sheetCount = state.livePreview.sheetCount,
                            aspectRatio = renderSettings.sheetAspect,
                            image = outcome.value.firstOrNull(),
                            hasContent = outcome.value.isNotEmpty(),
                        ),
                    )
                }
            }
        }
    }

    private fun schedulePreview() {
        previewJob?.cancel()
        mutableState.update { it.copy(livePreview = it.livePreview.copy(isRendering = true)) }
        previewJob = viewModelScope.launch {
            delay(PreviewDebounceMillis)
            renderPreview()
        }
    }

    private fun observeSelection() {
        viewModelScope.launch {
            session.selection.collect { selection ->
                val total = selection.documents.sumOf { it.detectedPageCount ?: 1 }
                AppLog.info("setup: selection docs=${selection.documents.size} pages=$total printer=${selection.printer?.displayName}")
                mutableState.update { current ->
                    val available = availablePaperSizes(selection.capabilities, selection.settings.paperSize)
                    val validation = SettingsValidator.validate(
                        context = appContext,
                        requested = selection.settings,
                        capabilities = selection.capabilities,
                        totalPages = total,
                    )
                    current.copy(
                        selection = selection,
                        resolvedSettings = validation.settings,
                        availablePaperSizes = available,
                        paperChoices = PaperChoices.forPrinter(selection.capabilities, validation.settings.paperSize),
                        totalPages = total,
                        selectedPages = SettingsValidator.selectedPageCount(validation.settings, total),
                        warnings = validation.warnings,
                        pageRangeError = validation.pageRangeError,
                        livePreview = current.livePreview.copy(
                            sheetCount = container.sheetPreviewRenderer.sheetCountFor(
                                selection.documents,
                                validation.settings,
                            ),
                        ),
                    )
                }
                schedulePreview()
            }
        }
    }

    private fun loadDefaults() {
        viewModelScope.launch {
            val preferences = container.preferencesRepository.preferences.first()
            session.updateSettings { preferences.defaultsForSettings() }
            if (session.selection.value.printer == null) {
                selectPrinter(com.dosprint.domain.model.PrinterProfile.PdfDestination)
            }
        }
    }

    fun dispatch(action: SetupAction) {
        when (action) {
            is SetupAction.SelectPrinter -> selectPrinter(action.printer)
            is SetupAction.SelectPaperSize -> mutateSettings { it.copy(paperSize = action.paperSize) }
            is SetupAction.ToggleLandscape -> mutateSettings { it.copy(landscape = action.landscape) }
            is SetupAction.SelectColorMode -> mutateSettings { it.copy(colorMode = action.mode) }
            is SetupAction.SelectResolution -> mutateSettings { it.copy(resolution = action.resolution) }
            is SetupAction.SelectScaling -> mutateSettings { it.copy(scaling = action.scaling, scalePercent = 100) }
            is SetupAction.SelectMarginPreset -> mutateSettings { it.copy(marginPreset = action.preset) }
            is SetupAction.SelectPageSelection ->
                mutateSettings { it.copy(pageSelection = action.mode) }

            is SetupAction.SetPageSegments ->
                mutateSettings { it.copy(pageSegments = action.segments) }

            is SetupAction.ToggleReversePageOrder -> mutateSettings { it.copy(reversePageOrder = action.reverse) }

            is SetupAction.SelectPosition -> mutateSettings { it.copy(position = action.position) }

            is SetupAction.SelectPageScaling -> mutateSettings { it.copy(pageScaling = action.scaling) }

            is SetupAction.SetScalePercent ->
                mutateSettings { it.copy(scalePercent = action.percent.coerceIn(MIN_SCALE_PERCENT, MAX_SCALE_PERCENT)) }

            is SetupAction.SelectMargins -> mutateSettings { it.copy(margins = action.margins) }

            is SetupAction.SelectManualDuplex -> mutateSettings { it.copy(manualDuplex = action.duplex) }

            is SetupAction.SelectDuplex -> mutateSettings { it.copy(duplex = action.duplex) }
            is SetupAction.ToggleCollate -> mutateSettings { it.copy(collate = action.collate) }
            is SetupAction.SetCopies -> mutateSettings { it.copy(copies = action.copies.coerceIn(MIN_COPIES, MAX_COPIES)) }
            is SetupAction.SelectSheetLayout -> mutateSettings { it.copy(pagesPerSheet = action.layout.count) }
            is SetupAction.SelectPagesPerSheet ->
                mutateSettings { it.copy(pagesPerSheet = action.count.coerceIn(1, MaxPagesPerSheet)) }
            is SetupAction.SelectPageOrder -> mutateSettings { it.copy(pageOrder = action.order) }
            is SetupAction.SelectSides -> mutateSettings { it.copy(sides = action.sides) }
            is SetupAction.SelectSheetsPerBooklet -> mutateSettings { it.copy(sheetsPerBooklet = action.sheets) }
            is SetupAction.SetGridGutter -> mutateSettings { it.copy(gridGutterFraction = action.fraction) }
            is SetupAction.SetGridMargin -> mutateSettings { it.copy(gridMarginFraction = action.fraction) }
            is SetupAction.SetCellBorder -> mutateSettings { it.copy(cellBorderFraction = action.fraction) }
            is SetupAction.ApplyLayoutPreset -> applyLayoutPreset(action.preset)
            is SetupAction.ToggleAllPages -> toggleAllPages(action.allPages)
            SetupAction.ClearPreviewError -> mutableState.update { it.copy(previewError = null) }
            SetupAction.RetryPreview -> renderPreview()
            SetupAction.OpenLogs -> Unit
            is SetupAction.PageRangeInputChanged -> updatePageRangeInput(action.input)
            SetupAction.ApplyPageRange -> applyPageRange()
            is SetupAction.SetWatermark -> mutateSettings { it.copy(watermark = action.style) }
            is SetupAction.SetWatermarkText -> mutateSettings { it.copy(watermarkText = action.text) }
            is SetupAction.ToggleBorderless -> mutateSettings { it.copy(borderless = action.borderless) }
            is SetupAction.Rotate -> mutateSettings { it.copy(rotation = action.rotation) }
            SetupAction.OpenPreview -> Unit
            SetupAction.Print -> submit()
            SetupAction.ClearSnackbar -> mutableState.update { it.copy(snackbarMessage = null) }
            SetupAction.DismissSubmitError -> mutableState.update { it.copy(submitError = null) }
        }
    }

    private fun toggleAllPages(allPages: Boolean) {
        if (allPages) {
            mutateSettings { it.copy(pageRange = PageRange.All) }
        }
    }

    private fun updatePageRangeInput(input: String) {
        mutableState.update { it.copy(pageRangeInput = input) }
    }

    private fun selectPrinter(printer: PrinterProfile) {
        viewModelScope.launch {
            val capabilities = when (val outcome = container.printerRepository.capabilitiesFor(printer.id)) {
                is Outcome.Success -> outcome.value
                is Outcome.Failure -> com.dosprint.domain.model.PrinterCapabilities.Unknown.copy(printerId = printer.id)
            }
            session.setPrinter(printer, capabilities)
            mutableState.update { it.copy(snackbarMessage = PRINTER_SELECTED) }
        }
    }

    private fun applyPageRange() {
        val current = mutableState.value
        val (range, error) = SettingsValidator.parsePageRange(
            context = appContext,
            input = current.pageRangeInput,
            totalPages = current.totalPages,
        )
        if (error != null) {
            mutableState.update { it.copy(pageRangeError = error) }
            return
        }
        mutableState.update { it.copy(pageRangeError = null) }
        mutateSettings { it.copy(pageRange = range) }
    }

    private fun applyLayoutPreset(preset: com.dosprint.domain.model.LayoutPreset) {
        val current = mutableState.value
        val paper = com.dosprint.domain.model.PaperSize.byId(preset.paperId)
        session.updateSettings { settings ->
            preset.appliesTo(settings).let { resolved ->
                if (paper != null) resolved.copy(paperSize = paper) else resolved
            }
        }
        mutableState.update { it.copy(snackbarMessage = LAYOUT_PRESET_APPLIED) }
        if (preset.category == com.dosprint.domain.model.LayoutCategory.PHOTO ||
            preset.category == com.dosprint.domain.model.LayoutCategory.LABELS
        ) {
            mutableState.update { it.copy(snackbarMessage = LAYOUT_PRESET_APPLIED) }
        }
    }

    fun submit() {
        val current = mutableState.value
        if (current.pageRangeError != null) {
            mutableState.update { it.copy(submitError = it.pageRangeError) }
            return
        }
        val printer = current.printer ?: PrinterProfile.PdfDestination
        if (current.printer == null) {
            session.setPrinter(printer, PdfDestinationCapabilities)
        }
        if (current.documents.isEmpty()) {
            mutableState.update { it.copy(submitError = NO_DOCUMENT) }
            return
        }
        mutableState.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            val capabilities = when (val outcome = container.printerRepository.capabilitiesFor(printer.id)) {
                is Outcome.Success -> outcome.value
                is Outcome.Failure -> com.dosprint.domain.model.PrinterCapabilities.Unknown.copy(printerId = printer.id)
            }
            val documents: List<PrintDocument> = current.documents
            val result = container.printRepository.submit(
                documents = documents,
                printer = printer,
                capabilities = capabilities,
                settings = current.settings,
                jobId = UUID.randomUUID().toString(),
            )
            mutableState.update { state ->
                when (result) {
                    is Outcome.Success -> state.copy(isSubmitting = false, snackbarMessage = JOB_SUBMITTED)
                    is Outcome.Failure -> state.copy(isSubmitting = false, submitError = SUBMIT_FAILED)
                }
            }
            container.preferencesRepository.setLastSettings(current.settings)
        }
    }

    fun currentSettings(): PrintSettings = mutableState.value.settings

    fun totalPageCount(): Int = mutableState.value.totalPages

    fun selectedPageCount(): Int = mutableState.value.selectedPages

    fun documents(): List<PrintDocument> = mutableState.value.documents

    fun printerProfile(): PrinterProfile? = mutableState.value.printer

    private fun mutateSettings(transform: (PrintSettings) -> PrintSettings) {
        session.updateSettings(transform)
        schedulePreview()
    }

    fun updateImagePage(transform: (ImagePageSettings) -> ImagePageSettings) {
        mutateSettings { settings -> settings.copy(imagePageSettings = transform(settings.imagePageSettings)) }
    }

    /** Replaces the whole photo layout, for callers that already built the next value. */
    fun setImagePage(next: ImagePageSettings) {
        mutateSettings { it.copy(imagePageSettings = next) }
    }

    private fun availablePaperSizes(
        capabilities: com.dosprint.domain.model.PrinterCapabilities,
        current: com.dosprint.domain.model.PaperSize,
    ): List<com.dosprint.domain.model.PaperSize> {
        if (capabilities.unknown || capabilities.supportedPaperSizes.isEmpty()) {
            return com.dosprint.domain.model.PaperSize.COMMON
        }
        val sizes = capabilities.supportedPaperSizes.toMutableList()
        if (sizes.none { it.matches(current) }) {
            sizes += current
        }
        return sizes
    }

    class Factory(
        private val container: AppContainer,
        private val session: SessionStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SetupViewModel(container, session) as T
    }

    internal companion object {
        const val MIN_COPIES = PrintSettings.MIN_COPIES
        const val MIN_SCALE_PERCENT = 10
        const val MAX_SCALE_PERCENT = 400
        const val MAX_COPIES = PrintSettings.MAX_COPIES
        const val MaxPagesPerSheet = com.dosprint.domain.model.LayoutGrid.MAX_SAFE_SLOTS
        const val LAYOUT_PRESET_APPLIED = "layout_preset_applied"
        const val PreviewDebounceMillis = 220L
        const val PreviewSheetLimit = 1
        const val PREVIEW_FAILED = "preview_failed"
        const val AREA_PREVIEW_RENDER = "previewRender"
        const val NO_DOCUMENTS = "no_document"
        const val FallbackPreviewWidth = 900
        const val PRINTER_SELECTED = "printer_selected"
        const val JOB_SUBMITTED = "job_submitted"
        const val SUBMIT_FAILED = "submit_failed"
        const val NO_DOCUMENT = "no_document"
    }
}

internal fun logSubmitFailure(error: Throwable) {
    AppLog.warn(AppLog.throwableTag("submitPrint", error))
}
