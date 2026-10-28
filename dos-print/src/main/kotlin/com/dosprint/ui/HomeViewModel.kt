package com.dosprint.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dosprint.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val mutableState = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    init {
        observeHome()
        refresh()
    }

    private fun observeHome() {
        viewModelScope.launch {
            container.documentRepository.favoriteDocuments.collect { documents ->
                mutableState.update { it.copy(favoriteDocuments = documents.take(FAVORITE_LIMIT)) }
            }
        }
        viewModelScope.launch {
            container.printerRepository.printers.collect { printers ->
                mutableState.update { it.copy(printers = printers) }
            }
        }
        viewModelScope.launch {
            container.printerRepository.defaultPrinter.collect { printer ->
                mutableState.update { it.copy(defaultPrinter = printer) }
            }
        }
    }

    fun acceptAction(action: HomeAction) {
        dispatch(action)
    }

    fun dispatch(action: HomeAction) {
        when (action) {
            HomeAction.Refresh -> refresh()
            HomeAction.PickDocuments,
            HomeAction.PickImages,
            HomeAction.OpenPrinters,
            HomeAction.OpenDocuments,
            -> Unit
            is HomeAction.ToggleFavorite -> toggleFavorite(action.uri, action.favorite)
            is HomeAction.DocumentClicked -> Unit
            HomeAction.ClearSnackbar -> clearSnackbar()
        }
    }

    fun refresh() {
        mutableState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val available = container.printerRepository.isPrintServiceAvailable()
            val discovered = if (available) {
                runCatching { container.printerRepository.refreshPrinters() }
                    .getOrDefault(emptyList())
            } else {
                emptyList()
            }
            mutableState.update {
                it.copy(
                    isLoading = false,
                    isPrintServiceAvailable = available,
                    printers = discovered.ifEmpty { it.printers },
                )
            }
        }
    }

    fun toggleFavorite(uri: String, favorite: Boolean) {
        viewModelScope.launch {
            container.documentRepository.setFavorite(uri, favorite)
            mutableState.update { state ->
                state.copy(
                    snackbarMessage = if (favorite) FAVORITE_ADDED else FAVORITE_REMOVED,
                )
            }
        }
    }

    fun clearSnackbar() {
        mutableState.update { it.copy(snackbarMessage = null) }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(container) as T
    }

    private companion object {
        const val FAVORITE_LIMIT = 20
        const val FAVORITE_ADDED = "favorite_added"
        const val FAVORITE_REMOVED = "favorite_removed"
    }
}
