package com.dosprint.data.render

import android.graphics.Bitmap
import com.dosprint.core.AppLog

class PreviewBitmapCache(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) {
    private val entries = LinkedHashMap<String, Bitmap>(0, LOAD_FACTOR, true)

    @Synchronized
    fun get(key: String): Bitmap? = entries[key]?.takeIf { !it.isRecycled }

    @Synchronized
    fun put(key: String, bitmap: Bitmap) {
        if (entries.size >= maxEntries) {
            val oldestKey = entries.keys.firstOrNull()
            if (oldestKey != null && oldestKey != key) {
                entries.remove(oldestKey)?.recycleIfUnused()
            }
        }
        entries[key] = bitmap
    }

    @Synchronized
    fun clear() {
        entries.values.forEach { it.recycleIfUnused() }
        entries.clear()
    }

    @Synchronized
    fun size(): Int = entries.size

    private fun Bitmap.recycleIfUnused() {
        if (!isRecycled && !isDirtyFree()) {
            recycle()
        }
    }

    private fun Bitmap.isDirtyFree(): Boolean = try {
        width == 0 || height == 0
    } catch (error: Exception) {
        AppLog.warn(AppLog.throwableTag("bitmapProbe", error))
        false
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 12
        const val LOAD_FACTOR = 0.75f
    }
}
