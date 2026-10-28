package com.dosprint.data.presets

import com.dosprint.core.AppLog
import com.dosprint.data.local.JsonFileStore
import com.dosprint.data.local.PresetFile
import com.dosprint.data.local.StoredPreset
import com.dosprint.data.preferences.PrintSettingsCodec
import com.dosprint.domain.model.PrintPreset
import com.dosprint.domain.model.PrintSettings
import com.dosprint.domain.repository.PresetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PresetRepositoryImpl(
    private val store: JsonFileStore<PresetFile>,
) : PresetRepository {

    override val presets: Flow<List<PrintPreset>> = store.value.map { file ->
        file.presets.map { it.toPreset() }.sortedBy { it.name.lowercase() }
    }

    override suspend fun load() {
        store.load()
    }

    override suspend fun upsert(preset: PrintPreset) {
        store.update { file ->
            val stored = preset.toStored()
            val merged = file.presets.filterNot { it.id == preset.id } + stored
            file.copy(presets = merged.take(MAX_PRESETS))
        }
    }

    override suspend fun delete(presetId: String) {
        store.update { file -> file.copy(presets = file.presets.filterNot { it.id == presetId }) }
    }

    override suspend fun markUsed(presetId: String) {
        store.update { file ->
            file.copy(
                presets = file.presets.map { stored ->
                    if (stored.id == presetId) {
                        stored.copy(
                            lastUsedAtMillis = System.currentTimeMillis(),
                            usageCount = stored.usageCount + 1,
                        )
                    } else {
                        stored
                    }
                },
            )
        }
    }

    override suspend fun findById(presetId: String): PrintPreset? =
        store.value.value.presets.firstOrNull { it.id == presetId }?.toPreset()

    private fun PrintPreset.toStored(): StoredPreset = StoredPreset(
        id = id,
        name = name,
        encodedSettings = PrintSettingsCodec.encode(settings),
        createdAtMillis = createdAtMillis,
        lastUsedAtMillis = lastUsedAtMillis,
        usageCount = usageCount,
    )

    private fun StoredPreset.toPreset(): PrintPreset {
        val decoded = PrintSettingsCodec.decode(encodedSettings)
        if (decoded == null) {
            AppLog.warn("Preset $id has unreadable settings; falling back to defaults")
        }
        return PrintPreset(
            id = id,
            name = name,
            settings = decoded ?: PrintSettings(),
            createdAtMillis = createdAtMillis,
            lastUsedAtMillis = lastUsedAtMillis,
            usageCount = usageCount,
        )
    }

    private companion object {
        const val MAX_PRESETS = 100
    }
}
