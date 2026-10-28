package com.dosprint.data.documents

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.dosprint.core.AppLog
import com.dosprint.core.DispatcherProvider
import com.dosprint.data.local.DocumentFile
import com.dosprint.data.local.JsonFileStore
import com.dosprint.data.local.StoredDocument
import com.dosprint.domain.model.DocumentAvailability
import com.dosprint.domain.model.DocumentFormat
import com.dosprint.domain.model.DocumentKind
import com.dosprint.domain.model.PrintDocument
import com.dosprint.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class DocumentRepositoryImpl(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val pageCounter: DocumentPageCounter,
    private val store: JsonFileStore<DocumentFile>,
) : DocumentRepository {

    override val recentDocuments: Flow<List<PrintDocument>> =
        store.value.map { file -> file.documents.toPrintDocuments() }

    override val favoriteDocuments: Flow<List<PrintDocument>> =
        store.value.map { file -> file.documents.filter { it.isFavorite }.toPrintDocuments() }

    init {
        AppLog.info("Document repository ready")
    }

    override suspend fun load() {
        store.load()
    }

    override suspend fun describe(uri: String): PrintDocument? = withContext(dispatchers.io) {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return@withContext null
        val metadata = readMetadata(parsed)
        buildDocument(parsed, metadata)
    }

    override suspend fun describeAll(uris: List<String>): List<PrintDocument> =
        withContext(dispatchers.io) {
            uris.mapNotNull { raw ->
                val parsed = runCatching { Uri.parse(raw) }.getOrNull() ?: return@mapNotNull null
                val metadata = readMetadata(parsed)
                buildDocument(parsed, metadata)
            }
        }

    override suspend fun resolveContentUri(uris: List<String>): List<String> = withContext(dispatchers.io) {
        uris.mapNotNull { raw ->
            val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return@mapNotNull null
            when (uri.scheme) {
                ContentResolverSchemes.CONTENT -> raw
                ContentResolverSchemes.FILE -> resolveLegacyPath(uri)
                else -> null
            }
        }
    }

    override suspend fun addRecent(document: PrintDocument) {
        store.update { file ->
            val stored = document.toStored()
            val filtered = file.documents.filterNot { it.uri == document.uri }
            val merged = (listOf(stored) + filtered)
                .take(MAX_RECENT_DOCUMENTS)
                .sortedByDescending { it.lastPrintedAtMillis ?: it.addedAtMillis }
            file.copy(documents = merged)
        }
    }

    override suspend fun touchPrinted(documentUris: List<String>) {
        if (documentUris.isEmpty()) {
            return
        }
        val timestamp = System.currentTimeMillis()
        store.update { file ->
            val targets = documentUris.toSet()
            val updated = file.documents
                .map { stored ->
                    if (stored.uri in targets) stored.copy(lastPrintedAtMillis = timestamp) else stored
                }
                .sortedByDescending { it.lastPrintedAtMillis ?: it.addedAtMillis }
            file.copy(documents = updated)
        }
    }

    override suspend fun setFavorite(uri: String, favorite: Boolean) {
        store.update { file ->
            val existing = file.documents
            val updated = if (existing.any { it.uri == uri }) {
                existing.map { if (it.uri == uri) it.copy(isFavorite = favorite) else it }
            } else {
                val fallbackName = uri.substringAfterLast('/').ifBlank { "Document" }
                val created = StoredDocument(
                    uri = uri,
                    displayName = fallbackName,
                    sizeBytes = 0,
                    mimeType = DocumentFormat.Unknown.mimeType,
                    extension = fallbackName.substringAfterLast('.', ""),
                    formatKind = DocumentKind.UNKNOWN.name,
                    addedAtMillis = System.currentTimeMillis(),
                    isFavorite = favorite,
                )
                (listOf(created) + existing).take(MAX_RECENT_DOCUMENTS)
            }
            file.copy(documents = updated)
        }
    }

    override suspend fun removeRecent(uri: String) {
        store.update { file -> file.copy(documents = file.documents.filterNot { it.uri == uri }) }
    }

    override suspend fun clearRecents() {
        store.update { file -> file.copy(documents = file.documents.filter { it.isFavorite }) }
    }

    override suspend fun persistPermission(uri: String): Boolean = withContext(dispatchers.io) {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return@withContext false
        if (parsed.scheme != ContentResolverSchemes.CONTENT) {
            return@withContext parsed.scheme == ContentResolverSchemes.FILE
        }
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(parsed, flags)
            true
        } catch (error: SecurityException) {
            AppLog.warn(AppLog.throwableTag("persistPermission", error))
            false
        } catch (error: UnsupportedOperationException) {
            AppLog.debug("Persistable permission unsupported for $uri", error)
            false
        }
    }

    override suspend fun hasPermission(uri: String): Boolean = withContext(dispatchers.io) {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return@withContext false
        if (parsed.scheme == ContentResolverSchemes.FILE) {
            return@withContext runCatching { parsed.path?.let { File(it).canRead() } ?: false }.getOrDefault(false)
        }
        try {
            context.contentResolver.openInputStream(parsed)?.use { true } ?: false
        } catch (error: SecurityException) {
            AppLog.debug(AppLog.throwableTag("permissionProbe", error))
            false
        } catch (error: FileNotFoundException) {
            AppLog.debug(AppLog.throwableTag("permissionProbe", error))
            false
        } catch (error: IOException) {
            AppLog.debug(AppLog.throwableTag("permissionProbe", error))
            false
        }
    }

    override suspend fun validate(uri: String): Boolean = withContext(dispatchers.io) {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return@withContext false
        if (parsed.scheme == ContentResolverSchemes.FILE) {
            return@withContext runCatching {
                parsed.path?.let { File(it).let { file -> file.exists() && file.canRead() } } ?: false
            }.getOrDefault(false)
        }
        try {
            context.contentResolver.openFileDescriptor(parsed, "r")?.use { descriptor ->
                descriptor.statSize >= 0
            } ?: false
        } catch (error: Exception) {
            AppLog.warn(AppLog.throwableTag("validate", error))
            false
        }
    }

    override suspend fun pageCount(uri: String): Int? = withContext(dispatchers.io) {
        pageCounter.pageCount(uri)
    }

    private fun buildDocument(uri: Uri, metadata: DocumentMetadata): PrintDocument {
        val name = metadata.displayName ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Document"
        val extension = name.substringAfterLast('.', "").lowercase()
        val mimeType = metadata.mimeType?.lowercase() ?: DocumentFormat.Unknown.mimeType
        val format = DocumentFormat.fromExtension(extension, mimeType)
        val availability = when (metadata.state) {
            MetadataState.FOUND -> DocumentAvailability.AVAILABLE
            MetadataState.MISSING -> DocumentAvailability.MISSING
            MetadataState.UNREADABLE -> DocumentAvailability.UNREADABLE
        }
        return PrintDocument(
            uri = uri.toString(),
            displayName = name,
            sizeBytes = metadata.sizeBytes,
            mimeType = mimeType,
            format = format,
            addedAtMillis = System.currentTimeMillis(),
            availability = availability,
        )
    }

    private fun readMetadata(uri: Uri): DocumentMetadata {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(uri, projection, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                DocumentMetadata(
                    displayName = nameIndex.takeIf { it >= 0 }?.let { cursor.getString(it) },
                    sizeBytes = sizeIndex.takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: -1L,
                    mimeType = context.contentResolver.getType(uri),
                    state = MetadataState.FOUND,
                )
            } else {
                fallbackMetadata(uri)
            }
        } catch (error: SecurityException) {
            AppLog.warn(AppLog.throwableTag("readMetadata", error))
            DocumentMetadata(displayName = null, sizeBytes = -1L, mimeType = null, state = MetadataState.UNREADABLE)
        } catch (error: IllegalArgumentException) {
            AppLog.warn(AppLog.throwableTag("readMetadataUri", error))
            fallbackMetadata(uri)
        } catch (error: Exception) {
            AppLog.error(AppLog.throwableTag("readMetadata", error))
            fallbackMetadata(uri)
        } finally {
            cursor?.close()
        }
    }

    private fun fallbackMetadata(uri: Uri): DocumentMetadata {
        if (uri.scheme == ContentResolverSchemes.FILE) {
            val file = uri.path?.let { File(it) }
            return when {
                file == null -> DocumentMetadata(null, -1L, null, MetadataState.MISSING)
                file.exists() && file.canRead() -> DocumentMetadata(
                    displayName = file.name,
                    sizeBytes = file.length(),
                    mimeType = null,
                    state = MetadataState.FOUND,
                )
                else -> DocumentMetadata(displayName = file.name, sizeBytes = -1L, mimeType = null, state = MetadataState.MISSING)
            }
        }
        if (DocumentsContract.isDocumentUri(context, uri)) {
            val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            val name = documentId?.substringAfterLast(':')
            val mimeType = context.contentResolver.getType(uri)
            return DocumentMetadata(displayName = name, sizeBytes = -1L, mimeType = mimeType, state = MetadataState.FOUND)
        }
        return DocumentMetadata(
            displayName = uri.lastPathSegment?.substringAfterLast('/'),
            sizeBytes = -1L,
            mimeType = context.contentResolver.getType(uri),
            state = MetadataState.FOUND,
        )
    }

    private fun resolveLegacyPath(uri: Uri): String? {
        val path = uri.path ?: return null
        val file = File(path)
        if (!file.exists()) {
            return null
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Files.getContentUri("external")
        }
        val selection = "${MediaStore.MediaColumns.DATA} = ?"
        return try {
            context.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), selection, arrayOf(path), null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        ContentUris.withAppendedId(collection, cursor.getLong(0)).toString()
                    } else {
                        uri.toString()
                    }
                } ?: uri.toString()
        } catch (error: Exception) {
            AppLog.warn(AppLog.throwableTag("resolveLegacyPath", error))
            uri.toString()
        }
    }

    private fun List<StoredDocument>.toPrintDocuments(): List<PrintDocument> = map { stored ->
        PrintDocument(
            uri = stored.uri,
            displayName = stored.displayName,
            sizeBytes = stored.sizeBytes,
            mimeType = stored.mimeType,
            format = DocumentFormat(DocumentKind.valueOf(stored.formatKind), stored.extension, stored.mimeType),
            addedAtMillis = stored.addedAtMillis,
            lastPrintedAtMillis = stored.lastPrintedAtMillis,
            isFavorite = stored.isFavorite,
            availability = DocumentAvailability.AVAILABLE,
            detectedPageCount = stored.detectedPageCount,
        )
    }

    private fun PrintDocument.toStored(): StoredDocument = StoredDocument(
        uri = uri,
        displayName = displayName,
        sizeBytes = sizeBytes,
        mimeType = mimeType,
        extension = format.extension,
        formatKind = format.kind.name,
        addedAtMillis = addedAtMillis,
        lastPrintedAtMillis = lastPrintedAtMillis,
        isFavorite = isFavorite,
        detectedPageCount = detectedPageCount,
    )
}

private data class DocumentMetadata(
    val displayName: String?,
    val sizeBytes: Long,
    val mimeType: String?,
    val state: MetadataState,
)

private enum class MetadataState {
    FOUND,
    MISSING,
    UNREADABLE,
}

private object ContentResolverSchemes {
    const val CONTENT = "content"
    const val FILE = "file"
}

private const val MAX_RECENT_DOCUMENTS = 200
