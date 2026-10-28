package com.dosprint.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dosprint.core.AppLog
import com.dosprint.core.ErrorReporter
import com.dosprint.di.AppContainer
import com.dosprint.domain.model.PrintDocument
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DocumentsViewModel(
    private val container: AppContainer,
    private val session: SessionStore,
) : ViewModel() {

    private val mutableState = MutableStateFlow(DocumentsUiState())
    val state: StateFlow<DocumentsUiState> = mutableState.asStateFlow()

    private val importer = DocumentImporter(
        describe = { uri -> container.documentRepository.describe(uri) },
        pageCountOf = { uri -> container.documentRepository.pageCount(uri) },
        persistPermission = { uri -> container.documentRepository.persistPermission(uri) },
        remember = { document -> container.documentRepository.addRecent(document) },
    )

    init {
        observeRecents()
        viewModelScope.launch { container.documentRepository.load() }
    }

    private fun observeRecents() {
        viewModelScope.launch {
            container.documentRepository.recentDocuments.collect { documents ->
                mutableState.update { it.copy(documents = documents.take(RECENT_LIMIT), isLoading = false) }
            }
        }
    }

    fun reload() {
        mutableState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            container.documentRepository.load()
            mutableState.update { it.copy(isLoading = false) }
        }
    }

    fun acceptAction(action: DocumentsAction) {
        dispatch(action)
    }

    fun dispatch(action: DocumentsAction) {
        when (action) {
            DocumentsAction.BrowseFiles, DocumentsAction.LoadRecents -> Unit
            is DocumentsAction.Search -> search(action.query)
            is DocumentsAction.Filter -> filter(action.filter)
            is DocumentsAction.ToggleSelection -> toggleSelection(action.uri)
            DocumentsAction.SelectAllVisible -> selectAllVisible()
            DocumentsAction.ClearSelection -> clearSelection()
            is DocumentsAction.ToggleFavorite -> toggleFavorite(action.uri)
            DocumentsAction.Continue -> commitSelection()
            DocumentsAction.ClearSnackbar -> clearSnackbar()
            DocumentsAction.OpenLogs -> Unit
            DocumentsAction.ClearError -> mutableState.update { it.copy(errorMessage = null, failedUris = emptyList()) }
        }
    }

    /**
     * Emits once per successful import.
     *
     * Picking files is only half a task: the user still has to set up the job and print it. The
     * signal lets the shell move them on to Setup instead of leaving them on an empty Home screen
     * wondering what happens next.
     */
    private val _imported = MutableSharedFlow<ImportEvent>(extraBufferCapacity = 1)
    val imported: SharedFlow<ImportEvent> = _imported.asSharedFlow()

    private val _importFailures = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val importFailures: SharedFlow<Int> = _importFailures.asSharedFlow()

    fun onImported(uris: List<String>, asImages: Boolean) {
        if (uris.isEmpty()) {
            return
        }
        AppLog.info("documents: picker returned ${uris.size} uri(s) images=$asImages")
        viewModelScope.launch {
            val result = importer.import(uris)
            result.failedUris.forEach { uri ->
                ErrorReporter.report(AREA_IMPORT, IllegalStateException("unreadable: $uri"))
            }
            if (result.importedCount == 0) {
                mutableState.update {
                    it.copy(errorMessage = IMPORT_FAILED, failedUris = result.failedUris)
                }
                _importFailures.tryEmit(result.failedCount)
                return@launch
            }
            session.setDocuments(result.documents)
            AppLog.info("documents: session now holds ${result.documents.size} new document(s)")
            _imported.tryEmit(
                ImportEvent(
                    count = result.importedCount,
                    asImages = asImages,
                    failed = result.failedCount,
                ),
            )
            mutableState.update { current ->
                current.copy(
                    selectedUris = result.documents.mapTo(mutableSetOf()) { it.uri },
                    snackbarMessage = if (result.isCompleteSuccess) IMPORT_DONE else IMPORT_PARTIAL,
                    errorMessage = null,
                    failedUris = result.failedUris,
                )
            }
        }
    }

    fun search(query: String) {
        mutableState.update { it.copy(query = query) }
    }

    fun filter(filter: DocumentFilter) {
        mutableState.update { it.copy(filter = filter) }
    }

    fun toggleSelection(uri: String) {
        mutableState.update { current ->
            val next = if (uri in current.selectedUris) {
                current.selectedUris - uri
            } else {
                current.selectedUris + uri
            }
            current.copy(selectedUris = next)
        }
    }

    fun selectAllVisible() {
        mutableState.update { current ->
            val visible = current.visibleDocuments.map { it.uri }.toSet()
            val next = if (current.allVisibleSelected) {
                current.selectedUris - visible
            } else {
                current.selectedUris + visible
            }
            current.copy(selectedUris = next)
        }
    }

    fun clearSelection() {
        mutableState.update { it.copy(selectedUris = emptySet()) }
    }

    fun toggleFavorite(uri: String) {
        viewModelScope.launch {
            val document = mutableState.value.documents.firstOrNull { it.uri == uri }
            val next = !(document?.isFavorite ?: false)
            container.documentRepository.setFavorite(uri, next)
            mutableState.update {
                it.copy(snackbarMessage = if (next) FAVORITE_ADDED else FAVORITE_REMOVED)
            }
        }
    }

    fun commitSelection() {
        val selected = mutableState.value.selectedDocuments
        if (selected.isEmpty()) {
            return
        }
        session.setDocuments(selected)
        viewModelScope.launch {
            val enriched = selected.map { document ->
                val pageCount = document.detectedPageCount
                    ?: container.documentRepository.pageCount(document.uri)
                if (pageCount == null) document else document.copy(detectedPageCount = pageCount)
            }
            session.setDocuments(enriched)
            mutableState.update { it.copy(selectedUris = emptySet(), snackbarMessage = null) }
        }
    }

    fun clearSnackbar() {
        mutableState.update { it.copy(snackbarMessage = null) }
    }

    class Factory(
        private val container: AppContainer,
        private val session: SessionStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DocumentsViewModel(container, session) as T
    }

    internal companion object {
        const val RECENT_LIMIT = 100
        const val IMPORT_DONE = "import_done"
        const val IMPORT_PARTIAL = "import_partial"
        const val IMPORT_FAILED = "import_failed"
        const val AREA_IMPORT = "documentImport"
        const val AREA_PERSIST = "documentPermission"
        const val AREA_RECENT = "documentRecent"
        const val FAVORITE_ADDED = "favorite_added"
        const val FAVORITE_REMOVED = "favorite_removed"
    }
}

internal fun List<PrintDocument>.namesOrEmpty(): List<String> = map { it.displayName }

/** Emitted after a successful import so the shell knows which flow to open. */
data class ImportEvent(val count: Int, val asImages: Boolean, val failed: Int = 0)
