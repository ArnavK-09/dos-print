package com.dosprint.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dosprint.core.AppLog
import com.dosprint.domain.model.ColorMode
import com.dosprint.domain.model.PrintResolution
import com.dosprint.domain.model.PrintSettings
import com.dosprint.domain.model.ThemeOption
import com.dosprint.domain.model.UserPreferences
import com.dosprint.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.preferencesStore: DataStore<Preferences> by preferencesDataStore(name = "dos_print_settings")

class PreferencesRepositoryImpl(
    private val context: Context,
) : PreferencesRepository {

    override val preferences: Flow<UserPreferences> = context.preferencesStore.data
        .catch { error ->
            if (error is IOException) {
                AppLog.error(AppLog.throwableTag("preferencesRead", error))
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map(::toUserPreferences)

    override suspend fun setTheme(theme: ThemeOption) = put(Keys.theme, theme.name)

    override suspend fun setDynamicColor(enabled: Boolean) = put(Keys.dynamicColor, enabled)

    override suspend fun setIncreaseContrast(enabled: Boolean) = put(Keys.increaseContrast, enabled)

    override suspend fun setLiquidGlassEnabled(enabled: Boolean) = put(Keys.liquidGlassEnabled, enabled)

    override suspend fun setDefaultQuality(resolution: PrintResolution) = put(Keys.defaultQuality, resolution.name)

    override suspend fun setDefaultColorMode(mode: ColorMode) = put(Keys.defaultColorMode, mode.name)

    override suspend fun setDefaultCopies(copies: Int) = put(
        Keys.defaultCopies,
        copies.coerceIn(PrintSettings.MIN_COPIES, PrintSettings.MAX_COPIES),
    )

    override suspend fun setDefaultPaperSizeId(id: String) = put(Keys.defaultPaperSize, id)

    override suspend fun setNotificationsEnabled(enabled: Boolean) = put(Keys.notifications, enabled)

    override suspend fun setKeepJobHistory(enabled: Boolean) = put(Keys.keepHistory, enabled)

    override suspend fun setHistoryLimit(limit: Int) = put(
        Keys.historyLimit,
        limit.coerceIn(UserPreferences.MIN_HISTORY_LIMIT, UserPreferences.MAX_HISTORY_LIMIT),
    )

    override suspend fun setBackgroundMonitoring(enabled: Boolean) = put(Keys.monitoring, enabled)

    override suspend fun setLowMemoryMode(enabled: Boolean) = put(Keys.lowMemory, enabled)

    override suspend fun setAutoDiscoverPrinters(enabled: Boolean) = put(Keys.autoDiscover, enabled)

    override suspend fun setAdvancedSetupEnabled(enabled: Boolean) = put(Keys.advancedSetup, enabled)

    override suspend fun setDefaultPrinterId(printerId: String?) {
        try {
            context.preferencesStore.edit { preferences ->
                if (printerId == null) {
                    preferences.remove(Keys.defaultPrinter)
                } else {
                    preferences[Keys.defaultPrinter] = printerId
                }
            }
        } catch (error: IOException) {
            AppLog.error(AppLog.throwableTag("setDefaultPrinterId", error))
        }
    }

    override suspend fun setLastSettings(settings: PrintSettings) {
        put(Keys.lastSettings, PrintSettingsCodec.encode(settings))
    }

    override suspend fun loadLastSettings(): PrintSettings? {
        val encoded = try {
            context.preferencesStore.data.first()[Keys.lastSettings]
        } catch (error: IOException) {
            AppLog.error(AppLog.throwableTag("preferencesReadLastSettings", error))
            null
        } ?: return null
        return PrintSettingsCodec.decode(encoded)
    }

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        try {
            context.preferencesStore.edit { preferences -> preferences[key] = value }
        } catch (error: IOException) {
            AppLog.error(AppLog.throwableTag("preferencesWrite", error))
        }
    }

    private fun toUserPreferences(preferences: Preferences): UserPreferences {
        val defaults = UserPreferences()
        return UserPreferences(
            theme = preferences.readEnum(Keys.theme, defaults.theme),
            useDynamicColor = preferences[Keys.dynamicColor] ?: defaults.useDynamicColor,
            increaseContrast = preferences[Keys.increaseContrast] ?: defaults.increaseContrast,
            liquidGlassEnabled = preferences[Keys.liquidGlassEnabled] ?: defaults.liquidGlassEnabled,
            defaultQuality = preferences.readEnum(Keys.defaultQuality, defaults.defaultQuality),
            defaultColorMode = preferences.readEnum(Keys.defaultColorMode, defaults.defaultColorMode),
            defaultCopies = preferences[Keys.defaultCopies] ?: defaults.defaultCopies,
            defaultPaperSizeId = preferences[Keys.defaultPaperSize] ?: defaults.defaultPaperSizeId,
            notificationsEnabled = preferences[Keys.notifications] ?: defaults.notificationsEnabled,
            keepJobHistory = preferences[Keys.keepHistory] ?: defaults.keepJobHistory,
            historyLimit = preferences[Keys.historyLimit] ?: defaults.historyLimit,
            backgroundMonitoring = preferences[Keys.monitoring] ?: defaults.backgroundMonitoring,
            lowMemoryMode = preferences[Keys.lowMemory] ?: defaults.lowMemoryMode,
            autoDiscoverPrinters = preferences[Keys.autoDiscover] ?: defaults.autoDiscoverPrinters,
            defaultPrinterId = preferences[Keys.defaultPrinter],
            advancedSetupEnabled = preferences[Keys.advancedSetup] ?: defaults.advancedSetupEnabled,
        )
    }

    private inline fun <reified T : Enum<T>> Preferences.readEnum(key: Preferences.Key<String>, fallback: T): T {
        val stored = this[key] ?: return fallback
        return runCatching { enumValueOf<T>(stored) }.getOrDefault(fallback)
    }

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val increaseContrast = booleanPreferencesKey("increase_contrast")
        val liquidGlassEnabled = booleanPreferencesKey("liquid_glass_enabled")
        val defaultQuality = stringPreferencesKey("default_quality")
        val defaultColorMode = stringPreferencesKey("default_color_mode")
        val defaultCopies = intPreferencesKey("default_copies")
        val defaultPaperSize = stringPreferencesKey("default_paper_size")
        val notifications = booleanPreferencesKey("notifications_enabled")
        val keepHistory = booleanPreferencesKey("keep_history")
        val historyLimit = intPreferencesKey("history_limit")
        val monitoring = booleanPreferencesKey("background_monitoring")
        val lowMemory = booleanPreferencesKey("low_memory_mode")
        val autoDiscover = booleanPreferencesKey("auto_discover")
        val defaultPrinter = stringPreferencesKey("default_printer")
        val advancedSetup = booleanPreferencesKey("advanced_setup")
        val lastSettings = stringPreferencesKey("last_settings")
    }
}
