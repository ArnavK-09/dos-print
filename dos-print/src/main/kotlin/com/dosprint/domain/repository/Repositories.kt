package com.dosprint.domain.repository

import com.dosprint.domain.model.PrintDocument
import com.dosprint.domain.model.PrintJob
import com.dosprint.domain.model.PrintPreset
import com.dosprint.domain.model.PrintSettings
import com.dosprint.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setTheme(theme: com.dosprint.domain.model.ThemeOption)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setIncreaseContrast(enabled: Boolean)

    suspend fun setDefaultQuality(resolution: com.dosprint.domain.model.PrintResolution)

    suspend fun setDefaultColorMode(mode: com.dosprint.domain.model.ColorMode)

    suspend fun setDefaultCopies(copies: Int)

    suspend fun setDefaultPaperSizeId(id: String)

    suspend fun setNotificationsEnabled(enabled: Boolean)

    suspend fun setKeepJobHistory(enabled: Boolean)

    suspend fun setHistoryLimit(limit: Int)

    suspend fun setBackgroundMonitoring(enabled: Boolean)

    suspend fun setLowMemoryMode(enabled: Boolean)

    suspend fun setAutoDiscoverPrinters(enabled: Boolean)

    suspend fun setAdvancedSetupEnabled(enabled: Boolean)

    suspend fun setDefaultPrinterId(printerId: String?)

    suspend fun setLastSettings(settings: PrintSettings)

    suspend fun loadLastSettings(): PrintSettings?
}

interface DocumentRepository {
    val recentDocuments: Flow<List<PrintDocument>>

    val favoriteDocuments: Flow<List<PrintDocument>>

    suspend fun load()

    suspend fun describe(uri: String): PrintDocument?

    suspend fun describeAll(uris: List<String>): List<PrintDocument>

    suspend fun resolveContentUri(uris: List<String>): List<String>

    suspend fun addRecent(document: PrintDocument)

    suspend fun touchPrinted(documentUris: List<String>)

    suspend fun setFavorite(uri: String, favorite: Boolean)

    suspend fun removeRecent(uri: String)

    suspend fun clearRecents()

    suspend fun persistPermission(uri: String): Boolean

    suspend fun hasPermission(uri: String): Boolean

    suspend fun validate(uri: String): Boolean

    suspend fun pageCount(uri: String): Int?
}

interface HistoryRepository {
    val jobs: Flow<List<PrintJob>>

    suspend fun load()

    suspend fun upsert(job: PrintJob)

    suspend fun delete(jobId: String)

    suspend fun clear()

    suspend fun find(jobId: String): PrintJob?

    suspend fun jobsSnapshot(): List<PrintJob>
}

interface PresetRepository {
    val presets: Flow<List<PrintPreset>>

    suspend fun load()

    suspend fun upsert(preset: PrintPreset)

    suspend fun delete(presetId: String)

    suspend fun markUsed(presetId: String)

    suspend fun findById(presetId: String): PrintPreset?
}
