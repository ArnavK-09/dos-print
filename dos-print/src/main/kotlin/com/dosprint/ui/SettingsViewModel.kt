package com.dosprint.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dosprint.BuildConfig
import com.dosprint.data.render.PreviewBitmapCache
import com.dosprint.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

interface NotificationPermissionChecker {
    fun isGranted(): Boolean
}

class SettingsViewModel(
    private val container: AppContainer,
    private val previewCache: PreviewBitmapCache,
    private val notificationPermissionChecker: NotificationPermissionChecker,
) : ViewModel() {

    private val mutableState = MutableStateFlow(
        SettingsUiState(versionName = BuildConfig.VERSION_NAME),
    )
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    private val preferences = container.preferencesRepository

    init {
        observePreferences()
        observePrinters()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferences.preferences.collect { prefs ->
                mutableState.update { it.copy(preferences = prefs) }
            }
        }
    }

    private fun observePrinters() {
        viewModelScope.launch {
            container.printerRepository.printers.collect { printers ->
                mutableState.update { it.copy(printerCount = printers.size) }
            }
        }
    }

    fun acceptAction(action: SettingsAction) {
        dispatch(action)
    }

    fun dispatch(action: SettingsAction) {
        viewModelScope.launch {
            when (action) {
                is SettingsAction.SelectTheme -> preferences.setTheme(action.theme)
                is SettingsAction.ToggleDynamicColor -> preferences.setDynamicColor(action.enabled)
                is SettingsAction.ToggleContrast -> preferences.setIncreaseContrast(action.enabled)
                is SettingsAction.ToggleLiquidGlass -> preferences.setLiquidGlassEnabled(action.enabled)
                is SettingsAction.SelectDefaultQuality -> preferences.setDefaultQuality(action.resolution)
                is SettingsAction.SelectDefaultColorMode -> preferences.setDefaultColorMode(action.mode)
                is SettingsAction.SetDefaultCopies -> preferences.setDefaultCopies(action.copies)
                is SettingsAction.ToggleNotifications -> {
                    preferences.setNotificationsEnabled(action.enabled)
                    updateNotificationState(action.enabled)
                }
                is SettingsAction.ToggleKeepHistory -> preferences.setKeepJobHistory(action.enabled)
                is SettingsAction.SetHistoryLimit -> preferences.setHistoryLimit(action.limit)
                is SettingsAction.ToggleMonitoring -> preferences.setBackgroundMonitoring(action.enabled)
                is SettingsAction.ToggleLowMemory -> preferences.setLowMemoryMode(action.enabled)
                is SettingsAction.ToggleAutoDiscover -> preferences.setAutoDiscoverPrinters(action.enabled)
                is SettingsAction.SetDefaultPrinter -> preferences.setDefaultPrinterId(action.printerId)
                SettingsAction.ClearPreviewCache -> {
                    previewCache.clear()
                    mutableState.update { it.copy(snackbarMessage = CACHE_CLEARED) }
                }
                SettingsAction.RequestNotificationsPermission ->
                    mutableState.update { it.copy(notificationRequestVisible = true) }
                SettingsAction.ClearSnackbar -> mutableState.update { it.copy(snackbarMessage = null) }
            }
        }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        mutableState.update {
            it.copy(
                notificationRequestVisible = false,
                isNotificationPermissionGranted = granted,
            )
        }
    }

    private fun updateNotificationState(enabled: Boolean) {
        val granted = notificationPermissionChecker.isGranted()
        mutableState.update {
            it.copy(
                isNotificationPermissionGranted = granted,
                notificationRequestVisible = enabled && !granted,
            )
        }
    }

    class Factory(
        private val container: AppContainer,
        private val previewCache: PreviewBitmapCache,
        private val notificationPermissionChecker: NotificationPermissionChecker,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(container, previewCache, notificationPermissionChecker) as T
    }

    private companion object {
        const val CACHE_CLEARED = "cache_cleared"
    }
}
