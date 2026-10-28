package com.dosprint.data.local

import com.dosprint.core.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

class JsonFileStore<T : Any>(
    private val file: File,
    private val serializer: kotlinx.serialization.KSerializer<T>,
    private val emptyValue: T,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val state = MutableStateFlow(emptyValue)

    val value: StateFlow<T> get() = state.asStateFlow()

    suspend fun load(): T = withContext(dispatcher) { loadLocked() }

    private suspend fun loadLocked(): T = mutex.withLock {
        if (!file.exists()) {
            state.value = emptyValue
            return@withLock emptyValue
        }
        val loaded = try {
            json.decodeFromString(serializer, file.readText())
        } catch (error: SerializationException) {
            AppLog.error(AppLog.throwableTag("jsonDecode(${file.name})", error))
            quarantine()
            emptyValue
        } catch (error: IOException) {
            AppLog.error(AppLog.throwableTag("jsonRead(${file.name})", error))
            emptyValue
        } catch (error: OutOfMemoryError) {
            AppLog.error("Out of memory decoding ${file.name}", error)
            emptyValue
        }
        state.value = loaded
        loaded
    }

    suspend fun update(transform: (T) -> T): T = withContext(dispatcher) { updateLocked(transform) }

    private suspend fun updateLocked(transform: (T) -> T): T = mutex.withLock {
        val current = if (state.value == emptyValue && file.exists()) {
            try {
                json.decodeFromString(serializer, file.readText())
            } catch (error: Exception) {
                AppLog.error(AppLog.throwableTag("jsonDecode(${file.name})", error))
                emptyValue
            }
        } else {
            state.value
        }
        val updated = transform(current)
        writeAtomically(updated)
        state.value = updated
        updated
    }

    suspend fun reset(): T = withContext(dispatcher) { resetLocked() }

    private suspend fun resetLocked(): T = mutex.withLock {
        deleteQuietly(file)
        state.value = emptyValue
        emptyValue
    }

    private fun writeAtomically(value: T) {
        try {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(json.encodeToString(serializer, value))
            if (file.exists() && !file.delete()) {
                AppLog.warn("Could not delete previous file ${file.name}")
            }
            if (!temporary.renameTo(file)) {
                file.writeText(temporary.readText())
                temporary.delete()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLog.error(AppLog.throwableTag("jsonWrite(${file.name})", error), error)
        }
    }

    private fun quarantine() {
        deleteQuietly(File(file.parentFile, "${file.name}.corrupt"))
        renameQuietly(file, File(file.parentFile, "${file.name}.corrupt"))
    }

    private fun deleteQuietly(target: File) {
        runCatching { if (target.exists()) target.delete() }
            .onFailure { AppLog.warn(AppLog.throwableTag("delete(${target.name})", it)) }
    }

    private fun renameQuietly(source: File, target: File) {
        runCatching { source.renameTo(target) }
            .onFailure { AppLog.warn(AppLog.throwableTag("rename(${source.name})", it)) }
    }
}
